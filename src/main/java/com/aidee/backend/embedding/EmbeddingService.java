package com.aidee.backend.embedding;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private static final String EMBEDDING_MODEL = "gemini-embedding-001";
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    @Value("${gemini.api-key}")
    private String apiKey;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public float[] embed(String text) {
        try {
            String requestBody = objectMapper.writeValueAsString(
                    java.util.Map.of(
                            "content", java.util.Map.of(
                                    "parts", java.util.List.of(java.util.Map.of("text", text))
                            ),
                            "outputDimensionality", 768
                    )
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + EMBEDDING_MODEL + ":embedContent?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode values = root.path("embedding").path("values");

            if (values.isMissingNode() || values.size() == 0) {
                throw new RuntimeException("임베딩 API 응답 오류: " + response.body());
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
