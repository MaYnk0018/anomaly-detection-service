package com.anomalyService.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.anomalyService.entity.AnomalyEntity.Severity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnomalyMessage {
    private String anomalyId;
    private String serviceId;
    private LocalDateTime detectedAt;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private int errorCount;
    private double baselineMean;
    private double baselineStddev;
    private double zScore;
    private Severity severity;
    private List<Map<String, Object>> logSample;
}
