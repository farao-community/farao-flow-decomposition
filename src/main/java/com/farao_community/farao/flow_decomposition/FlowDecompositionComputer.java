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
import com.powsybl.openloadflow.sensi.OpenSensitivityAnalysisProvider;
import com.powsybl.sensitivity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
public class FlowDecompositionComputer {
    private static final double EPSILON = 1e-5;
    private static final double DEFAULT_GLSK_FACTOR = 0.0;
    private static final String ALLOCATED_COLUMN_NAME = "Allocated";
    private static final Logger LOGGER = LoggerFactory.getLogger(FlowDecompositionComputer.class);
    private final LoadFlowParameters loadFlowParameters;
    private final SensitivityAnalysisParameters sensitivityAnalysisParameters;

    public FlowDecompositionComputer() {
        this.loadFlowParameters = initLoadFlowParameters();
        this.sensitivityAnalysisParameters = initSensitivityAnalysisParameters(loadFlowParameters);
    }

    private static SensitivityAnalysisParameters initSensitivityAnalysisParameters(LoadFlowParameters loadFlowParameters) {
        SensitivityAnalysisParameters parameters = SensitivityAnalysisParameters.load();
        parameters.setLoadFlowParameters(loadFlowParameters);
        LOGGER.debug("Using following sensitivity analysis parameters: " + parameters);
        return parameters;
    }

    private static LoadFlowParameters initLoadFlowParameters() {
        LoadFlowParameters parameters = LoadFlowParameters.load();
        parameters.setDc(true);
        LOGGER.debug("Using following load flow parameters: " + parameters);
        return parameters;
    }

    private Map<Country, Map<String, Double>> buildAutoGlsks(Network network) {
        Map<Country, Map<String, Double>> glsks = network.getCountries().stream().collect(Collectors.toMap(
            Function.identity(),
            country -> new HashMap<>()));
        network.getGeneratorStream()
                .forEach(generator -> {
                    Country generatorCountry = NetworkUtil.getInjectionCountry(generator);
                    glsks.get(generatorCountry).put(generator.getId(), generator.getTargetP());
                });
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
                .collect(Collectors.toList());
    }

    private double getIndividualNodalInjectionForAllocatedFlows(
            Injection<?> injection,
            Map<Country, Map<String, Double>> glsks,
            Map<Country, Double> netPositions) {
        Country injectionCountry = NetworkUtil.getInjectionCountry(injection);
        return glsks.get(injectionCountry).getOrDefault(injection.getId(), DEFAULT_GLSK_FACTOR) * netPositions.get(injectionCountry);
    }

    private Map<Country, Double> getZonesNetPosition(Network network) {
        LoadFlow.run(network, loadFlowParameters);
        return NetworkUtil.computeNetPositions(network);
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

    private SparseMatrixWithIndexesTriplet convertToNodalInjectionMatrix(Map<String, Double> nodalInjections, Map<String, Integer> nodeIndex) {
        Map<String, Double> nonZeroInjections = nodalInjections.entrySet().stream()
                .filter(this::isNotZero)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
        ));
        SparseMatrixWithIndexesTriplet nodalInjectionMatrix = new SparseMatrixWithIndexesTriplet(
                nodeIndex, ALLOCATED_COLUMN_NAME, nonZeroInjections.size());
        nonZeroInjections.forEach(
            (injectionId, injectionValue) -> nodalInjectionMatrix.addItem(injectionId, ALLOCATED_COLUMN_NAME, injectionValue)
        );
        return nodalInjectionMatrix;
    }

    private SparseMatrixWithIndexesTriplet getNodalInjectionsForAllocatedFlowsMatrix(
            Network network,
            Map<Country, Map<String, Double>> glsks,
            Map<String, Integer> nodeIndex) {
        Map<String, Double> nodalInjectionsForAllocatedFlow = getNodalInjectionsForAllocatedFlows(network, glsks);
        return convertToNodalInjectionMatrix(nodalInjectionsForAllocatedFlow, nodeIndex);
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
        Country country1 = NetworkUtil.getTerminalCountry(branch.getTerminal1());
        Country country2 = NetworkUtil.getTerminalCountry(branch.getTerminal2());
        return !country1.equals(country2);
    }

    private SensitivityFactor getSensitivityFactor(String node, Branch xnec) {
        return new SensitivityFactor(
                SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, xnec.getId(),
                SensitivityVariableType.INJECTION_ACTIVE_POWER, node,
                false,
                ContingencyContext.none()
        );
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

    private SparseMatrixWithIndexesTriplet getPtdfMatrixTriplet(
            Map<String, Integer> xnecIndex,
            Map<String, Integer> nodeIndex,
            List<SensitivityFactor> factors,
            SensitivityAnalysisResult sensiResult) {
        SparseMatrixWithIndexesTriplet ptdfMatrixTriplet = new SparseMatrixWithIndexesTriplet(xnecIndex, nodeIndex, factors.size() + 1);
        for (SensitivityValue sensitivityValue : sensiResult.getValues()) {
            SensitivityFactor factor = factors.get(sensitivityValue.getFactorIndex());
            ptdfMatrixTriplet.addItem(factor.getFunctionId(), factor.getVariableId(), sensitivityValue.getValue());
        }
        return ptdfMatrixTriplet;
    }

    private SparseMatrixWithIndexesTriplet getPtdfMatrix(Network network, List<Branch> xnecList, Map<String, Integer> xnecIndex, List<String> nodeList, Map<String, Integer> nodeIndex) {
        List<SensitivityFactor> factors = getFactors(xnecList, nodeList);
        SensitivityAnalysisResult sensiResult = getSensitivityAnalysisResult(network, factors);
        return getPtdfMatrixTriplet(xnecIndex, nodeIndex, factors, sensiResult);
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

    private SparseMatrixWithIndexesCSC getAllocatedFlowsMatrix(SparseMatrixWithIndexesTriplet ptdfMatrix, SparseMatrixWithIndexesTriplet nodalInjectionsSparseMatrix) {
        return SparseMatrixWithIndexesCSC.mult(ptdfMatrix.toCSCMatrix(), nodalInjectionsSparseMatrix.toCSCMatrix());
    }

    public FlowDecompositionResults run(Network network, boolean saveIntermediate) {
        FlowDecompositionResults flowDecompositionResults = new FlowDecompositionResults(saveIntermediate);
        List<Branch> xnecList = selectXnecs(network);
        List<String> nodeList = getNodeList(network);
        Map<String, Integer> xnecIndex = getXnecIndex(xnecList);
        Map<String, Integer> nodeIndex = getNodeIndex(nodeList);

        Map<Country, Map<String, Double>> glsks = buildAutoGlsks(network);
        flowDecompositionResults.saveGlsks(glsks);

        SparseMatrixWithIndexesTriplet nodalInjectionsMatrix = getNodalInjectionsForAllocatedFlowsMatrix(network, glsks, nodeIndex);
        flowDecompositionResults.saveNodalInjectionsMatrix(nodalInjectionsMatrix);

        SparseMatrixWithIndexesTriplet ptdfMatrix = getPtdfMatrix(network, xnecList, xnecIndex, nodeList, nodeIndex);
        flowDecompositionResults.savePtdfMatrix(ptdfMatrix);

        SparseMatrixWithIndexesCSC allocatedFlowsMatrix = getAllocatedFlowsMatrix(ptdfMatrix, nodalInjectionsMatrix);
        flowDecompositionResults.saveDecomposedFlowsMatrix(allocatedFlowsMatrix);

        return flowDecompositionResults;
    }

    public FlowDecompositionResults run(Network network) {
        return run(network, false);
    }
}
