package com.anomalyService.analyzer;

import org.springframework.stereotype.Component;

import com.anomalyService.entity.AnomalyEntity.Severity;

@Component
public class SeverityClassifier {

    public Severity classify(double zScore) {
        if (zScore >= 5.0) return Severity.CRITICAL;
        if (zScore >= 3.5) return Severity.HIGH;
        if (zScore >= 2.5) return Severity.MEDIUM;
        return Severity.LOW;
    }
}
