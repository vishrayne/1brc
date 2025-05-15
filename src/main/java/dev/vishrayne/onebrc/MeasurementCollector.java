package dev.vishrayne.onebrc;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class MeasurementCollector implements Collector<Measurement, // Input element type
        SimpleAggregatorMap<SegmentKey, MeasurementAggregator>, // Accumulator type (our custom map)
        Map<SegmentKey, ResultRow>> /* Final result type */ {

    public static final int INITIAL_MAP_CAPACITY = 2048;

    // Private constructor - use static factory method
    private MeasurementCollector() {
    }

    public static MeasurementCollector create() {
        return new MeasurementCollector();
    }

    @Override
    public Supplier<SimpleAggregatorMap<SegmentKey, MeasurementAggregator>> supplier() {
        return MeasurementCollector::supplySimpleMap;
    }

    @Override
    public BiConsumer<SimpleAggregatorMap<SegmentKey, MeasurementAggregator>, Measurement> accumulator() {
        return MeasurementCollector::acceptNewMeasurement;
    }

    @Override
    public BinaryOperator<SimpleAggregatorMap<SegmentKey, MeasurementAggregator>> combiner() {
        return MeasurementCollector::mergeSimpleMaps;
    }

    @Override
    public Function<SimpleAggregatorMap<SegmentKey, MeasurementAggregator>, Map<SegmentKey, ResultRow>> finisher() {
        return MeasurementCollector::toSortedMap;
    }

    @Override
    public Set<Characteristics> characteristics() {
        // Define the collector's properties
        // See explanation below
        return EnumSet.of(
                // Characteristics.CONCURRENT, // Cannot use - accumulator map is not thread-safe for concurrent writes
                Characteristics.UNORDERED // Input order doesn't matter for the final grouped result
        // Characteristics.IDENTITY_FINISH // Cannot use - finisher transforms the accumulator type
        );
        // If accumulator IS thread-safe AND combiner is just merging, CONCURRENT could be used.
        // If finisher was Function.identity() or accumulator == final result, IDENTITY_FINISH would be used.
    }

    // Creates a new accumulator map for each thread (or for sequential stream)
    private static SimpleAggregatorMap<SegmentKey, MeasurementAggregator> supplySimpleMap() {
        return new SimpleAggregatorMap<>(INITIAL_MAP_CAPACITY);
    }

    // Add a single MeasurementHolder to the map
    private static void acceptNewMeasurement(SimpleAggregatorMap<SegmentKey, MeasurementAggregator> map, Measurement measurement) {
        SegmentKey key = measurement.stationSegmentKey;
        // computeIfAbsent gets or creates the aggregator, then we accumulate the measurement
        MeasurementAggregator aggregator = map.computeIfAbsent(key, k -> new MeasurementAggregator());
        aggregator.accumulate(measurement);
    }

    // Merge two accumulator maps (from different threads)
    private static SimpleAggregatorMap<SegmentKey, MeasurementAggregator> mergeSimpleMaps(SimpleAggregatorMap<SegmentKey, MeasurementAggregator> map1,
                                                                                          SimpleAggregatorMap<SegmentKey, MeasurementAggregator> map2) {
        // Iterate through the second map (map2)
        map2.forEach((key, aggregator2) -> {
            // Try to get the aggregator for the same key from the first map (map1)
            MeasurementAggregator aggregator1 = map1.get(key);
            if (aggregator1 == null) {
                // If key doesn't exist in map1, put aggregator2 from map2 into map1
                map1.put(key, aggregator2);
            }
            else {
                // If key exists in map1, combine aggregator2 into aggregator1
                // Assumes MeasurementAggregator.combine modifies aggregator1 in place
                aggregator1.combine(aggregator2);
                // No need to call map1.put again if combine mutated aggregator1
            }
        });

        // Return the merged map (map1)
        return map1;
    }

    // Transform the final accumulator map into the result map
    private static Map<SegmentKey, ResultRow> toSortedMap(SimpleAggregatorMap<SegmentKey, MeasurementAggregator> accumulatorMap) {
        // Create the final result map, sorted by SegmentKey
        Map<SegmentKey, ResultRow> resultMap = new TreeMap<>();
        // Iterate through the accumulated map
        accumulatorMap.forEach((key, aggregator) -> {
            // Finish each aggregation and put the final ResultRow into the TreeMap
            resultMap.put(key, aggregator.finish()); // Assuming MeasurementAggregator has finish method
        });
        return resultMap;
    }
}
