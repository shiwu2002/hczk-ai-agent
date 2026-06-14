package com.hczk.hczkaiagentserver.knowledge.controller;

import com.hczk.hczkaiagentserver.knowledge.dto.HealthResponse;
import com.hczk.hczkaiagentserver.knowledge.embedder.Embedder;
import com.hczk.hczkaiagentserver.knowledge.milvus.MilvusManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    private final MilvusManager milvusManager;
    private final Embedder embedder;

    /**
     * Liveness check - process alive returns 200
     */
    @GetMapping("/live")
    public ResponseEntity<HealthResponse> liveness() {
        return ResponseEntity.ok(new HealthResponse("alive", null, null));
    }

    /**
     * Readiness check - verifies Milvus + Embedding service availability
     */
    @GetMapping("/ready")
    public ResponseEntity<HealthResponse> readiness() {
        String milvusStatus = milvusManager.isConnected() ? "connected" : "disconnected";
        String embeddingStatus;
        try {
            embeddingStatus = embedder.isAvailable() ? "available" : "unavailable";
        } catch (Exception e) {
            embeddingStatus = "unavailable";
        }

        String overallStatus = "connected".equals(milvusStatus) && "available".equals(embeddingStatus)
                ? "ready" : "not_ready";

        HealthResponse response = new HealthResponse(overallStatus, milvusStatus, embeddingStatus);
        return "ready".equals(overallStatus)
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(503).body(response);
    }

    /**
     * Legacy health check - only checks Milvus connection
     */
    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        String milvusStatus = milvusManager.isConnected() ? "connected" : "disconnected";
        HealthResponse response = new HealthResponse(milvusStatus, milvusStatus, null);
        return "connected".equals(milvusStatus)
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(503).body(response);
    }
}
