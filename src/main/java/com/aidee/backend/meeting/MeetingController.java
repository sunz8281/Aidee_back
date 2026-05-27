package com.aidee.backend.meeting;

import com.aidee.backend.auth.User;
import com.aidee.backend.meeting.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;

    @GetMapping("/projects/{projectId}/meetings")
    public ResponseEntity<Map<String, List<MeetingSummaryResponse>>> getMeetings(
            @PathVariable String projectId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of("items", meetingService.getMeetings(projectId, user.getId())));
    }

    @PostMapping("/projects/{projectId}/meetings")
    public ResponseEntity<MeetingCreateResponse> createMeeting(
            @PathVariable String projectId,
            @RequestBody(required = false) CreateMeetingRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(meetingService.createMeeting(projectId, request, user.getId()));
    }

    @PostMapping(value = "/meetings/{meetingId}/audio", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter uploadAudio(@PathVariable String meetingId,
                                   @RequestParam("audioFile") MultipartFile audioFile) {
        return meetingService.processAudio(meetingId, audioFile);
    }

    @GetMapping("/meetings/{meetingId}")
    public ResponseEntity<MeetingDetailResponse> getMeeting(@PathVariable String meetingId) {
        return ResponseEntity.ok(meetingService.getMeeting(meetingId));
    }

    @GetMapping(value = "/meetings/{meetingId}/status/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamStatus(@PathVariable String meetingId) {
        return meetingService.streamStatus(meetingId);
    }

    @PatchMapping("/meetings/{meetingId}")
    public ResponseEntity<Void> updateMeeting(@PathVariable String meetingId,
                                               @RequestBody UpdateMeetingRequest request) {
        meetingService.updateMeeting(meetingId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/meetings/{meetingId}")
    public ResponseEntity<Void> deleteMeeting(@PathVariable String meetingId) {
        meetingService.deleteMeeting(meetingId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/projects/{projectId}/memos")
    public ResponseEntity<Map<String, List<MemoItemResponse>>> getMemos(
            @PathVariable String projectId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of("items", meetingService.getMemos(projectId, user.getId())));
    }

    @PatchMapping("/meetings/{meetingId}/memo")
    public ResponseEntity<Void> updateMemo(@PathVariable String meetingId,
                                            @RequestBody UpdateMemoRequest request) {
        meetingService.updateMemo(meetingId, request.memo());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/meetings/{meetingId}/speakers/{label}")
    public ResponseEntity<Void> updateSpeakerName(@PathVariable String meetingId,
                                                   @PathVariable String label,
                                                   @RequestBody UpdateSpeakerNameRequest request) {
        meetingService.updateSpeakerName(meetingId, label, request.name());
        return ResponseEntity.noContent().build();
    }
}
