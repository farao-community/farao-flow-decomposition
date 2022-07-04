/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.iidm.network.Country;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
public class FlowDecompositionResults {
    private final boolean saveIntermediates;
    private SparseMatrixWithIndexesCSC decomposedFlowsMatrix;
    private Map<Country, Map<String, Double>> glsks;
    private SparseMatrixWithIndexesTriplet nodalInjectionsMatrix;
    private SparseMatrixWithIndexesTriplet ptdfMatrix;
    private Map<String, Double> dcNodalInjections;

    FlowDecompositionResults(boolean saveIntermediates) {
        this.saveIntermediates = saveIntermediates;
    }

    public Map<String, DecomposedFlow> getDecomposedFlowsMap(boolean fillZeros) {
        return decomposedFlowsMatrix.toMap(fillZeros).entrySet()
            .stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> flowPartsMapToDecomposedFlow(entry.getValue())));
    }

    public Map<String, DecomposedFlow> getDecomposedFlowsMap() {
        return getDecomposedFlowsMap(false);
    }

    private DecomposedFlow flowPartsMapToDecomposedFlow(Map<String, Double> flowPartsMap) {
        return new DecomposedFlow(flowPartsMap);
    }

    void saveDecomposedFlowsMatrix(SparseMatrixWithIndexesCSC decomposedFlowsMatrix) {
        this.decomposedFlowsMatrix = decomposedFlowsMatrix;
    }

    void saveGlsks(Map<Country, Map<String, Double>> glsks) {
        if (saveIntermediates) {
            this.glsks = glsks;
        }
    }

    void saveNodalInjectionsMatrix(SparseMatrixWithIndexesTriplet nodalInjectionsMatrix) {
        if (saveIntermediates) {
            this.nodalInjectionsMatrix = nodalInjectionsMatrix;
        }
    }

    void savePtdfMatrix(SparseMatrixWithIndexesTriplet ptdfMatrix) {
        if (saveIntermediates) {
            this.ptdfMatrix = ptdfMatrix;
        }
    }

    Optional<Map<Country, Map<String, Double>>> getGlsks() {
        return Optional.ofNullable(glsks);
    }

    Optional<Map<String, Map<String, Double>>> getNodalInjectionsMap(boolean fillZeros) {
        return Optional.ofNullable(nodalInjectionsMatrix).map(matrix -> matrix.toMap(fillZeros));
    }

    Optional<Map<String, Map<String, Double>>> getNodalInjectionsMap() {
        return getNodalInjectionsMap(false);
    }

    Optional<Map<String, Map<String, Double>>> getPtdfMap() {
        return Optional.ofNullable(ptdfMatrix).map(SparseMatrixWithIndexesTriplet::toMap);
    }

    Optional<Map<String, Double>> getDcNodalInjectionsMap() {
        return Optional.ofNullable(dcNodalInjections);
    }

    public void saveDcNodalInjections(Map<String, Double> dcNodalInjections) {
        this.dcNodalInjections = dcNodalInjections;
    }
}
