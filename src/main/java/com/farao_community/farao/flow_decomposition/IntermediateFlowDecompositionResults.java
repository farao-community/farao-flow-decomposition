package com.farao_community.farao.flow_decomposition;

import com.powsybl.iidm.network.Country;

import java.util.Map;

class IntermediateFlowDecompositionResults {
    private final boolean saveIntermediate;
    private Map<Country, Map<String, Double>> glsks;
    private SparseMatrixWithIndexesTriplet nodalInjectionsMatrix;
    private SparseMatrixWithIndexesTriplet ptdfMatrix;

    IntermediateFlowDecompositionResults(boolean saveIntermediate) {
        this.saveIntermediate = saveIntermediate;
    }

    public Map<Country, Map<String, Double>> getGlsks() {
        return glsks;
    }

    void setGlsks(Map<Country, Map<String, Double>> glsks) {
        if (saveIntermediate) {
            this.glsks = glsks;
        }
    }

    public SparseMatrixWithIndexesTriplet getNodalInjectionsMatrix() {
        return nodalInjectionsMatrix;
    }

    void setNodalInjectionsMatrix(SparseMatrixWithIndexesTriplet nodalInjectionsMatrix) {
        if (saveIntermediate) {
            this.nodalInjectionsMatrix = nodalInjectionsMatrix;
        }
    }

    public SparseMatrixWithIndexesTriplet getPtdfMatrix() {
        return ptdfMatrix;
    }

    void setPtdfMatrix(SparseMatrixWithIndexesTriplet ptdfMatrix) {
        if (saveIntermediate) {
            this.ptdfMatrix = ptdfMatrix;
        }
    }
}
