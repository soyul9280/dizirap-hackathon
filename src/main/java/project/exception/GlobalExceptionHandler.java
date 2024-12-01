package project.exception;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public String handleException(Exception ex, Model model) {
        // 예외 메시지를 모델에 추가하여 error.html로 전달
        model.addAttribute("error", ex.getMessage());
        return "error";  // error.html 페이지 반환
    }
}
