package com.anomalyService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.anomalyService.analyzer.WindowState;
import com.anomalyService.entity.LogLevel;

class WindowStateTest {

    private static final int WINDOW_MINUTES = 10;
    private static final int BUCKET_SECONDS = 60;
    private static final int MIN_DATA_POINTS = 5;
    private static final long BUCKET_MS = 60_000L;

    private WindowState window;

    @BeforeEach
    void setUp() {
        window = new WindowState("test-service", WINDOW_MINUTES, BUCKET_SECONDS);
    }

    @Test
    @DisplayName("Returns null before min data points are reached")
    void coldStartReturnsNull() {
        addErrors(3, 0 * BUCKET_MS, 5);
        addErrors(3, 1 * BUCKET_MS, 5);
        addErrors(3, 2 * BUCKET_MS, 5);

        WindowState.Stats stats = window.getStats(MIN_DATA_POINTS);

        assertThat(stats).isNull();
    }

    @Test
    @DisplayName("Returns null when stddev is zero")
    void flatLineReturnsNull() {
        for (int i = 0; i < MIN_DATA_POINTS + 1; i++) {
            addErrors(5, (long) i * BUCKET_MS, 5);
        }

        WindowState.Stats stats = window.getStats(MIN_DATA_POINTS);

        assertThat(stats).isNull();
    }

    @Test
    @DisplayName("Z-score is unavailable when all buckets match")
    void zScoreUnavailableWhenCurrentMatchesMean() {
        for (int i = 0; i < 5; i++) {
            addErrors(10, (long) i * BUCKET_MS, 10);
        }
        addErrors(10, 5 * BUCKET_MS, 10);

        WindowState.Stats stats = window.getStats(MIN_DATA_POINTS);

        assertThat(stats).isNull();
    }

    @Test
    @DisplayName("Detects a clear spike")
    void detectsSpike() {
        int[] baseline = {1, 2, 1, 2, 1};
        for (int i = 0; i < baseline.length; i++) {
            addErrors(baseline[i], (long) i * BUCKET_MS, baseline[i]);
        }

        addErrors(50, 5 * BUCKET_MS, 50);

        WindowState.Stats stats = window.getStats(MIN_DATA_POINTS);

        assertThat(stats).isNotNull();
        assertThat(stats.currentCount()).isEqualTo(50);
        assertThat(stats.zScore()).isGreaterThan(2.0);
        assertThat(stats.mean()).isCloseTo(1.4, within(0.01));
    }

    @Test
    @DisplayName("Current bucket is excluded from the baseline")
    void excludesCurrentBucketFromBaseline() {
        int[] baseline = {1, 2, 1, 2, 1};
        for (int i = 0; i < baseline.length; i++) {
            addErrors(baseline[i], (long) i * BUCKET_MS, baseline[i]);
        }
        addErrors(100, 5 * BUCKET_MS, 100);

        WindowState.Stats stats = window.getStats(MIN_DATA_POINTS);

        assertThat(stats).isNotNull();
        assertThat(stats.mean()).isCloseTo(1.4, within(0.01));
        assertThat(stats.currentCount()).isEqualTo(100);
    }

    @Test
    @DisplayName("Fills skipped and current buckets with zero errors")
    void fillsZeroErrorBuckets() {
        addErrors(1, 0L, 1);
        addErrors(2, BUCKET_MS, 2);
        window.addLog(LogLevel.INFO, 5 * BUCKET_MS, Map.of("level", "INFO"));

        WindowState.Stats stats = window.getStats(MIN_DATA_POINTS);

        assertThat(stats).isNotNull();
        assertThat(stats.currentCount()).isZero();
        assertThat(stats.mean()).isCloseTo(0.6, within(0.01));
    }

    @Test
    @DisplayName("Stale buckets are evicted outside the window")
    void evictsStaleBuckets() {
        long now = 60 * BUCKET_MS;

        addErrors(100, 0L, 100);
        addErrors(100, 1 * BUCKET_MS, 100);

        for (int i = 51; i <= 60; i++) {
            int count = i % 2 == 0 ? 5 : 6;
            addErrors(count, (long) i * BUCKET_MS, count);
        }

        window.addLog(LogLevel.ERROR, now, Map.of());

        WindowState.Stats stats = window.getStats(MIN_DATA_POINTS);

        assertThat(stats).isNotNull();
        assertThat(stats.mean()).isLessThan(10.0);
    }

    @Test
    @DisplayName("INFO and WARN logs do not affect error bucket counts")
    void nonErrorLevelsDoNotIncrement() {
        for (int i = 0; i < MIN_DATA_POINTS + 2; i++) {
            window.addLog(LogLevel.INFO, (long) i * BUCKET_MS, Map.of("level", "INFO"));
            window.addLog(LogLevel.WARN, (long) i * BUCKET_MS, Map.of("level", "WARN"));
        }

        WindowState.Stats stats = window.getStats(MIN_DATA_POINTS);

        assertThat(stats).isNull();
    }

    @Test
    @DisplayName("FATAL logs are counted the same as ERROR")
    void fatalLogsAreCountedAsErrors() {
        int[] baseline = {1, 2, 1, 2, 1};
        for (int i = 0; i < baseline.length; i++) {
            addErrors(baseline[i], (long) i * BUCKET_MS, baseline[i]);
        }

        long spikeTime = 5 * BUCKET_MS;
        for (int i = 0; i < 50; i++) {
            window.addLog(LogLevel.FATAL, spikeTime, Map.of("i", i));
        }

        WindowState.Stats stats = window.getStats(MIN_DATA_POINTS);

        assertThat(stats).isNotNull();
        assertThat(stats.currentCount()).isEqualTo(50);
        assertThat(stats.zScore()).isGreaterThanOrEqualTo(2.0);
    }

    @Test
    @DisplayName("Log sample is capped at 20 entries")
    void logSampleCappedAt20() {
        long t = 0;
        for (int i = 0; i < 50; i++) {
            window.addLog(LogLevel.ERROR, t, Map.of("i", i));
        }

        assertThat(window.getLogSample()).hasSize(20);
    }

    private void addErrors(int count, long timestampMs, int label) {
        for (int i = 0; i < count; i++) {
            window.addLog(LogLevel.ERROR, timestampMs, Map.of("label", label, "i", i));
        }
    }
}
