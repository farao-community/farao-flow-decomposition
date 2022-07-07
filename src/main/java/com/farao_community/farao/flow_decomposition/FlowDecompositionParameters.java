/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
public class FlowDecompositionParameters {
    private static final boolean DEFAULT_ENABLE_LOSSES_COMPENSATION = false;
    private static final double DEFAULT_LOSSES_COMPENSATION_EPSILON = 1e-5;
    private static final double DEFAULT_SENSITIVITY_EPSILON = 1e-5;
    private boolean enableLossesCompensation;
    private double lossCompensationEpsilon;
    private double sensitivityEpsilon;

    public FlowDecompositionParameters() {
        this.enableLossesCompensation = DEFAULT_ENABLE_LOSSES_COMPENSATION;
        this.lossCompensationEpsilon = DEFAULT_LOSSES_COMPENSATION_EPSILON;
        this.sensitivityEpsilon = DEFAULT_SENSITIVITY_EPSILON;
    }

    public void enableLossesCompensation(boolean enableLossesCompensation) {
        this.enableLossesCompensation = enableLossesCompensation;
    }

    public boolean lossesCompensationEnabled() {
        return enableLossesCompensation;
    }

    public double getLossCompensationEpsilon() {
        return lossCompensationEpsilon;
    }

    public void setLossCompensationEpsilon(double lossCompensationEpsilon) {
        this.lossCompensationEpsilon = lossCompensationEpsilon;
    }

    public double getSensitivityEpsilon() {
        return sensitivityEpsilon;
    }

    public void setSensitivityEpsilon(double sensitivityEpsilon) {
        this.sensitivityEpsilon = sensitivityEpsilon;
    }
}
