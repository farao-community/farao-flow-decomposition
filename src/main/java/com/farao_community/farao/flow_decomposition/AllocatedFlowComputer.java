package com.farao_community.farao.flow_decomposition;

import com.farao_community.farao.commons.EICode;
import com.farao_community.farao.data.refprog.reference_program.ReferenceProgram;
import com.farao_community.farao.data.refprog.reference_program.ReferenceProgramBuilder;

import com.powsybl.contingency.ContingencyContext;
import com.powsybl.iidm.import_.Importers;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Injection;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import org.ejml.data.*;
import com.powsybl.openloadflow.sensi.OpenSensitivityAnalysisProvider;
import com.powsybl.sensitivity.*;
import org.ejml.simple.SimpleOperations;
import org.ejml.simple.ops.SimpleOperations_DDRM;
import org.ejml.simple.ops.SimpleOperations_DSCC;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AllocatedFlowComputer {
    private static final Country DEFAULTCOUNTRY = null;
    private static final String DEFAULTLOADFLOWPROVIDER = null;
    private static final double DEFAULTGLSK = 0.0;

    public Map<String, Double> run(String networkFileName) {
        // Network
        Network network = Importers.loadNetwork(networkFileName, getClass().getResourceAsStream(networkFileName));
        //Zone extraction
        Set<Country> setOfZones = network.getSubstationStream().map(substation -> substation.getCountry().orElse(DEFAULTCOUNTRY)).collect(Collectors.toSet());
        //GLSK computation: for each zone, count number of generator, assign them a coefficient
        Map<Country, List<String>> nodeInZoneMap = setOfZones.stream().collect(Collectors.toMap(Function.identity(), zone -> new ArrayList<>(), (z1, z2) -> z1));
        network.getGeneratorStream().forEach(generator -> nodeInZoneMap.get(generator.getRegulatingTerminal().getVoltageLevel().getSubstation().get().getCountry().orElse(DEFAULTCOUNTRY)).add(generator.getId()));
        // plus intelligent si on crée les clefs du map à la volée ? getOrDefault
        // attention si substation connectée ou dans la maille principale
        Map<Country, Double> totalPowerOfGeneratorsPerZone = new HashMap<>();
        nodeInZoneMap.forEach(((country, generators) -> totalPowerOfGeneratorsPerZone.put(country, generators.stream().map(generator -> network.getGenerator(generator).getTargetP()).reduce(0., Double::sum))));
        Map<String, Double> glskPerNode = new HashMap<>();
        nodeInZoneMap.forEach(((country, generators) -> {
            generators.forEach(generator -> glskPerNode.put(generator, network.getGenerator(generator).getTargetP() / totalPowerOfGeneratorsPerZone.get(country)));
        }));

        //Net Position extraction
        LoadFlowParameters dcLoadFlowParameters = LoadFlowParameters.load();
        dcLoadFlowParameters.setDc(true);
        ReferenceProgram referenceProgram = ReferenceProgramBuilder.buildReferenceProgram(network, DEFAULTLOADFLOWPROVIDER, dcLoadFlowParameters);
        Map<EICode, Double> netPosition = referenceProgram.getAllGlobalNetPositions();

        //XNEC extraction
        List<Branch> xnecList = network.getBranchStream().filter(branch -> branch.getTerminal1().getVoltageLevel().getSubstation().get().getCountry().orElse(DEFAULTCOUNTRY) != branch.getTerminal2().getVoltageLevel().getSubstation().get().getCountry().orElse(DEFAULTCOUNTRY)).collect(Collectors.toList()); // toujours plus long !
        // may stay as a Stream ?
        //DC PTDF Sensibility
        OpenSensitivityAnalysisProvider sensiProvider = new OpenSensitivityAnalysisProvider();
        SensitivityAnalysis.Runner sensiRunner = new SensitivityAnalysis.Runner(sensiProvider);
        SensitivityAnalysisParameters sensiParameters = new SensitivityAnalysisParameters();
        LoadFlowParameters lfParameters = sensiParameters.getLoadFlowParameters();
        lfParameters.setDc(true);
        List<SensitivityFactor> factors = new ArrayList<>();
        List<Injection> nodeList = Stream.concat(network.getLoadStream(), network.getGeneratorStream()).collect(Collectors.toList());
        nodeList.forEach(node -> xnecList.forEach(xnec -> factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, xnec.getId(), SensitivityVariableType.INJECTION_ACTIVE_POWER, node.getId(), false, ContingencyContext.none()))));
        SensitivityAnalysisResult sensiResult = sensiRunner.run(network, factors, sensiParameters);
        //Allocated flow in a Hash map
        //nodeInZoneMap

        double[] netPositionList = nodeList.stream().mapToDouble(injection -> (double) netPosition.get(new EICode(injection.getTerminal().getVoltageLevel().getSubstation().get().getCountry().orElse(DEFAULTCOUNTRY)))).toArray();
        Matrix netPositionMatrix = new DMatrixRMaj(nodeList.size(), 1, true, netPositionList);
        double[] glskList = nodeList.stream().mapToDouble(injection -> glskPerNode.getOrDefault(injection.getId(), DEFAULTGLSK)).toArray();
        Matrix glskMatrix = new DMatrixRMaj(nodeList.size(), 1, true, glskList);
        DMatrixRMaj nodalInjectionsMatrix = new DMatrixRMaj(nodeList.size(), 1);
        SimpleOperations simpleOperationsDdrm = new SimpleOperations_DDRM();
        simpleOperationsDdrm.elementMult(netPositionMatrix, glskMatrix, nodalInjectionsMatrix);
        DMatrixSparseCSC ptdfMatrix = new DMatrixSparseCSC(xnecList.size(), nodeList.size(), factors.size()+1);
        for (Iterator<SensitivityValue> iterator = sensiResult.getValues().iterator(); iterator.hasNext();) {
            SensitivityValue sensitivityValue = iterator.next();
            SensitivityFactor factor = factors.get(sensitivityValue.getFactorIndex());
            String xnecId = factor.getFunctionId();
            int xnecIndex = xnecList.indexOf(network.getBranch(xnecId));
            String nodeId = factor.getVariableId();
            int nodeIndex = nodeList.indexOf(Optional.ofNullable((Injection) network.getGenerator(nodeId)).orElse(network.getLoad(nodeId)));
            ptdfMatrix.set(xnecIndex, nodeIndex, sensitivityValue.getValue());
        }
        ptdfMatrix.print();
        SimpleOperations simpleOperationsDscc = new SimpleOperations_DSCC();
        DMatrixSparseCSC allocatedFlowsMatrix = new DMatrixSparseCSC(xnecList.size(), 1);
        DMatrixSparseCSC nodalInjectionsSparseMatrix = new DMatrixSparseCSC(nodeList.size(), 1);
        for (int row=0; row<nodalInjectionsMatrix.getNumRows(); row++) {
            for (int col=0; col<nodalInjectionsMatrix.getNumCols(); col++) {
                nodalInjectionsSparseMatrix.set(row, col, nodalInjectionsMatrix.get(row, col));
            }
        }
        simpleOperationsDscc.mult(ptdfMatrix, nodalInjectionsSparseMatrix, allocatedFlowsMatrix);
        allocatedFlowsMatrix.print();
        Map<String, Double> allocatedFlowMap = new HashMap<>();
        for (int row = 0; row<allocatedFlowsMatrix.getNumRows(); row++) {
            allocatedFlowMap.put(xnecList.get(row).getId(), allocatedFlowsMatrix.get(row, 0, 0.0));
        }
        return allocatedFlowMap;
    }
}
