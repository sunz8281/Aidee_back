package com.aidee.backend.agent;

import com.aidee.backend.agent.dto.AgentRequest;
import com.aidee.backend.agent.dto.MessageDto;
import com.aidee.backend.ai.GeminiClient;
import com.aidee.backend.common.ResourceNotFoundException;
import com.aidee.backend.common.VectorConverter;
import com.aidee.backend.embedding.EmbeddingService;
import com.aidee.backend.embedding.ScriptEmbedding;
import com.aidee.backend.embedding.ScriptEmbeddingRepository;
import com.aidee.backend.meeting.MeetingService;
import com.aidee.backend.meeting.dto.CreateMeetingRequest;
import com.aidee.backend.meeting.dto.MeetingDetailResponse;
import com.aidee.backend.meeting.MeetingRepository;
import com.aidee.backend.project.ProjectRepository;
import com.aidee.backend.schedule.ScheduleService;
import com.aidee.backend.schedule.dto.CreateScheduleRequest;
import com.aidee.backend.schedule.dto.UpdateScheduleRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private static final int TOP_K = 5;
    private static final double SIMILARITY_THRESHOLD = 0.65;
    private static final int MAX_TOOL_ROUNDS = 5;

    private final ProjectRepository projectRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingService meetingService;
    private final ScheduleService scheduleService;
    private final ScriptEmbeddingRepository scriptEmbeddingRepository;
    private final EmbeddingService embeddingService;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;
    private final VectorConverter vectorConverter;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public SseEmitter chat(String projectId, String meetingId, AgentRequest request) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("프로젝트를 찾을 수 없습니다: " + projectId);
        }
        if (meetingId != null && !meetingRepository.existsById(meetingId)) {
            throw new ResourceNotFoundException("회의를 찾을 수 없습니다: " + meetingId);
        }

        SseEmitter emitter = new SseEmitter(3 * 60 * 1000L);

        executor.submit(() -> {
            try {
                List<Map<String, Object>> contents = buildContents(request);

                // tool loop: 스트리밍하면서 function call 감지 → 툴 실행 → 반복
                for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
                    String body = buildBody(contents);
                    log.info("[Agent] 라운드 {}/{} - contents:{}, 바디:{}bytes", round + 1, MAX_TOOL_ROUNDS, contents.size(), body.length());

                    List<tools.jackson.databind.JsonNode> functionCallParts = geminiClient.streamContentWithTools(
                            GeminiClient.TEXT_MODEL, body,
                            text -> { try { sendDelta(emitter, text); } catch (IOException e) { throw new RuntimeException(e); } }
                    );

                    log.info("[Agent] 라운드 {} 완료 - functionCalls: {}", round + 1, functionCallParts.size());

                    if (functionCallParts.isEmpty()) {
                        // 텍스트 응답 → 이미 스트리밍 완료
                        break;
                    }

                    // model turn (functionCall) 추가
                    contents.add(Map.of(
                            "role", "model",
                            "parts", functionCallParts
                    ));

                    // 각 functionCall 실행
                    List<Map<String, Object>> functionResponses = new ArrayList<>();
                    for (tools.jackson.databind.JsonNode part : functionCallParts) {
                        tools.jackson.databind.JsonNode fc = part.path("functionCall");
                        String toolName = fc.path("name").asText();
                        tools.jackson.databind.JsonNode args = fc.path("args");
                        emitter.send(SseEmitter.event().name("tool_call").data("{\"tool\":\"" + toolName + "\"}"));
                        String result = executeTool(toolName, args, projectId, meetingId);
                        log.info("[Agent] 툴 '{}' 결과: {}자", toolName, result.length());

                        functionResponses.add(Map.of("functionResponse", Map.of(
                                "name", toolName,
                                "response", Map.of("result", result)
                        )));
                    }

                    log.info("[Agent] functionResponses 추가 후 다음 라운드 진입");
                    contents.add(Map.of("role", "user", "parts", functionResponses));
                }

                emitter.send(SseEmitter.event().name("done").data("{}"));
                emitter.complete();

            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private String executeTool(String toolName, JsonNode args, String projectId, String meetingId) {
        try {
            return switch (toolName) {
                case "search_meeting_records" -> searchMeetingRecords(
                        args.path("query").asText(), projectId, meetingId);

                case "get_meetings" -> objectMapper.writeValueAsString(
                        meetingService.getMeetings(projectId));

                case "get_meeting" -> objectMapper.writeValueAsString(
                        meetingService.getMeeting(args.path("meeting_id").asText()));

                case "get_memos" -> objectMapper.writeValueAsString(
                        meetingService.getMemos(projectId));

                case "update_memo" -> {
                    meetingService.updateMemo(
                            args.path("meeting_id").asText(),
                            args.path("memo").asText());
                    yield "메모가 수정되었습니다.";
                }

                case "delete_memo" -> {
                    meetingService.updateMemo(args.path("meeting_id").asText(), null);
                    yield "메모가 삭제되었습니다.";
                }

                case "get_schedules" -> {
                    int year = args.has("year") ? args.path("year").asInt() : LocalDateTime.now().getYear();
                    int month = args.has("month") ? args.path("month").asInt() : LocalDateTime.now().getMonthValue();
                    LocalDate from = LocalDate.of(year, month, 1);
                    LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
                    yield objectMapper.writeValueAsString(scheduleService.getSchedules(projectId, from, to));
                }

                case "create_schedule" -> {
                    CreateScheduleRequest req = new CreateScheduleRequest(
                            args.path("title").asText(),
                            LocalDateTime.parse(args.path("start_time").asText()),
                            LocalDateTime.parse(args.path("end_time").asText()),
                            args.path("all_day").asBoolean(false),
                            "agent",
                            args.has("meeting_id") ? args.path("meeting_id").asText() : null
                    );
                    yield objectMapper.writeValueAsString(scheduleService.createSchedule(projectId, req));
                }

                case "update_schedule" -> {
                    UpdateScheduleRequest req = new UpdateScheduleRequest(
                            args.path("title").asText(),
                            LocalDateTime.parse(args.path("start_time").asText()),
                            LocalDateTime.parse(args.path("end_time").asText()),
                            args.path("all_day").asBoolean(false),
                            args.has("meeting_id") ? args.path("meeting_id").asText() : null
                    );
                    yield objectMapper.writeValueAsString(scheduleService.updateSchedule(args.path("id").asText(), req));
                }

                case "delete_schedule" -> {
                    scheduleService.deleteSchedule(args.path("id").asText());
                    yield "일정이 삭제되었습니다.";
                }

                case "delete_meeting" -> {
                    meetingService.deleteMeeting(args.path("meeting_id").asText());
                    yield "회의가 삭제되었습니다.";
                }

                default -> "알 수 없는 도구: " + toolName;
            };
        } catch (Exception e) {
            return "오류: " + e.getMessage();
        }
    }

    private String searchMeetingRecords(String query, String projectId, String meetingId) {
        try {
            float[] queryEmbedding = embeddingService.embed(query);
            String queryVector = vectorConverter.convertToDatabaseColumn(queryEmbedding);

            List<ScriptEmbedding> relevant = meetingId != null
                    ? scriptEmbeddingRepository.findSimilarByMeeting(meetingId, queryVector, TOP_K, SIMILARITY_THRESHOLD)
                    : scriptEmbeddingRepository.findSimilarByProject(projectId, queryVector, TOP_K, SIMILARITY_THRESHOLD);

            if (relevant.isEmpty()) return "관련 회의 기록 없음";

            return relevant.stream()
                    .map(e -> String.format("회의명: %s / 날짜: %s / %d초 부근\n내용: %s",
                            e.getMeetingTitle(),
                            e.getMeetingAt().toLocalDate(),
                            e.getStartTime(),
                            e.getText()))
                    .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            return "검색 실패: " + e.getMessage();
        }
    }

    private void sendDelta(SseEmitter emitter, String text) throws IOException {
        emitter.send(SseEmitter.event()
                .name("delta")
                .data("{\"text\":\"" + text.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n") + "\"}"));
    }

    private List<Map<String, Object>> buildContents(AgentRequest request) {
        List<Map<String, Object>> contents = new ArrayList<>();
        if (request.history() != null) {
            for (MessageDto msg : request.history()) {
                contents.add(Map.of(
                        "role", msg.role().equals("assistant") ? "model" : "user",
                        "parts", List.of(Map.of("text", msg.content()))
                ));
            }
        }
        contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", request.message()))
        ));
        return contents;
    }

    private String buildBody(List<Map<String, Object>> contents) throws Exception {
        String systemPrompt = "당신은 Aidee 프로젝트 관리 AI 어시스턴트입니다.\n" +
                "규칙:\n" +
                "1. ID를 모르는 리소스를 삭제/수정할 때는 반드시 먼저 목록 조회 도구를 호출해 ID를 확인한다.\n" +
                "2. 사용자에게 ID를 묻지 않는다. 도구로 직접 조회한다.\n" +
                "3. 회의 내용 질문은 search_meeting_records로 검색 후 어느 회의에서 나온 내용인지 포함해 답변한다.\n" +
                "4. 답변은 간결하게 한다.";

        List<Map<String, Object>> tools = List.of(Map.of("function_declarations", List.of(
                toolDef("search_meeting_records", "회의 스크립트에서 관련 내용을 검색합니다. 회의 기록에 대한 질문일 때 사용하세요.",
                        Map.of("query", strParam("검색할 내용")), List.of("query")),

                toolDef("get_meetings", "프로젝트의 회의 목록을 조회합니다.",
                        Map.of(), List.of()),

                toolDef("get_meeting", "특정 회의의 상세 정보를 조회합니다.",
                        Map.of("meeting_id", strParam("회의 ID")), List.of("meeting_id")),

                toolDef("get_memos", "프로젝트의 메모 목록을 조회합니다.",
                        Map.of(), List.of()),

                toolDef("update_memo", "회의의 메모를 생성하거나 수정합니다.",
                        Map.of("meeting_id", strParam("회의 ID"), "memo", strParam("메모 내용")),
                        List.of("meeting_id", "memo")),

                toolDef("delete_memo", "회의의 메모를 삭제합니다.",
                        Map.of("meeting_id", strParam("회의 ID")), List.of("meeting_id")),

                toolDef("get_schedules", "프로젝트의 일정 목록을 조회합니다. year, month를 지정하지 않으면 현재 월로 조회합니다.",
                        Map.of("year", intParam("조회 연도"), "month", intParam("조회 월")), List.of()),

                toolDef("create_schedule", "새 일정을 생성합니다.",
                        Map.of("title", strParam("일정 제목"),
                                "start_time", strParam("시작 시간 (ISO 8601, 예: 2026-05-14T10:00:00)"),
                                "end_time", strParam("종료 시간 (ISO 8601)"),
                                "all_day", Map.of("type", "boolean", "description", "종일 여부"),
                                "meeting_id", strParam("연결할 회의 ID (선택)")),
                        List.of("title", "start_time", "end_time")),

                toolDef("update_schedule", "기존 일정을 수정합니다.",
                        Map.of("id", strParam("일정 ID"),
                                "title", strParam("일정 제목"),
                                "start_time", strParam("시작 시간 (ISO 8601)"),
                                "end_time", strParam("종료 시간 (ISO 8601)"),
                                "all_day", Map.of("type", "boolean", "description", "종일 여부")),
                        List.of("id", "title", "start_time", "end_time")),

                toolDef("delete_schedule", "일정을 삭제합니다.",
                        Map.of("id", strParam("일정 ID")), List.of("id")),

                toolDef("delete_meeting", "회의를 삭제합니다. 회의 ID를 모를 경우 먼저 get_meetings로 목록을 조회하세요.",
                        Map.of("meeting_id", strParam("삭제할 회의 ID")), List.of("meeting_id"))
        )));

        Map<String, Object> body = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "tools", tools,
                "contents", contents
        );
        return objectMapper.writeValueAsString(body);
    }

    private Map<String, Object> toolDef(String name, String description,
                                         Map<String, Object> properties, List<String> required) {
        return Map.of(
                "name", name,
                "description", description,
                "parameters", Map.of(
                        "type", "object",
                        "properties", properties,
                        "required", required
                )
        );
    }

    private Map<String, Object> strParam(String description) {
        return Map.of("type", "string", "description", description);
    }

    private Map<String, Object> intParam(String description) {
        return Map.of("type", "integer", "description", description);
    }
}
