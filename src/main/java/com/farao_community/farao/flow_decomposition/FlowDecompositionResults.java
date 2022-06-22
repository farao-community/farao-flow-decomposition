/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
class FlowDecompositionResults {
    private Optional<IntermediateFlowDecompositionResults> intermediateResults;
    private SparseMatrixWithIndexesCSC decomposedFlowsMatrix;
    private static final String ALLOCATED_COLUMN_NAME = "Allocated";

    public FlowDecompositionResults(boolean saveIntermediate) {
        if (saveIntermediate) {
            intermediateResults = Optional.of(new IntermediateFlowDecompositionResults());
        } else {
            intermediateResults = Optional.empty();
        }
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

    void setDecomposedFlowsMatrix(SparseMatrixWithIndexesCSC decomposedFlowsMatrix) {
        this.decomposedFlowsMatrix = decomposedFlowsMatrix;
    }

    public boolean hasIntermediateResults() {
        return intermediateResults.isPresent();
    }

    public IntermediateFlowDecompositionResults getIntermediateResults() {
        if (hasIntermediateResults()) {
            return intermediateResults.get();
        }
        return null;
    }
}
