/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import java.util.Optional;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
class FlowDecompositionResults {
    private Optional<IntermediateFlowDecompositionResults> intermediateResults;
    private SparseMatrixWithIndexesCSC allocatedFlowsMatrix;

    public FlowDecompositionResults(boolean saveIntermediate) {
        if (saveIntermediate) {
            intermediateResults = Optional.of(new IntermediateFlowDecompositionResults());
        } else {
            intermediateResults = Optional.empty();
        }
    }

    public SparseMatrixWithIndexesCSC getAllocatedFlowsMatrix() {
        return allocatedFlowsMatrix;
    }

    void setAllocatedFlowsMatrix(SparseMatrixWithIndexesCSC allocatedFlowsMatrix) {
        this.allocatedFlowsMatrix = allocatedFlowsMatrix;
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
