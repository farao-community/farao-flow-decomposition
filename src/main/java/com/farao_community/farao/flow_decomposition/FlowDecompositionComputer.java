/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.commons.PowsyblException;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(FlowDecompositionComputer.class);
    private final LoadFlowParameters loadFlowParameters;
    private final FlowDecompositionParameters parameters;

    public FlowDecompositionComputer() {
        this(new FlowDecompositionParameters());
    }

    public FlowDecompositionComputer(FlowDecompositionParameters parameters) {
        this.parameters = parameters;
        this.loadFlowParameters = initLoadFlowParameters();
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

    private List<Injection<?>> getNodeList(Network network) {
        return getAllNetworkInjections(network)
            .filter(this::isInjectionConnected)
            .filter(this::isInjectionInMainSynchronousComponent)
            .collect(Collectors.toList());
    }

    private Map<Country, Double> getZonesNetPosition(Network network) {
        NetPositionComputer netPositionComputer = new NetPositionComputer(loadFlowParameters);
        return netPositionComputer.run(network);
    }

    private List<Branch> selectXnecs(Network network) {
        return network.getBranchStream()
                .filter(this::isAnInterconnection)
                .collect(Collectors.toList());
    }

    private boolean hasNeutralStep(TwoWindingsTransformer pst) {
        PhaseTapChanger phaseTapChanger = pst.getPhaseTapChanger();
        if (phaseTapChanger == null) {
            return false;
        }
        return phaseTapChanger.getNeutralStep().isPresent();
    }

    private List<String> getPstIdList(Network network) {
        return network.getTwoWindingsTransformerStream()
            .filter(this::hasNeutralStep)
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

    private boolean isAnInterconnection(Branch<?> branch) {
        Country country1 = NetworkUtil.getTerminalCountry(branch.getTerminal1());
        Country country2 = NetworkUtil.getTerminalCountry(branch.getTerminal2());
        return !country1.equals(country2);
    }

    private List<String> getNodeIdList(List<Injection<?>> nodeList) {
        return nodeList.stream()
            .map(Injection::getId)
            .collect(Collectors.toList());
    }

    private SparseMatrixWithIndexesCSC getAllocatedLoopFlowsMatrix(SparseMatrixWithIndexesTriplet ptdfMatrix,
                                                                   SparseMatrixWithIndexesTriplet nodalInjectionsSparseMatrix) {
        return SparseMatrixWithIndexesCSC.mult(ptdfMatrix.toCSCMatrix(), nodalInjectionsSparseMatrix.toCSCMatrix());
    }

    private void compensateLosses(Network network) {
        LossesCompensator lossesCompensator = new LossesCompensator(loadFlowParameters);
        lossesCompensator.run(network);
    }


    public FlowDecompositionResults run(Network network, boolean saveIntermediate) {
        FlowDecompositionResults flowDecompositionResults = new FlowDecompositionResults(saveIntermediate);

        Map<Country, Double> netPositions = getZonesNetPosition(network);

        if (parameters.lossesCompensationEnabled()) {
            compensateLosses(network);
        }
        LoadFlow.run(network, loadFlowParameters);

        List<Branch> xnecList = selectXnecs(network);
        List<Injection<?>> nodeList = getNodeList(network);
        List<String> nodeIdList = getNodeIdList(nodeList);
        List<String> pstList = getPstIdList(network);
        Map<String, Integer> xnecIndex = getXnecIndex(xnecList);
        Map<String, Integer> nodeIndex = NetworkUtil.getIndex(nodeIdList);
        Map<String, Integer> pstIndex = NetworkUtil.getIndex(pstList);

        Map<Country, Map<String, Double>> glsks = buildAutoGlsks(network);
        flowDecompositionResults.saveGlsks(glsks);

        NodalInjectionComputer nodalInjectionComputer = new NodalInjectionComputer(nodeList);
        Map<String, Double> dcNodalInjection = nodalInjectionComputer.getDCNodalInjections();
        flowDecompositionResults.saveDcNodalInjections(dcNodalInjection);

        SparseMatrixWithIndexesTriplet nodalInjectionsMatrix = nodalInjectionComputer.getNodalInjectionsMatrix(network, glsks, netPositions, dcNodalInjection, nodeIndex);
        flowDecompositionResults.saveNodalInjectionsMatrix(nodalInjectionsMatrix);

        SensitivityAnalyser sensitivityAnalyser = new SensitivityAnalyser(loadFlowParameters, network, xnecList, xnecIndex);
        SparseMatrixWithIndexesTriplet ptdfMatrix = sensitivityAnalyser.getSensibilityMatrix(nodeIdList, nodeIndex, SensitivityVariableType.INJECTION_ACTIVE_POWER);
        flowDecompositionResults.savePtdfMatrix(ptdfMatrix);

        SparseMatrixWithIndexesCSC allocatedLoopFlowsMatrix = getAllocatedLoopFlowsMatrix(ptdfMatrix, nodalInjectionsMatrix);
        flowDecompositionResults.saveAllocatedAndLoopFlowsMatrix(allocatedLoopFlowsMatrix);

        SparseMatrixWithIndexesTriplet psdfMatrix = sensitivityAnalyser.getSensibilityMatrix(pstList, pstIndex, SensitivityVariableType.TRANSFORMER_PHASE);
        flowDecompositionResults.savePsdfMatrix(psdfMatrix);

        PstFlowComputer pstFlowComputer = new PstFlowComputer();
        SparseMatrixWithIndexesCSC pstFlowMatrix = pstFlowComputer.getPstFlowMatrix(network, pstList, pstIndex, psdfMatrix);
        flowDecompositionResults.savePstFlowMatrix(pstFlowMatrix);

        return flowDecompositionResults;
    }

    public FlowDecompositionResults run(Network network) {
        return run(network, false);
    }
}
