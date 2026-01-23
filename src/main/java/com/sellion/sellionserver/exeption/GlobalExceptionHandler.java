package com.sellion.sellionserver.exeption;

import com.sellion.sellionserver.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.nio.file.AccessDeniedException;
import java.util.stream.Collectors;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Вспомогательный метод для проверки: является ли запрос API-запросом
    private boolean isApiRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/");
    }

    // 1. Обработка ошибок валидации (@Valid / @RequestBody)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request, Model model) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        if (isApiRequest(request)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Ошибка данных: " + errorMessage));
        }

        model.addAttribute("errorTitle", "Ошибка валидации");
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("errorCode", "400");
        return "error";
    }

    // 2. Обработка ошибки 404 (Страница не найдена)
    @ExceptionHandler(NoHandlerFoundException.class)
    public Object handle404(HttpServletRequest request, Model model) {
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Ресурс не найден: " + request.getRequestURI()));
        }

        model.addAttribute("errorTitle", "Страница не найдена");
        model.addAttribute("errorMessage", "Путь " + request.getRequestURI() + " не существует в системе SELLION.");
        model.addAttribute("errorCode", "404");
        return "error";
    }

    // 3. Обработка ошибок доступа (Security 403)
    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(HttpServletRequest request, Model model) {
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Доступ запрещен для данного API ключа"));
        }

        model.addAttribute("errorTitle", "Доступ запрещен");
        model.addAttribute("errorMessage", "У вашей учетной записи недостаточно прав.");
        model.addAttribute("errorCode", "403");
        return "error";
    }

    // 4. Глобальная обработка всех остальных исключений (500)
    @ExceptionHandler(Exception.class)
    public Object handleInternalError(Exception ex, HttpServletRequest request, Model model) {
        log.error(">>> КРИТИЧЕСКАЯ ОШИБКА: ", ex); // Идеально: используем логгер вместо System.err

        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Ошибка сервера: " + ex.getMessage()));
        }

        model.addAttribute("errorTitle", "Внутренняя ошибка сервера");
        model.addAttribute("errorMessage", "Произошло что-то непредвиденное. Пожалуйста, свяжитесь с администратором.");
        model.addAttribute("errorCode", "500");
        return "error";
    }
}
