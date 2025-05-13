/*
 *  Copyright 2023 The original authors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package dev.morling.onebrc;

import dev.vishrayne.onebrc.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collector;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.groupingBy;

public class CalculateAverage_vishrayne {
    private static final String FILE = "./measurements.txt";

    record Measurement(String station, double value) {
        private Measurement(String[] parts) {
            this(parts[0], Double.parseDouble(parts[1]));
        }
    }

    public static void main(String[] args) throws IOException {
        // sequentialReadTest();
        sequentialMemorySegmentReadTest();
        // calculate();
    }

    // Specialized double parser from Claude
    private static double parseDoubleManually(String s, int startIndex) {
        double result = 0;
        boolean negative = false;
        int i = startIndex;
        int len = s.length();

        if (s.charAt(i) == '-') {
            negative = true;
            i++;
        }

        // Parse the whole number part
        for (; i < len && s.charAt(i) != '.'; i++) {
            result = result * 10 + (s.charAt(i) - '0');
        }

        // Parse decimal part if present
        if (i < len && s.charAt(i) == '.') {
            double factor = 0.1;
            i++;
            for (; i < len; i++) {
                result += (s.charAt(i) - '0') * factor;
                factor *= 0.1;
            }
        }

        return negative ? -result : result;
    }

    private static void sequentialReadTest() throws IOException {
        Collector<Measurement, MeasurementAggregator, ResultRow> collector = Collector.of(
                MeasurementAggregator::new,
                (a, m) -> {
                    a.min = Math.min(a.min, m.value);
                    a.max = Math.max(a.max, m.value);
                    a.sum += m.value;
                    a.count++;
                },
                (agg1, agg2) -> {
                    agg1.combine(agg2);
                    return agg1;
                },
                agg -> new ResultRow(agg.min, (Math.round(agg.sum * 10.0) / 10.0) / agg.count, agg.max));

        try (Stream<String> lines = Files.lines(Paths.get(FILE))) {
            Map<String, ResultRow> measurements = lines
                    .parallel()
                    .map(l -> {
                        int separatorIndex = l.indexOf(';');
                        return new Measurement(
                                l.substring(0, separatorIndex),
                                parseDoubleManually(l, separatorIndex + 1));
                    })
                    .distinct()
                    .collect(groupingBy(CalculateAverage_vishrayne.Measurement::station, TreeMap::new, collector));

            System.out.println(measurements.size());
        }
    }

    private static void sequentialMemorySegmentReadTest() throws IOException {
        MemorySegmentLinesSpliterator spliterator = new MemorySegmentLinesSpliterator(Paths.get(FILE));
        Map<SegmentKey, ResultRow> sortedMeasurements = StreamSupport.stream(spliterator, false)
                .parallel()
                .map(dev.vishrayne.onebrc.Measurement::new)
                .collect(MeasurementCollector.create());

        System.out.println(sortedMeasurements.size());

        spliterator.close();
    }
}
