/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.sensitivity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This class provides a computer that can run on a network and returns flow decomposition results.
 * The computer parameters are managed by the flow decomposition parameters library.
 *
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 * @see FlowDecompositionResults
 * @see FlowDecompositionParameters
 * @see Network
 */
public class FlowDecompositionComputer {
    private static final Logger LOGGER = LoggerFactory.getLogger(FlowDecompositionComputer.class);
    private final LoadFlowParameters loadFlowParameters;
    private final FlowDecompositionParameters parameters;

    public FlowDecompositionComputer() {
        this(new FlowDecompositionParameters());
    }

    /**
     * The computer may use some predefined parameters.
     *
     * @param parameters flow decomposition parameters
     */
    public FlowDecompositionComputer(FlowDecompositionParameters parameters) {
        this.parameters = parameters;
        this.loadFlowParameters = initLoadFlowParameters();
    }

    /**
     * This function will run the flow decomposition computer on a network.
     *
     * @param network will be modified !
     * @param saveIntermediate when the decomposition is running, we can save or not the intermediate results.
     * @return A flow decomposition results: FlowDecompositionResults
     */
    public FlowDecompositionResults run(Network network, boolean saveIntermediate) {
        FlowDecompositionResults flowDecompositionResults = new FlowDecompositionResults(network, saveIntermediate);

        //AC LF
        List<Branch> xnecList = new XnecSelector().run(network);
        Map<Country, Double> netPositions = getZonesNetPosition(network, flowDecompositionResults);
        flowDecompositionResults.saveAcReferenceFlow(getXnecReferenceFlows(xnecList));
        compensateLosses(network);

        // None
        NetworkMatrixIndexes networkMatrixIndexes = new NetworkMatrixIndexes(network, xnecList);
        Map<Country, Map<String, Double>> glsks = getGlsks(network, flowDecompositionResults);

        // DC LF
        SparseMatrixWithIndexesTriplet nodalInjectionsMatrix = getNodalInjectionsMatrix(network,
            flowDecompositionResults, netPositions, networkMatrixIndexes, glsks);
        flowDecompositionResults.saveDcReferenceFlow(getXnecReferenceFlows(xnecList));

        // DC Sensi
        SensitivityAnalyser sensitivityAnalyser = getSensitivityAnalyser(network, networkMatrixIndexes);
        SparseMatrixWithIndexesTriplet ptdfMatrix = getPtdfMatrix(flowDecompositionResults,
            networkMatrixIndexes, sensitivityAnalyser);
        SparseMatrixWithIndexesTriplet psdfMatrix = getPsdfMatrix(flowDecompositionResults,
            networkMatrixIndexes, sensitivityAnalyser);

        // None
        computeAllocatedAndLoopFlows(flowDecompositionResults, nodalInjectionsMatrix, ptdfMatrix);
        computePstFlows(network, flowDecompositionResults, networkMatrixIndexes, psdfMatrix);

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

    private Map<Country, Double> getZonesNetPosition(Network network,
                                                     FlowDecompositionResults flowDecompositionResults) {
        NetPositionComputer netPositionComputer = new NetPositionComputer(loadFlowParameters);
        Map<Country, Double> netPosition = netPositionComputer.run(network);
        flowDecompositionResults.saveACNetPosition(netPosition);
        return netPosition;
    }

    private Map<String, Double> getXnecReferenceFlows(List<Branch> xnecList) {
        ReferenceFlowComputer referenceFlowComputer = new ReferenceFlowComputer();
        return referenceFlowComputer.run(xnecList);
    }

    private void compensateLosses(Network network) {
        if (parameters.lossesCompensationEnabled()) {
            LossesCompensator lossesCompensator = new LossesCompensator(loadFlowParameters, parameters);
            lossesCompensator.run(network);
        }
    }

    private Map<Country, Map<String, Double>> getGlsks(Network network,
                                                       FlowDecompositionResults flowDecompositionResults) {
        GlskComputer glskComputer = new GlskComputer();
        Map<Country, Map<String, Double>> glsks = glskComputer.run(network);
        flowDecompositionResults.saveGlsks(glsks);
        return glsks;
    }

    private SparseMatrixWithIndexesTriplet getNodalInjectionsMatrix(Network network,
                                                                    FlowDecompositionResults flowDecompositionResults,
                                                                    Map<Country, Double> netPositions,
                                                                    NetworkMatrixIndexes networkMatrixIndexes,
                                                                    Map<Country, Map<String, Double>> glsks) {
        NodalInjectionComputer nodalInjectionComputer = new NodalInjectionComputer(networkMatrixIndexes);
        Map<String, Double> dcNodalInjection = getDcNodalInjection(network, flowDecompositionResults, networkMatrixIndexes);

        return getNodalInjectionsMatrix(network, flowDecompositionResults, netPositions, glsks, nodalInjectionComputer, dcNodalInjection);
    }

    private Map<String, Double> getDcNodalInjection(Network network,
                                                    FlowDecompositionResults flowDecompositionResults,
                                                    NetworkMatrixIndexes networkMatrixIndexes) {
        ReferenceNodalInjectionComputer referenceNodalInjectionComputer = new ReferenceNodalInjectionComputer(networkMatrixIndexes);
        Map<String, Double> dcNodalInjection = referenceNodalInjectionComputer.run(network, loadFlowParameters);
        flowDecompositionResults.saveDcNodalInjections(dcNodalInjection);
        return dcNodalInjection;
    }

    private SparseMatrixWithIndexesTriplet getNodalInjectionsMatrix(Network network,
                                                                    FlowDecompositionResults flowDecompositionResults,
                                                                    Map<Country, Double> netPositions,
                                                                    Map<Country, Map<String, Double>> glsks,
                                                                    NodalInjectionComputer nodalInjectionComputer,
                                                                    Map<String, Double> dcNodalInjection) {
        SparseMatrixWithIndexesTriplet nodalInjectionsMatrix =
            nodalInjectionComputer.run(network,
                glsks, netPositions, dcNodalInjection);
        flowDecompositionResults.saveNodalInjectionsMatrix(nodalInjectionsMatrix);
        return nodalInjectionsMatrix;
    }

    private SensitivityAnalyser getSensitivityAnalyser(Network network, NetworkMatrixIndexes networkMatrixIndexes) {
        return new SensitivityAnalyser(loadFlowParameters, parameters, network, networkMatrixIndexes);
    }

    private SparseMatrixWithIndexesTriplet getPtdfMatrix(FlowDecompositionResults flowDecompositionResults,
                                                         NetworkMatrixIndexes networkMatrixIndexes,
                                                         SensitivityAnalyser sensitivityAnalyser) {
        SparseMatrixWithIndexesTriplet ptdfMatrix =
            sensitivityAnalyser.run(networkMatrixIndexes.getNodeIdList(),
                networkMatrixIndexes.getNodeIndex(),
                SensitivityVariableType.INJECTION_ACTIVE_POWER);
        flowDecompositionResults.savePtdfMatrix(ptdfMatrix);
        return ptdfMatrix;
    }

    private void computeAllocatedAndLoopFlows(FlowDecompositionResults flowDecompositionResults,
                                              SparseMatrixWithIndexesTriplet nodalInjectionsMatrix,
                                              SparseMatrixWithIndexesTriplet ptdfMatrix) {
        SparseMatrixWithIndexesCSC allocatedLoopFlowsMatrix =
            SparseMatrixWithIndexesCSC.mult(ptdfMatrix.toCSCMatrix(), nodalInjectionsMatrix.toCSCMatrix());
        flowDecompositionResults.saveAllocatedAndLoopFlowsMatrix(allocatedLoopFlowsMatrix);
    }

    private SparseMatrixWithIndexesTriplet getPsdfMatrix(FlowDecompositionResults flowDecompositionResults,
                                                         NetworkMatrixIndexes networkMatrixIndexes,
                                                         SensitivityAnalyser sensitivityAnalyser) {
        SparseMatrixWithIndexesTriplet psdfMatrix =
            sensitivityAnalyser.run(networkMatrixIndexes.getPstList(),
                networkMatrixIndexes.getPstIndex(), SensitivityVariableType.TRANSFORMER_PHASE);
        flowDecompositionResults.savePsdfMatrix(psdfMatrix);
        return psdfMatrix;
    }

    private void computePstFlows(Network network,
                                 FlowDecompositionResults flowDecompositionResults,
                                 NetworkMatrixIndexes networkMatrixIndexes,
                                 SparseMatrixWithIndexesTriplet psdfMatrix) {
        SparseMatrixWithIndexesCSC pstFlowMatrix = PstFlowComputer.run(network, networkMatrixIndexes, psdfMatrix);
        flowDecompositionResults.savePstFlowMatrix(pstFlowMatrix);
    }
}
