package com.aidee.backend.meeting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SttService {

    @Value("${clova.speech.invoke-url}")
    private String invokeUrl;

    @Value("${clova.speech.secret}")
    private String secret;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * S3 presigned URL을 CLOVA Speech에 전달해 STT 처리.
     * 파일을 두 번 올리지 않아도 됨.
     */
    public SttResult transcribe(String audioUrl, Consumer<String> onChunk, Consumer<SttResult.Segment> onSegment) {
        try {
            String requestBody = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
                put("url", audioUrl);
                put("language", "ko-KR");
                put("completion", "sync");
                put("wordAlignment", false);
                put("fullText", true);
            }});

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(invokeUrl.strip().replaceAll("/+$", "") + "/recognizer/url"))
                    .header("X-CLOVASPEECH-API-KEY", secret.strip())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            log.info("[CLOVA STT] 요청 시작 (url 방식)");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("CLOVA Speech API 오류 (HTTP " + response.statusCode() + "): " + response.body());
            }

            return parseResponse(response.body(), onChunk, onSegment);

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("STT 처리 실패", e);
        }
    }

    private SttResult parseResponse(String responseBody, Consumer<String> onChunk, Consumer<SttResult.Segment> onSegment) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        String result = root.path("result").asText();
        if (!"COMPLETED".equals(result)) {
            throw new RuntimeException("CLOVA STT 실패: " + root.path("message").asText());
        }

        String fullText = root.path("text").asText();
        List<SttResult.Segment> segments = new ArrayList<>();

        JsonNode segs = root.path("segments");
        if (segs.isArray() && !segs.isEmpty()) {
            for (JsonNode seg : segs) {
                int startSec = seg.path("start").asInt() / 1000;
                String text = seg.path("text").asText();
                SttResult.Segment segment = new SttResult.Segment(startSec, text);
                segments.add(segment);
                onChunk.accept(text);
                onSegment.accept(segment);
            }
        } else if (!fullText.isBlank()) {
            SttResult.Segment segment = new SttResult.Segment(0, fullText);
            segments.add(segment);
            onChunk.accept(fullText);
            onSegment.accept(segment);
        }

        String joined = fullText.isBlank()
                ? segments.stream().map(SttResult.Segment::text).collect(Collectors.joining(" "))
                : fullText;

        log.info("[CLOVA STT] 완료 - 세그먼트: {}개, 전체 텍스트: {}자", segments.size(), joined.length());
        return new SttResult(joined, segments);
    }
}
