package com.hczk.hczkaiagentserver.entity;

import com.hczk.hczkaiagentserver.enums.PlatformType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "platform_configs")
@Data
public class PlatformConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlatformType platformType;

    @Column(name = "app_id")
    private String appId;

    @Column(name = "app_secret")
    private String appSecret;

    @Column(name = "webhook_url")
    private String webhookUrl;

    @Column(name = "auto_reply")
    private Boolean autoReply = false;

    @Column(name = "enabled")
    private Boolean enabled = false;

    @ManyToOne
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
