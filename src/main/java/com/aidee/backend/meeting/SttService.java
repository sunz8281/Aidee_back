package com.aidee.backend.meeting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SttService {

    private static final String CLOVA_URL = "https://clovaspeech-gw.ncloud.com/recog/v1/recognize";

    @Value("${clova.speech.secret}")
    private String secret;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public SttResult transcribe(String filePath, Consumer<String> onChunk, Consumer<SttResult.Segment> onSegment) {
        try {
            byte[] audioBytes = Files.readAllBytes(Path.of(filePath));
            String boundary = UUID.randomUUID().toString().replace("-", "");

            String params = "{\"language\":\"ko-KR\",\"completion\":\"sync\",\"wordAlignment\":false,\"fullText\":true}";

            ByteArrayOutputStream body = new ByteArrayOutputStream();
            appendPart(body, boundary, "params", "application/json", params.getBytes());
            appendFilePart(body, boundary, "media", audioBytes);
            body.write(("--" + boundary + "--\r\n").getBytes());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CLOVA_URL))
                    .header("X-CLOVASPEECH-API-GW-SERVICE-SECRET", secret)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                    .build();

            log.info("[CLOVA STT] 요청 시작 (파일 크기: {} bytes)", audioBytes.length);
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

    private void appendPart(ByteArrayOutputStream out, String boundary, String name, String contentType, byte[] data) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes());
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n").getBytes());
        out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes());
        out.write(data);
        out.write("\r\n".getBytes());
    }

    private void appendFilePart(ByteArrayOutputStream out, String boundary, String name, byte[] data) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes());
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"audio\"\r\n").getBytes());
        out.write("Content-Type: application/octet-stream\r\n\r\n".getBytes());
        out.write(data);
        out.write("\r\n".getBytes());
    }
}
