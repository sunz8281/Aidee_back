package com.aidee.backend.agent;

import com.aidee.backend.agent.dto.AgentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping(value = "/projects/{projectId}/agent", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@PathVariable String projectId,
                            @RequestParam(required = false) String meetingId,
                            @RequestBody AgentRequest request) {
        return agentService.chat(projectId, meetingId, request);
    }
}
