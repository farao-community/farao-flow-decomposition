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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
public class FlowDecompositionComputer {
    private static final double DEFAULT_GLSK_FACTOR = 0.0;
    private static final double DEFAULT_SENSIBILITY_EPSILON = 1e-5;
    private static final String ALLOCATED_COLUMN_NAME = "Allocated";
    private static final String PST_COLUMN_NAME = "PST";
    private static final Logger LOGGER = LoggerFactory.getLogger(FlowDecompositionComputer.class);
    private final LoadFlowParameters loadFlowParameters;
    private final SensitivityAnalysisParameters sensitivityAnalysisParameters;
    private final FlowDecompositionParameters parameters;

    public FlowDecompositionComputer() {
        this(new FlowDecompositionParameters());
    }

    public FlowDecompositionComputer(FlowDecompositionParameters parameters) {
        this.parameters = parameters;
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

    private boolean isInjectionConnected(Injection<?> injection) {
        return injection.getTerminal().isConnected();
    }

    private boolean isInjectionInMainSynchronousComponent(Injection<?> injection) {
        return injection.getTerminal().getBusBreakerView().getBus().isInMainSynchronousComponent();
    }

    private Stream<Injection<?>> getAllNetworkInjections(Network network) {
        return network.getConnectableStream()
            .filter(Injection.class::isInstance)
            .map(connectable -> (Injection<?>) connectable);
    }

    private List<Injection<?>> getAllValidNetworkInjections(Network network) {
        return getAllNetworkInjections(network)
            .filter(this::isInjectionConnected)
            .filter(this::isInjectionInMainSynchronousComponent)
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
        NetPositionComputer netPositionComputer = new NetPositionComputer(loadFlowParameters);
        return netPositionComputer.run(network);
    }

    private Map<String, Double> getNodalInjectionsForAllocatedFlows(
            Network network,
            Map<Country, Map<String, Double>> glsks,
            Map<Country, Double> netPositions) {
        return getAllValidNetworkInjections(network)
                .stream()
                .collect(Collectors.toMap(
                    Injection::getId,
                    injection -> getIndividualNodalInjectionForAllocatedFlows(injection, glsks, netPositions)
                    )
                );
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
                nodeIndex, getIndex(columns), nodalInjectionsForAllocatedFlow.size());
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
        Map<Country, Double> netPositions,
        Map<String, Double> dcNodalInjection,
        Map<String, Integer> nodeIndex) {
        Map<String, Double> nodalInjectionsForAllocatedFlow = getNodalInjectionsForAllocatedFlows(network, glsks, netPositions);
        return convertToNodalInjectionMatrix(network, glsks, nodalInjectionsForAllocatedFlow, dcNodalInjection, nodeIndex);
    }

    private List<Branch> selectXnecs(Network network) {
        return network.getBranchStream()
                .filter(this::isAnInterconnection)
                .collect(Collectors.toList());
    }

    private List<String> getPstIdList(Network network) {
        return network.getTwoWindingsTransformerStream()
            .map(Identifiable::getId)
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

    private Map<String, Integer> getIndex(List<String> idList) {
        return IntStream.range(0, idList.size())
            .boxed()
            .collect(Collectors.toMap(
                idList::get,
                Function.identity()
            ));
    }

    private boolean isAnInterconnection(Branch<?> branch) {
        Country country1 = NetworkUtil.getTerminalCountry(branch.getTerminal1());
        Country country2 = NetworkUtil.getTerminalCountry(branch.getTerminal2());
        return !country1.equals(country2);
    }

    private SensitivityFactor getSensitivityFactor(String pst, Branch<?> xnec, SensitivityVariableType sensitivityVariableType) {
        return new SensitivityFactor(
            SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, xnec.getId(),
            sensitivityVariableType, pst,
            false,
            ContingencyContext.none()
        );
    }

    private List<SensitivityFactor> getFactors(List<Branch> xnecList, List<String> nodeList, SensitivityVariableType sensitivityVariableType) {
        List<SensitivityFactor> factors = new ArrayList<>();
        nodeList.forEach(
            node -> xnecList.forEach(
                xnec -> factors.add(getSensitivityFactor(node, xnec, sensitivityVariableType))));
        return factors;
    }

    private SensitivityAnalysisResult getSensitivityAnalysisResult(Network network, List<SensitivityFactor> factors) {
        return SensitivityAnalysis.run(network, factors, sensitivityAnalysisParameters);
    }

    private SparseMatrixWithIndexesTriplet getSensibilityMatrixTriplet(
            Map<String, Integer> xnecIndex,
            Map<String, Integer> nodeIndex,
            List<SensitivityFactor> factors,
            SensitivityAnalysisResult sensiResult) {
        SparseMatrixWithIndexesTriplet ptdfMatrixTriplet = new SparseMatrixWithIndexesTriplet(xnecIndex,
            nodeIndex,
            factors.size() + 1,
            DEFAULT_SENSIBILITY_EPSILON);
        LOGGER.debug("Filtering Sensitivity values with epsilon = {}", DEFAULT_SENSIBILITY_EPSILON);
        for (SensitivityValue sensitivityValue : sensiResult.getValues()) {
            SensitivityFactor factor = factors.get(sensitivityValue.getFactorIndex());
            double sensitivity = sensitivityValue.getValue();
            double referenceOrientedSensitivity = sensitivityValue.getFunctionReference() < 0 ? -sensitivity : sensitivity;
            ptdfMatrixTriplet.addItem(factor.getFunctionId(), factor.getVariableId(), referenceOrientedSensitivity);
        }
        return ptdfMatrixTriplet;
    }

    private SparseMatrixWithIndexesTriplet getSensibilityMatrix(Network network,
                                                                List<Branch> xnecList,
                                                                Map<String, Integer> xnecIndex,
                                                                List<String> nodeList,
                                                                Map<String, Integer> nodeIndex,
                                                                SensitivityVariableType sensitivityVariableType) {
        List<SensitivityFactor> factors = getFactors(xnecList, nodeList, sensitivityVariableType);
        SensitivityAnalysisResult sensiResult = getSensitivityAnalysisResult(network, factors);
        return getSensibilityMatrixTriplet(xnecIndex, nodeIndex, factors, sensiResult);
    }

    private List<String> getNodeIdList(Network network) {
        return getAllValidNetworkInjections(network)
            .stream()
            .map(Injection::getId)
            .collect(Collectors.toList());
    }

    private SparseMatrixWithIndexesCSC getAllocatedLoopFlowsMatrix(SparseMatrixWithIndexesTriplet ptdfMatrix,
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

    private void compensateLosses(Network network) {
        LossesCompensator lossesCompensator = new LossesCompensator(loadFlowParameters);
        lossesCompensator.run(network);
    }

    private SparseMatrixWithIndexesTriplet getDeltaTapMatrix(Network network, List<String> pstList, Map<String, Integer> pstIndex) {
        SparseMatrixWithIndexesTriplet deltaTapMatrix = new SparseMatrixWithIndexesTriplet(pstIndex, PST_COLUMN_NAME, pstIndex.size());
        for (String pst: pstList) {
            PhaseTapChanger phaseTapChanger = network.getTwoWindingsTransformer(pst).getPhaseTapChanger();
            if (phaseTapChanger.getNeutralPosition().isEmpty()) {
                throw new PowsyblException(String.format("Two Windings Transformer %s has no neutral position", pst));
            }
            deltaTapMatrix.addItem(pst, PST_COLUMN_NAME, phaseTapChanger.getCurrentStep().getAlpha() - phaseTapChanger.getNeutralStep().get().getAlpha());
        }
        return deltaTapMatrix;
    }

    private SparseMatrixWithIndexesCSC getPstFlowMatrix(Network network, List<String> pstList, Map<String, Integer> pstIndex, SparseMatrixWithIndexesTriplet psdfMatrix) {
        SparseMatrixWithIndexesTriplet deltaTapMatrix = getDeltaTapMatrix(network, pstList, pstIndex);
        return SparseMatrixWithIndexesCSC.mult(psdfMatrix.toCSCMatrix(), deltaTapMatrix.toCSCMatrix());
    }

    public FlowDecompositionResults run(Network network, boolean saveIntermediate) {
        FlowDecompositionResults flowDecompositionResults = new FlowDecompositionResults(saveIntermediate);

        Map<Country, Double> netPositions = getZonesNetPosition(network);

        if (parameters.lossesCompensationEnabled()) {
            compensateLosses(network);
        }

        List<Branch> xnecList = selectXnecs(network);
        List<String> nodeList = getNodeIdList(network);
        List<String> pstList = getPstIdList(network);
        Map<String, Integer> xnecIndex = getXnecIndex(xnecList);
        Map<String, Integer> nodeIndex = getIndex(nodeList);
        Map<String, Integer> pstIndex = getIndex(pstList);

        Map<Country, Map<String, Double>> glsks = buildAutoGlsks(network);
        flowDecompositionResults.saveGlsks(glsks);

        LoadFlow.run(network, loadFlowParameters);
        Map<String, Double> dcNodalInjection = getDCNodalInjections(network, nodeList);
        flowDecompositionResults.saveDcNodalInjections(dcNodalInjection);

        SparseMatrixWithIndexesTriplet nodalInjectionsMatrix = getNodalInjectionsMatrix(network, glsks, netPositions, dcNodalInjection, nodeIndex);
        flowDecompositionResults.saveNodalInjectionsMatrix(nodalInjectionsMatrix);

        SparseMatrixWithIndexesTriplet ptdfMatrix = getSensibilityMatrix(network, xnecList, xnecIndex, nodeList, nodeIndex, SensitivityVariableType.INJECTION_ACTIVE_POWER);
        flowDecompositionResults.savePtdfMatrix(ptdfMatrix);

        SparseMatrixWithIndexesCSC allocatedLoopFlowsMatrix = getAllocatedLoopFlowsMatrix(ptdfMatrix, nodalInjectionsMatrix);
        flowDecompositionResults.saveAllocatedAndLoopFlowsMatrix(allocatedLoopFlowsMatrix);

        SparseMatrixWithIndexesTriplet psdfMatrix = getSensibilityMatrix(network, xnecList, xnecIndex, pstList, pstIndex, SensitivityVariableType.TRANSFORMER_PHASE);
        flowDecompositionResults.savePsdfMatrix(psdfMatrix);

        SparseMatrixWithIndexesCSC pstFlowMatrix = getPstFlowMatrix(network, pstList, pstIndex, psdfMatrix);
        flowDecompositionResults.savePstFlowMatrix(pstFlowMatrix);



        return flowDecompositionResults;
    }

    public FlowDecompositionResults run(Network network) {
        return run(network, false);
    }
}
