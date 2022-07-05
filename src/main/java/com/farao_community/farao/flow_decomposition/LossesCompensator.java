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
import com.powsybl.iidm.network.TieLine;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
class LossesCompensator extends AbstractAcLoadFlowRunner<Void> {
    LossesCompensator(LoadFlowParameters initialLoadFlowParameters) {
        super(initialLoadFlowParameters);
    }

    Void run(Network network) {
        LoadFlow.run(network, loadFlowParameters);
        network.getBranchStream().forEach(this::compensateLossesOnBranch);
        return null;
    }

    private void compensateLossesOnBranch(Branch<?> branch) {
        if (branch instanceof TieLine) {
            compensateLossesOnTieLine((TieLine) branch);
        } else {
            Terminal sendingTerminal = getSendingTerminal(branch);
            String lossesId = "LOSSES " + branch.getId();
            double losses = branch.getTerminal1().getP() + branch.getTerminal2().getP();
            createLoadForLossesOnTerminal(sendingTerminal, lossesId, losses);
        }
    }

    private void compensateLossesOnTieLine(TieLine tieLine) {
        double r1 = tieLine.getHalf1().getR();
        double r2 = tieLine.getHalf2().getR();
        double r = r1 + r2;
        Terminal terminal1 = tieLine.getTerminal1();
        Terminal terminal2 = tieLine.getTerminal2();
        double losses = terminal1.getP() + terminal2.getP();
        double lossesSide1 = losses * r1 / r;
        double lossesSide2 = losses * r2 / r;
        String lossesIdSide1 = "LOSSES " + tieLine.getHalf1().getId();
        String lossesIdSide2 = "LOSSES " + tieLine.getHalf2().getId();

        createLoadForLossesOnTerminal(terminal1, lossesIdSide1, lossesSide1);
        createLoadForLossesOnTerminal(terminal2, lossesIdSide2, lossesSide2);
    }

    private void createLoadForLossesOnTerminal(Terminal terminal, String lossesId, double losses) {
        terminal.getVoltageLevel().newLoad()
                .setId(lossesId)
                .setBus(terminal.getBusBreakerView().getBus().getId())
                .setP0(losses)
                .setQ0(0)
                .add();
    }

    private Terminal getSendingTerminal(Branch<?> branch) {
        return branch.getTerminal1().getP() > 0 ? branch.getTerminal1() : branch.getTerminal2();
    }
}
