package com.anomalyService.dto;

import java.time.LocalDateTime;
import java.util.Map;

import com.anomalyService.entity.LogLevel;

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
public class StoredLogMessage {
    private Long logDbId;
    private String serviceId;
    private String serviceName;
    private LogLevel level;
    private String message;
    private Map<String, Object> metadata;
    private LocalDateTime timestamp;
}
