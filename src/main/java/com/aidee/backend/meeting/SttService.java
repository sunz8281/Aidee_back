package com.aidee.backend.meeting;

import com.aidee.backend.ai.GeminiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class SttService {

    private final GeminiClient geminiClient;

    public String transcribe(String filePath, Consumer<Integer> progressCallback) {
        try {
            progressCallback.accept(10);

            byte[] audioBytes = Files.readAllBytes(Path.of(filePath));
            String base64Audio = Base64.getEncoder().encodeToString(audioBytes);

            progressCallback.accept(30);

            String requestBody = """
                    {
                      "contents": [{
                        "parts": [
                          {"text": "음성 파일을 텍스트로 변환해 주세요. 음성 내용의 텍스트만 반환해 주세요."},
                          {"inline_data": {"mime_type": "audio/mpeg", "data": "%s"}}
                        ]
                      }]
                    }
                    """.formatted(base64Audio);

            progressCallback.accept(50);
            String result = geminiClient.generateContent(GeminiClient.AUDIO_MODEL, requestBody);
            progressCallback.accept(100);

            return result;

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("STT 처리 실패", e);
        }
    }
}
