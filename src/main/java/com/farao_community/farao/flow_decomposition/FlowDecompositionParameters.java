/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import java.nio.file.Path;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
public class FlowDecompositionParameters {
    static final boolean SAVE_INTERMEDIATE = true;
    static final boolean NOT_SAVE_INTERMEDIATE = false;
    static final boolean ENABLE_EXPORT_RESCALED_RESULTS = true;
    static final boolean DISABLE_EXPORT_RESCALED_RESULTS = false;
    static final double SENSITIVITY_EPSILON = 1e-5;
    static final double NO_SENSITIVITY_EPSILON = -1;
    static final boolean DISABLE_LOSSES_COMPENSATION = false;
    static final boolean ENABLE_LOSSES_COMPENSATION = true;
    static final double LOSSES_COMPENSATION_EPSILON = 1e-5;
    static final double NO_LOSSES_COMPENSATION_EPSILON = -1;
    private static final boolean DEFAULT_SAVE_INTERMEDIATE = NOT_SAVE_INTERMEDIATE;
    private static final boolean DEFAULT_ENABLE_LOSSES_COMPENSATION = DISABLE_LOSSES_COMPENSATION;
    private static final double DEFAULT_LOSSES_COMPENSATION_EPSILON = LOSSES_COMPENSATION_EPSILON;
    private static final double DEFAULT_SENSITIVITY_EPSILON = SENSITIVITY_EPSILON;
    private static final boolean DEFAULT_ENABLE_EXPORT_RESCALED = ENABLE_EXPORT_RESCALED_RESULTS;
    private static final Path DEFAULT_EXPORT_DIR = Path.of("/tmp");
    private boolean saveIntermediate;
    private boolean enableLossesCompensation;
    private double lossesCompensationEpsilon;
    private double sensitivityEpsilon;
    private boolean enableExportRescaled;
    private Path exportDir;

    public FlowDecompositionParameters() {
        this.saveIntermediate = DEFAULT_SAVE_INTERMEDIATE;
        this.enableLossesCompensation = DEFAULT_ENABLE_LOSSES_COMPENSATION;
        this.lossesCompensationEpsilon = DEFAULT_LOSSES_COMPENSATION_EPSILON;
        this.sensitivityEpsilon = DEFAULT_SENSITIVITY_EPSILON;
        this.enableExportRescaled = DEFAULT_ENABLE_EXPORT_RESCALED;
        this.exportDir = DEFAULT_EXPORT_DIR;
    }

    public boolean isSaveIntermediate() {
        return saveIntermediate;
    }

    public void setSaveIntermediate(boolean saveIntermediate) {
        this.saveIntermediate = saveIntermediate;
    }

    public void enableLossesCompensation(boolean enableLossesCompensation) {
        this.enableLossesCompensation = enableLossesCompensation;
    }

    public boolean isLossesCompensationEnabled() {
        return enableLossesCompensation;
    }

    public double getLossesCompensationEpsilon() {
        return lossesCompensationEpsilon;
    }

    public void setLossesCompensationEpsilon(double lossesCompensationEpsilon) {
        this.lossesCompensationEpsilon = lossesCompensationEpsilon;
    }

    public double getSensitivityEpsilon() {
        return sensitivityEpsilon;
    }

    public void setSensitivityEpsilon(double sensitivityEpsilon) {
        this.sensitivityEpsilon = sensitivityEpsilon;
    }

    public boolean isEnableExportRescaled() {
        return enableExportRescaled;
    }

    public void setEnableExportRescaled(boolean enableExportRescaled) {
        this.enableExportRescaled = enableExportRescaled;
    }

    public Path getExportDir() {
        return exportDir;
    }

    public void setExportDir(Path exportDir) {
        this.exportDir = exportDir;
    }
}
