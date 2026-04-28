package com.anomalyService.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.anomalyService.entity.AnomalyEntity;

@Repository
public interface AnomalyRepository extends JpaRepository<AnomalyEntity, String> {

    Page<AnomalyEntity> findAllByOrderByDetectedAtDesc(Pageable pageable);

    Page<AnomalyEntity> findByServiceIdOrderByDetectedAtDesc(String serviceId, Pageable pageable);

    List<AnomalyEntity> findByServiceIdAndDetectedAtAfterOrderByDetectedAtDesc(
            String serviceId, LocalDateTime after);
}
