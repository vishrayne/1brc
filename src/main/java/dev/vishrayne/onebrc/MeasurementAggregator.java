package dev.vishrayne.onebrc;

public class MeasurementAggregator {
    public double min = Double.POSITIVE_INFINITY;
    public double max = Double.NEGATIVE_INFINITY;
    public double sum = 0;
    public long count = 0;

    public MeasurementAggregator() {
    } // Expected by Collector.of

    // Accumulate a measurement from MeasurementHolder
    public void accumulate(Measurement measurement) {
        double value = measurement.value; // Assuming MeasurementHolder.value exists
        this.sum += value;
        this.count++;
        if (value < this.min) {
            this.min = value;
        }
        if (value > this.max) {
            this.max = value;
        }
    }

    // Combine this aggregator with another
    public MeasurementAggregator combine(MeasurementAggregator other) {
        this.sum += other.sum;
        this.count += other.count;
        if (other.min < this.min) {
            this.min = other.min;
        }
        if (other.max > this.max) {
            this.max = other.max;
        }
        return this; // Return this for chaining if needed, or for the combiner
    }

    public ResultRow finish() {
        if (count == 0)
            return new ResultRow(0, 0, 0); // Or handle as appropriate
        return new ResultRow(min, sum / count, max);
    }

    @Override
    public String toString() { // For debugging
        return (count > 0) ? (min + "/" + (sum / count) + "/" + max + " (" + count + ")") : "empty";
    }
}