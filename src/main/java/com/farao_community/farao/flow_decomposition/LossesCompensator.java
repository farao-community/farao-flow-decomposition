/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
public class LossesCompensator {
    private final LoadFlowParameters loadFlowParameters;

    public LossesCompensator(LoadFlowParameters initialLoadFlowParameters) {
        this.loadFlowParameters = enforceAcLoadFlowCalculation(initialLoadFlowParameters);
    }

    private LoadFlowParameters enforceAcLoadFlowCalculation(LoadFlowParameters initialLoadFlowParameters) {
        LoadFlowParameters acEnforcedParameters = initialLoadFlowParameters.copy();
        acEnforcedParameters.setDc(false);
        return acEnforcedParameters;
    }

    public void compensateLosses(Network network) {
        LoadFlow.run(network, loadFlowParameters);
        network.getBranchStream().forEach(this::addLoadNode);
    }

    private void addLoadNode(Branch<?> branch) {
        Terminal sendingTerminal = getSendingTerminal(branch);
        sendingTerminal.getVoltageLevel().newLoad()
                .setId("LOSSES " + branch.getId())
                .setBus(sendingTerminal.getBusBreakerView().getBus().getId())
                .setP0(branch.getTerminal1().getP() + branch.getTerminal2().getP())
                .setQ0(0)
                .add();
    }

    private Terminal getSendingTerminal(Branch<?> branch) {
        return branch.getTerminal1().getP() > 0 ? branch.getTerminal1() : branch.getTerminal2();
    }
}
