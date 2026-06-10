package com.anomalyService.analyzer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.anomalyService.entity.LogLevel;

/**
 * Sliding window state for one service.
 */
public class WindowState {

    private final String serviceId;
    private final long windowSizeMs;
    private final long bucketSizeMs;

    private final LinkedHashMap<Long, Integer> buckets = new LinkedHashMap<>();
    private final LinkedList<Map<String, Object>> logSample = new LinkedList<>();
    private static final int MAX_SAMPLE_SIZE = 20;
    private Long lastBucketKey;

    public WindowState(String serviceId, int windowSizeMinutes, int bucketSizeSeconds) {
        this.serviceId = serviceId;
        this.windowSizeMs = (long) windowSizeMinutes * 60 * 1000;
        this.bucketSizeMs = (long) bucketSizeSeconds * 1000;
    }
    // Fix anomaly windows to include zero-error buckets
    public void addLog(LogLevel level, long timestampMs, Map<String, Object> logEntry) {
        long currentBucketKey = timestampMs / bucketSizeMs;
        if(lastBucketKey == null){
            lastBucketKey = currentBucketKey;
        } else if (currentBucketKey > lastBucketKey) {
            // Fill in zero-count buckets for any gaps
            for(long k = lastBucketKey+1; k< currentBucketKey;k++){
                buckets.putIfAbsent(k, 0);
            }
            lastBucketKey= currentBucketKey ;
        }
        buckets.putIfAbsent(currentBucketKey, 0);


        if (level == LogLevel.ERROR || level == LogLevel.FATAL) {
            long bucketKey = timestampMs / bucketSizeMs;
            buckets.merge(bucketKey, 1, Integer::sum);

            if (logSample.size() >= MAX_SAMPLE_SIZE) {
                logSample.removeFirst();
            }
            logSample.addLast(logEntry);
        }
        evictStale(timestampMs);
    }

    private void evictStale(long nowMs) {
        long cutoffBucket = (nowMs - windowSizeMs) / bucketSizeMs;
        buckets.entrySet().removeIf(e -> e.getKey() < cutoffBucket);
    }
    
    public Stats getStats(int minDataPoints) {
        if (buckets.size() < minDataPoints + 1) return null;

        //find last inserted bucket
        long currentBucketKey= buckets.keySet().stream().mapToLong(Long::longValue).max().orElseThrow();
        int currentCount = buckets.get(currentBucketKey);
        //finding baseline values
        List<Integer> values = buckets.entrySet().stream().filter(e-> !e.getKey().equals(currentBucketKey)).map(Map.Entry::getValue).toList();

        // List<Integer> values = new ArrayList<>(buckets.values());
        int n = values.size();

        double sum = 0;
        for (int v : values) sum += v;
        double mean = sum / n;

        double variance = 0;
        for (int v : values) variance += Math.pow(v - mean, 2);
        double stddev = Math.sqrt(variance / n);

        //misses a spike after a flat baseline, such as [0,0,0,0] -> 10.
        if (stddev == 0) return null;

        long latestBucketKey = buckets.keySet().stream().mapToLong(Long::longValue).max().orElse(0);
        // int currentCount = buckets.getOrDefault(latestBucketKey, 0);

        double zScore = (currentCount - mean) / stddev;

        long windowEndMs = latestBucketKey * bucketSizeMs + bucketSizeMs;
        long windowStartMs = windowEndMs - windowSizeMs;

        return new Stats(mean, stddev, currentCount, zScore, windowStartMs, windowEndMs);
    }

    public List<Map<String, Object>> getLogSample() {
        return new ArrayList<>(logSample);
    }

    public String getServiceId() {
        return serviceId;
    }

    public record Stats(
            double mean,
            double stddev,
            int currentCount,
            double zScore,
            long windowStartMs,
            long windowEndMs
    ) {}
}
