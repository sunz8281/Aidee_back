package com.aidee.backend.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiClient {

    @Value("${gemini.base-url:https://aiplatform.googleapis.com/v1/publishers/google/models/}")
    private String BASE_URL;

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.text-model:gemini-2.5-flash}")
    public String TEXT_MODEL;

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
        streamContentWithTools(model, requestBody, onText);
    }

    /**
     * 스트리밍 요청. 텍스트 청크는 onText로 전달하고,
     * function call이 감지되면 해당 parts 노드 목록을 반환한다.
     * 텍스트 응답이면 빈 리스트 반환.
     */
    public List<JsonNode> streamContentWithTools(String model, String requestBody, Consumer<String> onText) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + model + ":streamGenerateContent?key=" + apiKey + "&alt=sse"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<java.util.stream.Stream<String>> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofLines());

        if (response.statusCode() != 200) {
            String body = response.body().collect(java.util.stream.Collectors.joining("\n"));
            throw new RuntimeException("Gemini API 오류 (HTTP " + response.statusCode() + "): " + body);
        }

        log.info("[Gemini] 스트림 시작 (모델: {})", model);
        List<JsonNode> functionCallParts = new java.util.ArrayList<>();
        RuntimeException[] streamError = {null};
        int[] eventCount = {0};

        response.body()
                .filter(line -> line.startsWith("data: "))
                .map(line -> line.substring(6).trim())
                .filter(json -> !json.isEmpty())
                .forEach(json -> {
                    eventCount[0]++;
                    log.debug("[Gemini] SSE#{}: {}", eventCount[0], json.length() > 200 ? json.substring(0, 200) + "..." : json);
                    if (streamError[0] != null) return;
                    try {
                        JsonNode root = objectMapper.readTree(json);
                        if (root.has("error")) {
                            streamError[0] = new RuntimeException("Gemini API 오류: " + root.path("error").path("message").asText());
                            return;
                        }
                        JsonNode candidate = root.path("candidates").path(0);
                        String finishReason = candidate.path("finishReason").asText("");
                        if (!finishReason.isEmpty()) {
                            log.info("[Gemini] finishReason: {}", finishReason);
                        }
                        JsonNode parts = candidate.path("content").path("parts");
                        if (parts.isArray()) {
                            for (JsonNode part : parts) {
                                if (!part.path("functionCall").isMissingNode()) {
                                    String fnName = part.path("functionCall").path("name").asText();
                                    log.info("[Gemini] functionCall 감지: {}", fnName);
                                    functionCallParts.add(part);
                                } else {
                                    JsonNode text = part.path("text");
                                    if (!text.isMissingNode() && !text.asText().isEmpty()) {
                                        log.debug("[Gemini] 텍스트 청크: {}자", text.asText().length());
                                        onText.accept(text.asText());
                                    }
                                }
                            }
                        } else {
                            log.debug("[Gemini] parts 없음 (candidate: {})", candidate.toString().length() > 100 ? candidate.toString().substring(0, 100) : candidate.toString());
                        }
                    } catch (Exception e) {
                        streamError[0] = new RuntimeException("stream 파싱 오류", e);
                    }
                });

        log.info("[Gemini] 스트림 완료 - 이벤트: {}, functionCalls: {}", eventCount[0], functionCallParts.size());
        if (streamError[0] != null) throw streamError[0];
        return functionCallParts;
    }

    public JsonNode generateContentRaw(String model, String requestBody) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + model + ":generateContent?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = objectMapper.readTree(response.body());
        if (root.has("error")) {
            throw new RuntimeException("Gemini API 오류: " + root.path("error").path("message").asText());
        }
        return root;
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
