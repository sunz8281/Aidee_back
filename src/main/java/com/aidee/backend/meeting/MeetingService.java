package com.aidee.backend.meeting;

import com.aidee.backend.auth.User;
import com.aidee.backend.common.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import com.aidee.backend.meeting.dto.*;
import com.aidee.backend.project.Project;
import com.aidee.backend.project.ProjectRepository;
import com.aidee.backend.schedule.Schedule;
import com.aidee.backend.schedule.ScheduleRepository;
import com.aidee.backend.embedding.EmbeddingService;
import com.aidee.backend.embedding.ScriptEmbedding;
import com.aidee.backend.embedding.ScriptEmbeddingRepository;
import com.aidee.backend.script.ScriptRepository;
import com.aidee.backend.script.ScriptSegment;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingService {

    private final ProjectRepository projectRepository;
    private final MeetingRepository meetingRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScriptRepository scriptRepository;
    private final ScriptEmbeddingRepository scriptEmbeddingRepository;
    private final EmbeddingService embeddingService;
    private final SttService sttService;
    private final LlmService llmService;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucket;

    private final SpeakerNameRepository speakerNameRepository;
    private final MeetingProgressBroadcaster broadcaster;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Transactional(readOnly = true)
    public List<MeetingSummaryResponse> getMeetings(String projectId, String userId) {
        if (!projectRepository.existsByIdAndUserId(projectId, userId)) {
            throw new ResourceNotFoundException("프로젝트를 찾을 수 없습니다: " + projectId);
        }
        return meetingRepository.findByProjectIdOrderByMeetingAtDesc(projectId).stream()
                .map(MeetingSummaryResponse::from)
                .toList();
    }

    @Transactional
    public MeetingCreateResponse createMeeting(String projectId, CreateMeetingRequest request, String userId) {
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("프로젝트를 찾을 수 없습니다: " + projectId));

        String title = (request != null && request.title() != null && !request.title().isBlank())
                ? request.title()
                : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " 회의";

        LocalDateTime meetingAt = (request != null && request.meetingAt() != null)
                ? request.meetingAt()
                : LocalDateTime.now();

        Meeting meeting = Meeting.create(project, title, meetingAt);
        meetingRepository.save(meeting);
        project.touch();
        projectRepository.save(project);
        return MeetingCreateResponse.from(meeting);
    }

    public SseEmitter processAudio(String meetingId, MultipartFile audioFile) {
        Meeting existing = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("회의를 찾을 수 없습니다: " + meetingId));

        if (existing.getStatus() != MeetingStatus.PENDING) {
            throw new IllegalStateException("이미 처리 중이거나 완료된 회의입니다.");
        }

        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        broadcaster.register(meetingId, emitter);

        executor.submit(() -> {
            Path tempFile = null;
            try {
                String s3Key = "audio/" + UUID.randomUUID() + "_" + audioFile.getOriginalFilename();
                tempFile = Files.createTempFile("audio_", "_" + audioFile.getOriginalFilename());
                Files.copy(audioFile.getInputStream(), tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                // S3 업로드
                broadcast(meetingId, "upload", "녹음 파일을 업로드하는 중입니다");
                s3Client.putObject(
                        PutObjectRequest.builder().bucket(bucket).key(s3Key).build(),
                        RequestBody.fromFile(tempFile)
                );
                updateMeetingRecordingFile(meetingId, s3Key);
                broadcast(meetingId, "upload", "녹음 파일 업로드가 완료되었습니다");

                // S3 presigned URL 생성 후 CLOVA STT에 전달
                String audioUrl = generateAudioUrl(s3Key);
                broadcast(meetingId, "stt", "음성을 텍스트로 변환하는 중입니다");
                SttResult sttResult = sttService.transcribe(
                        audioUrl,
                        chunk -> {},
                        segment -> broadcaster.send(meetingId, "stt",
                                "{\"startTime\":" + segment.startTime() +
                                ",\"speaker\":" + (segment.speaker() != null ? "\"" + segment.speaker() + "\"" : "null") +
                                ",\"text\":\"" + segment.text().replace("\"", "\\\"").replace("\n", "\\n") + "\"}")
                );
                broadcast(meetingId, "stt_done", "음성 변환이 완료되었습니다");

                // LLM 분석
                broadcast(meetingId, "analyzing", "회의 내용을 분석하는 중입니다");
                LlmAnalysisResult result = llmService.analyze(sttResult.fullText(), chunk ->
                        broadcast(meetingId, "analyzing", chunk));

                // 처리 결과 저장 (반드시 완료)
                saveAnalysisResult(meetingId, sttResult, result);

                // 완료 이벤트: 전체 회의 상세 전송
                try {
                    MeetingDetailResponse detail = getMeeting(meetingId);
                    broadcaster.send(meetingId, "done", objectMapper.writeValueAsString(detail));
                } catch (Exception e) {
                    log.warn("done 이벤트 직렬화 실패: {}", e.getMessage());
                    broadcaster.send(meetingId, "done",
                            "{\"meetingId\":\"" + meetingId + "\",\"message\":\"분석이 완료되었습니다\"}");
                }
                broadcaster.complete(meetingId);

            } catch (Exception e) {
                log.error("오디오 처리 실패 (meetingId={}): {}", meetingId, e.getMessage(), e);
                failMeeting(meetingId);
                broadcaster.completeWithError(meetingId, "처리 중 오류가 발생했습니다");
            } finally {
                if (tempFile != null) {
                    try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
                }
            }
        });

        return emitter;
    }

    @Transactional
    protected void updateMeetingRecordingFile(String meetingId, String s3Key) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("회의를 찾을 수 없습니다: " + meetingId));
        meeting.startProcessing(s3Key);
        meetingRepository.save(meeting);
    }

    @Transactional
    protected void saveAnalysisResult(String meetingId, SttResult sttResult, LlmAnalysisResult result) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("회의를 찾을 수 없습니다: " + meetingId));

        scriptRepository.deleteByMeetingId(meetingId);
        scriptEmbeddingRepository.deleteByMeetingId(meetingId);

        // 1단계: 스크립트 저장 (DB only, 트랜잭션 짧게)
        List<ScriptSegment> savedScripts = new ArrayList<>();
        for (SttResult.Segment seg : sttResult.segments()) {
            savedScripts.add(scriptRepository.save(
                    ScriptSegment.create(meeting, seg.startTime(), seg.text(), seg.speaker())));
        }

        // 2단계: 임베딩 계산 + 저장 (외부 API 호출을 DB 커넥션과 분리)
        for (int i = 0; i < savedScripts.size(); i++) {
            ScriptSegment script = savedScripts.get(i);
            SttResult.Segment seg = sttResult.segments().get(i);
            try {
                float[] embedding = embeddingService.embed(seg.text());
                scriptEmbeddingRepository.save(ScriptEmbedding.create(
                        script.getId(), meeting.getId(), meeting.getProject().getId(),
                        meeting.getTitle(), meeting.getMeetingAt(),
                        seg.startTime(), seg.text(), embedding));
            } catch (Exception e) {
                log.warn("세그먼트 임베딩 실패 (startTime={}): {}", seg.startTime(), e.getMessage());
            }
        }

        for (LlmAnalysisResult.ScheduleData sd : result.schedules()) {
            try {
                scheduleRepository.save(Schedule.create(
                        meeting.getProject(), meeting,
                        sd.title(), sd.startTime(), sd.endTime(), sd.allDay(), "ai"
                ));
            } catch (Exception e) {
                log.warn("스케줄 저장 실패 (title={}): {}", sd.title(), e.getMessage());
            }
        }

        meeting.completeProcessing(result.summary(), result.memo());
        meetingRepository.save(meeting);
    }

    public SseEmitter streamStatus(String meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("회의를 찾을 수 없습니다: " + meetingId));

        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        // 이미 완료/실패 상태면 즉시 신호만 전송 (detail은 GET /meetings/:id로 조회)
        if (meeting.getStatus() == MeetingStatus.DONE) {
            executor.submit(() -> {
                try {
                    emitter.send(SseEmitter.event().name("done").data("{\"meetingId\":\"" + meetingId + "\"}"));
                    emitter.complete();
                } catch (Exception ignored) {}
            });
            return emitter;
        }

        if (meeting.getStatus() == MeetingStatus.FAILED) {
            executor.submit(() -> {
                try {
                    emitter.send(SseEmitter.event().name("failed").data("{\"status\":\"failed\"}"));
                    emitter.complete();
                } catch (Exception ignored) {}
            });
            return emitter;
        }

        // 처리 중인 경우 broadcaster에 등록해 실시간 이벤트 수신
        broadcaster.register(meetingId, emitter);
        return emitter;
    }

    @Transactional
    protected void failMeeting(String meetingId) {
        meetingRepository.findById(meetingId).ifPresent(m -> {
            m.failProcessing();
            meetingRepository.save(m);
        });
    }

    @Transactional(readOnly = true)
    public MeetingDetailResponse getMeeting(String meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("회의를 찾을 수 없습니다: " + meetingId));

        List<ScriptSegmentResponse> scripts = scriptRepository
                .findByMeetingIdOrderByStartTimeAsc(meetingId)
                .stream().map(ScriptSegmentResponse::from).toList();

        List<com.aidee.backend.schedule.dto.ScheduleResponse> schedules = scheduleRepository
                .findByMeetingId(meetingId)
                .stream().map(com.aidee.backend.schedule.dto.ScheduleResponse::from).toList();

        java.util.Map<String, String> speakerNames = speakerNameRepository.findByMeetingId(meetingId)
                .stream().collect(java.util.stream.Collectors.toMap(SpeakerName::getLabel, SpeakerName::getName));

        String audioUrl = generateAudioUrl(meeting.getRecordingFile());
        return MeetingDetailResponse.of(meeting, scripts, schedules, audioUrl, speakerNames);
    }

    // 모든 구독자에게 이벤트 브로드캐스트 — 메시지 이스케이핑 포함
    private void broadcast(String meetingId, String event, String message) {
        broadcaster.send(meetingId, event,
                "{\"message\":\"" + message.replace("\"", "\\\"").replace("\n", "\\n") + "\"}");
    }

    private String generateAudioUrl(String s3Key) {
        if (s3Key == null) return null;
        return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(1))
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(s3Key)
                        .build())
                .build())
                .url().toString();
    }

    @Transactional(readOnly = true)
    public MeetingDetailResponse getSharedMeeting(String shareToken, String meetingId) {
        Project project = projectRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new ResourceNotFoundException("유효하지 않은 공유 링크입니다."));

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("회의를 찾을 수 없습니다: " + meetingId));

        if (!meeting.getProject().getId().equals(project.getId())) {
            throw new ResourceNotFoundException("회의를 찾을 수 없습니다: " + meetingId);
        }

        List<ScriptSegmentResponse> scripts = scriptRepository
                .findByMeetingIdOrderByStartTimeAsc(meetingId)
                .stream().map(ScriptSegmentResponse::from).toList();

        List<com.aidee.backend.schedule.dto.ScheduleResponse> schedules = scheduleRepository
                .findByMeetingId(meetingId)
                .stream().map(com.aidee.backend.schedule.dto.ScheduleResponse::from).toList();

        java.util.Map<String, String> speakerNames = speakerNameRepository.findByMeetingId(meetingId)
                .stream().collect(java.util.stream.Collectors.toMap(SpeakerName::getLabel, SpeakerName::getName));

        String audioUrl = generateAudioUrl(meeting.getRecordingFile());
        return MeetingDetailResponse.of(meeting, scripts, schedules, audioUrl, speakerNames);
    }

    @Transactional
    public void updateMeeting(String meetingId, UpdateMeetingRequest request) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("회의를 찾을 수 없습니다: " + meetingId));
        meeting.updateTitle(request.title());
        meeting.updateMeetingAt(request.meetingAt());
        meeting.getProject().touch();
    }

    @Transactional
    public void deleteMeeting(String meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("회의를 찾을 수 없습니다: " + meetingId));
        scheduleRepository.deleteByMeetingId(meetingId);
        scriptEmbeddingRepository.deleteByMeetingId(meetingId);
        scriptRepository.deleteByMeetingId(meetingId);
        speakerNameRepository.deleteByMeetingId(meetingId);
        meeting.getProject().touch();
        meetingRepository.delete(meeting);
    }

    @Transactional(readOnly = true)
    public List<MemoItemResponse> getMemos(String projectId, String userId) {
        if (!projectRepository.existsByIdAndUserId(projectId, userId)) {
            throw new ResourceNotFoundException("프로젝트를 찾을 수 없습니다: " + projectId);
        }
        return meetingRepository.findByProjectIdAndMemoIsNotNull(projectId).stream()
                .filter(m -> m.getMemo() != null && !m.getMemo().isBlank())
                .map(MemoItemResponse::from)
                .toList();
    }

    @Transactional
    public void updateMemo(String meetingId, String memo) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("회의를 찾을 수 없습니다: " + meetingId));
        meeting.updateMemo(memo);
        meeting.getProject().touch();
    }

    @Transactional
    public void updateSpeakerName(String meetingId, String label, String name) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("회의를 찾을 수 없습니다: " + meetingId));
        speakerNameRepository.findByMeetingIdAndLabel(meetingId, label)
                .ifPresentOrElse(
                        s -> { s.updateName(name); speakerNameRepository.save(s); },
                        () -> speakerNameRepository.save(SpeakerName.create(meeting, label, name))
                );
    }
}
