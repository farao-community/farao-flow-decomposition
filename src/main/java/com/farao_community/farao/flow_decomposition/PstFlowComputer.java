/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.PhaseTapChanger;
import com.powsybl.iidm.network.PhaseTapChangerStep;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
class PstFlowComputer {
    private static final String PST_COLUMN_NAME = "PST Flow";

    SparseMatrixWithIndexesCSC getPstFlowMatrix(Network network, NetworkIndexes networkIndexes, SparseMatrixWithIndexesTriplet psdfMatrix) {
        SparseMatrixWithIndexesTriplet deltaTapMatrix = getDeltaTapMatrix(network, networkIndexes);
        return SparseMatrixWithIndexesCSC.mult(psdfMatrix.toCSCMatrix(), deltaTapMatrix.toCSCMatrix());
    }

    private SparseMatrixWithIndexesTriplet getDeltaTapMatrix(Network network, NetworkIndexes networkIndexes) {
        SparseMatrixWithIndexesTriplet deltaTapMatrix = new SparseMatrixWithIndexesTriplet(networkIndexes.getPstIndex(), PST_COLUMN_NAME, networkIndexes.getNumberOfPst());
        for (String pst: networkIndexes.getPstList()) {
            PhaseTapChanger phaseTapChanger = network.getTwoWindingsTransformer(pst).getPhaseTapChanger();
            Optional<PhaseTapChangerStep> neutralStep = phaseTapChanger.getNeutralStep();
            double deltaTap = 0.0;
            if (neutralStep.isPresent()) {
                deltaTap = phaseTapChanger.getCurrentStep().getAlpha() - neutralStep.get().getAlpha();
            }
            deltaTapMatrix.addItem(pst, PST_COLUMN_NAME, deltaTap);
        }
        return deltaTapMatrix;
    }
}
