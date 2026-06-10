package com.anomalyService.analyzer;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.anomalyService.entity.AnomalyEntity.Severity;
import com.anomalyService.entity.LogLevel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlidingWindowAnalyzer {

    private final SeverityClassifier severityClassifier;

//     private final WindowState windowState;
    @Value("${logmind.anomaly.window-size-minutes:60}")
    private int windowSizeMinutes;

    @Value("${logmind.anomaly.bucket-size-seconds:60}")
    private int bucketSizeSeconds;

    @Value("${logmind.anomaly.z-score-threshold:2.5}")
    private double zScoreThreshold;

    @Value("${logmind.anomaly.min-data-points:20}")
    private int minDataPoints;

    private final ConcurrentHashMap<String, WindowState> windows = new ConcurrentHashMap<>();

    public Optional<DetectedAnomaly> analyze(
            String serviceId,
            LogLevel level,
            long timestampMs,
            Map<String, Object> logEntry) {

        WindowState window = windows.computeIfAbsent(
                serviceId,
                id -> new WindowState(id, windowSizeMinutes, bucketSizeSeconds));

        window.addLog(level, timestampMs, logEntry);

        WindowState.Stats stats = window.getStats(minDataPoints);
        if (stats == null) return Optional.empty();
        if (stats.zScore() <= zScoreThreshold) return Optional.empty();

        log.warn("Anomaly detected: service={} zScore={} currentCount={} mean={}",
                serviceId, String.format("%.2f", stats.zScore()), stats.currentCount(), stats.mean());

        return Optional.of(new DetectedAnomaly(
                serviceId,
                stats,
                severityClassifier.classify(stats.zScore()),
                window.getLogSample()));
    }

    public record DetectedAnomaly(
            String serviceId,
            WindowState.Stats stats,
            Severity severity,
            java.util.List<Map<String, Object>> logSample
    ) {}
}
