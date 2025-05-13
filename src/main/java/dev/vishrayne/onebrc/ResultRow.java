package dev.vishrayne.onebrc;

public record ResultRow(double min, double mean, double max) implements Comparable<ResultRow> {
    @Override
    public String toString() {
        return round(min) + "/" + round(mean) + "/" + round(max);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    // Example comparison, useful if ResultRow itself needs sorting later
    @Override
    public int compareTo(ResultRow other) {
        return Double.compare(this.mean, other.mean);
    }
}