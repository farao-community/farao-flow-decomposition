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

    private SparseMatrixWithIndexesCSC getAllocatedLoopFlowsMatrix(SparseMatrixWithIndexesTriplet ptdfMatrix,
                                                                   SparseMatrixWithIndexesTriplet nodalInjectionsSparseMatrix) {
        return SparseMatrixWithIndexesCSC.mult(ptdfMatrix.toCSCMatrix(), nodalInjectionsSparseMatrix.toCSCMatrix());
    }

    private void compensateLosses(Network network) {
        if (parameters.lossesCompensationEnabled()) {
            LossesCompensator lossesCompensator = new LossesCompensator(loadFlowParameters);
            lossesCompensator.run(network);
        }
    }


    public FlowDecompositionResults run(Network network, boolean saveIntermediate) {
        FlowDecompositionResults flowDecompositionResults = new FlowDecompositionResults(saveIntermediate);

        Map<Country, Double> netPositions = getZonesNetPosition(network, flowDecompositionResults);

        compensateLosses(network);

        LoadFlow.run(network, loadFlowParameters);

        NetworkIndexes networkIndexes = new NetworkIndexes(network);

        GlskComputer glskComputer = new GlskComputer();
        Map<Country, Map<String, Double>> glsks = glskComputer.buildAutoGlsks(network);
        flowDecompositionResults.saveGlsks(glsks);

        NodalInjectionComputer nodalInjectionComputer = new NodalInjectionComputer(networkIndexes);
        Map<String, Double> dcNodalInjection = nodalInjectionComputer.getDCNodalInjections();
        flowDecompositionResults.saveDcNodalInjections(dcNodalInjection);

        SparseMatrixWithIndexesTriplet nodalInjectionsMatrix = nodalInjectionComputer.getNodalInjectionsMatrix(network, glsks, netPositions, dcNodalInjection);
        flowDecompositionResults.saveNodalInjectionsMatrix(nodalInjectionsMatrix);

        SensitivityAnalyser sensitivityAnalyser = new SensitivityAnalyser(loadFlowParameters, network, networkIndexes.getXnecList(), networkIndexes.getXnecIndex());
        SparseMatrixWithIndexesTriplet ptdfMatrix = sensitivityAnalyser.getSensibilityMatrix(networkIndexes.getNodeIdList(), networkIndexes.getNodeIndex(), SensitivityVariableType.INJECTION_ACTIVE_POWER);
        flowDecompositionResults.savePtdfMatrix(ptdfMatrix);

        SparseMatrixWithIndexesCSC allocatedLoopFlowsMatrix = getAllocatedLoopFlowsMatrix(ptdfMatrix, nodalInjectionsMatrix);
        flowDecompositionResults.saveAllocatedAndLoopFlowsMatrix(allocatedLoopFlowsMatrix);

        SparseMatrixWithIndexesTriplet psdfMatrix = sensitivityAnalyser.getSensibilityMatrix(networkIndexes.getPstList(), networkIndexes.getPstIndex(), SensitivityVariableType.TRANSFORMER_PHASE);
        flowDecompositionResults.savePsdfMatrix(psdfMatrix);

        PstFlowComputer pstFlowComputer = new PstFlowComputer();
        SparseMatrixWithIndexesCSC pstFlowMatrix = pstFlowComputer.getPstFlowMatrix(network, networkIndexes, psdfMatrix);
        flowDecompositionResults.savePstFlowMatrix(pstFlowMatrix);

        return flowDecompositionResults;
    }

    public FlowDecompositionResults run(Network network) {
        return run(network, false);
    }
}
