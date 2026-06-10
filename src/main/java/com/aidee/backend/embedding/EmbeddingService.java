package com.aidee.backend.embedding;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    // Vertex AI predict 형식 전체 URL
    // 예: https://us-central1-aiplatform.googleapis.com/v1/projects/MY_PROJECT/locations/us-central1/publishers/google/models/gemini-embedding-001:predict
    @Value("${gemini.embedding-url}")
    private String embeddingUrl;

    @Value("${gemini.api-key}")
    private String apiKey;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public float[] embed(String text) {
        try {
            String requestBody = objectMapper.writeValueAsString(
                    Map.of(
                            "instances", List.of(Map.of("content", text)),
                            "parameters", Map.of("outputDimensionality", 768)
                    )
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(embeddingUrl + "?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            log.debug("[Embedding] 응답 HTTP {}: {}", response.statusCode(),
                    response.body().length() > 300 ? response.body().substring(0, 300) : response.body());
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode values = root.path("predictions").path(0).path("embeddings").path("values");

            if (values.isMissingNode() || values.size() == 0) {
                log.error("[Embedding] 응답 파싱 실패 (HTTP {}): {}", response.statusCode(), response.body());
                throw new RuntimeException("임베딩 API 응답 오류 (HTTP " + response.statusCode() + "): " + response.body());
            }

            float[] result = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                result[i] = (float) values.get(i).asDouble();
            }
            return result;

        } catch (Exception e) {
            throw new RuntimeException("임베딩 생성 실패", e);
        }
    }
}
