package project.controller;

import org.springframework.beans.factory.annotation.Value;
import project.service.VoiceAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.nio.file.Path;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Controller
public class MainController {

    private final VoiceAnalysisService voiceAnalysisService;

    @Autowired
    public MainController(VoiceAnalysisService voiceAnalysisService) {
        this.voiceAnalysisService = voiceAnalysisService;
    }

    // 메인 화면
    @GetMapping("/")
    public String home() {
        return "index";  // 2.html 반환
    }

    // 음성 녹음 페이지
    @GetMapping("/record")
    public String record() {
        return "record";  // 음성 녹음 페이지로 연결
    }

    @PostMapping("/upload")
    public String uploadFile(@RequestParam("voiceFile") MultipartFile file, Model model) {
        try {
            // 파일 저장 로직 (예: 파일 시스템에 저장)
            if (!file.isEmpty()) {
                // 업로드 성공시 모델에 성공 메시지 추가
                model.addAttribute("successMessage", "업로드 성공!");
            }
        } catch (Exception e) {
            // 실패 시 처리 (예: 에러 메시지 추가)
            model.addAttribute("errorMessage", "업로드 실패! 다시 시도해주세요.");
        }

        // 업로드가 끝난 후 동일 페이지로 리디렉션
        return "redirect:/upload";  // 업로드 페이지로 리디렉션하여 성공 메시지 표시
    }

    // 텍스트를 소리내는 페이지
    @GetMapping("/text-to-speech")
    public String textToSpeech() {
        return "text-to-speech";  // 텍스트 입력 및 소리내기, 노래 페이지로 연결
    }

    public VoiceAnalysisService getVoiceAnalysisService() {
        return voiceAnalysisService;
    }

    @RestController
    public static class TextToSpeechController {

        @Value("${api.key}") // application.properties에서 API 키 읽기
        private String API_KEY;

        private final Path uploadDirectory = Paths.get("upload");

        private List<String> audioFiles = new ArrayList<>();

        @PostMapping("/process-text")
        public ResponseEntity<Map<String, String>> processText(@RequestBody Map<String, String> payload) {
            String text = payload.get("text");
            if (text == null || text.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "텍스트가 비어 있습니다."));
            }

            // 텍스트를 안전한 파일 이름으로 변환
            String fileName = sanitizeFileName(text) + ".wav";

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + API_KEY);

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("voice_uuid", "720b370a");
            requestBody.put("data", text);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

            try {
                String API_ENDPOINT = "https://p.cluster.resemble.ai/synthesize";
                ResponseEntity<Map> response = restTemplate.postForEntity(API_ENDPOINT, request, Map.class);

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    Map<String, Object> responseBody = response.getBody();

                    Boolean success = (Boolean) responseBody.get("success");
                    if (Boolean.TRUE.equals(success)) {
                        String audioContent = (String) responseBody.get("audio_content");
                        if (audioContent != null) {
                            byte[] audioBytes = Base64.getDecoder().decode(audioContent.replaceAll("\\s+", ""));
                            Path outputPath = uploadDirectory.resolve(fileName).toAbsolutePath();

                            // 'upload' 폴더가 없으면 생성
                            if (!Files.exists(outputPath.getParent())) {
                                Files.createDirectories(outputPath.getParent());
                            }

                            Files.write(outputPath, audioBytes);

                            // 오디오 파일 목록에 추가
                            audioFiles.add("/upload/" + fileName);

                            return ResponseEntity.ok(Map.of("message", "오디오 생성 및 저장 성공", "filePath", "/upload/" + fileName));
                        } else {
                            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "audio_content 누락"));
                        }
                    } else {
                        String errorMessage = (String) responseBody.get("message");
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", errorMessage));
                    }
                }
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "오디오 생성 실패"));
            } catch (Exception e) {
                e.printStackTrace();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "API 요청 실패"));
            }
        }

        // 텍스트를 파일 이름에 사용할 수 있는 안전한 문자열로 변환
        private String sanitizeFileName(String text) {
            // 파일 이름에 사용할 수 없는 문자들을 제거 (예: /, \, :, *, ?, ", <, >, |)
            String sanitized = text.replaceAll("[^a-zA-Z0-9\\s]", "").replaceAll("\\s+", "_");
            return sanitized;
        }

        @GetMapping("/audio-files")
        public List<String> getAudioFiles() {
            List<String> audioFiles = new ArrayList<>();
            File uploadDir = new File("upload");
            if (uploadDir.exists() && uploadDir.isDirectory()) {
                File[] files = uploadDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile()) {
                            audioFiles.add("/upload/" + file.getName());
                        }
                    }
                }
            }
            return audioFiles;
        }
    }
}
