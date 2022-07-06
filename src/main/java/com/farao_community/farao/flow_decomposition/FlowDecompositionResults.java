/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.iidm.network.Country;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
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
    private final boolean saveIntermediates;
    private SparseMatrixWithIndexesCSC allocatedAndLoopFlowsMatrix;
    private Map<String, Map<String, Double>> pstFlowMap;
    private Map<Country, Map<String, Double>> glsks;
    private SparseMatrixWithIndexesTriplet ptdfMatrix;
    private SparseMatrixWithIndexesTriplet psdfMatrix;
    private SparseMatrixWithIndexesTriplet nodalInjectionsMatrix;
    private Map<String, Double> dcNodalInjections;
    private DecomposedFlowMapCache decomposedFlowMapCache;

    FlowDecompositionResults(boolean saveIntermediates) {
        this.saveIntermediates = saveIntermediates;
    }

    /**
     * @param fillZeros Ignore the sparse property of the flow decomposition.
     *                  It fills blanks with zeros.
     * @return A flow decomposition map. The keys are the XNEC ids and the values are {@code DecomposedFlow} objects.
     */
    public Map<String, DecomposedFlow> getDecomposedFlowsMap(boolean fillZeros) {
        if (isDecomposedFlowMapCacheValid(fillZeros)) {
            return decomposedFlowMapCache.cacheValue;
        }
        invalidateDecomposedFlowMapCache();
        Map<String, DecomposedFlow> decomposedFlowMap = allocatedAndLoopFlowsMatrix.toMap(fillZeros).entrySet()
            .stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                this::flowPartsMapToDecomposedFlow));
        resetDecomposedFlowMapCache(decomposedFlowMap, fillZeros);
        return decomposedFlowMap;
    }

    public Map<String, DecomposedFlow> getDecomposedFlowsMap() {
        return getDecomposedFlowsMap(false);
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
     * The one of the column id is the {@code "Allocated"}. It corresponds to the allocated nodal injection.
     * The other column ids are Zone Ids as Strings. Each column corresponds to the nodal injection in this zone.
     * @param fillZeros Ignore the sparse property of the nodal injections.
     *                  It fills blanks with zeros.
     * @return An optional containing nodal injections
     */
    public Optional<Map<String, Map<String, Double>>> getNodalInjectionsMap(boolean fillZeros) {
        return Optional.ofNullable(nodalInjectionsMatrix).map(matrix -> matrix.toMap(fillZeros));
    }

    public Optional<Map<String, Map<String, Double>>> getNodalInjectionsMap() {
        return getNodalInjectionsMap(false);
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

        public DecomposedFlowMapCache(Map<String, DecomposedFlow> decomposedFlowMap, boolean fillZeros) {
            this.cacheValue = decomposedFlowMap;
            this.filledWithZeros = fillZeros;
        }
    }

    private boolean isDecomposedFlowMapCacheValid(boolean fillZeros) {
        return Objects.nonNull(decomposedFlowMapCache) && fillZeros == decomposedFlowMapCache.filledWithZeros;
    }

    private void invalidateDecomposedFlowMapCache() {
        this.decomposedFlowMapCache = null;
    }

    private void resetDecomposedFlowMapCache(Map<String, DecomposedFlow> decomposedFlowMap, boolean fillZeros) {
        decomposedFlowMapCache = new DecomposedFlowMapCache(decomposedFlowMap, fillZeros);
    }

    private DecomposedFlow flowPartsMapToDecomposedFlow(Map.Entry<String, Map<String, Double>> entry) {
        return new DecomposedFlow(entry.getValue(), pstFlowMap.get(entry.getKey()));
    }

    void saveAllocatedAndLoopFlowsMatrix(SparseMatrixWithIndexesCSC allocatedAndLoopFlowsMatrix) {
        this.allocatedAndLoopFlowsMatrix = allocatedAndLoopFlowsMatrix;
        invalidateDecomposedFlowMapCache();
    }

    void savePstFlowMatrix(SparseMatrixWithIndexesCSC pstFlowMatrix) {
        this.pstFlowMap = pstFlowMatrix.toMap(true);
        invalidateDecomposedFlowMapCache();
    }

    void saveGlsks(Map<Country, Map<String, Double>> glsks) {
        if (saveIntermediates) {
            this.glsks = glsks;
        }
    }

    void savePtdfMatrix(SparseMatrixWithIndexesTriplet ptdfMatrix) {
        if (saveIntermediates) {
            this.ptdfMatrix = ptdfMatrix;
        }
    }

    void savePsdfMatrix(SparseMatrixWithIndexesTriplet psdfMatrix) {
        if (saveIntermediates) {
            this.psdfMatrix = psdfMatrix;
        }
    }

    void saveNodalInjectionsMatrix(SparseMatrixWithIndexesTriplet nodalInjectionsMatrix) {
        if (saveIntermediates) {
            this.nodalInjectionsMatrix = nodalInjectionsMatrix;
        }
    }

    public void saveDcNodalInjections(Map<String, Double> dcNodalInjections) {
        if (saveIntermediates) {
            this.dcNodalInjections = dcNodalInjections;
        }
    }
}
