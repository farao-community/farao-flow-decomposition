/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.sensitivity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
public class FlowDecompositionComputer {
    private static final double DEFAULT_GLSK_FACTOR = 0.0;
    private static final double DEFAULT_PTDF_EPSILON = 1e-5;
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
        LOGGER.debug("Using following sensitivity analysis parameters: {}", parameters);
        return parameters;
    }

    private static LoadFlowParameters initLoadFlowParameters() {
        LoadFlowParameters parameters = LoadFlowParameters.load();
        parameters.setDc(true);
        LOGGER.debug("Using following load flow parameters: {}", parameters);
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

    private Predicate<Injection<?>> isInjectionConnected() {
        return injection -> injection.getTerminal().isConnected();
    }

    private Predicate<Injection<?>> isInjectionInMainSynchronousComponent() {
        return injection -> injection.getTerminal().getBusBreakerView().getBus().isInMainSynchronousComponent();
    }

    private Stream<Injection<?>> getAllNetworkInjections(Network network) {
        return network.getConnectableStream()
            .filter(Injection.class::isInstance)
            .map(connectable -> (Injection<?>) connectable);
    }

    private List<Injection<?>> getAllValidNetworkInjections(Network network) {
        return getAllNetworkInjections(network)
            .filter(isInjectionConnected())
            .filter(isInjectionInMainSynchronousComponent())
            .collect(Collectors.toList());
    }

    private double getIndividualNodalInjectionForAllocatedFlows(
            Injection<?> injection,
            Map<Country, Map<String, Double>> glsks,
            Map<Country, Double> netPositions) {
        Country injectionCountry = NetworkUtil.getInjectionCountry(injection);
        return glsks.get(injectionCountry).getOrDefault(injection.getId(), DEFAULT_GLSK_FACTOR)
            * netPositions.get(injectionCountry);
    }

    private Map<Country, Double> getZonesNetPosition(Network network) {
        LoadFlow.run(network, loadFlowParameters);
        return NetworkUtil.computeNetPositions(network);
    }

    private Map<String, Double> getNodalInjectionsForAllocatedFlows(Network network, Map<Country, Map<String, Double>> glsks) {
        Map<Country, Double> netPositions = getZonesNetPosition(network);
        return getAllValidNetworkInjections(network)
                .stream()
                .collect(Collectors.toMap(
                    Injection::getId,
                    injection -> getIndividualNodalInjectionForAllocatedFlows(injection, glsks, netPositions)
                    )
                );
    }

    private Map<String, Integer> getCountryIndex(List<String> countryList) {
        return IntStream.range(0, countryList.size())
            .boxed()
            .collect(Collectors.toMap(
                countryList::get,
                Function.identity()
            ));
    }

    private SparseMatrixWithIndexesTriplet convertToNodalInjectionMatrix(
        Network network,
        Map<Country, Map<String, Double>> glsks,
        Map<String, Double> nodalInjectionsForAllocatedFlow,
        Map<String, Double> dcNodalInjection,
        Map<String, Integer> nodeIndex) {
        List<String> columns = glsks.keySet().stream()
            .map(Enum::toString)
            .collect(Collectors.toList());
        columns.add(ALLOCATED_COLUMN_NAME);
        SparseMatrixWithIndexesTriplet nodalInjectionMatrix = new SparseMatrixWithIndexesTriplet(
                nodeIndex, getCountryIndex(columns), nodalInjectionsForAllocatedFlow.size());
        nodalInjectionsForAllocatedFlow.forEach(
            (injectionId, injectionValue) -> nodalInjectionMatrix.addItem(injectionId, ALLOCATED_COLUMN_NAME, injectionValue)
        );
        dcNodalInjection.forEach(
            (dcInjectionId, dcInjectionValue) -> nodalInjectionMatrix.addItem(
                dcInjectionId,
                NetworkUtil.getIdentifiableCountry(network, dcInjectionId).toString(),
                dcInjectionValue - nodalInjectionsForAllocatedFlow.get(dcInjectionId)
            ));
        return nodalInjectionMatrix;
    }

    private SparseMatrixWithIndexesTriplet getNodalInjectionsMatrix(
        Network network,
        Map<Country, Map<String, Double>> glsks,
        Map<String, Double> dcNodalInjection,
        Map<String, Integer> nodeIndex) {
        Map<String, Double> nodalInjectionsForAllocatedFlow = getNodalInjectionsForAllocatedFlows(network, glsks);
        return convertToNodalInjectionMatrix(network, glsks, nodalInjectionsForAllocatedFlow, dcNodalInjection, nodeIndex);
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
        return SensitivityAnalysis.run(network, factors, sensitivityAnalysisParameters);
    }

    private SparseMatrixWithIndexesTriplet getPtdfMatrixTriplet(
            Map<String, Integer> xnecIndex,
            Map<String, Integer> nodeIndex,
            List<SensitivityFactor> factors,
            SensitivityAnalysisResult sensiResult) {
        SparseMatrixWithIndexesTriplet ptdfMatrixTriplet = new SparseMatrixWithIndexesTriplet(xnecIndex,
            nodeIndex,
            factors.size() + 1,
            DEFAULT_PTDF_EPSILON);
        LOGGER.debug("Filtering PTDF values with epsilon = {}", DEFAULT_PTDF_EPSILON);
        for (SensitivityValue sensitivityValue : sensiResult.getValues()) {
            SensitivityFactor factor = factors.get(sensitivityValue.getFactorIndex());
            double sensitivity = sensitivityValue.getValue();
            double referenceOrientedSensitivity = sensitivityValue.getFunctionReference() < 0 ? -sensitivity : sensitivity;
            ptdfMatrixTriplet.addItem(factor.getFunctionId(), factor.getVariableId(), referenceOrientedSensitivity);
        }
        return ptdfMatrixTriplet;
    }

    private SparseMatrixWithIndexesTriplet getPtdfMatrix(Network network,
                                                         List<Branch> xnecList,
                                                         Map<String, Integer> xnecIndex,
                                                         List<String> nodeList,
                                                         Map<String, Integer> nodeIndex) {
        List<SensitivityFactor> factors = getFactors(xnecList, nodeList);
        SensitivityAnalysisResult sensiResult = getSensitivityAnalysisResult(network, factors);
        return getPtdfMatrixTriplet(xnecIndex, nodeIndex, factors, sensiResult);
    }

    private List<String> getNodeIdList(Network network) {
        return getAllValidNetworkInjections(network)
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

    private SparseMatrixWithIndexesCSC getAllocatedFlowsMatrix(SparseMatrixWithIndexesTriplet ptdfMatrix,
                                                               SparseMatrixWithIndexesTriplet nodalInjectionsSparseMatrix) {
        return SparseMatrixWithIndexesCSC.mult(ptdfMatrix.toCSCMatrix(), nodalInjectionsSparseMatrix.toCSCMatrix());
    }

    private double getReferenceInjection(Network network, String nodeId) {
        Identifiable<?> node = network.getIdentifiable(nodeId);
        double p = 0.0;
        if (node instanceof Injection) {
            p = -((Injection) node).getTerminal().getP();
        }
        if (Double.isNaN(p)) {
            throw new PowsyblException(String.format("Reference nodal injection cannot be a Nan for node %s", nodeId));
        }
        return p;
    }

    private Map<String, Double> getDCNodalInjections(Network network, List<String> nodeList) {
        return nodeList.stream().collect(Collectors.toMap(Function.identity(), nodeId -> getReferenceInjection(network, nodeId)));
    }

    public FlowDecompositionResults run(Network network, boolean saveIntermediate) {
        FlowDecompositionResults flowDecompositionResults = new FlowDecompositionResults(saveIntermediate);
        List<Branch> xnecList = selectXnecs(network);
        List<String> nodeList = getNodeIdList(network);
        Map<String, Integer> xnecIndex = getXnecIndex(xnecList);
        Map<String, Integer> nodeIndex = getNodeIndex(nodeList);

        Map<Country, Map<String, Double>> glsks = buildAutoGlsks(network);
        flowDecompositionResults.saveGlsks(glsks);

        LoadFlow.run(network, loadFlowParameters);
        Map<String, Double> dcNodalInjection = getDCNodalInjections(network, nodeList);
        flowDecompositionResults.saveDcNodalInjections(dcNodalInjection);

        SparseMatrixWithIndexesTriplet nodalInjectionsMatrix = getNodalInjectionsMatrix(network, glsks, dcNodalInjection, nodeIndex);
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
