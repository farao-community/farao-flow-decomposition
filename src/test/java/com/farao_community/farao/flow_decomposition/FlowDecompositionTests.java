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
class FlowDecompositionTests {
    private static final double EPSILON = 1e-3;

    @Test
    void checkThatLossesCompensationIsIntegratedInNetworkToSendingEndOfBranch() {
        String networkFileName = "NETWORK_SINGLE_LOAD_TWO_GENERATORS.uct";
        Network singleLoadTwoGeneratorsNetwork = Importers.loadNetwork(networkFileName, getClass().getResourceAsStream(networkFileName));
        Branch<?> branchWithGeneratorOnTerminal1 = singleLoadTwoGeneratorsNetwork.getBranch("FGEN1 11 FLOAD 11 1");
        Branch<?> branchWithGeneratorOnTerminal2 = singleLoadTwoGeneratorsNetwork.getBranch("FLOAD 11 FGEN2 11 1");
        Generator generatorFromBranch1 = singleLoadTwoGeneratorsNetwork.getGenerator("FGEN1 11_generator");
        Generator generatorFromBranch2 = singleLoadTwoGeneratorsNetwork.getGenerator("FGEN2 11_generator");
        Load CentralLoad = singleLoadTwoGeneratorsNetwork.getLoad("FLOAD 11_load");
        double targetV = generatorFromBranch1.getTargetV();
        double targetP = generatorFromBranch1.getTargetP();
        double expectedI = targetP / targetV;
        double expectedLossesOnASingleLine = ((Line) branchWithGeneratorOnTerminal1).getR() * expectedI * expectedI;
        LoadFlowParameters dcLoadFlowParameters = LoadFlowParameters.load();
        dcLoadFlowParameters.setDc(true);
        LoadFlow.run(singleLoadTwoGeneratorsNetwork, dcLoadFlowParameters);
        assertEquals(targetP, branchWithGeneratorOnTerminal1.getTerminal1().getP(), EPSILON);
        assertEquals(targetP, branchWithGeneratorOnTerminal2.getTerminal2().getP(), EPSILON);
        assertEquals(-targetP, generatorFromBranch1.getTerminal().getP(), EPSILON);
        assertEquals(-targetP, generatorFromBranch2.getTerminal().getP(), EPSILON);
        assertEquals(targetP * 2, CentralLoad.getTerminal().getP(), EPSILON);

        LossesCompensationEngine engine = new LossesCompensationEngine(dcLoadFlowParameters);
        engine.compensateLosses(singleLoadTwoGeneratorsNetwork);

        Load lossesLoadFromBranch1 = singleLoadTwoGeneratorsNetwork.getLoad("LOSSES FGEN1 11 FLOAD 11 1");
        assertNotNull(lossesLoadFromBranch1);
        assertEquals(expectedLossesOnASingleLine, lossesLoadFromBranch1.getP0(), EPSILON);
        Load lossesLoadFromBranch2 = singleLoadTwoGeneratorsNetwork.getLoad("LOSSES FLOAD 11 FGEN2 11 1");
        assertNotNull(lossesLoadFromBranch2);
        assertEquals(expectedLossesOnASingleLine, lossesLoadFromBranch2.getP0(), EPSILON);
        LoadFlow.run(singleLoadTwoGeneratorsNetwork, dcLoadFlowParameters);
        assertEquals(targetP, branchWithGeneratorOnTerminal1.getTerminal1().getP(), EPSILON);
        assertEquals(targetP, branchWithGeneratorOnTerminal2.getTerminal2().getP(), EPSILON);
        assertEquals(-targetP - expectedLossesOnASingleLine, generatorFromBranch1.getTerminal().getP(), EPSILON);
        assertEquals(-targetP - expectedLossesOnASingleLine, generatorFromBranch2.getTerminal().getP(), EPSILON);
        assertEquals(targetP * 2, CentralLoad.getTerminal().getP(), EPSILON);
    }
}
