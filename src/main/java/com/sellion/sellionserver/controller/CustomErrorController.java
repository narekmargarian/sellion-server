package com.sellion.sellionserver.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        // Значения по умолчанию
        String errorCode = "APP ERROR";
        String errorTitle = "Системный сбой";
        String errorMessage = "Произошла непредвиденная ошибка в логике Sellion.";

        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());

            switch (statusCode) {
                case 400: // Bad Request
                    errorCode = "400";
                    errorTitle = "Некорректный запрос";
                    errorMessage = "Android отправил неверные данные. Проверьте JSON-структуру.";
                    break;
                case 401: // Unauthorized
                    errorCode = "401";
                    errorTitle = "Ошибка входа";
                    errorMessage = "Ваша сессия истекла или логин/пароль неверны.";
                    break;
                case 403: // Forbidden
                    errorCode = "403";
                    errorTitle = "Доступ запрещен";
                    errorMessage = "Ваша роль (Оператор/Бухгалтер) не позволяет зайти в этот раздел.";
                    break;
                case 404: // Not Found
                    errorCode = "404";
                    errorTitle = "Адрес не найден";
                    errorMessage = "Запрашиваемая страница удалена или никогда не существовала.";
                    break;
                case 405: // Method Not Allowed
                    errorCode = "405";
                    errorTitle = "Метод запрещен";
                    errorMessage = "Вы пытаетесь отправить данные GET-запросом там, где нужен POST.";
                    break;
                case 408: // Timeout
                    errorCode = "408";
                    errorTitle = "Время вышло";
                    errorMessage = "Сервер слишком долго ждал ответа от базы данных MySQL.";
                    break;
                case 500: // Internal Server Error
                    errorCode = "500";
                    errorTitle = "Критическая ошибка кода";
                    errorMessage = "Ошибка в Java-коде или базе данных. Проверьте логи сервера.";
                    break;
                case 503: // Service Unavailable
                    errorCode = "503";
                    errorTitle = "Сервис недоступен";
                    errorMessage = "Сервер перегружен или база данных MySQL выключена.";
                    break;
            }
        }

        model.addAttribute("errorCode", errorCode);
        model.addAttribute("errorTitle", errorTitle);
        model.addAttribute("errorMessage", errorMessage);

        return "error";
    }
}