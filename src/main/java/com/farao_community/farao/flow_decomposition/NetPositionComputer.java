/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler{@literal <hugo.schindler@rte-france.com>}
 */
class NetPositionComputer extends AbstractAcLoadFlowRunner<Map<Country, Double>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetPositionComputer.class);

    public NetPositionComputer(LoadFlowParameters initialLoadFlowParameters) {
        super(initialLoadFlowParameters);
    }

    public Map<Country, Double> run(Network network) {
        LoadFlowResult loadFlowResult = LoadFlow.run(network, loadFlowParameters);
        if (!loadFlowResult.isOk()) {
            LOGGER.error("AC Load Flow diverged !");
        }
        return computeNetPositions(network);
    }

    static Map<Country, Double> computeNetPositions(Network network) {
        Map<Country, Double> netPositions = new EnumMap<>(Country.class);

        network.getDanglingLineStream().forEach(danglingLine -> {
            Country country = NetworkUtil.getTerminalCountry(danglingLine.getTerminal());
            addLeavingFlow(netPositions, danglingLine, country);
        });

        network.getLineStream().forEach(line -> {
            Country countrySide1 = NetworkUtil.getTerminalCountry(line.getTerminal1());
            Country countrySide2 = NetworkUtil.getTerminalCountry(line.getTerminal2());
            if (countrySide1.equals(countrySide2)) {
                return;
            }
            addLeavingFlow(netPositions, line, countrySide1);
            addLeavingFlow(netPositions, line, countrySide2);
        });

        network.getHvdcLineStream().forEach(hvdcLine -> {
            Country countrySide1 = NetworkUtil.getTerminalCountry(hvdcLine.getConverterStation1().getTerminal());
            Country countrySide2 = NetworkUtil.getTerminalCountry(hvdcLine.getConverterStation2().getTerminal());
            if (countrySide1.equals(countrySide2)) {
                return;
            }
            addLeavingFlow(netPositions, hvdcLine, countrySide1);
            addLeavingFlow(netPositions, hvdcLine, countrySide2);
        });

        return netPositions;
    }

    private static void addLeavingFlow(Map<Country, Double> netPositions, DanglingLine danglingLine, Country country) {
        Double previousValue = getPreviousValue(netPositions, country);
        netPositions.put(country, previousValue + getLeavingFlow(danglingLine));
    }

    private static Double getPreviousValue(Map<Country, Double> netPositions, Country country) {
        Double previousValue;
        if (netPositions.get(country) != null) {
            previousValue = netPositions.get(country);
        } else {
            previousValue = (double) 0;
        }
        return previousValue;
    }

    private static void addLeavingFlow(Map<Country, Double> netPositions, Line line, Country country) {
        Double previousValue = getPreviousValue(netPositions, country);
        netPositions.put(country, previousValue + getLeavingFlow(line, country));
    }

    private static void addLeavingFlow(Map<Country, Double> netPositions, HvdcLine hvdcLine, Country country) {
        Double previousValue = getPreviousValue(netPositions, country);
        netPositions.put(country, previousValue + getLeavingFlow(hvdcLine, country));
    }

    private static double getLeavingFlow(DanglingLine danglingLine) {
        return danglingLine.getTerminal().isConnected() && !Double.isNaN(danglingLine.getTerminal().getP()) ? danglingLine.getTerminal().getP() : 0;
    }

    private static double getLeavingFlow(Line line, Country country) {
        double flowSide1 = line.getTerminal1().isConnected() && !Double.isNaN(line.getTerminal1().getP()) ? line.getTerminal1().getP() : 0;
        double flowSide2 = line.getTerminal2().isConnected() && !Double.isNaN(line.getTerminal2().getP()) ? line.getTerminal2().getP() : 0;
        double directFlow = (flowSide1 - flowSide2) / 2;
        return country.equals(NetworkUtil.getTerminalCountry(line.getTerminal1())) ? directFlow : -directFlow;
    }

    private static double getLeavingFlow(HvdcLine hvdcLine, Country country) {
        double flowSide1 = hvdcLine.getConverterStation1().getTerminal().isConnected() && !Double.isNaN(hvdcLine.getConverterStation1().getTerminal().getP()) ? hvdcLine.getConverterStation1().getTerminal().getP() : 0;
        double flowSide2 = hvdcLine.getConverterStation2().getTerminal().isConnected() && !Double.isNaN(hvdcLine.getConverterStation2().getTerminal().getP()) ? hvdcLine.getConverterStation2().getTerminal().getP() : 0;
        double directFlow = (flowSide1 - flowSide2) / 2;
        return country.equals(NetworkUtil.getTerminalCountry(hvdcLine.getConverterStation1().getTerminal())) ? directFlow : -directFlow;
    }
}
