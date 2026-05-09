package com.aidee.backend.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class GeminiClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    public static final String AUDIO_MODEL = "gemini-3-flash-preview";
    public static final String TEXT_MODEL = "gemini-2.5-flash";

    @Value("${gemini.api-key}")
    private String apiKey;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public String generateContent(String model, String requestBody) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + model + ":generateContent?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return extractText(response.body());
    }

    public void streamContent(String model, String requestBody, Consumer<String> onText) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + model + ":streamGenerateContent?key=" + apiKey + "&alt=sse"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<java.util.stream.Stream<String>> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofLines());

        response.body()
                .filter(line -> line.startsWith("data: "))
                .map(line -> line.substring(6).trim())
                .filter(json -> !json.isEmpty())
                .forEach(json -> {
                    try {
                        JsonNode root = objectMapper.readTree(json);
                        JsonNode text = root.path("candidates").path(0)
                                .path("content").path("parts").path(0).path("text");
                        if (!text.isMissingNode() && !text.asText().isEmpty()) {
                            onText.accept(text.asText());
                        }
                    } catch (Exception ignored) {}
                });
    }

    private String extractText(String responseJson) throws IOException {
        JsonNode root = objectMapper.readTree(responseJson);
        if (root.has("error")) {
            throw new RuntimeException("Gemini API 오류: " + root.path("error").path("message").asText());
        }
        return root.path("candidates").path(0)
                .path("content").path("parts").path(0)
                .path("text").asText();
    }
}
