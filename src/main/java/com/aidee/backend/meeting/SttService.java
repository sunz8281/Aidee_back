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

    public SttResult transcribe(String filePath, Consumer<String> onChunk) {
        try {
            byte[] audioBytes = Files.readAllBytes(Path.of(filePath));
            String base64Audio = Base64.getEncoder().encodeToString(audioBytes);

            String prompt = """
                    음성 파일을 텍스트로 변환해주세요. 아래 JSON 형식으로만 반환하고 다른 텍스트는 포함하지 마세요.
                    startTime은 해당 발화가 시작되는 시각(초 단위 정수)입니다.

                    {
                      "segments": [
                        {"startTime": 0, "text": "첫 번째 발화 내용"},
                        {"startTime": 15, "text": "두 번째 발화 내용"}
                      ]
                    }
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

            StringBuilder accumulated = new StringBuilder();
            geminiClient.streamContent(GeminiClient.AUDIO_MODEL, requestBody, chunk -> {
                onChunk.accept(chunk);
                accumulated.append(chunk);
            });

            return parseResult(accumulated.toString());

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("STT 처리 실패", e);
        } catch (Exception e) {
            throw new RuntimeException("STT 파싱 실패", e);
        }
    }

    private SttResult parseResult(String raw) throws Exception {
        String json = raw.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
        }

        JsonNode root = objectMapper.readTree(json);
        List<SttResult.Segment> segments = new ArrayList<>();
        StringBuilder fullText = new StringBuilder();

        for (JsonNode s : root.path("segments")) {
            int startTime = s.path("startTime").asInt();
            String text = s.path("text").asText();
            segments.add(new SttResult.Segment(startTime, text));
            if (!fullText.isEmpty()) fullText.append(" ");
            fullText.append(text);
        }

        return new SttResult(fullText.toString(), segments);
    }
}
