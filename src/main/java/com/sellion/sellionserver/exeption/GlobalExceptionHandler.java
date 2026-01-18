package com.sellion.sellionserver.exeption;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {

    // 1. Обработка ошибки 404 (Страница не найдена)
    @ExceptionHandler(NoHandlerFoundException.class)
    public String handle404(HttpServletRequest request, Model model) {
        model.addAttribute("errorTitle", "Страница не найдена");
        model.addAttribute("errorMessage", "Путь " + request.getRequestURI() + " не существует в системе SELLION.");
        model.addAttribute("errorCode", "404");
        return "error"; // Откроет templates/error.html
    }

    // 2. Обработка ошибки 500 (Ошибка в коде или базе данных)
    @ExceptionHandler(Exception.class)
    public String handleInternalError(Exception ex, Model model) {
        // Логируем ошибку в консоль сервера для отладки
        System.err.println(">>> КРИТИЧЕСКАЯ ОШИБКА: Ошибка в коде или базе данных :" + ex.getMessage());
        ex.printStackTrace();

        model.addAttribute("errorTitle", "Внутренняя ошибка сервера");
        model.addAttribute("errorMessage", "Произошло что-то непредвиденное: " + ex.getMessage());
        model.addAttribute("errorCode", "500");
        return "error";
    }

    // 3. Обработка ошибок доступа (Security 403)
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public String handleAccessDenied(Model model) {
        model.addAttribute("errorTitle", "Доступ запрещен");
        model.addAttribute("errorMessage", "У вас нет прав для просмотра этого раздела.");
        model.addAttribute("errorCode", "403");
        return "error";
    }
}