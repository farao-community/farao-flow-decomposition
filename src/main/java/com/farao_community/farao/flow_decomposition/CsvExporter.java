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
class CsvExporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(CsvExporter.class);

    /**
     * Export to CSV
     * @param dirPath path to local directory
     * @param results results to be saved
     */
    void export(Path dirPath, String basename, Map<String, DecomposedFlow> results) {
        CSVFormat format = CSVFormat.RFC4180;
        Path path = Paths.get(dirPath.toString(), basename + ".csv");
        LOGGER.debug("Saving flow decomposition results in file {}", path);
        try (
            BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
            CSVPrinter printer = new CSVPrinter(writer, format);
        ) {
            Collection<DecomposedFlow> decomposedFlows = results.values();
            printer.print("");
            for (Map.Entry<String, DecomposedFlow> xnecId : results.entrySet()) {
                printer.print(xnecId.getKey());
            }
            printer.println();
            Set<String> columns = results.entrySet().iterator().next().getValue().keySet();
            for (String key : columns) {
                printer.print(key);
                for (DecomposedFlow decomposedFlow : decomposedFlows) {
                    printer.print(decomposedFlow.get(key));
                }
                printer.println();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void export(FlowDecompositionParameters parameters, FlowDecompositionResults flowDecompositionResults) {
        export(parameters.getExportDir(), flowDecompositionResults.getId(), flowDecompositionResults.getDecomposedFlowsMap());
    }

    void export(FlowDecompositionParameters parameters, FlowDecompositionResults flowDecompositionResults, String prefix) {
        export(parameters.getExportDir(), prefix + flowDecompositionResults.getId(),
            flowDecompositionResults.getDecomposedFlowsMap());
    }
}
