package com.sellion.sellionserver.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDateTime;
@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        // 1. ПРОВЕРКА: Если это фоновый запрос (AJAX/Fetch), не переходим на страницу ошибки
        String requestedWith = request.getHeader("X-Requested-With");
        String acceptHeader = request.getHeader("Accept");

        // Если запрос ожидает JSON или это AJAX, возвращаем null.
        // В этом случае Spring просто отдаст статус (403, 500 и т.д.), и сработает ваш showToast в JS.
        if ("XMLHttpRequest".equals(requestedWith) || (acceptHeader != null && acceptHeader.contains("application/json"))) {
            return null;
        }

        // 2. ЛОГИКА ДЛЯ ОБЫЧНЫХ ПЕРЕХОДОВ (если ввели неверный адрес в строку браузера)
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        String errorCode = "APP ERROR";
        String errorTitle = "Системный сбой";
        String errorMessage = "Произошла непредвиденная ошибка в логике Sellion.";

        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());

            switch (statusCode) {
                case 400:
                    errorCode = "400";
                    errorTitle = "Некорректный запрос";
                    errorMessage = "Неверные данные запроса. Проверьте структуру JSON.";
                    break;
                case 401:
                    errorCode = "401";
                    errorTitle = "Ошибка входа";
                    errorMessage = "Ваша сессия истекла или логин/пароль неверны.";
                    break;
                case 403:
                    errorCode = "403";
                    errorTitle = "Доступ запрещен";
                    errorMessage = "Ваша роль не позволяет выполнить это действие или зайти в этот раздел.";
                    break;
                case 404:
                    errorCode = "404";
                    errorTitle = "Адрес не найден";
                    errorMessage = "Запрашиваемая страница удалена или никогда не существовала.";
                    break;
                case 500:
                    errorCode = "500";
                    errorTitle = "Критическая ошибка";
                    errorMessage = "Ошибка в Java-коде или базе данных. Проверьте логи сервера.";
                    break;
                case 503:
                    errorCode = "503";
                    errorTitle = "Сервис недоступен";
                    errorMessage = "Сервер перегружен или база данных MySQL выключена.";
                    break;
                default:
                    errorCode = String.valueOf(statusCode);
                    break;
            }
        }

        model.addAttribute("errorCode", errorCode);
        model.addAttribute("errorTitle", errorTitle);
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("timestamp", LocalDateTime.now());

        // Возвращаем страницу только для обычных браузерных переходов
        return "error";
    }
}
