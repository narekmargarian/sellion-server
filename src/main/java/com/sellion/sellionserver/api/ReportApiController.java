package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.ReturnOrder;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ReturnOrderRepository;
import com.sellion.sellionserver.services.EmailService;
import com.sellion.sellionserver.services.InvoiceExcelService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
    public void exportOrdersDetailed(@RequestParam String start, @RequestParam String end, HttpServletResponse response) throws IOException {
        List<Order> orders = orderRepository.findOrdersBetweenDates(start + "T00:00:00", end + "T23:59:59");

        // Теперь здесь нет ошибки, так как мы передаем список заказов целиком
        Workbook workbook = invoiceExcelService.generateExcel(orders, null, "Մանրամասն հաշվետվություն");

        sendExcelResponse(response, workbook, "Detailed_Report_" + start + ".xlsx");
    }

    @PostMapping("/send-to-accountant")
    public ResponseEntity<?> sendToAccountant(@RequestParam String start, @RequestParam String end, @RequestParam String email) {
        try {
            List<Order> orders = orderRepository.findOrdersBetweenDates(start + "T00:00:00", end + "T23:59:59");
            List<ReturnOrder> returns = returnOrderRepository.findReturnsBetweenDates(start + "T00:00:00", end + "T23:59:59");

            Workbook workbook = invoiceExcelService.generateExcel(orders, returns, "Ֆինանսական հաշվետվություն");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            byte[] bytes = bos.toByteArray();
            workbook.close();

            emailService.sendReportWithAttachment(
                    email,
                    "Sellion ERP: Հաշվետվություն " + start + " / " + end,
                    "Добрый день. Во вложении финансовый отчет (армянская версия).",
                    bytes,
                    "Financial_Report_" + start + ".xlsx"
            );

            return ResponseEntity.ok(Map.of("message", "Հաշվետվությունն ուղարկված է " + email));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Ошибка: " + e.getMessage()));
        }
    }

    private void sendExcelResponse(HttpServletResponse response, Workbook workbook, String fileName) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
        workbook.write(response.getOutputStream());

        if (workbook instanceof SXSSFWorkbook sx) {
            sx.dispose();
        }
        workbook.close();
    }
}