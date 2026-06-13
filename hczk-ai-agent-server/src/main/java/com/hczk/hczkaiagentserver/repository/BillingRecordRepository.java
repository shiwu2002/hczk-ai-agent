package com.hczk.hczkaiagentserver.repository;

import com.hczk.hczkaiagentserver.entity.BillingRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BillingRecordRepository extends JpaRepository<BillingRecord, Long> {
    List<BillingRecord> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<BillingRecord> findByUserId(Long userId, Pageable pageable);

    List<BillingRecord> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
