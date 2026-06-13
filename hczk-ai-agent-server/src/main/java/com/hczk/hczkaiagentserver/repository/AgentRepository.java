package com.hczk.hczkaiagentserver.repository;

import com.hczk.hczkaiagentserver.entity.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentRepository extends JpaRepository<Agent, Long> {
    List<Agent> findByUserId(Long userId);

    List<Agent> findByStatus(String status);
}
