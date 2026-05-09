package com.aidee.backend.meeting;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class SttService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${clova.speech.invoke-url}")
    private String invokeUrl;

    @Value("${clova.speech.secret-key}")
    private String secretKey;

    public SttResult transcribe(String filePath, Consumer<SttResult.Segment> onSegment) {
        try {
            byte[] audioBytes = Files.readAllBytes(Path.of(filePath));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(invokeUrl + "/recognizer/upload"))
                    .header("X-CLOVASPEECH-API-KEY", secretKey)
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(audioBytes))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Clova Speech API 오류: " + response.statusCode() + " " + response.body());
            }

            return parseResult(response.body(), onSegment);

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("STT 처리 실패", e);
        } catch (Exception e) {
            throw new RuntimeException("STT 파싱 실패", e);
        }
    }

    private SttResult parseResult(String responseBody, Consumer<SttResult.Segment> onSegment) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        List<SttResult.Segment> segments = new ArrayList<>();
        StringBuilder fullText = new StringBuilder();

        for (JsonNode s : root.path("segments")) {
            int startTime = (int) (s.path("start").asLong() / 1000); // ms → 초
            String text = s.path("text").asText();
            SttResult.Segment seg = new SttResult.Segment(startTime, text);
            segments.add(seg);
            onSegment.accept(seg);
            if (!fullText.isEmpty()) fullText.append(" ");
            fullText.append(text);
        }

        return new SttResult(fullText.toString(), segments);
    }
}