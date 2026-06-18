package com.sellion.sellionserver.services;

import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.entity.Invoice;
import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.InvoiceRepository;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {
    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final ClientRepository clientRepository;
    private final PaymentRepository paymentRepository; // ДОБАВЛЕНО для контроля оплат
    private final FinanceService financeService;

    /**
     * ЭТАП 2: Создание инвойса из зарезервированного заказа.
     */
    @Transactional(rollbackFor = Exception.class)
    public Invoice createInvoiceFromOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Заказ #" + orderId + " не найден"));

        if (order.getInvoiceId() != null) {
            log.warn("Для заказа #{} уже существует инвойс ID: {}", orderId, order.getInvoiceId());
            return null;
        }

        if (order.getStatus() != OrderStatus.RESERVED) {
            throw new RuntimeException("Ошибка: Нельзя выставить счет. Заказ должен иметь статус RESERVED. Текущий статус: " + order.getStatus());
        }

        String cleanShopName = (order.getShopName() != null) ? order.getShopName().trim() : "";
        Client client = clientRepository.findByNameIgnoreCase(cleanShopName)
                .orElseThrow(() -> new RuntimeException("Критическая ошибка: Магазин '" + cleanShopName + "' не найден в справочнике!"));

        String invoiceNumber = "INV-" + LocalDate.now().getYear() + "-" + order.getId();

        Invoice invoice = new Invoice();
        invoice.setOrder(order);
        invoice.setClientId(client.getId());
        invoice.setShopName(cleanShopName);
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setTotalAmount(order.getTotalAmount());
        invoice.setStatus("UNPAID");

        Invoice savedInvoice = invoiceRepository.saveAndFlush(invoice);

        order.setInvoiceId(savedInvoice.getId());
        order.setStatus(OrderStatus.PROCESSED);
        orderRepository.saveAndFlush(order);

        try {
            financeService.registerOperation(
                    client.getId(),
                    "ORDER",
                    order.getTotalAmount(),
                    savedInvoice.getId(),
                    "Счет " + invoiceNumber,
                    order.getShopName()
            );
        } catch (Exception e) {
            log.error("Критическая ошибка при регистрации финансов для инвойса {}", invoiceNumber);
            throw new RuntimeException("Ошибка финансов: " + e.getMessage());
        }

        log.info("Инвойс {} успешно создан для магазина {}. Статус заказа изменен на PROCESSED", invoiceNumber, order.getShopName());
        return savedInvoice;
    }

    /**
     * АТОМАРНАЯ ОТМЕНА ЗАКАЗА И СЧЕТА С КОРРЕКТИРОВКОЙ БАЛАНСА.
     * Предотвращает зависание долгов в системе при аннулировании документов.
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelInvoiceOrOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Заказ #" + orderId + " не найден"));

        // Ситуация 1: Заказ новый или принят, счет еще не выставлялся
        if (order.getInvoiceId() == null) {
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.saveAndFlush(order);
            log.info("Заказ #{} успешно отменен. Финансовые корректировки не требуются.", orderId);
            return;
        }

        // Ситуация 2: По заказу уже выставлен счет
        Invoice invoice = invoiceRepository.findById(order.getInvoiceId())
                .orElseThrow(() -> new RuntimeException("Связанный счет #" + order.getInvoiceId() + " не найден"));

        // ЗАЩИТА: Запрещено отменять счета, по которым уже пошли платежи
        if ("PAID".equals(invoice.getStatus()) || "PARTIAL".equals(invoice.getStatus()) ||
                (invoice.getPaidAmount() != null && invoice.getPaidAmount().compareTo(BigDecimal.ZERO) > 0)) {
            throw new RuntimeException("Невозможно отменить счет #" + invoice.getInvoiceNumber() +
                    ", так как по нему зарегистрированы оплаты! Сначала аннулируйте платежи.");
        }

        // 1. Аннулируем статусы документов в БД
        invoice.setStatus("CANCELLED");
        invoiceRepository.saveAndFlush(invoice);

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.saveAndFlush(order);

        // 2. ФИНАНСОВЫЙ ОТКАТ (Сторнирование долга)
        // Тип "RETURN" уменьшит долг клиента на сумму отмененного счета
        try {
            financeService.registerOperation(
                    invoice.getClientId(),
                    "RETURN", // Используем RETURN для списания долга обратно
                    invoice.getTotalAmount(),
                    invoice.getId(),
                    "Аннулирование счета " + invoice.getInvoiceNumber() + " (Отмена заказа)",
                    invoice.getShopName()
            );
        } catch (Exception e) {
            log.error("Критическая ошибка при финансовом откате отмены счета {}", invoice.getInvoiceNumber());
            throw new RuntimeException("Ошибка финансового отката: " + e.getMessage());
        }

        log.info("Счет {} и заказ #{} успешно аннулированы. Проведена корректировка долга на сумму {}",
                invoice.getInvoiceNumber(), orderId, invoice.getTotalAmount());
    }

    public void exportDebtsToExcel(String start, String end, OutputStream outputStream) throws IOException {
        List<Invoice> allInvoices = invoiceRepository.findAll();

        List<Invoice> filteredInvoices = allInvoices.stream()
                .filter(inv -> {
                    if (start == null || end == null || inv.getCreatedAt() == null) return true;
                    LocalDate invDate = inv.getCreatedAt().toLocalDate();
                    LocalDate startDate = LocalDate.parse(start);
                    LocalDate endDate = LocalDate.parse(end);
                    return !invDate.isBefore(startDate) && !invDate.isAfter(endDate);
                })
                .collect(Collectors.toList());

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Список долгов");

            // 1. Создаем общий стиль для границ ячеек
            CellStyle cellStyle = workbook.createCellStyle();
            cellStyle.setBorderTop(BorderStyle.THIN);
            cellStyle.setBorderBottom(BorderStyle.THIN);
            cellStyle.setBorderLeft(BorderStyle.THIN);
            cellStyle.setBorderRight(BorderStyle.THIN);

            // Жирный шрифт для шапки и Итого
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.cloneStyleFrom(cellStyle);
            headerStyle.setFont(boldFont);

            // 2. Создаем заголовки строго по фото (A-F)
            Row headerRow = sheet.createRow(0);
            String[] headers = {"ID Счета", "Менеджер", "Дата", "Магазин", "Сумма", "Статус"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 3. Заполняем данные
            int rowNum = 1;
            for (Invoice inv : filteredInvoices) {
                Row row = sheet.createRow(rowNum++);

                // Добавляем все ячейки по порядку и применяем границы
                Cell cell0 = row.createCell(0);
                cell0.setCellValue(inv.getId() != null ? inv.getId().toString() : "N/A");
                cell0.setCellStyle(cellStyle);

                Cell cell1 = row.createCell(1);
                // Если в Invoice есть имя менеджера, подставьте его сюда вместо ""
                cell1.setCellValue("");
                cell1.setCellStyle(cellStyle);

                Cell cell2 = row.createCell(2);
                // Подставляем дату создания, если она есть
                cell2.setCellValue(inv.getCreatedAt() != null ? inv.getCreatedAt().toLocalDate().toString() : "");
                cell2.setCellStyle(cellStyle);

                Cell cell3 = row.createCell(3);
                cell3.setCellValue(inv.getShopName() != null ? inv.getShopName() : "");
                cell3.setCellStyle(cellStyle);

                Cell cell4 = row.createCell(4);
                BigDecimal amount = inv.getTotalAmount();
                cell4.setCellValue(amount != null ? amount.doubleValue() : 0.0);
                cell4.setCellStyle(cellStyle);

                Cell cell5 = row.createCell(5);
                cell5.setCellValue(inv.getStatus() != null ? inv.getStatus() : "");
                cell5.setCellStyle(cellStyle);
            }

            // 4. Создаем строку "Итого" (строго под колонкой "Магазин" и формула под "Сумма")
            Row totalRow = sheet.createRow(rowNum);

            // Пустые ячейки с границами для сохранения сетки как на фото
            for (int i = 0; i < 6; i++) {
                totalRow.createCell(i).setCellStyle(headerStyle);
            }

            // Текст "Итого" в колонку D (индекс 3)
            totalRow.getCell(3).setCellValue("Итого");

            // Формула СУММ в колонку E (индекс 4) для диапазона от строки 2 до последней заполненной
            if (rowNum > 1) {
                totalRow.getCell(4).setCellFormula("SUM(E2:E" + rowNum + ")");
            } else {
                totalRow.getCell(4).setCellValue(0.0);
            }

            // Автоподбор ширины колонок, чтобы текст не скрывался
            for (int i = 0; i < 6; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
        }
    }
}
