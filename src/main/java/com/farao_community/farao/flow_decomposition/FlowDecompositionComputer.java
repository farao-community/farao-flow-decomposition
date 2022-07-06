/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.sensitivity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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

    public FlowDecompositionResults run(Network network, boolean saveIntermediate) {
        FlowDecompositionResults flowDecompositionResults = new FlowDecompositionResults(saveIntermediate);

        Map<Country, Double> netPositions = getZonesNetPosition(network, flowDecompositionResults);

        compensateLosses(network);

        NetworkIndexes networkIndexes = new NetworkIndexes(network);

        Map<Country, Map<String, Double>> glsks = getGlsks(network, flowDecompositionResults);

        SparseMatrixWithIndexesTriplet nodalInjectionsMatrix = getNodalInjectionsMatrix(network, flowDecompositionResults, netPositions, networkIndexes, glsks);

        SensitivityAnalyser sensitivityAnalyser = getSensitivityAnalyser(network, networkIndexes);
        SparseMatrixWithIndexesTriplet ptdfMatrix = getPtdfMatrix(flowDecompositionResults, networkIndexes, sensitivityAnalyser);
        computeAllocatedAndLoopFlows(flowDecompositionResults, nodalInjectionsMatrix, ptdfMatrix);

        SparseMatrixWithIndexesTriplet psdfMatrix = getPsdfMatrix(flowDecompositionResults, networkIndexes, sensitivityAnalyser);
        computePstFlows(network, flowDecompositionResults, networkIndexes, psdfMatrix);

        return flowDecompositionResults;
    }

    public FlowDecompositionResults run(Network network) {
        return run(network, false);
    }

    private static LoadFlowParameters initLoadFlowParameters() {
        LoadFlowParameters parameters = LoadFlowParameters.load();
        parameters.setDc(true);
        LOGGER.debug("Using following load flow parameters: {}", parameters);
        return parameters;
    }

    private Map<Country, Double> getZonesNetPosition(Network network, FlowDecompositionResults flowDecompositionResults) {
        NetPositionComputer netPositionComputer = new NetPositionComputer(loadFlowParameters);
        Map<Country, Double> netPosition = netPositionComputer.run(network);
        flowDecompositionResults.saveACNetPosition(netPosition);
        return netPosition;
    }

    private void compensateLosses(Network network) {
        if (parameters.lossesCompensationEnabled()) {
            LossesCompensator lossesCompensator = new LossesCompensator(loadFlowParameters);
            lossesCompensator.run(network);
        }
    }

    private Map<Country, Map<String, Double>> getGlsks(Network network, FlowDecompositionResults flowDecompositionResults) {
        GlskComputer glskComputer = new GlskComputer();
        Map<Country, Map<String, Double>> glsks = glskComputer.buildAutoGlsks(network);
        flowDecompositionResults.saveGlsks(glsks);
        return glsks;
    }

    private SparseMatrixWithIndexesTriplet getNodalInjectionsMatrix(Network network, FlowDecompositionResults flowDecompositionResults, Map<Country, Double> netPositions, NetworkIndexes networkIndexes, Map<Country, Map<String, Double>> glsks) {
        NodalInjectionComputer nodalInjectionComputer = new NodalInjectionComputer(networkIndexes);
        LoadFlow.run(network, loadFlowParameters);
        Map<String, Double> dcNodalInjection = nodalInjectionComputer.getDCNodalInjections();
        flowDecompositionResults.saveDcNodalInjections(dcNodalInjection);

        SparseMatrixWithIndexesTriplet nodalInjectionsMatrix = nodalInjectionComputer.getAllocatedAndLoopFlowNodalInjectionsMatrix(network, glsks, netPositions, dcNodalInjection);
        flowDecompositionResults.saveNodalInjectionsMatrix(nodalInjectionsMatrix);
        return nodalInjectionsMatrix;
    }

    private SensitivityAnalyser getSensitivityAnalyser(Network network, NetworkIndexes networkIndexes) {
        return new SensitivityAnalyser(loadFlowParameters, network, networkIndexes.getXnecList(), networkIndexes.getXnecIndex());
    }

    private SparseMatrixWithIndexesTriplet getPtdfMatrix(FlowDecompositionResults flowDecompositionResults, NetworkIndexes networkIndexes, SensitivityAnalyser sensitivityAnalyser) {
        SparseMatrixWithIndexesTriplet ptdfMatrix = sensitivityAnalyser.getSensibilityMatrix(networkIndexes.getNodeIdList(), networkIndexes.getNodeIndex(), SensitivityVariableType.INJECTION_ACTIVE_POWER);
        flowDecompositionResults.savePtdfMatrix(ptdfMatrix);
        return ptdfMatrix;
    }

    private void computeAllocatedAndLoopFlows(FlowDecompositionResults flowDecompositionResults, SparseMatrixWithIndexesTriplet nodalInjectionsMatrix, SparseMatrixWithIndexesTriplet ptdfMatrix) {
        SparseMatrixWithIndexesCSC allocatedLoopFlowsMatrix = SparseMatrixWithIndexesCSC.mult(ptdfMatrix.toCSCMatrix(), nodalInjectionsMatrix.toCSCMatrix());
        flowDecompositionResults.saveAllocatedAndLoopFlowsMatrix(allocatedLoopFlowsMatrix);
    }

    private SparseMatrixWithIndexesTriplet getPsdfMatrix(FlowDecompositionResults flowDecompositionResults, NetworkIndexes networkIndexes, SensitivityAnalyser sensitivityAnalyser) {
        SparseMatrixWithIndexesTriplet psdfMatrix = sensitivityAnalyser.getSensibilityMatrix(networkIndexes.getPstList(), networkIndexes.getPstIndex(), SensitivityVariableType.TRANSFORMER_PHASE);
        flowDecompositionResults.savePsdfMatrix(psdfMatrix);
        return psdfMatrix;
    }

    private void computePstFlows(Network network, FlowDecompositionResults flowDecompositionResults, NetworkIndexes networkIndexes, SparseMatrixWithIndexesTriplet psdfMatrix) {
        PstFlowComputer pstFlowComputer = new PstFlowComputer();
        SparseMatrixWithIndexesCSC pstFlowMatrix = pstFlowComputer.getPstFlowMatrix(network, networkIndexes, psdfMatrix);
        flowDecompositionResults.savePstFlowMatrix(pstFlowMatrix);
    }
}
