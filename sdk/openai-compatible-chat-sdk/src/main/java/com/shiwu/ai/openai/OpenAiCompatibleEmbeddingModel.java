package com.shiwu.ai.openai;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

@Slf4j
public class OpenAiCompatibleEmbeddingModel implements EmbeddingModel {

    private final String fullUrl;
    private final String model;
    private final String apiKey;
    private final int dimension;
    private final Duration timeout = Duration.ofSeconds(30);
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleEmbeddingModel(String fullUrl, String model, String apiKey, int dimension) {
        this.fullUrl = fullUrl;
        this.model = model;
        this.apiKey = apiKey;
        this.dimension = dimension;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .executor(Executors.newFixedThreadPool(2, r -> {
                    Thread t = new Thread(r, "openai-embed-http");
                    t.setDaemon(true);
                    return t;
                }))
                .build();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = request.getInstructions();
        if (texts == null || texts.isEmpty()) {
            return new EmbeddingResponse(List.of());
        }

        try {
            OpenAiCompatibleEmbeddingDto.Request req = new OpenAiCompatibleEmbeddingDto.Request();
            req.setModel(model);
            req.setInput(texts);
            req.setEncodingFormat("float");
            req.setDimensions(dimension);

            String requestBody = objectMapper.writeValueAsString(req);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .header("Content-Type", "application/json")
                    .timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody));

            if (apiKey != null && !apiKey.trim().isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }

            HttpResponse<String> httpResponse = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            int statusCode = httpResponse.statusCode();
            String rawBody = httpResponse.body();

            if (statusCode >= 400) {
                log.error("OpenAI compatible embedding API error: status={}, body={}", statusCode, rawBody);
                throw new RuntimeException("Embedding API 调用失败 (HTTP " + statusCode + "): " + rawBody);
            }

            OpenAiCompatibleEmbeddingDto.Response resp = objectMapper.readValue(rawBody, OpenAiCompatibleEmbeddingDto.Response.class);

            if (resp.getData() == null || resp.getData().isEmpty()) {
                log.warn("OpenAI compatible embedding returned empty data");
                return new EmbeddingResponse(List.of());
            }

            List<Embedding> embeddings = new ArrayList<>();
            for (OpenAiCompatibleEmbeddingDto.EmbeddingData data : resp.getData()) {
                float[] embeddingVector = toFloatArray(data.getEmbedding());
                embeddingVector = truncateToDimension(embeddingVector);
                embeddings.add(new Embedding(embeddingVector, data.getIndex()));
            }

            log.debug("OpenAI compatible embedding success: model={}, texts={}, dims={}",
                    model, texts.size(), embeddings.isEmpty() ? 0 : embeddings.get(0).getOutput().length);

            return new EmbeddingResponse(embeddings);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenAI compatible embedding call failed: {}", e.getMessage(), e);
            throw new RuntimeException("Embedding API 调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public float[] embed(String text) {
        EmbeddingResponse response = call(new EmbeddingRequest(List.of(text), null));
        if (response == null || response.getResults().isEmpty()) {
            return new float[0];
        }
        return response.getResults().get(0).getOutput();
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public int dimensions() {
        return dimension;
    }

    private float[] toFloatArray(List<Double> doubleList) {
        if (doubleList == null) {
            return new float[0];
        }
        float[] result = new float[doubleList.size()];
        for (int i = 0; i < doubleList.size(); i++) {
            result[i] = doubleList.get(i).floatValue();
        }
        return result;
    }

    private float[] truncateToDimension(float[] vector) {
        if (vector.length <= dimension) {
            return vector;
        }
        float[] truncated = new float[dimension];
        System.arraycopy(vector, 0, truncated, 0, dimension);
        return truncated;
    }
}
