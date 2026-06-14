package com.hczk.hczkaiagentserver.knowledge.embedder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hczk.hczkaiagentserver.knowledge.config.EmbeddingProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class Embedder {

    private final EmbeddingProperties properties;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    // LRU Cache for embeddings
    private final Map<String, float[]> cache;

    public Embedder(EmbeddingProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(properties.getTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getTimeoutMs(), TimeUnit.MILLISECONDS)
                .writeTimeout(properties.getTimeoutMs(), TimeUnit.MILLISECONDS)
                .build();

        // LRU cache
        this.cache = Collections.synchronizedMap(new LinkedHashMap<String, float[]>(properties.getCacheMaxSize(), 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
                return size() > properties.getCacheMaxSize();
            }
        });

        // Circuit breaker: 5 failures -> open, 30s -> half-open
        this.circuitBreaker = CircuitBreaker.of("embedding", CircuitBreakerConfig.custom()
                .failureRateThreshold(100)
                .slidingWindowSize(5)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .waitDurationInOpenState(java.time.Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(1)
                .build());

        // Retry: max 2 attempts, 500ms base interval, exponential backoff
        this.retry = Retry.of("embedding", RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(java.time.Duration.ofMillis(500))
                .retryOnException(e -> !(e instanceof ClientErrorException))
                .build());
    }

    /**
     * Embed a single text, using cache
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[properties.getDimension()];
        }
        String cacheKey = text.hashCode() + "_" + text.length();
        float[] cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<float[]> results = embedBatch(List.of(text));
        if (!results.isEmpty()) {
            cache.put(cacheKey, results.get(0));
            return results.get(0);
        }
        throw new RuntimeException("Failed to embed text");
    }

    /**
     * Batch embed texts, separating cache hits from misses
     */
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> results = new ArrayList<>(texts.size());
        List<Integer> missIndices = new ArrayList<>();
        List<String> missTexts = new ArrayList<>();

        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            if (text == null || text.isBlank()) {
                results.add(new float[properties.getDimension()]);
                continue;
            }
            String cacheKey = text.hashCode() + "_" + text.length();
            float[] cached = cache.get(cacheKey);
            if (cached != null) {
                results.add(cached);
            } else {
                results.add(null);
                missIndices.add(i);
                missTexts.add(text);
            }
        }

        if (missTexts.isEmpty()) {
            return results;
        }

        // Process misses in batches
        int batchSize = properties.getBatchSize();
        for (int i = 0; i < missTexts.size(); i += batchSize) {
            List<String> batch = missTexts.subList(i, Math.min(i + batchSize, missTexts.size()));
            List<float[]> batchResults = callEmbeddingApi(batch);
            for (int j = 0; j < batch.size(); j++) {
                int resultIndex = missIndices.get(i + j);
                float[] embedding = batchResults.get(j);
                results.set(resultIndex, embedding);
                String cacheKey = missTexts.get(i + j).hashCode() + "_" + missTexts.get(i + j).length();
                cache.put(cacheKey, embedding);
            }
        }

        return results;
    }

    private List<float[]> callEmbeddingApi(List<String> texts) {
        Callable<List<float[]>> callable = () -> doCallEmbeddingApi(texts);
        Callable<List<float[]>> retryCallable = Retry.decorateCallable(retry, callable);
        Callable<List<float[]>> cbCallable = CircuitBreaker.decorateCallable(circuitBreaker, retryCallable);
        try {
            return cbCallable.call();
        } catch (Exception e) {
            log.error("Embedding API call failed after retries: {}", e.getMessage());
            throw new RuntimeException("Embedding API call failed: " + e.getMessage());
        }
    }

    private List<float[]> doCallEmbeddingApi(List<String> texts) throws IOException {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", properties.getModel());
        requestBody.put("input", texts);

        String json = objectMapper.writeValueAsString(requestBody);
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));

        Request.Builder requestBuilder = new Request.Builder()
                .url(properties.getBaseUrl() + "/embeddings")
                .post(body);

        if (properties.getApiKey() != null && !properties.getApiKey().isEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer " + properties.getApiKey());
        }

        try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "unknown";
                if (response.code() >= 400 && response.code() < 500) {
                    throw new ClientErrorException("Client error " + response.code() + ": " + errorBody);
                }
                throw new IOException("Embedding API error " + response.code() + ": " + errorBody);
            }

            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode dataNode = root.get("data");

            List<float[]> embeddings = new ArrayList<>();
            // Sort by index to ensure order
            List<JsonNode> sorted = new ArrayList<>();
            dataNode.forEach(sorted::add);
            sorted.sort(Comparator.comparingInt(n -> n.get("index").asInt()));

            for (JsonNode item : sorted) {
                JsonNode embeddingNode = item.get("embedding");
                float[] embedding = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    embedding[i] = (float) embeddingNode.get(i).asDouble();
                }
                embeddings.add(embedding);
            }
            return embeddings;
        }
    }

    /**
     * Check if embedding service is available
     */
    public boolean isAvailable() {
        try {
            Request request = new Request.Builder()
                    .url(properties.getBaseUrl().replaceAll("/v1$", "") + "/v1/models")
                    .get()
                    .build();
            if (properties.getApiKey() != null && !properties.getApiKey().isEmpty()) {
                request = new Request.Builder()
                        .url(properties.getBaseUrl().replaceAll("/v1$", "") + "/v1/models")
                        .addHeader("Authorization", "Bearer " + properties.getApiKey())
                        .get()
                        .build();
            }
            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Custom exception for 4xx client errors (should not be retried)
     */
    private static class ClientErrorException extends RuntimeException {
        public ClientErrorException(String message) {
            super(message);
        }
    }
}
