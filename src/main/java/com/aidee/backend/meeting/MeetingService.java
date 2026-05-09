package com.aidee.backend.meeting;

import com.aidee.backend.common.ResourceNotFoundException;
import com.aidee.backend.meeting.dto.*;
import com.aidee.backend.project.Project;
import com.aidee.backend.project.ProjectRepository;
import com.aidee.backend.schedule.Schedule;
import com.aidee.backend.schedule.ScheduleRepository;
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

@Service
@RequiredArgsConstructor
public class MeetingService {

    private final ProjectRepository projectRepository;
    private final MeetingRepository meetingRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScriptRepository scriptRepository;
    private final SttService sttService;
    private final LlmService llmService;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucket;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Transactional(readOnly = true)
    public List<MeetingSummaryResponse> getMeetings(String projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("프로젝트를 찾을 수 없습니다: " + projectId);
        }
        return meetingRepository.findByProjectId(projectId).stream()
                .map(MeetingSummaryResponse::from)
                .toList();
    }

    @Transactional
    public MeetingCreateResponse createMeeting(String projectId, CreateMeetingRequest request) {
        Project project = projectRepository.findById(projectId)
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
        meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("회의를 찾을 수 없습니다: " + meetingId));

        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        executor.submit(() -> {
            Path tempFile = null;
            try {
                // 임시 파일로 저장
                String s3Key = "audio/" + UUID.randomUUID() + "_" + audioFile.getOriginalFilename();
                tempFile = Files.createTempFile("audio_", "_" + audioFile.getOriginalFilename());
                Files.copy(audioFile.getInputStream(), tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                // S3 업로드
                sendProgress(emitter, "upload", "녹음 파일을 업로드하는 중입니다");
                s3Client.putObject(
                        PutObjectRequest.builder().bucket(bucket).key(s3Key).build(),
                        RequestBody.fromFile(tempFile)
                );
                updateMeetingRecordingFile(meetingId, s3Key);
                sendProgress(emitter, "upload", "녹음 파일 업로드가 완료되었습니다");

                // STT 처리
                sendProgress(emitter, "stt", "음성을 텍스트로 변환하는 중입니다");
                SttResult sttResult = sttService.transcribe(tempFile.toString(), progress ->
                        sendProgress(emitter, "stt", "음성을 텍스트로 변환하는 중입니다 (" + progress + "%)"));

                sendProgress(emitter, "stt_done", "음성 변환이 완료되었습니다");

                // LLM 분석
                LlmAnalysisResult result = llmService.analyze(sttResult.fullText(), step ->
                        sendProgress(emitter, "analyzing", step));

                saveAnalysisResult(meetingId, sttResult, result);

                emitter.send(SseEmitter.event()
                        .name("done")
                        .data("{\"step\":\"done\",\"message\":\"분석이 완료되었습니다\",\"meetingId\":\"" + meetingId + "\"}"));
                emitter.complete();

            } catch (Exception e) {
                emitter.completeWithError(e);
            } finally {
                // 임시 파일 삭제
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

        for (SttResult.Segment seg : sttResult.segments()) {
            scriptRepository.save(ScriptSegment.create(meeting, seg.startTime(), seg.text()));
        }

        for (LlmAnalysisResult.ScheduleData sd : result.schedules()) {
            scheduleRepository.save(Schedule.create(
                    meeting.getProject(), meeting,
                    sd.title(), sd.startTime(), sd.endTime(), sd.allDay(), "ai"
            ));
        }

        meeting.completeProcessing(result.summary());
        meetingRepository.save(meeting);
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

        String audioUrl = generateAudioUrl(meeting.getRecordingFile());
        return MeetingDetailResponse.of(meeting, scripts, schedules, audioUrl);
    }

    private void sendProgress(SseEmitter emitter, String step, String message) {
        try {
            emitter.send(SseEmitter.event()
                    .name("progress")
                    .data("{\"step\":\"" + step + "\",\"message\":\"" + message + "\"}"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
        meeting.getProject().touch();
        meetingRepository.delete(meeting);
    }

    @Transactional(readOnly = true)
    public List<MemoItemResponse> getMemos(String projectId) {
        if (!projectRepository.existsById(projectId)) {
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
}
