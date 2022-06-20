/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import org.ejml.data.DMatrixSparse;
import org.ejml.data.DMatrixSparseCSC;
import org.ejml.sparse.csc.CommonOps_DSCC;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
class SparseMatrixWithIndexesCSC extends SparseMatrixWithIndexes {
    private DMatrixSparseCSC cscMatrix;

    SparseMatrixWithIndexesCSC(Map<String, Integer> rowIndex, Map<String, Integer> colIndex, DMatrixSparseCSC cscMatrix) {
        super(rowIndex, colIndex);
        this.cscMatrix = cscMatrix;
    }
    SparseMatrixWithIndexesCSC(Map<String, Integer> rowIndex, Map<String, Integer> colIndex) {
        this(rowIndex, colIndex, new DMatrixSparseCSC(rowIndex.size(), colIndex.size()));
    }

    SparseMatrixWithIndexesCSC(Map<String, Integer> rowIndex, String colName) {
        this(rowIndex, Map.of(colName, 0));
    }

    private Map<Integer, String> inverseIndex(Map<String, Integer> index) {
        return index.entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
    }

    public Map<String, Map<String, Double>> toMap() {
        Map<String, Map<String, Double>> result = rowIndex
            .entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                stringIntegerEntry -> new HashMap<String, Double>(colIndex.size())
            ));
        Map<Integer, String> colIndexInversed = inverseIndex(colIndex);
        Map<Integer, String> rowIndexInversed = inverseIndex(rowIndex);
        for (Iterator<DMatrixSparse.CoordinateRealValue> iterator = cscMatrix.createCoordinateIterator(); iterator.hasNext(); ) {
            DMatrixSparse.CoordinateRealValue cell = iterator.next();
            result.get(rowIndexInversed.get(cell.row)).put(colIndexInversed.get(cell.col), cell.value);
        }
        return result;
    }

    DMatrixSparseCSC getCSCMatrix() {
        return cscMatrix;
    }

    public void mult(SparseMatrixWithIndexesCSC matrix1, SparseMatrixWithIndexesCSC matrix2) {
        CommonOps_DSCC.mult(matrix1.getCSCMatrix(), matrix2.getCSCMatrix(), this.cscMatrix);
    }
}