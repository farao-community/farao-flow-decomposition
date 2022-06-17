/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.contingency.ContingencyContext;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import org.ejml.data.*;
import com.powsybl.openloadflow.sensi.OpenSensitivityAnalysisProvider;
import com.powsybl.sensitivity.*;
import org.ejml.ops.DConvertMatrixStruct;
import org.ejml.simple.SimpleOperations;
import org.ejml.simple.ops.SimpleOperations_DSCC;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
public class AllocatedFlowComputer {
    private static final double EPSILON = 1e-5;
    private static final double DEFAULT_GLSK_FACTOR = 0.0;
    private final LoadFlowParameters loadFlowParameters;
    private final SensitivityAnalysisParameters sensitivityAnalysisParameters;

    public AllocatedFlowComputer() {
        this.loadFlowParameters = initLoadFlowParameters();
        this.sensitivityAnalysisParameters = initSensitivityAnalysisParameters(loadFlowParameters);
    }

    private static SensitivityAnalysisParameters initSensitivityAnalysisParameters(LoadFlowParameters loadFlowParameters) {
        SensitivityAnalysisParameters parameters = SensitivityAnalysisParameters.load();
        parameters.setLoadFlowParameters(loadFlowParameters);
        return parameters;
    }

    private static LoadFlowParameters initLoadFlowParameters() {
        LoadFlowParameters parameters = LoadFlowParameters.load();
        parameters.setDc(true);
        return parameters;
    }

    private Optional<Country> getTerminalCountry(Terminal terminal) {
        return terminal.getVoltageLevel().getSubstation().get().getCountry();
    }

    private Country getInjectionCountry(Injection<?> injection) {
        return getTerminalCountry(injection.getTerminal()).orElse(null);
    }

    private Map<Country, Map<String, Double>> buildAutoGlsks(Network network) {
        Map<Country, Map<String, Double>> glsks = network.getCountries().stream().collect(Collectors.toMap(
                Function.identity(),
                country -> new HashMap<>()));
        network.getGeneratorStream()
                .filter(this::isValid)
                .forEach(generator -> {
                        Country generatorCountry = getInjectionCountry(generator);
                        glsks.get(generatorCountry).put(generator.getId(), generator.getTargetP());
                    }
                );
        glsks.forEach((country, glsk) -> {
            double glskSum = glsk.values().stream().mapToDouble(factor -> factor).sum();
            glsk.forEach((key, value) -> glsk.put(key, value / glskSum));
        });
        return glsks;
    }

    private List<Injection<?>> getAllNetworkInjections(Network network) {
        return network.getConnectableStream()
                .filter(Injection.class::isInstance)
                .map(connectable -> (Injection<?>) connectable)
                .filter(this::isValid)
                .collect(Collectors.toList());
    }

    private boolean isValid(Injection<?> injection) {
        return true;
    }

    private double getIndividualNodalInjectionForAllocatedFlows(Injection<?> injection, Map<Country, Map<String, Double>> glsks, Map<Country, Double> netPositions) {
        Country injectionCountry = getInjectionCountry(injection);
        return glsks.get(injectionCountry).getOrDefault(injection.getId(), DEFAULT_GLSK_FACTOR) * netPositions.get(injectionCountry);
    }

    private Map<Country, Double> getZonesNetPosition(Network network) {
        LoadFlow.run(network, loadFlowParameters);
        return new CountryNetPositionComputation(network).getNetPositions();
    }

    private Map<String, Double> getNodalInjectionsForAllocatedFlows(Network network, Map<Country, Map<String, Double>> glsks) {
        Map<Country, Double> netPositions = getZonesNetPosition(network);
        return getAllNetworkInjections(network)
                .stream()
                .collect(Collectors.toMap(
                        Injection::getId,
                        injection -> getIndividualNodalInjectionForAllocatedFlows(injection, glsks, netPositions)
                        )
                );
    }

    private DMatrixSparseCSC convertToMatrix(Map<String, Double> nodalInjections, Map<String, Integer> nodeIndex) {
        Map<String, Double> nonZeroInjections = nodalInjections.entrySet().stream()
                .filter(this::isNotZero)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
        ));
        DMatrixSparseTriplet work = new DMatrixSparseTriplet(nodalInjections.size(), 1, nonZeroInjections.size());
        nonZeroInjections.forEach(
                (injectionId, injectionValue) -> work.addItem(nodeIndex.get(injectionId), 0, injectionValue)
        );
        return DConvertMatrixStruct.convert(work, (DMatrixSparseCSC) null);
    }

    private boolean isNotZero(Map.Entry<String, Double> stringDoubleEntry) {
        return Math.abs(stringDoubleEntry.getValue()) > EPSILON;
    }

    private List<Branch> selectXnecs(Network network) {
        return network.getBranchStream()
                .filter(this::isAnInterconnection)
                .collect(Collectors.toList());
    }

    private Map<String, Integer> getXnecIndex(List<Branch> xnecList) {
        return IntStream.range(0, xnecList.size())
            .boxed()
            .collect(Collectors.toMap(
                i -> xnecList.get(i).getId(),
                Function.identity()
            ));
    }

    private boolean isAnInterconnection(Branch<?> branch) {
        Optional<Country> country1 = getTerminalCountry(branch.getTerminal1());
        Optional<Country> country2 = getTerminalCountry(branch.getTerminal2());
        return country1.isPresent() && country2.isPresent() && !country1.equals(country2);
    }

    private SensitivityFactor getSensitivityFactor(String node, Branch xnec) {
        return new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, xnec.getId(), SensitivityVariableType.INJECTION_ACTIVE_POWER, node, false, ContingencyContext.none());
    }

    private List<SensitivityFactor> getFactors(List<Branch> xnecList, List<String> nodeList) {
        List<SensitivityFactor> factors = new ArrayList<>();
        nodeList.forEach(
            node -> xnecList.forEach(
                xnec -> factors.add(getSensitivityFactor(node, xnec))));
        return factors;
    }

    private SensitivityAnalysisResult getSensitivityAnalysisResult(Network network, List<SensitivityFactor> factors) {
        OpenSensitivityAnalysisProvider sensiProvider = new OpenSensitivityAnalysisProvider();
        SensitivityAnalysis.Runner sensiRunner = new SensitivityAnalysis.Runner(sensiProvider);
        return sensiRunner.run(network, factors, sensitivityAnalysisParameters);
    }

    private DMatrixSparseTriplet getPtdfMatrixTriplet(Map<String, Integer> xnecIndex, Map<String, Integer> nodeIndex, List<SensitivityFactor> factors, SensitivityAnalysisResult sensiResult) {
        DMatrixSparseTriplet ptdfMatrixTriplet = new DMatrixSparseTriplet(xnecIndex.size(), nodeIndex.size(), factors.size() + 1);
        for (Iterator<SensitivityValue> iterator = sensiResult.getValues().iterator(); iterator.hasNext(); ) {
            SensitivityValue sensitivityValue = iterator.next();
            SensitivityFactor factor = factors.get(sensitivityValue.getFactorIndex());
            String xnecId = factor.getFunctionId();
            int xnecI = xnecIndex.get(xnecId);
            String nodeId = factor.getVariableId();
            int nodeI = nodeIndex.get(nodeId);
            ptdfMatrixTriplet.addItem(xnecI, nodeI, sensitivityValue.getValue());
        }
        return ptdfMatrixTriplet;
    }

    private DMatrixSparseCSC getPtdfMatrix(Network network, List<Branch> xnecList, Map<String, Integer> xnecIndex, List<String> nodeList, Map<String, Integer> nodeIndex) {
        List<SensitivityFactor> factors = getFactors(xnecList, nodeList);
        SensitivityAnalysisResult sensiResult = getSensitivityAnalysisResult(network, factors);
        DMatrixSparseTriplet ptdfMatrixTriplet = getPtdfMatrixTriplet(xnecIndex, nodeIndex, factors, sensiResult);
        return DConvertMatrixStruct.convert(ptdfMatrixTriplet, (DMatrixSparseCSC) null);
    }

    private List<String> getNodeList(Network network) {
        return getAllNetworkInjections(network)
            .stream()
            .map(Injection::getId)
            .collect(Collectors.toList());
    }

    private Map<String, Integer> getNodeIndex(List<String> nodeList) {
        return IntStream.range(0, nodeList.size())
            .boxed()
            .collect(Collectors.toMap(
                nodeList::get,
                Function.identity()
            ));
    }

    private DMatrixSparseCSC getAllocatedFlowsMatrix(List<Branch> xnecList, DMatrixSparseCSC ptdfMatrix, DMatrixSparseCSC nodalInjectionsSparseMatrix) {
        DMatrixSparseCSC allocatedFlowsMatrix = new DMatrixSparseCSC(xnecList.size(), 1);
        SimpleOperations simpleOperationsDscc = new SimpleOperations_DSCC();
        simpleOperationsDscc.mult(ptdfMatrix, nodalInjectionsSparseMatrix, allocatedFlowsMatrix);
        return allocatedFlowsMatrix;
    }

    private Map<String, Double> getAllocatedFlowMap(List<Branch> xnecList, DMatrixSparseCSC allocatedFlowsMatrix) {
        Map<String, Double> allocatedFlowMap = new HashMap<>();
        for (int row = 0; row< allocatedFlowsMatrix.getNumRows(); row++) {
            allocatedFlowMap.put(xnecList.get(row).getId(), allocatedFlowsMatrix.get(row, 0, 0.0));
        }
        return allocatedFlowMap;
    }

    public Map<String, Double> run(Network network) {
        List<Branch> xnecList = selectXnecs(network);
        List<String> nodeList = getNodeList(network);
        Map<String, Integer> xnecIndex = getXnecIndex(xnecList);
        Map<String, Integer> nodeIndex = getNodeIndex(nodeList);

        Map<Country, Map<String, Double>> glsks = buildAutoGlsks(network);
        Map<String, Double> nodalInjectionsForAllocatedFlow = getNodalInjectionsForAllocatedFlows(network, glsks);

        DMatrixSparseCSC ptdfMatrix = getPtdfMatrix(network, xnecList, xnecIndex, nodeList, nodeIndex);
        DMatrixSparseCSC nodalInjectionsMatrix = convertToMatrix(nodalInjectionsForAllocatedFlow, nodeIndex);
        DMatrixSparseCSC allocatedFlowsMatrix = getAllocatedFlowsMatrix(xnecList, ptdfMatrix, nodalInjectionsMatrix);
        return getAllocatedFlowMap(xnecList, allocatedFlowsMatrix);
    }
}
