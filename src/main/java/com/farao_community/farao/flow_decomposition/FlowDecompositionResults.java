/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;

/**
 * This class provides flow decomposition results from a network.
 * Those results are returned by a flowDecompositionComputer when run on a network.
 * By default, the results only contain the flow decomposition of the XNECs.
 * If this runner has its argument {@code saveIntermediate} set to {@code true},
 * then the results will contain supplementary information.
 *
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 * @see FlowDecompositionComputer
 * @see DecomposedFlow
 */
public class FlowDecompositionResults {
    static final boolean FILL_ZEROS = true;
    static final boolean NOT_FILL_ZEROS = false;
    private static final boolean DEFAULT_FILL_ZEROS = NOT_FILL_ZEROS;
    private final boolean saveIntermediate;
    private String id;
    private final String networkId;
    private SparseMatrixWithIndexesCSC allocatedAndLoopFlowsMatrix;
    private Map<String, Map<String, Double>> pstFlowMap;
    private Map<String, Double> acReferenceFlow;
    private Map<String, Double> dcReferenceFlow;
    private Map<Country, Double> acNetPosition;
    private Map<Country, Map<String, Double>> glsks;
    private SparseMatrixWithIndexesTriplet ptdfMatrix;
    private SparseMatrixWithIndexesTriplet psdfMatrix;
    private SparseMatrixWithIndexesTriplet nodalInjectionsMatrix;
    private Map<String, Double> dcNodalInjections;
    private DecomposedFlowMapCache decomposedFlowMapCache;
    private Map<String, DecomposedFlow> rescaledDecomposedFlowMap;

    FlowDecompositionResults(Network network, FlowDecompositionParameters parameters) {
        this.saveIntermediate = parameters.isSaveIntermediate();
        this.networkId = network.getNameOrId();
        String date = new SimpleDateFormat("yyyyMMdd-HHmmss").format(Date.from(Instant.now()));
        this.id = "Flow_Decomposition_Results_of_" + date + "_on_network_" + networkId;
    }

    /**
     * @return Network Id
     */
    public String getNetworkId() {
        return networkId;
    }

    /**
     * @return Id composed of a time format and the network id.
     */
    public String getId() {
        return id;
    }

    /**
     * @param fillZeros Ignore the sparse property of the flow decomposition.
     *                  It fills blanks with zeros.
     * @return A flow decomposition map. The keys are the XNEC ids and the values are {@code DecomposedFlow} objects.
     */
    public Map<String, DecomposedFlow> getDecomposedFlowsMap(boolean fillZeros) {
        if (!isDecomposedFlowMapCacheValid(fillZeros)) {
            initializeDecomposedFlowMapCache(fillZeros);
        }
        return decomposedFlowMapCache.cacheValue;
    }

    public Map<String, DecomposedFlow> getDecomposedFlowsMap() {
        return getDecomposedFlowsMap(DEFAULT_FILL_ZEROS);
    }

    /**
     * @return A rescaled flow decomposition map. The keys are the XNEC ids and the values are {@code DecomposedFlow} objects. This object is dense.
     */
    public Map<String, DecomposedFlow> getRescaledDecomposedFlowsMap() {
        return rescaledDecomposedFlowMap;
    }

    /**
     * GLSKs are an intermediate results.
     * They are represented as a sparse map of map.
     * The first key is a zone, the second key is a node id and the value is the GLSK of the node in the country.
     * They will be saved if this runner has its argument {@code saveIntermediate} set to {@code true}.
     *
     * @return An optional containing GLSKs.
     */
    public Optional<Map<Country, Double>> getAcNetPositions() {
        return Optional.ofNullable(acNetPosition);
    }

    /**
     * GLSKs are an intermediate results.
     * They are represented as a sparse map of map.
     * The first key is a zone, the second key is a node id and the value is the GLSK of the node in the country.
     * They will be saved if this runner has its argument {@code saveIntermediate} set to {@code true}.
     * @return An optional containing GLSKs.
     */
    public Optional<Map<Country, Map<String, Double>>> getGlsks() {
        return Optional.ofNullable(glsks);
    }

    /**
     * PTDFs are an intermediate results.
     * They will be saved if this runner has its argument {@code saveIntermediate} set to {@code true}.
     * They are represented as a sparse map of map.
     * The first key is a XNEC id, the second key is a node id and the value is the PTDF.
     * @return An optional containing PTDFs
     */
    public Optional<Map<String, Map<String, Double>>> getPtdfMap() {
        return Optional.ofNullable(ptdfMatrix).map(SparseMatrixWithIndexesTriplet::toMap);
    }

    /**
     * PSDFs are an intermediate results.
     * They will be saved if this runner has its argument {@code saveIntermediate} set to {@code true}.
     * They are represented as a sparse map of map.
     * The first key is a XNEC id, the second key is a node id and the value is the PSDF.
     * @return An optional containing PSDFs
     */
    public Optional<Map<String, Map<String, Double>>> getPsdfMap() {
        return Optional.ofNullable(psdfMatrix).map(SparseMatrixWithIndexesTriplet::toMap);
    }

    /**
     * Nodal injections are an intermediate results.
     * They will be saved if this runner has its argument {@code saveIntermediate} set to {@code true}.
     * They are represented as a sparse map of map.
     * The first key is a node id, the second key is a column identifier and the value is the nodal injection.
     * The one of the column id is the {@code "Allocated Flow"}. It corresponds to the allocated nodal injection.
     * The other column ids are Zone Ids as Strings with a prefix {@code "Loop Flow from XX"}.
     * Each column corresponds to the nodal injection in this zone.
     * @param fillZeros Ignore the sparse property of the nodal injections.
     *                  It fills blanks with zeros.
     * @return An optional containing nodal injections
     */
    public Optional<Map<String, Map<String, Double>>> getAllocatedAndLoopFlowNodalInjectionsMap(boolean fillZeros) {
        return Optional.ofNullable(nodalInjectionsMatrix).map(matrix -> matrix.toMap(fillZeros));
    }

    public Optional<Map<String, Map<String, Double>>> getAllocatedAndLoopFlowNodalInjectionsMap() {
        return getAllocatedAndLoopFlowNodalInjectionsMap(DEFAULT_FILL_ZEROS);
    }

    /**
     * DC Nodal injections are an intermediate results.
     * They will be saved if this runner has its argument {@code saveIntermediate} set to {@code true}.
     * They are represented as a map.
     * The key is a node id and the value is the DC nodal injection.
     * @return An optional containing DC nodal injections
     */
    public Optional<Map<String, Double>> getDcNodalInjectionsMap() {
        return Optional.ofNullable(dcNodalInjections);
    }

    static class DecomposedFlowMapCache {
        private final Map<String, DecomposedFlow> cacheValue;
        private final boolean filledWithZeros;

        DecomposedFlowMapCache(Map<String, DecomposedFlow> decomposedFlowMap, boolean fillZeros) {
            this.cacheValue = decomposedFlowMap;
            this.filledWithZeros = fillZeros;
        }

        DecomposedFlowMapCache copy() {
            TreeMap<String, DecomposedFlow> decomposedFlowMap = new TreeMap<>();
            cacheValue.forEach((s, decomposedFlow) -> decomposedFlowMap.put(s, decomposedFlow.copy()));
            return new DecomposedFlowMapCache(decomposedFlowMap, filledWithZeros);
        }
    }

    private boolean isDecomposedFlowMapCacheValid(boolean fillZeros) {
        return Objects.nonNull(decomposedFlowMapCache) && fillZeros == decomposedFlowMapCache.filledWithZeros;
    }

    private void initializeDecomposedFlowMapCache(boolean fillZeros) {
        invalidateDecomposedFlowMapCache();
        Map<String, DecomposedFlow> decomposedFlowsMap = new TreeMap<>();
        allocatedAndLoopFlowsMatrix.toMap(fillZeros)
            .forEach((xnecId, decomposedFlow) -> decomposedFlowsMap.put(xnecId, createDecomposedFlow(xnecId, decomposedFlow)));
        resetDecomposedFlowMapCache(decomposedFlowsMap, fillZeros);
    }

    private void invalidateDecomposedFlowMapCache() {
        this.decomposedFlowMapCache = null;
    }

    private void resetDecomposedFlowMapCache(Map<String, DecomposedFlow> decomposedFlowMap, boolean fillZeros) {
        decomposedFlowMapCache = new DecomposedFlowMapCache(decomposedFlowMap, fillZeros);
    }

    private DecomposedFlow createDecomposedFlow(String xnecId, Map<String, Double> decomposedFlow) {
        return new DecomposedFlow(decomposedFlow, pstFlowMap.get(xnecId),
            acReferenceFlow.get(xnecId), dcReferenceFlow.get(xnecId));
    }

    void setId(String id) {
        this.id = id;
    }

    void saveAllocatedAndLoopFlowsMatrix(SparseMatrixWithIndexesCSC allocatedAndLoopFlowsMatrix) {
        this.allocatedAndLoopFlowsMatrix = allocatedAndLoopFlowsMatrix;
        invalidateDecomposedFlowMapCache();
    }

    void savePstFlowMatrix(SparseMatrixWithIndexesCSC pstFlowMatrix) {
        this.pstFlowMap = pstFlowMatrix.toMap(FILL_ZEROS);
        invalidateDecomposedFlowMapCache();
    }

    void saveAcReferenceFlow(Map<String, Double> acReferenceFlow) {
        this.acReferenceFlow = acReferenceFlow;
        invalidateDecomposedFlowMapCache();
    }

    void saveDcReferenceFlow(Map<String, Double> dcReferenceFlow) {
        this.dcReferenceFlow = dcReferenceFlow;
        invalidateDecomposedFlowMapCache();
    }

    void saveRescaledDecomposedFlowMap(Map<String, DecomposedFlow> decomposedFlowMap) {
        this.rescaledDecomposedFlowMap = decomposedFlowMap;
    }

    void saveACNetPosition(Map<Country, Double> acNetPosition) {
        if (saveIntermediate) {
            this.acNetPosition = acNetPosition;
        }
    }

    void saveGlsks(Map<Country, Map<String, Double>> glsks) {
        if (saveIntermediate) {
            this.glsks = glsks;
        }
    }

    void savePtdfMatrix(SparseMatrixWithIndexesTriplet ptdfMatrix) {
        if (saveIntermediate) {
            this.ptdfMatrix = ptdfMatrix;
        }
    }

    void savePsdfMatrix(SparseMatrixWithIndexesTriplet psdfMatrix) {
        if (saveIntermediate) {
            this.psdfMatrix = psdfMatrix;
        }
    }

    void saveNodalInjectionsMatrix(SparseMatrixWithIndexesTriplet nodalInjectionsMatrix) {
        if (saveIntermediate) {
            this.nodalInjectionsMatrix = nodalInjectionsMatrix;
        }
    }

    void saveDcNodalInjections(Map<String, Double> dcNodalInjections) {
        if (saveIntermediate) {
            this.dcNodalInjections = dcNodalInjections;
        }
    }
}
