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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
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

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

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
        return MeetingCreateResponse.from(meeting);
    }

    public SseEmitter processAudio(String meetingId, MultipartFile audioFile) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("회의를 찾을 수 없습니다: " + meetingId));

        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        executor.submit(() -> {
            try {
                // 파일 저장
                String filename = "audio/" + UUID.randomUUID() + "_" + audioFile.getOriginalFilename();
                Path audioPath = Paths.get(uploadDir).toAbsolutePath().resolve(filename);
                Files.createDirectories(audioPath.getParent());
                Files.copy(audioFile.getInputStream(), audioPath);

                updateMeetingRecordingFile(meetingId, filename);

                // STT 처리
                String script = sttService.transcribe(audioPath.toString(), progress -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("stt_progress")
                                .data("{\"progress\":" + progress + "}"));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                emitter.send(SseEmitter.event()
                        .name("stt_done")
                        .data("{\"script\":\"" + script.replace("\"", "\\\"").replace("\n", "\\n") + "\"}"));

                // LLM 분석
                LlmAnalysisResult result = llmService.analyze(script, step -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("ai_progress")
                                .data("{\"step\":\"" + step + "\"}"));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                // 결과 저장
                saveAnalysisResult(meetingId, result);

                emitter.send(SseEmitter.event()
                        .name("done")
                        .data("{\"meetingId\":\"" + meetingId + "\"}"));
                emitter.complete();

            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @Transactional
    protected void updateMeetingRecordingFile(String meetingId, String filename) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("회의를 찾을 수 없습니다: " + meetingId));
        meeting.startProcessing(filename);
        meetingRepository.save(meeting);
    }

    @Transactional
    protected void saveAnalysisResult(String meetingId, LlmAnalysisResult result) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("회의를 찾을 수 없습니다: " + meetingId));

        // 스크립트 세그먼트 저장
        for (LlmAnalysisResult.ScriptData sd : result.scripts()) {
            ScriptSegment segment = ScriptSegment.create(meeting, sd.startTime(), sd.contents());
            scriptRepository.save(segment);
        }

        // 일정 저장
        for (LlmAnalysisResult.ScheduleData sd : result.schedules()) {
            Schedule schedule = Schedule.create(
                    meeting.getProject(), meeting,
                    sd.title(), sd.startTime(), sd.endTime(), sd.allDay(), "ai"
            );
            scheduleRepository.save(schedule);
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

        return MeetingDetailResponse.of(meeting, scripts, schedules);
    }

    @Transactional
    public void updateMeeting(String meetingId, UpdateMeetingRequest request) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("회의를 찾을 수 없습니다: " + meetingId));
        meeting.updateTitle(request.title());
        meeting.updateMeetingAt(request.meetingAt());
    }

    @Transactional
    public void deleteMeeting(String meetingId) {
        if (!meetingRepository.existsById(meetingId)) {
            throw new ResourceNotFoundException("회의를 찾을 수 없습니다: " + meetingId);
        }
        meetingRepository.deleteById(meetingId);
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
    }
}
