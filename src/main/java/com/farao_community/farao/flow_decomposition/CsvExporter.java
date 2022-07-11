/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
public class CsvExporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(CsvExporter.class);
    private static final boolean EXPORT_RESCALED_RESULTS = false;
    private static final boolean FILL_ZEROS = true;

    /**
     * Export to CSV
     * @param dirPath path to local directory. The filename will be the result id.
     * @param results results to be saved
     */
    public void export(Path dirPath, FlowDecompositionResults results, Boolean exportRescaledResults) {
        CSVFormat format = CSVFormat.RFC4180;
        String basename = results.getId();
        String networkId = results.getNetworkId();
        Path path = Paths.get(dirPath.toString(), basename + ".csv");
        LOGGER.debug("Saving network {} in file {}", networkId, path);
        try (
            BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
            CSVPrinter printer = new CSVPrinter(writer, format);
        ) {
            Collection<DecomposedFlow> decomposedFlows;
            if (exportRescaledResults) {
                decomposedFlows = results.getRescaledDecomposedFlowsMap(FILL_ZEROS).values();
            } else {
                decomposedFlows = results.getDecomposedFlowsMap(FILL_ZEROS).values();
            }
            printer.print("");
            for (Map.Entry<String, DecomposedFlow> entry : results.getDecomposedFlowsMap(FILL_ZEROS).entrySet()) {
                printer.print(entry.getKey());
            }
            printer.println();
            Set<String> rows = results.getDecomposedFlowsMap().entrySet().iterator().next().getValue().getDecomposedFlowMap().keySet();
            for (String row : rows) {
                printer.print(row);
                for (DecomposedFlow decomposedFlow : decomposedFlows) {
                    printer.print(decomposedFlow.getDecomposedFlowMap().get(row));
                }
                printer.println();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void export(Path dirPath, FlowDecompositionResults results) {
        export(dirPath, results, EXPORT_RESCALED_RESULTS);
    }
}
