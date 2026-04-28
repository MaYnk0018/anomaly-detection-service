package com.anomalyService.consumer;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.anomalyService.analyzer.SlidingWindowAnalyzer;
import com.anomalyService.analyzer.WindowState;
import com.anomalyService.dto.StoredLogMessage;
import com.anomalyService.entity.AnomalyEntity;
import com.anomalyService.publisher.AnomalyPublisher;
import com.anomalyService.repository.AnomalyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StoredLogConsumer {

    private final SlidingWindowAnalyzer analyzer;
    private final AnomalyRepository anomalyRepository;
    private final AnomalyPublisher anomalyPublisher;

    @KafkaListener(
            topics = "stored-logs",
            groupId = "anomaly-group",
            containerFactory = "storedLogKafkaListenerContainerFactory")
    public void consume(StoredLogMessage msg, Acknowledgment ack) {
        try {
            long timestampMs = msg.getTimestamp()
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli();

            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("timestamp", msg.getTimestamp().toString());
            logEntry.put("level", msg.getLevel().name());
            logEntry.put("message", msg.getMessage());
            if (msg.getMetadata() != null) {
                logEntry.put("metadata", msg.getMetadata());
            }

            analyzer.analyze(msg.getServiceId(), msg.getLevel(), timestampMs, logEntry)
                    .ifPresent(anomaly -> {
                        AnomalyEntity saved = anomalyRepository.save(toEntity(anomaly));
                        anomalyPublisher.publish(saved);
                        log.warn("Anomaly persisted and published: id={} service={} severity={} zScore={}",
                                saved.getId(), saved.getServiceId(), saved.getSeverity(), saved.getZScore());
                    });

            ack.acknowledge();
        } catch (Exception e) {
            log.error("stored-logs processing failed for serviceId={}: {}",
                    msg != null ? msg.getServiceId() : "unknown", e.getMessage(), e);
        }
    }

    private AnomalyEntity toEntity(SlidingWindowAnalyzer.DetectedAnomaly anomaly) {
        WindowState.Stats stats = anomaly.stats();

        AnomalyEntity entity = new AnomalyEntity();
        entity.setServiceId(anomaly.serviceId());
        entity.setDetectedAt(LocalDateTime.now());
        entity.setWindowStart(toLocalDateTime(stats.windowStartMs()));
        entity.setWindowEnd(toLocalDateTime(stats.windowEndMs()));
        entity.setErrorCount(stats.currentCount());
        entity.setBaselineMean(stats.mean());
        entity.setBaselineStddev(stats.stddev());
        entity.setZScore(stats.zScore());
        entity.setSeverity(anomaly.severity());
        entity.setLogSample(anomaly.logSample());
        entity.setStatus(AnomalyEntity.AnomalyStatus.OPEN);
        return entity;
    }

    private static LocalDateTime toLocalDateTime(long epochMs) {
        return LocalDateTime.ofEpochSecond(epochMs / 1000, 0, ZoneOffset.UTC);
    }
}
