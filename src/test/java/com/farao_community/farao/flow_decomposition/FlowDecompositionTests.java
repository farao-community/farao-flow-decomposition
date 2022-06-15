/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.farao_community.farao.commons.EICode;
import com.farao_community.farao.data.refprog.reference_program.ReferenceProgram;
import com.farao_community.farao.data.refprog.reference_program.ReferenceProgramBuilder;
import com.powsybl.commons.datasource.FileDataSource;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.iidm.export.Exporters;
import com.powsybl.iidm.import_.Importers;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.sensi.OpenSensitivityAnalysisProvider;
import com.powsybl.sensitivity.*;
import org.junit.jupiter.api.Test;
//import com.powsybl.openloadflow.network.FourBusNetworkFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
class FlowDecompositionTests {
    private static final double EPSILON = 1e-3;

    @Test
    void checkThatLossesCompensationIsIntegratedInNetworkToSendingEndOfBranch() {
        String networkFileName = "NETWORK_SINGLE_LOAD_TWO_GENERATORS.uct";
        Network singleLoadTwoGeneratorsNetwork = Importers.loadNetwork(networkFileName, getClass().getResourceAsStream(networkFileName));
        Branch<?> branchWithGeneratorOnTerminal1 = singleLoadTwoGeneratorsNetwork.getBranch("FGEN1 11 BLOAD 11 1");
        Branch<?> branchWithGeneratorOnTerminal2 = singleLoadTwoGeneratorsNetwork.getBranch("BLOAD 11 FGEN2 11 1");
        Generator generatorFromBranch1 = singleLoadTwoGeneratorsNetwork.getGenerator("FGEN1 11_generator");
        Generator generatorFromBranch2 = singleLoadTwoGeneratorsNetwork.getGenerator("FGEN2 11_generator");
        Load CentralLoad = singleLoadTwoGeneratorsNetwork.getLoad("BLOAD 11_load");
        double targetV = generatorFromBranch1.getTargetV();
        double targetP = generatorFromBranch1.getTargetP();
        double expectedI = targetP / targetV;
        double expectedLossesOnASingleLine = ((Line) branchWithGeneratorOnTerminal1).getR() * expectedI * expectedI;
        LoadFlowParameters dcLoadFlowParameters = LoadFlowParameters.load();
        dcLoadFlowParameters.setDc(true);
        //dcLoadFlowParameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P); // default
        LoadFlow.run(singleLoadTwoGeneratorsNetwork, dcLoadFlowParameters);
        assertEquals(targetP, branchWithGeneratorOnTerminal1.getTerminal1().getP(), EPSILON);
        assertEquals(targetP, branchWithGeneratorOnTerminal2.getTerminal2().getP(), EPSILON);
        assertEquals(-targetP, generatorFromBranch1.getTerminal().getP(), EPSILON);
        assertEquals(-targetP, generatorFromBranch2.getTerminal().getP(), EPSILON);
        //assertEquals(targetP * 2+1, CentralLoad.getTerminal().getP(), EPSILON);

        LossesCompensationEngine engine = new LossesCompensationEngine(dcLoadFlowParameters);
        engine.compensateLosses(singleLoadTwoGeneratorsNetwork);

        Load lossesLoadFromBranch1 = singleLoadTwoGeneratorsNetwork.getLoad("LOSSES FGEN1 11 BLOAD 11 1");
        assertNotNull(lossesLoadFromBranch1);
        assertEquals("FGEN1 ", lossesLoadFromBranch1.getTerminal().getVoltageLevel().getSubstation().get().getId());
        assertEquals(expectedLossesOnASingleLine, lossesLoadFromBranch1.getP0(), EPSILON);
        Load lossesLoadFromBranch2 = singleLoadTwoGeneratorsNetwork.getLoad("LOSSES BLOAD 11 FGEN2 11 1");
        assertNotNull(lossesLoadFromBranch2);
        assertEquals("FGEN2 ", lossesLoadFromBranch2.getTerminal().getVoltageLevel().getSubstation().get().getId());
        assertEquals(expectedLossesOnASingleLine, lossesLoadFromBranch2.getP0(), EPSILON);
        //LoadFlow.run(singleLoadTwoGeneratorsNetwork, dcLoadFlowParameters);
        //assertEquals(targetP, branchWithGeneratorOnTerminal1.getTerminal1().getP(), EPSILON);
        //assertEquals(targetP, branchWithGeneratorOnTerminal2.getTerminal2().getP(), EPSILON);
        //assertEquals(-targetP - expectedLossesOnASingleLine, generatorFromBranch1.getTerminal().getP(), EPSILON);
        //assertEquals(-targetP - expectedLossesOnASingleLine, generatorFromBranch2.getTerminal().getP(), EPSILON);
        //assertEquals(targetP * 2, CentralLoad.getTerminal().getP(), EPSILON);
    }

    @Test
    void checkThatLossesCompensationIsIntegratedInNetworkToSendingEndOfBranchWithLoadBalance() {
        String networkFileName = "NETWORK_SINGLE_LOAD_TWO_GENERATORS.uct";
        Network singleLoadTwoGeneratorsNetwork = Importers.loadNetwork(networkFileName, getClass().getResourceAsStream(networkFileName));
        Branch<?> branchWithGeneratorOnTerminal1 = singleLoadTwoGeneratorsNetwork.getBranch("FGEN1 11 FLOAD 11 1");
        Branch<?> branchWithGeneratorOnTerminal2 = singleLoadTwoGeneratorsNetwork.getBranch("FLOAD 11 FGEN2 11 1");
        Generator generatorFromBranch1 = singleLoadTwoGeneratorsNetwork.getGenerator("FGEN1 11_generator");
        Generator generatorFromBranch2 = singleLoadTwoGeneratorsNetwork.getGenerator("FGEN2 11_generator");
        Load CentralLoad = singleLoadTwoGeneratorsNetwork.getLoad("FLOAD 11_load");
        double targetV = generatorFromBranch1.getTargetV();
        double targetP = generatorFromBranch1.getTargetP();
        double expectedI = targetP / targetV;
        double expectedLossesOnASingleLine = ((Line) branchWithGeneratorOnTerminal1).getR() * expectedI * expectedI;
        LoadFlowParameters dcLoadFlowParameters = LoadFlowParameters.load();
        dcLoadFlowParameters.setDc(true);
        dcLoadFlowParameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        LoadFlow.run(singleLoadTwoGeneratorsNetwork, dcLoadFlowParameters);
        assertEquals(targetP, branchWithGeneratorOnTerminal1.getTerminal1().getP(), EPSILON);
        assertEquals(targetP, branchWithGeneratorOnTerminal2.getTerminal2().getP(), EPSILON);
        assertEquals(-targetP, generatorFromBranch1.getTerminal().getP(), EPSILON);
        assertEquals(-targetP, generatorFromBranch2.getTerminal().getP(), EPSILON);
        assertEquals(targetP * 2, CentralLoad.getTerminal().getP(), EPSILON);

        LossesCompensationEngine engine = new LossesCompensationEngine(dcLoadFlowParameters);
        engine.compensateLosses(singleLoadTwoGeneratorsNetwork);

        Load lossesLoadFromBranch1 = singleLoadTwoGeneratorsNetwork.getLoad("LOSSES FGEN1 11 FLOAD 11 1");
        assertNotNull(lossesLoadFromBranch1);
        assertEquals(expectedLossesOnASingleLine, lossesLoadFromBranch1.getP0(), EPSILON);
        Load lossesLoadFromBranch2 = singleLoadTwoGeneratorsNetwork.getLoad("LOSSES FLOAD 11 FGEN2 11 1");
        assertNotNull(lossesLoadFromBranch2);
        assertEquals(expectedLossesOnASingleLine, lossesLoadFromBranch2.getP0(), EPSILON);
        LoadFlow.run(singleLoadTwoGeneratorsNetwork, dcLoadFlowParameters);
        assertEquals(targetP - expectedLossesOnASingleLine, branchWithGeneratorOnTerminal1.getTerminal1().getP(), EPSILON);
        assertEquals(targetP - expectedLossesOnASingleLine, branchWithGeneratorOnTerminal2.getTerminal2().getP(), EPSILON);
        assertEquals(-targetP, generatorFromBranch1.getTerminal().getP(), EPSILON);
        assertEquals(-targetP, generatorFromBranch2.getTerminal().getP(), EPSILON);
        assertEquals((targetP - expectedLossesOnASingleLine) * 2, CentralLoad.getTerminal().getP(), EPSILON);
    }

    protected static SensitivityAnalysisParameters createParameters(boolean dc, String slackBusId, boolean distributedSlack) {
        return createParameters(dc, List.of(slackBusId), distributedSlack);
    }

    protected static SensitivityAnalysisParameters createParameters(boolean dc, List<String> slackBusesIds, boolean distributedSlack) {
        SensitivityAnalysisParameters sensiParameters = new SensitivityAnalysisParameters();
        LoadFlowParameters lfParameters = sensiParameters.getLoadFlowParameters();
        lfParameters.setDc(dc)
            .setDistributedSlack(distributedSlack);
        OpenLoadFlowParameters.create(lfParameters)
            .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
            .setSlackBusesIds(slackBusesIds);
        return sensiParameters;
    }

    protected static SensitivityFactor createBranchFlowPerInjectionIncrease(String functionId, String variableId, String contingencyId, Branch.Side side) {
        SensitivityFunctionType ftype = side.equals(Branch.Side.ONE) ? SensitivityFunctionType.BRANCH_ACTIVE_POWER_1 : SensitivityFunctionType.BRANCH_ACTIVE_POWER_2;
        return new SensitivityFactor(ftype, functionId, SensitivityVariableType.INJECTION_ACTIVE_POWER, variableId, false, Objects.isNull(contingencyId) ? ContingencyContext.all() : ContingencyContext.specificContingency(contingencyId));
    }

    protected static <T extends Injection<T>> List<SensitivityFactor> createFactorMatrix(List<T> injections, List<Branch> branches, String contingencyId, Branch.Side side) {
        Objects.requireNonNull(injections);
        Objects.requireNonNull(branches);
        return injections.stream().flatMap(injection -> branches.stream().map(branch -> createBranchFlowPerInjectionIncrease(branch.getId(), injection.getId(), contingencyId, side))).collect(Collectors.toList());
    }

    protected static <T extends Injection<T>> List<SensitivityFactor> createFactorMatrix(List<T> injections, List<Branch> branches, Branch.Side side) {
        return createFactorMatrix(injections, branches, null, side);
    }

    protected static <T extends Injection<T>> List<SensitivityFactor> createFactorMatrix(List<T> injections, List<Branch> branches) {
        return createFactorMatrix(injections, branches, null, Branch.Side.ONE);
    }

    @Test
    void singleLoadNetworkSensitivityAnalysisAndFlowDecompositionTest() {
        //Network network = FourBusNetworkFactory.create();
        //runDcLf(network);
        String networkFileName = "NETWORK_SINGLE_LOAD_TWO_GENERATORS_WITH_COUNTRIES.uct";
        String gen1_id = "FGEN1 11_generator";
        String gen2_id = "BGEN2 11_generator";
        String line1_id = "FGEN1 11 BLOAD 11 1";
        String line2_id = "BLOAD 11 BGEN2 11 1";
        String load1_id = "BLOAD 11_load";
        String contingency1_id = "FGEN1 11 BLOAD 11 1_contingency";
        String contingency2_id = "BLOAD 11 BGEN2 11 1_contingency";
        String slackBus_id = "FGEN1 1_0";
        slackBus_id = "BLOAD 1_0";
        Network network = Importers.loadNetwork(networkFileName, getClass().getResourceAsStream(networkFileName));
        Exporters.export("XIIDM", network, new Properties(), new FileDataSource(Path.of("/tmp"), "000-LF_init"));
        LoadFlowParameters dcLoadFlowParameters = LoadFlowParameters.load();
        dcLoadFlowParameters.setDc(true);
        LoadFlow.run(network, dcLoadFlowParameters);

        List injectionList = Stream.concat(network.getLoadStream(), network.getGeneratorStream()).collect(Collectors.toList());
        List branchList = network.getBranchStream().collect(Collectors.toList());
        SensitivityAnalysisParameters sensiParameters = createParameters(true, slackBus_id, true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<Contingency> contingencies = Collections.emptyList();
        //contingencies = List.of(new Contingency(contingency1_id, new BranchContingency(line1_id)), new Contingency(contingency2_id, new BranchContingency(line2_id)));
        List<SensitivityFactor> factors = createFactorMatrix(injectionList, branchList);
        DenseMatrixFactory matrixFactory = new DenseMatrixFactory();
        OpenSensitivityAnalysisProvider sensiProvider = new OpenSensitivityAnalysisProvider(matrixFactory);
        SensitivityAnalysis.Runner sensiRunner = new SensitivityAnalysis.Runner(sensiProvider);
        SensitivityAnalysisResult sensiResult = sensiRunner.run(network, factors, contingencies, Collections.singletonList(new SensitivityVariableSet("variablePST", Collections.singletonList(new WeightedSensitivityVariable(line2_id, 1.0)))), sensiParameters);

        System.out.println(sensiResult.getValues());
        assertEquals(6, sensiResult.getPreContingencyValues().size());
        assertEquals(+0.5d, sensiResult.getBranchFlow1SensitivityValue(gen1_id, line1_id), EPSILON);
        assertEquals(+0.5d, sensiResult.getBranchFlow1SensitivityValue(gen1_id, line2_id), EPSILON);
        assertEquals(-0.5d, sensiResult.getBranchFlow1SensitivityValue(gen2_id, line1_id), EPSILON);
        assertEquals(-0.5d, sensiResult.getBranchFlow1SensitivityValue(gen2_id, line2_id), EPSILON);
        assertEquals(-0.5d, sensiResult.getBranchFlow1SensitivityValue(load1_id, line1_id), EPSILON);
        assertEquals(+0.5d, sensiResult.getBranchFlow1SensitivityValue(load1_id, line2_id), EPSILON);
        assertEquals(+100d, sensiResult.getBranchFlow1FunctionReferenceValue(line1_id), EPSILON);
        assertEquals(-100d, sensiResult.getBranchFlow1FunctionReferenceValue(line2_id), EPSILON);

        if (contingencies.size() != 0) {
            assertEquals(6, sensiResult.getValues(contingency1_id).size()); // disconnect slack bus  if slack bus is gen1 ?!
            assertEquals(+0.0d, sensiResult.getBranchFlow1SensitivityValue(contingency1_id, gen1_id, line1_id), EPSILON);
            assertEquals(+0.0d, sensiResult.getBranchFlow1SensitivityValue(contingency1_id, gen1_id, line2_id), EPSILON);
            assertEquals(+0.0d, sensiResult.getBranchFlow1SensitivityValue(contingency1_id, gen2_id, line1_id), EPSILON);
            assertEquals(-0.0d, sensiResult.getBranchFlow1SensitivityValue(contingency1_id, gen2_id, line2_id), EPSILON);
            assertEquals(+0.0d, sensiResult.getBranchFlow1SensitivityValue(contingency1_id, load1_id, line1_id), EPSILON);
            assertEquals(+1.0d, sensiResult.getBranchFlow1SensitivityValue(contingency1_id, load1_id, line2_id), EPSILON);
            assertEquals(+0.0d, sensiResult.getBranchFlow1FunctionReferenceValue(contingency1_id, line1_id), EPSILON);
            assertEquals(-200d, sensiResult.getBranchFlow1FunctionReferenceValue(contingency1_id, line2_id), EPSILON);

            assertEquals(6, sensiResult.getValues(contingency2_id).size());
            assertEquals(+0.0d, sensiResult.getBranchFlow1SensitivityValue(contingency2_id, gen1_id, line1_id), EPSILON);
            assertEquals(+0.0d, sensiResult.getBranchFlow1SensitivityValue(contingency2_id, gen1_id, line2_id), EPSILON);
            assertEquals(+0.0d, sensiResult.getBranchFlow1SensitivityValue(contingency2_id, gen2_id, line1_id), EPSILON);
            assertEquals(+0.0d, sensiResult.getBranchFlow1SensitivityValue(contingency2_id, gen2_id, line2_id), EPSILON);
            assertEquals(-1.0d, sensiResult.getBranchFlow1SensitivityValue(contingency2_id, load1_id, line1_id), EPSILON);
            assertEquals(+0.0d, sensiResult.getBranchFlow1SensitivityValue(contingency2_id, load1_id, line2_id), EPSILON);
            assertEquals(+200d, sensiResult.getBranchFlow1FunctionReferenceValue(contingency2_id, line1_id), EPSILON);
            assertEquals(-0.0d, sensiResult.getBranchFlow1FunctionReferenceValue(contingency2_id, line2_id), EPSILON);
        }

        ReferenceProgram referenceProgram = ReferenceProgramBuilder.buildReferenceProgram(network, "default-impl-name", dcLoadFlowParameters);
        System.out.println(referenceProgram.getAllGlobalNetPositions());
        EICode eiCodeFrance = new EICode(Country.FR);
        EICode eiCodeBelgium = new EICode(Country.BE);
        System.out.println(eiCodeFrance);
        // Comment lister les pays présents dans un network ?
        assertEquals(+100d, referenceProgram.getGlobalNetPosition(eiCodeFrance), EPSILON);
        assertEquals(-100d, referenceProgram.getGlobalNetPosition(eiCodeBelgium), EPSILON);

        var nodalInjectionForAllocatedFlowGen1 = referenceProgram.getGlobalNetPosition(eiCodeFrance) * 1.0; // GSK = 1
        var nodalInjectionForAllocatedFlowGen2 = referenceProgram.getGlobalNetPosition(eiCodeBelgium) * 1.0; // GSK = 1
        var nodalInjectionForAllocatedFlowLoad = referenceProgram.getGlobalNetPosition(eiCodeBelgium) * 0.0; // GSK = 0
        var allocatedFlowFr = nodalInjectionForAllocatedFlowGen1 * sensiResult.getBranchFlow1SensitivityValue(gen1_id, line1_id); // "node to hub" PTDF ???

        // Comment avoir le réference flow ?
    }

    @Test
    void decomposeFlowTest() {
        String networkFileName = "NETWORK_SINGLE_LOAD_TWO_GENERATORS_WITH_COUNTRIES.uct";
        String gen1_id = "FGEN1 11_generator";
        String gen2_id = "BGEN2 11_generator";
        String line1_id = "FGEN1 11 BLOAD 11 1";
        String line2_id = "BLOAD 11 BGEN2 11 1";
        String load1_id = "BLOAD 11_load";

        Network network = Importers.loadNetwork(networkFileName, getClass().getResourceAsStream(networkFileName));
        Exporters.export("XIIDM", network, new Properties(), new FileDataSource(Path.of("/tmp"), "000-init"));

        OpenSensitivityAnalysisProvider sensiProvider = new OpenSensitivityAnalysisProvider();
        SensitivityAnalysis.Runner sensiRunner = new SensitivityAnalysis.Runner(sensiProvider);

        List injectionList = Stream.concat(network.getLoadStream(), network.getGeneratorStream()).collect(Collectors.toList());
        List branchList = network.getBranchStream().collect(Collectors.toList());
        List<SensitivityFactor> factors = createFactorMatrix(injectionList, branchList);

        SensitivityAnalysisParameters sensiParameters = new SensitivityAnalysisParameters();
        LoadFlowParameters lfParameters = sensiParameters.getLoadFlowParameters();
        lfParameters.setDc(true);

        SensitivityAnalysisResult sensiResult = sensiRunner.run(network, factors, sensiParameters);
        Exporters.export("XIIDM", network, new Properties(), new FileDataSource(Path.of("/tmp"), "001-afterSensi"));

        System.out.println(sensiResult.getValues());
        assertEquals(6, sensiResult.getPreContingencyValues().size());
        assertEquals(+0.5d, sensiResult.getBranchFlow1SensitivityValue(gen1_id, line1_id), EPSILON);
        assertEquals(+0.5d, sensiResult.getBranchFlow1SensitivityValue(gen1_id, line2_id), EPSILON);
        assertEquals(-0.5d, sensiResult.getBranchFlow1SensitivityValue(gen2_id, line1_id), EPSILON);
        assertEquals(-0.5d, sensiResult.getBranchFlow1SensitivityValue(gen2_id, line2_id), EPSILON);
        assertEquals(-0.5d, sensiResult.getBranchFlow1SensitivityValue(load1_id, line1_id), EPSILON);
        assertEquals(+0.5d, sensiResult.getBranchFlow1SensitivityValue(load1_id, line2_id), EPSILON);
        assertEquals(+100d, sensiResult.getBranchFlow1FunctionReferenceValue(line1_id), EPSILON);
        assertEquals(-100d, sensiResult.getBranchFlow1FunctionReferenceValue(line2_id), EPSILON);

        //assertEquals();

    }

    @Test
    void decomposeLoopFlowTest() {
        String networkFileName = "NETWORK_WITH_COUNTRIES_LOOP_FLOW.uct";
        String fgen_id = "FGEN  11_generator";
        String line1_id = "FGEN  11 BLOAD 11 1";
        String line2_id = "BLOAD 11 FLOAD 11 1";
        String bload_id = "BLOAD 11_load";
        String fload_id = "FLOAD 11_load";

        Network network = Importers.loadNetwork(networkFileName, getClass().getResourceAsStream(networkFileName));
        Exporters.export("XIIDM", network, new Properties(), new FileDataSource(Path.of("/tmp"), "000-init"));

        OpenSensitivityAnalysisProvider sensiProvider = new OpenSensitivityAnalysisProvider();
        SensitivityAnalysis.Runner sensiRunner = new SensitivityAnalysis.Runner(sensiProvider);

        List injectionList = Stream.concat(network.getLoadStream(), network.getGeneratorStream()).collect(Collectors.toList());
        List branchList = network.getBranchStream().collect(Collectors.toList());
        List<SensitivityFactor> factors = createFactorMatrix(injectionList, branchList);

        SensitivityAnalysisParameters sensiParameters = new SensitivityAnalysisParameters();
        LoadFlowParameters lfParameters = sensiParameters.getLoadFlowParameters();
        lfParameters.setDc(true);

        SensitivityAnalysisResult sensiResult = sensiRunner.run(network, factors, sensiParameters);
        Exporters.export("XIIDM", network, new Properties(), new FileDataSource(Path.of("/tmp"), "001-afterSensi"));

        System.out.println(sensiResult.getValues());
        assertEquals(4, sensiResult.getPreContingencyValues().size());
        assertEquals(+0.0d, sensiResult.getBranchFlow1SensitivityValue(fgen_id, line1_id), EPSILON);
        assertEquals(+0.0d, sensiResult.getBranchFlow1SensitivityValue(fgen_id, line2_id), EPSILON);
        //assertEquals(-1.0d, sensiResult.getBranchFlow1SensitivityValue(bload_id, line1_id), EPSILON);
        //assertEquals(-0.0d, sensiResult.getBranchFlow1SensitivityValue(bload_id, line2_id), EPSILON);
        assertEquals(-1.0d, sensiResult.getBranchFlow1SensitivityValue(fload_id, line1_id), EPSILON);
        assertEquals(-1.0d, sensiResult.getBranchFlow1SensitivityValue(fload_id, line2_id), EPSILON);
        assertEquals(+100d, sensiResult.getBranchFlow1FunctionReferenceValue(line1_id), EPSILON);
        assertEquals(+100d, sensiResult.getBranchFlow1FunctionReferenceValue(line2_id), EPSILON);

        //assertEquals();

    }

    @Test
    void decomposeLoopFlowOutsideCoreTest() {
        String networkFileName = "NETWORK_WITH_OUTSIDE_CORE_COUNTRIES_LOOP_FLOW.uct";
        String gb_gen_id = "5GEN  11_generator";
        String fr_gen_id = "FGEN  11_generator";
        String be_gen_id = "BGEN  11_generator";
        String line1_id = "5GEN  11 FGEN  11 1";
        String line2_id = "FGEN  11 BGEN  11 1";
        String line3_id = "BGEN  11 BLOAD 11 1";
        String line4_id = "BLOAD 11 FLOAD 11 1";
        String line5_id = "FLOAD 11 5LOAD 11 1";
        String be_load_id = "BLOAD 11_load";
        String fr_load_id = "FLOAD 11_load";
        String gb_load_id = "5LOAD 11_load";

        Network network = Importers.loadNetwork(networkFileName, getClass().getResourceAsStream(networkFileName));
        Exporters.export("XIIDM", network, new Properties(), new FileDataSource(Path.of("/tmp"), "000-init"));

        OpenSensitivityAnalysisProvider sensiProvider = new OpenSensitivityAnalysisProvider();
        SensitivityAnalysis.Runner sensiRunner = new SensitivityAnalysis.Runner(sensiProvider);

        List injectionList = Stream.concat(network.getLoadStream(), network.getGeneratorStream()).collect(Collectors.toList());
        List branchList = network.getBranchStream().collect(Collectors.toList());
        List<SensitivityFactor> factors = createFactorMatrix(injectionList, branchList);

        SensitivityAnalysisParameters sensiParameters = new SensitivityAnalysisParameters();
        LoadFlowParameters lfParameters = sensiParameters.getLoadFlowParameters();
        lfParameters.setDc(true);

        SensitivityAnalysisResult sensiResult = sensiRunner.run(network, factors, sensiParameters);
        Exporters.export("XIIDM", network, new Properties(), new FileDataSource(Path.of("/tmp"), "001-afterSensi"));

        System.out.println(sensiResult.getValues());
        assertEquals(30, sensiResult.getPreContingencyValues().size());
        assertEquals(+2/3d, sensiResult.getBranchFlow1SensitivityValue(gb_gen_id, line1_id), EPSILON);
        assertEquals(+1/3d, sensiResult.getBranchFlow1SensitivityValue(gb_gen_id, line2_id), EPSILON);
        assertEquals(+0.0d, sensiResult.getBranchFlow1SensitivityValue(gb_gen_id, line3_id), EPSILON);
        assertEquals(+0.0d, sensiResult.getBranchFlow1SensitivityValue(gb_gen_id, line4_id), EPSILON);
        assertEquals(+0.0d, sensiResult.getBranchFlow1SensitivityValue(gb_gen_id, line5_id), EPSILON);

        assertEquals(-1/3d, sensiResult.getBranchFlow1SensitivityValue(fr_gen_id, line1_id), EPSILON);
        assertEquals(+1/3d, sensiResult.getBranchFlow1SensitivityValue(fr_gen_id, line2_id), EPSILON);
        assertEquals(+0.0d, sensiResult.getBranchFlow1SensitivityValue(fr_gen_id, line3_id), EPSILON);
        assertEquals(+0.0d, sensiResult.getBranchFlow1SensitivityValue(fr_gen_id, line4_id), EPSILON);
        assertEquals(+0.0d, sensiResult.getBranchFlow1SensitivityValue(fr_gen_id, line5_id), EPSILON);

        assertEquals(-1/3d, sensiResult.getBranchFlow1SensitivityValue(be_gen_id, line1_id), EPSILON);
        assertEquals(-2/3d, sensiResult.getBranchFlow1SensitivityValue(be_gen_id, line2_id), EPSILON);
        assertEquals(+0.0d, sensiResult.getBranchFlow1SensitivityValue(be_gen_id, line3_id), EPSILON);
        assertEquals(+0.0d, sensiResult.getBranchFlow1SensitivityValue(be_gen_id, line4_id), EPSILON);
        assertEquals(+0.0d, sensiResult.getBranchFlow1SensitivityValue(be_gen_id, line5_id), EPSILON);

        assertEquals(-1/3d, sensiResult.getBranchFlow1SensitivityValue(be_load_id, line1_id), EPSILON);
        assertEquals(-2/3d, sensiResult.getBranchFlow1SensitivityValue(be_load_id, line2_id), EPSILON);
        assertEquals(-1.0d, sensiResult.getBranchFlow1SensitivityValue(be_load_id, line3_id), EPSILON);
        assertEquals(+0.0d, sensiResult.getBranchFlow1SensitivityValue(be_load_id, line4_id), EPSILON);
        assertEquals(+0.0d, sensiResult.getBranchFlow1SensitivityValue(be_load_id, line5_id), EPSILON);

        assertEquals(-1/3d, sensiResult.getBranchFlow1SensitivityValue(fr_load_id, line1_id), EPSILON);
        assertEquals(-2/3d, sensiResult.getBranchFlow1SensitivityValue(fr_load_id, line2_id), EPSILON);
        assertEquals(-1.0d, sensiResult.getBranchFlow1SensitivityValue(fr_load_id, line3_id), EPSILON);
        assertEquals(-1.0d, sensiResult.getBranchFlow1SensitivityValue(fr_load_id, line4_id), EPSILON);
        assertEquals(+0.0d, sensiResult.getBranchFlow1SensitivityValue(fr_load_id, line5_id), EPSILON);

        assertEquals(-1/3d, sensiResult.getBranchFlow1SensitivityValue(gb_load_id, line1_id), EPSILON);
        assertEquals(-2/3d, sensiResult.getBranchFlow1SensitivityValue(gb_load_id, line2_id), EPSILON);
        assertEquals(-1.0d, sensiResult.getBranchFlow1SensitivityValue(gb_load_id, line3_id), EPSILON);
        assertEquals(-1.0d, sensiResult.getBranchFlow1SensitivityValue(gb_load_id, line4_id), EPSILON);
        assertEquals(-1.0d, sensiResult.getBranchFlow1SensitivityValue(gb_load_id, line5_id), EPSILON);

        assertEquals(+100d, sensiResult.getBranchFlow1FunctionReferenceValue(line1_id), EPSILON);
        assertEquals(+200d, sensiResult.getBranchFlow1FunctionReferenceValue(line2_id), EPSILON);
        assertEquals(+300d, sensiResult.getBranchFlow1FunctionReferenceValue(line3_id), EPSILON);
        assertEquals(+200d, sensiResult.getBranchFlow1FunctionReferenceValue(line4_id), EPSILON);
        assertEquals(+100d, sensiResult.getBranchFlow1FunctionReferenceValue(line5_id), EPSILON);
    }

    @Test
    void decomposeLoopFlowWithCountriesTest() {
        String networkFileName = "NETWORK_WITH_COUNTRIES_PST.uct";
        String fgen_id = "FGEN  11_generator";
        String line1_id = "FGEN  11 BLOAD 11 1";
        String line2_id = "FGEN  11 BLOAD 11 2";
        String bload_id = "BLOAD 11_load";

        Network network = Importers.loadNetwork(networkFileName, getClass().getResourceAsStream(networkFileName));
        Exporters.export("XIIDM", network, new Properties(), new FileDataSource(Path.of("/tmp"), "000-init"));

        OpenSensitivityAnalysisProvider sensiProvider = new OpenSensitivityAnalysisProvider();
        SensitivityAnalysis.Runner sensiRunner = new SensitivityAnalysis.Runner(sensiProvider);

        //List injectionList = Stream.concat(network.getLoadStream(), network.getGeneratorStream()).collect(Collectors.toList());
        //List branchList = network.getBranchStream().collect(Collectors.toList());
        List<SensitivityFactor> factors = new ArrayList<>();//createFactorMatrix(injectionList, branchList);
        factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, line1_id, SensitivityVariableType.INJECTION_ACTIVE_POWER, fgen_id , false, ContingencyContext.none()));
        factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, line2_id, SensitivityVariableType.INJECTION_ACTIVE_POWER, fgen_id , false, ContingencyContext.none()));
        factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, line1_id, SensitivityVariableType.INJECTION_ACTIVE_POWER, bload_id, false, ContingencyContext.none()));
        factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, line2_id, SensitivityVariableType.INJECTION_ACTIVE_POWER, bload_id, false, ContingencyContext.none()));
        factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, line1_id, SensitivityVariableType.TRANSFORMER_PHASE     , line2_id, false, ContingencyContext.none()));
        factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, line2_id, SensitivityVariableType.TRANSFORMER_PHASE     , line2_id, false, ContingencyContext.none()));

        SensitivityAnalysisParameters sensiParameters = new SensitivityAnalysisParameters();
        LoadFlowParameters lfParameters = sensiParameters.getLoadFlowParameters();
        lfParameters.setDc(true);

        TwoWindingsTransformer twt = network.getTwoWindingsTransformer("FGEN  11 BLOAD 11 2");
        twt.getPhaseTapChanger().setTapPosition(twt.getPhaseTapChanger().getTapPosition()+1);

        SensitivityAnalysisResult sensiResult = sensiRunner.run(network, factors, sensiParameters);
        //SensitivityAnalysisResult sensiResult = sensiRunner.run(network, factors, Collections.emptyList(), Collections.singletonList(new SensitivityVariableSet("variablePST", Collections.singletonList(new WeightedSensitivityVariable(line2_id, 1.0)))), sensiParameters);
        Exporters.export("XIIDM", network, new Properties(), new FileDataSource(Path.of("/tmp"), "001-afterSensi"));

        System.out.println(sensiResult.getValues());
        assertEquals(6, sensiResult.getPreContingencyValues().size());
        assertEquals(+0.25d, sensiResult.getBranchFlow1SensitivityValue(fgen_id, line1_id), EPSILON);
        assertEquals(-0.25d, sensiResult.getBranchFlow1SensitivityValue(fgen_id, line2_id), EPSILON);
        assertEquals(-0.25d, sensiResult.getBranchFlow1SensitivityValue(bload_id, line1_id), EPSILON);
        assertEquals(+0.25d, sensiResult.getBranchFlow1SensitivityValue(bload_id, line2_id), EPSILON);
        assertEquals(+420.042573d, sensiResult.getBranchFlow1SensitivityValue(line2_id, line1_id), EPSILON);
        assertEquals(+420.042573d, sensiResult.getBranchFlow1SensitivityValue(line2_id, line2_id), EPSILON);
        var deltaAngle = twt.getPhaseTapChanger().getCurrentStep().getAlpha() - twt.getPhaseTapChanger().getNeutralStep().get().getAlpha(); // Attention Optional :(
        System.out.println(deltaAngle);
        assertEquals(+50d + 420.042573 * deltaAngle, sensiResult.getBranchFlow1FunctionReferenceValue(line1_id), EPSILON);
        assertEquals(-50d + 420.042573 * deltaAngle, sensiResult.getBranchFlow1FunctionReferenceValue(line2_id), EPSILON);

        //assertEquals();

    }

    @Test
    void flowPSTNetworkTest() {
        String networkFileName = "NETWORK_WITH_COUNTRIES_PST.uct";
        Network pstNetwork = Importers.loadNetwork(networkFileName, getClass().getResourceAsStream(networkFileName));
        Exporters.export("XIIDM", pstNetwork, new Properties(), new FileDataSource(Path.of("/tmp"), "000-PST_init"));
        TwoWindingsTransformer twt = pstNetwork.getTwoWindingsTransformer("FGEN  11 BLOAD 11 2");
        assertNotNull(twt);
        LoadFlowParameters dcLoadFlowParameters = LoadFlowParameters.load();
        dcLoadFlowParameters.setDc(true);
        LoadFlow.run(pstNetwork, dcLoadFlowParameters); // Fonctionne pas correctement ?
        Exporters.export("XIIDM", pstNetwork, new Properties(), new FileDataSource(Path.of("/tmp"), "001-PST_afterDCLoadFlow"));
        System.out.println(twt.getPhaseTapChanger().getTapPosition());
        System.out.println(twt.getPhaseTapChanger().getCurrentStep().getAlpha());
        twt.getPhaseTapChanger().setTapPosition(twt.getPhaseTapChanger().getTapPosition() + 1);
        System.out.println(twt.getPhaseTapChanger().getTapPosition());
        System.out.println(twt.getPhaseTapChanger().getCurrentStep().getAlpha() - twt.getPhaseTapChanger().getNeutralStep().get().getAlpha());
        LoadFlow.run(pstNetwork, dcLoadFlowParameters); // Fonctionne pas correctement ?
        Exporters.export("XIIDM", pstNetwork, new Properties(), new FileDataSource(Path.of("/tmp"), "002-PST_afterDCLoadFlow-tapChange"));
        System.out.println("plop");
    }
}
