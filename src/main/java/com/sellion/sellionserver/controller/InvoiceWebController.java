package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.repository.InvoiceRepository;
import com.sellion.sellionserver.services.InvoiceService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
@RequestMapping("/admin/invoices")
@RequiredArgsConstructor
public class InvoiceWebController {

    private final InvoiceService invoiceService;
    private final InvoiceRepository invoiceRepository;

    @PostMapping("/create-from-order/{orderId}")
    public String createFromOrder(@PathVariable Long orderId, RedirectAttributes redirectAttributes) {
        try {
            invoiceService.createInvoiceFromOrder(orderId);
            redirectAttributes.addFlashAttribute("success", "Счет успешно выставлен.");
        } catch (RuntimeException e) {
            // Здесь мы ловим твое "Недостаточно товара: Манго..." и отдаем на экран
            redirectAttributes.addFlashAttribute("error", "ОШИБКА: " + e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Системная ошибка: " + e.getMessage());
        }
        return "redirect:/admin?activeTab=tab-orders";
    }

    @GetMapping("/export-debts")
    public void exportDebts(@RequestParam(required = false) String start,
                            @RequestParam(required = false) String end,
                            HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=DebtList.xlsx");

            invoiceService.exportDebtsToExcel(start, end, response.getOutputStream());
            response.flushBuffer();

        } catch (IOException e) {
            try {
                // Отправляем статус ошибки и текст клиенту
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Ошибка генерации файла: " + e.getMessage());
            } catch (IOException ignored) {}
        }
    }


//    @PostMapping("/create-from-order/{orderId}")
//    @Transactional
//    public String createFromOrder(@PathVariable Long orderId, RedirectAttributes redirectAttributes) {
//        try {
//            // Пытаемся создать инвойс и ПОЛУЧАЕМ его объект
//            Invoice newInvoice = invoiceService.createInvoiceFromOrder(orderId);
//
//            if (newInvoice != null && newInvoice.getId() != null) {
//                redirectAttributes.addFlashAttribute("success", "Счет №" + newInvoice.getId() + " успешно выставлен.");
//                // Если все хорошо — идем во вкладку счетов
//                return "redirect:/admin?activeTab=tab-invoices";
//            } else {
//                // Если сервис вернул null, значит счет не создался
//                redirectAttributes.addFlashAttribute("error", "Не удалось создать счет. Проверьте данные заказа.");
//                return "redirect:/admin?activeTab=tab-orders";
//            }
//
//        } catch (Exception e) {
//            // Если была ошибка (например, на складе), транзакция откатилась
//            redirectAttributes.addFlashAttribute("error", "Ошибка сервера: " + e.getMessage());
//            // Возвращаемся в заказы, чтобы оператор видел, что ничего не произошло
//            return "redirect:/admin?activeTab=tab-orders";
//        }
//    }


}
