/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Country;

import java.util.Map;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
public class DecomposedFlow {
    Map<String, Double> decomposedFlowMap;
    private static final String ALLOCATED_COLUMN_NAME = "Allocated";

    DecomposedFlow(Map<String, Double> allocatedFlow) {
        this.decomposedFlowMap = allocatedFlow;
    }

    public Double getAllocatedFlow() {
        return decomposedFlowMap.get(ALLOCATED_COLUMN_NAME);
    }

    public double getLoopFlow(Country country) {
        if (!decomposedFlowMap.containsKey(country.toString())) {
            throw new PowsyblException("Country has to be present in the network");
        }
        return decomposedFlowMap.get(country.toString());
    }
}
