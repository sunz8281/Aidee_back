package com.aidee.backend.agent;

import com.aidee.backend.agent.dto.AgentRequest;
import com.aidee.backend.auth.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping(value = "/projects/{projectId}/agent", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@PathVariable String projectId,
                            @RequestParam(required = false) String meetingId,
                            @RequestBody AgentRequest request,
                            @AuthenticationPrincipal User user) {
        return agentService.chat(projectId, meetingId, request, user.getId());
    }
}
