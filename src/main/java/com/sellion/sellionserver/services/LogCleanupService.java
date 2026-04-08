package com.sellion.sellionserver.services;
import com.sellion.sellionserver.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogCleanupService {

    private final AuditLogRepository auditLogRepository;

    // Запускается каждый день в 01:00 ночи
    @Scheduled(cron = "0 0 1 * * ?")
    public void cleanupOldLogs() {
        // Вычисляем дату: текущий момент минус 30 дней (или 1 месяц)
        LocalDateTime cutoffDate = LocalDateTime.now().minusMonths(1);

        log.info("Запуск автоматической очистки логов. Удаление записей старше: {}", cutoffDate);

        try {
            auditLogRepository.deleteOlderThan(cutoffDate);
            log.info("Очистка успешно завершена.");
        } catch (Exception e) {
            log.error("Ошибка при автоматической очистке логов: {}", e.getMessage());
        }
    }
}