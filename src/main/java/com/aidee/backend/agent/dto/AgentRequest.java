package com.aidee.backend.agent.dto;

import java.util.List;

public record AgentRequest(
        String message,
        List<MessageDto> history
) {
}
