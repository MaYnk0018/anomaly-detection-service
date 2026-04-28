package com.anomalyService.publisher;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.anomalyService.dto.AnomalyMessage;
import com.anomalyService.entity.AnomalyEntity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyPublisher {

    private final KafkaTemplate<String, AnomalyMessage> kafkaTemplate;

    public void publish(AnomalyEntity entity) {
        AnomalyMessage msg = AnomalyMessage.builder()
                .anomalyId(entity.getId())
                .serviceId(entity.getServiceId())
                .detectedAt(entity.getDetectedAt())
                .windowStart(entity.getWindowStart())
                .windowEnd(entity.getWindowEnd())
                .errorCount(entity.getErrorCount())
                .baselineMean(entity.getBaselineMean())
                .baselineStddev(entity.getBaselineStddev())
                .zScore(entity.getZScore())
                .severity(entity.getSeverity())
                .logSample(entity.getLogSample())
                .build();

        kafkaTemplate.send("anomalies", entity.getServiceId(), msg)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish anomaly {}: {}", entity.getId(), ex.getMessage(), ex);
                    }
                });
    }
}
