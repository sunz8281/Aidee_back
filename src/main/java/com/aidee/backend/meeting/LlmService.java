package com.aidee.backend.meeting;

import com.aidee.backend.ai.GeminiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class LlmService {

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public LlmAnalysisResult analyze(String script, Consumer<String> stepCallback) {
        try {
            stepCallback.accept("요약 중");

            String prompt = """
                    다음 회의 스크립트를 분석하여 아래 JSON 형식으로만 반환해주세요. 다른 텍스트는 포함하지 마세요.
                    summary는 반드시 300자 이내로 작성해주세요.

                    스크립트:
                    %s

                    반환 형식:
                    {
                      "summary": "회의 전체 요약 (300자 이내)",
                      "schedules": [
                        {
                          "title": "일정 제목",
                          "startTime": "2026-05-16T10:00:00",
                          "endTime": "2026-05-16T11:00:00",
                          "allDay": false
                        }
                      ]
                    }
                    """.formatted(script);

            String requestBody = """
                    {
                      "contents": [{"parts": [{"text": %s}]}]
                    }
                    """.formatted(objectMapper.writeValueAsString(prompt));

            stepCallback.accept("일정 추출 중");
            String responseText = geminiClient.generateContent(GeminiClient.TEXT_MODEL, requestBody);

            String json = responseText.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
            }

            return parseResult(json);

        } catch (Exception e) {
            throw new RuntimeException("LLM 분석 실패", e);
        }
    }

    private LlmAnalysisResult parseResult(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);

        String summary = root.path("summary").asText();

        List<LlmAnalysisResult.ScheduleData> schedules = new ArrayList<>();
        for (JsonNode s : root.path("schedules")) {
            schedules.add(new LlmAnalysisResult.ScheduleData(
                    s.path("title").asText(),
                    LocalDateTime.parse(s.path("startTime").asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    LocalDateTime.parse(s.path("endTime").asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    s.path("allDay").asBoolean()
            ));
        }

        return new LlmAnalysisResult(summary, schedules);
    }
}
