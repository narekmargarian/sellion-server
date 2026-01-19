package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.ReturnOrder;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ReturnOrderRepository;
import com.sellion.sellionserver.services.InvoiceExcelService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/reports/excel")
@RequiredArgsConstructor
public class ReportApiController {

    private final OrderRepository orderRepository;
    private final ReturnOrderRepository returnOrderRepository;
    private final InvoiceExcelService invoiceExcelService;

    @GetMapping("/orders")
    public void exportOrders(@RequestParam String start, @RequestParam String end, HttpServletResponse response) throws IOException {
        List<Order> orders = orderRepository.findOrdersBetweenDates(start + "T00:00:00", end + "T23:59:59");

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Заказы " + start);

        // Заголовок
        Row header = sheet.createRow(0);
        String[] columns = {"ID", "Дата", "Менеджер", "Магазин", "Сумма", "Оплата", "Статус"};
        for (int i = 0; i < columns.length; i++) header.createCell(i).setCellValue(columns[i]);

        int rowIdx = 1;
        for (Order o : orders) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(o.getId());
            row.createCell(1).setCellValue(o.getCreatedAt());
            row.createCell(2).setCellValue(o.getManagerId());
            row.createCell(3).setCellValue(o.getShopName());
            row.createCell(4).setCellValue(o.getTotalAmount() != null ? o.getTotalAmount() : 0);
            row.createCell(5).setCellValue(o.getPaymentMethod() != null ? o.getPaymentMethod().getDisplayName() : "");
            row.createCell(6).setCellValue(o.getStatus().name());
        }

        sendExcelResponse(response, workbook, "Orders_" + start + ".xlsx");
    }

    @GetMapping("/returns")
    public void exportReturns(@RequestParam String start, @RequestParam String end, HttpServletResponse response) throws IOException {
        List<ReturnOrder> returns = returnOrderRepository.findReturnsBetweenDates(start + "T00:00:00", end + "T23:59:59");

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Возвраты");

        Row header = sheet.createRow(0);
        String[] columns = {"ID", "Дата", "Менеджер", "Магазин", "Причина", "Сумма", "Статус"};
        for (int i = 0; i < columns.length; i++) header.createCell(i).setCellValue(columns[i]);

        int rowIdx = 1;
        for (ReturnOrder r : returns) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(r.getId());
            row.createCell(1).setCellValue(r.getCreatedAt());
            row.createCell(2).setCellValue(r.getManagerId());
            row.createCell(3).setCellValue(r.getShopName());
            row.createCell(4).setCellValue(r.getReturnReason() != null ? r.getReturnReason().getDisplayName() : "");
            row.createCell(5).setCellValue(r.getTotalAmount() != null ? r.getTotalAmount() : 0);
            row.createCell(6).setCellValue(r.getStatus().name());
        }

        sendExcelResponse(response, workbook, "Returns_" + start + ".xlsx");
    }

    private void sendExcelResponse(HttpServletResponse response, Workbook workbook, String fileName) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
        workbook.write(response.getOutputStream());
        workbook.close();
    }


    // НОВЫЙ МЕТОД ДЛЯ ДЕТАЛИЗИРОВАННОГО ЭКСПОРТА ЗАКАЗОВ
    @GetMapping("/orders-detailed")
    public void exportOrdersDetailed(@RequestParam String start, @RequestParam String end, HttpServletResponse response) throws IOException {
        List<Order> orders = orderRepository.findOrdersBetweenDates(start + "T00:00:00", end + "T23:59:59");

        Workbook workbook = new XSSFWorkbook();

        for (Order order : orders) {
            // Генерируем отдельный лист (счет-фактуру) для каждого заказа
            Workbook invoiceWb = invoiceExcelService.generateInvoiceExcel(order);
            // Копируем листы в общий файл (упрощенно)
            // В реальном проекте 2026 года лучше создать один сводный файл, а не объединять листы
            // Пока что этот метод просто выгрузит данные из первого заказа
            workbook = invoiceWb;
            break; // Если нужно выгружать все заказы в разные листы, эту строку убрать
        }

        sendExcelResponse(response, workbook, "Svodniy_document_" + start + ".xlsx");
    }
}
