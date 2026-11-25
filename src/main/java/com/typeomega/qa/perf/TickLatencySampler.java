package com.typeomega.qa.perf;

import java.util.Arrays;

/**
 * Zero-boxing primitive {@code long[]} latency sampler discarding {@code warmupTicks} and computing exact
 * {@code avg}, {@code p50}, {@code p95}, {@code p99}, and {@code max} in milliseconds.
 */
public final class TickLatencySampler {

    public record Stats(int samples, double avgMs, double p50Ms, double p95Ms, double p99Ms, double maxMs) {}

    private final int warmupTicks;
    private final long[] samplesNanos;
    private int warmupSeen;
    private int count;
    private long totalNanos;
    private long maxNanos;

    public TickLatencySampler(int warmupTicks, int measureTicks) {
        if (warmupTicks < 0) throw new IllegalArgumentException("warmupTicks must be >= 0");
        if (measureTicks < 1) throw new IllegalArgumentException("measureTicks must be >= 1");
        this.warmupTicks = warmupTicks;
        this.samplesNanos = new long[measureTicks];
    }

    public boolean recordNanos(long nanos) {
        if (nanos < 0) nanos = 0;
        if (warmupSeen < warmupTicks) {
            warmupSeen++;
            return false;
        }
        if (count >= samplesNanos.length) return true;
        samplesNanos[count++] = nanos;
        totalNanos += nanos;
        if (nanos > maxNanos) maxNanos = nanos;
        return count >= samplesNanos.length;
    }

    public boolean isComplete() {
        return count >= samplesNanos.length;
    }

    public int sampleCount() {
        return count;
    }

    public Stats compute() {
        if (count == 0) {
            return new Stats(0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }
        long[] sorted = Arrays.copyOf(samplesNanos, count);
        Arrays.sort(sorted);
        double avgMs = (totalNanos / 1_000_000.0) / count;
        double p50Ms = percentile(sorted, 0.50) / 1_000_000.0;
        double p95Ms = percentile(sorted, 0.95) / 1_000_000.0;
        double p99Ms = percentile(sorted, 0.99) / 1_000_000.0;
        double maxMs = maxNanos / 1_000_000.0;
        return new Stats(count, avgMs, p50Ms, p95Ms, p99Ms, maxMs);
    }

    private static long percentile(long[] sorted, double p) {
        int index = (int) Math.ceil(p * sorted.length) - 1;
        if (index < 0) index = 0;
        if (index >= sorted.length) index = sorted.length - 1;
        return sorted[index];
    }
}
