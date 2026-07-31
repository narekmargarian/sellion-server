package com.sellion.sellionserver.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class PlatformBlockerFilter implements Filter {

    private static final String DESKTOP_AGENT_MARKER = "SellionCustomDesktopAgent/v1.0";
    private static final String ANDROID_PLATFORM_MARKER = "Sellion-Android-App-v1";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String uri = httpRequest.getRequestURI();

        // 1. Извлекаем данные для проверки из заголовков запроса
        String userAgent = httpRequest.getHeader("User-Agent");
        String platformHeader = httpRequest.getHeader("X-Sellion-Platform");

        // 2. Проверяем, наш ли это клиент (Десктоп или Андроид)
        boolean isDesktop = userAgent != null && userAgent.contains(DESKTOP_AGENT_MARKER);
        boolean isAndroid = ANDROID_PLATFORM_MARKER.equals(platformHeader);

        // 3. КРИТИЧНО ДЛЯ АНДРОИД: Если запрос идет к публичному API или сокетам,
        // мы доверяем встроенной защите Spring Security (она сама проверит токены)
        boolean isPublicApi = uri.startsWith("/api/public/") || uri.startsWith("/ws-sellion/");

        // 4. Если проверка пройдена или путь публичный — пускаем к системе. Все остальные — 404.
        if (isDesktop || isAndroid || isPublicApi) {
            chain.doFilter(request, response);
        } else {
            httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND, "Not Found");
        }
    }
}
