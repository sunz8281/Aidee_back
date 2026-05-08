package com.aidee.backend.meeting;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

@Service
public class LlmService {

    /**
     * 스크립트를 분석하여 요약/일정/스크립트 세그먼트를 반환한다 (스텁 구현).
     * stepCallback: 분석 단계 이름 콜백
     */
    public LlmAnalysisResult analyze(String script, Consumer<String> stepCallback) {
        try {
            stepCallback.accept("요약 중");
            Thread.sleep(500);
            stepCallback.accept("일정 추출 중");
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String summary = "회의에서 다음 달 일정 조율 및 프로젝트 진행 상황을 공유하였습니다. "
                + "다음 주 화요일 오전 10시에 팀 미팅이 예정되었습니다.";

        LocalDateTime now = LocalDateTime.now();
        List<LlmAnalysisResult.ScheduleData> schedules = List.of(
                new LlmAnalysisResult.ScheduleData(
                        "팀 미팅",
                        now.plusDays(7).withHour(10).withMinute(0).withSecond(0).withNano(0),
                        now.plusDays(7).withHour(11).withMinute(0).withSecond(0).withNano(0),
                        false
                )
        );

        List<LlmAnalysisResult.ScriptData> scripts = List.of(
                new LlmAnalysisResult.ScriptData(0, "안녕하세요. 오늘 회의를 시작하겠습니다."),
                new LlmAnalysisResult.ScriptData(5, "첫 번째 안건은 다음 달 일정 조율입니다."),
                new LlmAnalysisResult.ScriptData(15, "다음 주 화요일 오전 10시에 팀 미팅을 진행하기로 했습니다."),
                new LlmAnalysisResult.ScriptData(30, "두 번째 안건은 프로젝트 진행 상황 공유입니다."),
                new LlmAnalysisResult.ScriptData(45, "이상으로 오늘 회의를 마치겠습니다.")
        );

        return new LlmAnalysisResult(summary, schedules, scripts);
    }
}
