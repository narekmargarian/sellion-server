package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.Invoice;
import com.sellion.sellionserver.repository.InvoiceRepository;
import com.sellion.sellionserver.services.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/admin/invoices")
@RequiredArgsConstructor
public class InvoiceWebController {

    private final InvoiceService invoiceService;
    private final InvoiceRepository invoiceRepository;

//    @PostMapping("/create-from-order/{orderId}")
//    public String createFromOrder(@PathVariable Long orderId, RedirectAttributes redirectAttributes) {
//        try {
//            invoiceService.createInvoiceFromOrder(orderId);
//            redirectAttributes.addFlashAttribute("success", "Счет выставлен, товар списан со склада, долг записан.");
//        } catch (Exception e) {
//            // Если в сервисе случится RuntimeException (например, дефицит),
//            // @Transactional откатит всё назад (счет не создастся), а мы покажем ошибку.
//            redirectAttributes.addFlashAttribute("error", "Ошибка: " + e.getMessage());
//            return "redirect:/admin?activeTab=tab-orders";
//        }
//        return "redirect:/admin?activeTab=tab-invoices";
//    }

    @PostMapping("/create-from-order/{orderId}")
    public String createFromOrder(@PathVariable Long orderId, RedirectAttributes redirectAttributes) {
        try {
            // Пытаемся создать инвойс и ПОЛУЧАЕМ его объект
            Invoice newInvoice = invoiceService.createInvoiceFromOrder(orderId);

            if (newInvoice != null && newInvoice.getId() != null) {
                redirectAttributes.addFlashAttribute("success", "Счет №" + newInvoice.getId() + " успешно выставлен.");
                // Если все хорошо — идем во вкладку счетов
                return "redirect:/admin?activeTab=tab-invoices";
            } else {
                // Если сервис вернул null, значит счет не создался
                redirectAttributes.addFlashAttribute("error", "Не удалось создать счет. Проверьте данные заказа.");
                return "redirect:/admin?activeTab=tab-orders";
            }

        } catch (Exception e) {
            // Если была ошибка (например, на складе), транзакция откатилась
            redirectAttributes.addFlashAttribute("error", "Ошибка сервера: " + e.getMessage());
            // Возвращаемся в заказы, чтобы оператор видел, что ничего не произошло
            return "redirect:/admin?activeTab=tab-orders";
        }
    }


}
