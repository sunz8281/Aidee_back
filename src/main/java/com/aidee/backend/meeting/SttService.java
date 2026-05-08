package com.aidee.backend.meeting;

import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
public class SttService {

    /**
     * 오디오 파일을 STT 처리한다 (스텁 구현).
     * progressCallback: 0~100 진행률 콜백
     */
    public String transcribe(String filePath, Consumer<Integer> progressCallback) {
        try {
            for (int progress : new int[]{0, 25, 50, 75, 100}) {
                progressCallback.accept(progress);
                Thread.sleep(300);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "안녕하세요. 오늘 회의를 시작하겠습니다. "
                + "첫 번째 안건은 다음 달 일정 조율입니다. "
                + "다음 주 화요일 오전 10시에 팀 미팅을 진행하기로 했습니다. "
                + "두 번째 안건은 프로젝트 진행 상황 공유입니다. "
                + "이상으로 오늘 회의를 마치겠습니다.";
    }
}
