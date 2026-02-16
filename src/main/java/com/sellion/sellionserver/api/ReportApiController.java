package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.ReturnOrder;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ReturnOrderRepository;
import com.sellion.sellionserver.services.EmailService;
import com.sellion.sellionserver.services.InvoiceExcelService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/reports/excel")
@RequiredArgsConstructor
@Slf4j
public class ReportApiController {

    private final OrderRepository orderRepository;
    private final ReturnOrderRepository returnOrderRepository;
    private final InvoiceExcelService invoiceExcelService;
    private final EmailService emailService;

    /**
     * Экспорт детального отчета по заказам.
     * ИДЕАЛЬНО: Используем try-with-resources для мгновенного освобождения памяти.
     */
    @GetMapping("/orders-detailed")
    public ResponseEntity<?> exportOrdersDetailed(@RequestParam String start, @RequestParam String end) {
        try {
            LocalDateTime from = LocalDate.parse(start).atStartOfDay();
            LocalDateTime to = LocalDate.parse(end).atTime(LocalTime.MAX);

            List<Order> orders = orderRepository.findInvoicedOrdersBetweenDates(from, to);

            if (orders.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Заказы за период " + start + " - " + end + " не найдены."));
            }

            try (Workbook workbook = invoiceExcelService.generateExcel(orders, null, "Отчет по продажам")) {
                return getResponseEntity(workbook, "Orders_Report_" + start + ".xlsx");
            }
        } catch (Exception e) {
            log.error("Ошибка генерации отчета по заказам: ", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Массовая отправка данных бухгалтеру.
     * ИДЕАЛЬНО: Поддержка нескольких типов данных в одном письме с защитой от пустых отчетов.
     */
    @PostMapping("/send-to-accountant")
    public ResponseEntity<?> sendToAccountant(@RequestBody ReportRequest request) { // Использование DTO и @RequestBody
        try {
            // Извлекаем данные из объекта request
            String start = request.getStart();
            String end = request.getEnd();
            String email = request.getEmail();

            LocalDateTime from = LocalDate.parse(start).atStartOfDay();
            LocalDateTime to = LocalDate.parse(end).atTime(LocalTime.MAX);
            List<String> reportTypes = (request.getTypes() != null) ? request.getTypes() : Collections.emptyList();


            log.info("Поиск заказов с {} по {}. Выбрано типов: {}", from, to, reportTypes);

            // Логика выборки данных
            List<Order> orders = reportTypes.contains("orders") ?
                    orderRepository.findOrdersBetweenDates(from, to) : Collections.emptyList();

            log.info("Найдено заказов: {}", orders.size());

            List<ReturnOrder> returns = reportTypes.contains("returns") ?
                    returnOrderRepository.findReturnsBetweenDates(from, to) : Collections.emptyList();

            if (orders.isEmpty() && returns.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Нет данных для отправки за указанный период."));
            }

            // Генерация и отправка
            try (Workbook workbook = invoiceExcelService.generateExcel(orders, returns, "Sellion ERP: Финансовый отчет")) {
                byte[] bytes = workbookToBytes(workbook);

                emailService.sendReportWithAttachment(
                        email.trim(),
                        "Sellion ERP 2026: Отчет за " + start + " / " + end,
                        "Добрый день. Во вложении финансовый отчет системы Sellion.",
                        bytes,
                        "Financial_Report_" + start + ".xlsx"
                );
            }

            log.info("Отчет успешно отправлен на {}", email);
            return ResponseEntity.ok(Map.of("message", "Отчет успешно отправлен на " + email));

        } catch (Exception e) {
            log.error("Критическая ошибка отправки отчета: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Ошибка сервера при отправке: " + e.getMessage()));
        }
    }

    /**
     * Отправка выбранных корректировок (для изменения фактур).
     */
    @PostMapping("/send-selected-corrections")
    @Transactional(readOnly = true)
    public ResponseEntity<?> sendSelectedCorrections(@RequestBody Map<String, Object> payload) {
        try {
            List<Long> ids = ((List<?>) payload.get("ids")).stream()
                    .map(id -> Long.valueOf(id.toString()))
                    .toList();
            String email = (String) payload.get("email");

            if (ids.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Список ID пуст"));

            List<ReturnOrder> selectedReturns = returnOrderRepository.findAllById(ids);

            try (Workbook workbook = invoiceExcelService.generateExcel(null, selectedReturns, "РЕЕСТР КОРРЕКТИРОВОК")) {
                byte[] bytes = workbookToBytes(workbook);

                emailService.sendReportWithAttachment(
                        email.trim(),
                        "Sellion ERP: КОРРЕКТИРОВКИ ФАКТУР (" + LocalDate.now() + ")",
                        "Список корректировок для внесения изменений в первичные документы.",
                        bytes,
                        "Corrections_" + LocalDate.now() + ".xlsx"
                );
            }

            return ResponseEntity.ok(Map.of("success", true, "count", ids.size()));
        } catch (Exception e) {
            log.error("Ошибка отправки корректировок: ", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // --- ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ (ИДЕАЛЬНАЯ РАБОТА С ПАМЯТЬЮ) ---

    private byte[] workbookToBytes(Workbook workbook) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            workbook.write(bos);
            // Если используем SXSSF, обязательно вызываем dispose для очистки временных файлов
            if (workbook instanceof SXSSFWorkbook sx) {
                sx.dispose();
            }
            return bos.toByteArray();
        }
    }

    private ResponseEntity<byte[]> getResponseEntity(Workbook workbook, String fileName) throws IOException {
        byte[] bytes = workbookToBytes(workbook);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment().filename(fileName).build());
        headers.setContentLength(bytes.length);

        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    /**
     * Экспорт детального отчета по возвратам.
     * ДОБАВЛЕНО: Эндпоинт, который вызывается при нажатии кнопки "Экспорт Возвратов".
     */
    @GetMapping("/returns-detailed")
    public ResponseEntity<?> exportReturnsDetailed(@RequestParam String start, @RequestParam String end) {
        try {
            LocalDateTime from = LocalDate.parse(start).atStartOfDay();
            LocalDateTime to = LocalDate.parse(end).atTime(LocalTime.MAX);

            // Получаем список возвратов за период
            List<ReturnOrder> returns = returnOrderRepository.findReturnsBetweenDates(from, to);

            if (returns.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Возвраты за период " + start + " - " + end + " не найдены."));
            }

            // Генерируем Excel. Передаем null в заказы и наш список в возвраты.
            try (Workbook workbook = invoiceExcelService.generateExcel(null, returns, "Отчет по возвратам")) {
                return getResponseEntity(workbook, "Returns_Report_" + start + "_" + end + ".xlsx");
            }
        } catch (Exception e) {
            log.error("Ошибка генерации отчета по возвратам: ", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @Data
    public static class ReportRequest {
        private String start;
        private String end;
        private String email;
        private List<String> types;
    }

}
