package com.aidee.backend.meeting;

import com.aidee.backend.ai.GeminiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class SttService {

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public SttResult transcribe(String filePath, Consumer<String> onChunk, Consumer<SttResult.Segment> onSegment) {
        try {
            byte[] audioBytes = Files.readAllBytes(Path.of(filePath));
            String base64Audio = Base64.getEncoder().encodeToString(audioBytes);

            String prompt = """
                    음성 파일을 텍스트로 변환해주세요.
                    각 발화 세그먼트를 아래 형식의 JSON으로 한 줄에 하나씩 출력하세요. 다른 텍스트나 설명은 포함하지 마세요.
                    startTime은 해당 발화가 시작되는 시각(초 단위 정수)입니다.

                    {"startTime": 0, "text": "첫 번째 발화 내용"}
                    {"startTime": 15, "text": "두 번째 발화 내용"}
                    """;

            String requestBody = """
                    {
                      "contents": [{
                        "parts": [
                          {"text": %s},
                          {"inline_data": {"mime_type": "audio/mpeg", "data": "%s"}}
                        ]
                      }]
                    }
                    """.formatted(objectMapper.writeValueAsString(prompt), base64Audio);

            List<SttResult.Segment> segments = new ArrayList<>();
            StringBuilder lineBuffer = new StringBuilder();

            geminiClient.streamContent(GeminiClient.AUDIO_MODEL, requestBody, chunk -> {
                onChunk.accept(chunk);  // 청크 즉시 프론트로 전달
                lineBuffer.append(chunk);
                int newlineIdx;
                while ((newlineIdx = lineBuffer.indexOf("\n")) != -1) {
                    String line = lineBuffer.substring(0, newlineIdx).trim();
                    lineBuffer.delete(0, newlineIdx + 1);
                    if (line.isEmpty()) continue;
                    try {
                        JsonNode node = objectMapper.readTree(line);
                        SttResult.Segment seg = new SttResult.Segment(
                                node.path("startTime").asInt(),
                                node.path("text").asText()
                        );
                        segments.add(seg);
                        onSegment.accept(seg);
                    } catch (Exception ignored) {}
                }
            });

            // 버퍼에 남은 마지막 줄 처리
            String remaining = lineBuffer.toString().trim();
            if (!remaining.isEmpty()) {
                try {
                    JsonNode node = objectMapper.readTree(remaining);
                    SttResult.Segment seg = new SttResult.Segment(
                            node.path("startTime").asInt(),
                            node.path("text").asText()
                    );
                    segments.add(seg);
                    onSegment.accept(seg);
                } catch (Exception ignored) {}
            }

            StringBuilder fullText = new StringBuilder();
            for (SttResult.Segment seg : segments) {
                if (!fullText.isEmpty()) fullText.append(" ");
                fullText.append(seg.text());
            }

            return new SttResult(fullText.toString(), segments);

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("STT 처리 실패", e);
        }
    }
}