package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.ReturnOrder;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ReturnOrderRepository;
import com.sellion.sellionserver.services.EmailService;
import com.sellion.sellionserver.services.InvoiceExcelService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/reports/excel")
@RequiredArgsConstructor
public class ReportApiController {

    private final OrderRepository orderRepository;
    private final ReturnOrderRepository returnOrderRepository;
    private final InvoiceExcelService invoiceExcelService;
    private final EmailService emailService;

    @GetMapping("/orders-detailed")
    public ResponseEntity<?> exportOrdersDetailed(@RequestParam String start, @RequestParam String end) throws IOException {
        List<Order> orders = orderRepository.findOrdersBetweenDates(start + "T00:00:00", end + "T23:59:59");
        if (orders.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Заказы не найдены за указанный период."));
        }

        // Используем try-with-resources для надежного закрытия Workbook
        try (Workbook workbook = invoiceExcelService.generateExcel(orders, null, "Մանրամասն հաշվետվություն")) {
            return getResponseEntity(workbook, "Detailed_Report_Orders_" + start + ".xlsx");
        }
    }

    @GetMapping("/returns-detailed")
    public ResponseEntity<?> exportReturnsDetailed(@RequestParam String start, @RequestParam String end) throws IOException {
        List<ReturnOrder> returns = returnOrderRepository.findReturnsBetweenDates(start + "T00:00:00", end + "T23:59:59");
        if (returns.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Возвраты не найдены за указанный период."));
        }

        try (Workbook workbook = invoiceExcelService.generateExcel(null, returns, "Մանրամասն վերադարձի հաշվետվություն")) {
            return getResponseEntity(workbook, "Detailed_Report_Returns_" + start + ".xlsx");
        }
    }

    @PostMapping("/send-to-accountant")
    public ResponseEntity<?> sendToAccountant(
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam String email,
            @RequestParam(required = false) List<String> types) {
        try {
            List<String> reportTypes = (types != null) ? types : Collections.emptyList();
            List<Order> orders = reportTypes.contains("orders") ?
                    orderRepository.findOrdersBetweenDates(start + "T00:00:00", end + "T23:59:59") : Collections.emptyList();
            List<ReturnOrder> returns = reportTypes.contains("returns") ?
                    returnOrderRepository.findReturnsBetweenDates(start + "T00:00:00", end + "T23:59:59") : Collections.emptyList();

            if (orders.isEmpty() && returns.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Нет данных для отправки за указанный период."));
            }

            try (Workbook workbook = invoiceExcelService.generateExcel(orders, returns, "Отчет для бухгалтерии")) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                workbook.write(bos);
                byte[] bytes = bos.toByteArray();

                // Очистка временных файлов перед отправкой письма, чтобы не держать их в фоне
                if (workbook instanceof SXSSFWorkbook sx) {
                    sx.dispose();
                }

                emailService.sendReportWithAttachment(
                        email,
                        "Sellion ERP: Հաշվետվություն " + start + " / " + end,
                        "Добрый день. Во вложении финансовый отчет.",
                        bytes,
                        "Financial_Report_" + start + ".xlsx"
                );
            }

            return ResponseEntity.ok(Map.of("message", "Հաշվետվությունն ուղարկված է " + email));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Ошибка: " + e.getMessage()));
        }
    }

    // ВСПОМОГАТЕЛЬНЫЙ МЕТОД: Формирует HTTP-ответ с очисткой ресурсов POI
    private ResponseEntity<byte[]> getResponseEntity(Workbook workbook, String fileName) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        workbook.write(bos);
        byte[] bytes = bos.toByteArray();

        // Очистка временных XML-файлов SXSSF (крайне важно для 2026 года)
        if (workbook instanceof SXSSFWorkbook sx) {
            sx.dispose();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        // Стандарт RFC 6266 для корректных имен файлов
        headers.setContentDisposition(ContentDisposition.attachment().filename(fileName).build());
        headers.setContentLength(bytes.length);

        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }
}