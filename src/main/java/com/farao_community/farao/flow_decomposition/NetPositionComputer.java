/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;

import java.util.Map;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 */
public class NetPositionComputer extends AbstractAcLoadFlowRunner<Map<Country, Double>> {

    public NetPositionComputer(LoadFlowParameters initialLoadFlowParameters) {
        super(initialLoadFlowParameters);
    }

    public Map<Country, Double> run(Network network) {
        LoadFlow.run(network, loadFlowParameters);
        return NetworkUtil.computeNetPositions(network);
    }
}
