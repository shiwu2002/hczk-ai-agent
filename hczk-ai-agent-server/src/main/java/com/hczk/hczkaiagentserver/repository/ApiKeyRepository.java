package com.hczk.hczkaiagentserver.repository;

import com.hczk.hczkaiagentserver.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    Optional<ApiKey> findByApiKey(String apiKey);

    List<ApiKey> findByUserId(Long userId);
}
