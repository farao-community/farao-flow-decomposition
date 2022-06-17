/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.iidm.network.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author Peter Mitri {@literal <peter.mitri at rte-france.com>}
 */
public class CountryNetPositionComputation {

    private Network network;
    private Map<Country, Double> netPositions;

    public CountryNetPositionComputation(Network network) {
        this.network = network;
    }

    public Map<Country, Double> getNetPositions() {
        if (Objects.isNull(netPositions)) {
            computeNetPositions();
        }
        return netPositions;
    }

    private Country getTerminalCountry(Terminal terminal) {
        return terminal.getVoltageLevel().getSubstation().get().getCountry().get();
    }

    private void computeNetPositions() {
        netPositions = new HashMap<>();

        network.getDanglingLineStream().forEach(danglingLine -> {
            Country country = getTerminalCountry(danglingLine.getTerminal());
            addLeavingFlow(danglingLine, country);
        });

        network.getLineStream().forEach(line -> {
            Country countrySide1 = getTerminalCountry(line.getTerminal1());
            Country countrySide2 = getTerminalCountry(line.getTerminal2());
            if (countrySide1.equals(countrySide2)) {
                return;
            }
            addLeavingFlow(line, countrySide1);
            addLeavingFlow(line, countrySide2);
        });

        network.getHvdcLineStream().forEach(hvdcLine -> {
            Country countrySide1 = getTerminalCountry(hvdcLine.getConverterStation1().getTerminal());
            Country countrySide2 = getTerminalCountry(hvdcLine.getConverterStation2().getTerminal());
            if (countrySide1.equals(countrySide2)) {
                return;
            }
            addLeavingFlow(hvdcLine, countrySide1);
            addLeavingFlow(hvdcLine, countrySide2);
        });
    }

    private void addLeavingFlow(DanglingLine danglingLine, Country country) {
        Double previousValue = getPreviousValue(country);
        if (!Objects.isNull(country)) {
            netPositions.put(country, previousValue + getLeavingFlow(danglingLine));
        }
    }

    private Double getPreviousValue(Country country) {
        Double previousValue;
        if (netPositions.get(country) != null) {
            previousValue = netPositions.get(country);
        } else {
            previousValue = (double) 0;
        }
        return previousValue;
    }

    private void addLeavingFlow(Line line, Country country) {
        Double previousValue = getPreviousValue(country);
        if (!Objects.isNull(country)) {
            netPositions.put(country, previousValue + getLeavingFlow(line, country));
        }
    }

    private void addLeavingFlow(HvdcLine hvdcLine, Country country) {
        Double previousValue = getPreviousValue(country);
        if (!Objects.isNull(country)) {
            netPositions.put(country, previousValue + getLeavingFlow(hvdcLine, country));
        }
    }

    private double getLeavingFlow(DanglingLine danglingLine) {
        return danglingLine.getTerminal().isConnected() && !Double.isNaN(danglingLine.getTerminal().getP()) ? danglingLine.getTerminal().getP() : 0;
    }

    private double getLeavingFlow(Line line, Country country) {
        double flowSide1 = line.getTerminal1().isConnected() && !Double.isNaN(line.getTerminal1().getP()) ? line.getTerminal1().getP() : 0;
        double flowSide2 = line.getTerminal2().isConnected() && !Double.isNaN(line.getTerminal2().getP()) ? line.getTerminal2().getP() : 0;
        double directFlow = (flowSide1 - flowSide2) / 2;
        return country.equals(getTerminalCountry(line.getTerminal1())) ? directFlow : -directFlow;
    }

    private double getLeavingFlow(HvdcLine hvdcLine, Country country) {
        double flowSide1 = hvdcLine.getConverterStation1().getTerminal().isConnected() && !Double.isNaN(hvdcLine.getConverterStation1().getTerminal().getP()) ? hvdcLine.getConverterStation1().getTerminal().getP() : 0;
        double flowSide2 = hvdcLine.getConverterStation2().getTerminal().isConnected() && !Double.isNaN(hvdcLine.getConverterStation2().getTerminal().getP()) ? hvdcLine.getConverterStation2().getTerminal().getP() : 0;
        double directFlow = (flowSide1 - flowSide2) / 2;
        return country.equals(getTerminalCountry(hvdcLine.getConverterStation1().getTerminal())) ? directFlow : -directFlow;
    }
}
