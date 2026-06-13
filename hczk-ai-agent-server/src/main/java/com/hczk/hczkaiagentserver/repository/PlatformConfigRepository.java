package com.hczk.hczkaiagentserver.repository;

import com.hczk.hczkaiagentserver.entity.PlatformConfig;
import com.hczk.hczkaiagentserver.enums.PlatformType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlatformConfigRepository extends JpaRepository<PlatformConfig, Long> {
    Optional<PlatformConfig> findByPlatformType(PlatformType platformType);
}
