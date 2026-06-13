package com.hczk.hczkaiagentserver.repository;

import com.hczk.hczkaiagentserver.entity.RechargeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RechargeRecordRepository extends JpaRepository<RechargeRecord, Long> {
    List<RechargeRecord> findByUserIdOrderByCreatedAtDesc(Long userId);
}
