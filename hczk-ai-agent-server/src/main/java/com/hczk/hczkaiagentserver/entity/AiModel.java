package com.hczk.hczkaiagentserver.entity;

import com.hczk.hczkaiagentserver.enums.ModelStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_models")
@Data
public class AiModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String provider;

    @Column(name = "model_id", nullable = false)
    private String modelId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModelStatus status = ModelStatus.ACTIVE;

    @Column(name = "api_base")
    private String apiBase;

    @Column(name = "api_key")
    private String apiKey;

    @Column(name = "input_price", precision = 19, scale = 6)
    private BigDecimal inputPrice;

    @Column(name = "output_price", precision = 19, scale = 6)
    private BigDecimal outputPrice;

    @Column(name = "max_tokens")
    private Integer maxTokens;

    @Column(name = "thinking")
    private Boolean thinking = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
