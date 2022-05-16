/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.iidm.import_.Importers;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
public class FlowDecompositonTests {
    private static final double EPSILON = 1e-3;

    @Test
    void checkThatLossesCompensationIsIntegratedInNetworkToSendingEndOfBranch() {
        String networkFileName = "NETWORK_SINGLE_BRANCH.uct";
        Network singleBranchNetwork = Importers.loadNetwork(networkFileName, getClass().getResourceAsStream(networkFileName));
        Branch<?> singleBranch = singleBranchNetwork.getBranch("FGEN  11 FLOAD 11 1");
        Generator generator = singleBranchNetwork.getGenerator("FGEN  11_generator");
        Load load = singleBranchNetwork.getLoad("FLOAD 11_load");
        double targetV = generator.getTargetV();
        double targetP = generator.getTargetP();
        double expectedI = targetP / targetV;
        double expectedLosses = ((Line) singleBranch).getR() * expectedI * expectedI;
        LoadFlowParameters dcLoadFlowParameters = LoadFlowParameters.load();
        dcLoadFlowParameters.setDc(true);
        LoadFlow.run(singleBranchNetwork, dcLoadFlowParameters);
        assertEquals(targetP, singleBranch.getTerminal1().getP(), EPSILON);
        assertEquals(-targetP, generator.getTerminal().getP(), EPSILON);
        assertEquals(targetP, load.getTerminal().getP(), EPSILON);

        LossesCompensationEngine engine = new LossesCompensationEngine(dcLoadFlowParameters);
        engine.compensateLosses(singleBranchNetwork);

        Load lossesLoad = singleBranchNetwork.getLoad("LOSSES FGEN  11 FLOAD 11 1");
        assertNotNull(lossesLoad);
        assertEquals(expectedLosses, lossesLoad.getP0(), EPSILON);
        LoadFlow.run(singleBranchNetwork, dcLoadFlowParameters);
        assertEquals(targetP, singleBranch.getTerminal1().getP(), EPSILON);
        assertEquals(-targetP - expectedLosses, generator.getTerminal().getP(), EPSILON);
        assertEquals(targetP, load.getTerminal().getP(), EPSILON);
    }
}
