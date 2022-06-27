/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.iidm.network.Country;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
class FlowDecompositionResults {
    private final boolean saveIntermediates;
    private SparseMatrixWithIndexesCSC decomposedFlowsMatrix;
    private static final String ALLOCATED_COLUMN_NAME = "Allocated";
    private Map<Country, Map<String, Double>> glsks;
    private SparseMatrixWithIndexesTriplet nodalInjectionsMatrix;
    private SparseMatrixWithIndexesTriplet ptdfMatrix;

    FlowDecompositionResults(boolean saveIntermediates) {
        this.saveIntermediates = saveIntermediates;
    }

    public FlowDecompositionResults() {
        this(false);
    }

    public Map<String, DecomposedFlow> getDecomposedFlowsMap() {
        Map<String, Map<String, Double>> decomposedFlowsMapMap = decomposedFlowsMatrix.toMap();
        return decomposedFlowsMapMap.keySet()
            .stream()
            .collect(Collectors.toMap(
                Function.identity(),
                xnec -> new DecomposedFlow(decomposedFlowsMapMap.get(xnec).get(ALLOCATED_COLUMN_NAME)),
                (decomposedFlow, decomposedFlow2) -> decomposedFlow,
                TreeMap::new
            ));
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

    Map<Country, Map<String, Double>> getGlsks() {
        return glsks;
    }

    Map<String, Map<String, Double>> getNodalInjectionsMap() {
        return nodalInjectionsMatrix.toMap();
    }

    Map<String, Map<String, Double>> getPtdfMap() {
        return ptdfMatrix.toMap();
    }
}
