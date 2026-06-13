package com.hczk.hczkaiagentserver.entity;

import com.hczk.hczkaiagentserver.enums.AgentStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "agents")
@Data
public class Agent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @ManyToOne
    @JoinColumn(name = "model_id", nullable = false)
    private AiModel model;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentStatus status = AgentStatus.ACTIVE;

    @Column(name = "agent_type")
    private String agentType;

    @Column(name = "total_calls")
    private Long totalCalls = 0L;

    @Column(name = "total_tokens")
    private Long totalTokens = 0L;

    @Column(name = "avg_latency")
    private Integer avgLatency;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
