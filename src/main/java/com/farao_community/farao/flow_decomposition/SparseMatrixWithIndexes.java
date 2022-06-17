package com.farao_community.farao.flow_decomposition;

import org.ejml.data.DMatrixSparse;
import org.ejml.data.DMatrixSparseCSC;
import org.ejml.data.DMatrixSparseTriplet;
import org.ejml.ops.DConvertMatrixStruct;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class SparseMatrixWithIndexes {
    private Map<String, Integer> rowIndex;
    private Map<String, Integer> colIndex;
    private DMatrixSparseTriplet tripletMatrix;
    private DMatrixSparseCSC cscMatrix;

    public SparseMatrixWithIndexes(Map<String, Integer> rowIndex, Map<String, Integer> colIndex, Integer initLength) {
        this.rowIndex = rowIndex;
        this.colIndex = colIndex;
        this.tripletMatrix = new DMatrixSparseTriplet(rowIndex.size(), colIndex.size(), initLength);
    }
    public SparseMatrixWithIndexes(Map<String, Integer> rowIndex, String colName, Integer initLength) {
        this(rowIndex, Map.of(colName, 0), initLength);
    }

    public SparseMatrixWithIndexes(Map<String, Integer> rowIndex, String colName) {
        this(rowIndex, Map.of(colName, 0), 0);
    }

    public void addItem(String row, String col, double value) {
        if (!Double.isNaN(value)) {
            tripletMatrix.addItem(rowIndex.get(row), colIndex.get(col), value);
        }
    }

    public DMatrixSparseCSC getCDCMatrix() {
        if (Objects.isNull(cscMatrix)) {
             cscMatrix = DConvertMatrixStruct.convert(tripletMatrix, (DMatrixSparseCSC) null);
        }
        return cscMatrix;
    }

    public void setTo(DMatrixSparseCSC newCscMatrix) {
        cscMatrix = newCscMatrix;
    }

    private DMatrixSparse getdMatrixSparse() {
        DMatrixSparse matrix;
        if (tripletMatrix.getLength() != 0) {
            matrix = tripletMatrix;
        } else {
            matrix = cscMatrix;
        }
        return matrix;
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
        DMatrixSparse matrix = getdMatrixSparse();
        for (Iterator<DMatrixSparse.CoordinateRealValue> iterator = matrix.createCoordinateIterator(); iterator.hasNext();) {
            DMatrixSparse.CoordinateRealValue cell = iterator.next();
            result.get(rowIndexInversed.get(cell.row)).put(colIndexInversed.get(cell.col), cell.value);
        }
        return result;
    }
}
