package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.entity.Invoice;
import com.sellion.sellionserver.entity.Payment;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.InvoiceRepository;
import com.sellion.sellionserver.repository.PaymentRepository;
import com.sellion.sellionserver.services.FinanceService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
//@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
public class PaymentApiController {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final ClientRepository clientRepository;
    private final FinanceService financeService;

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<?> registerPayment(@RequestBody Payment payment) {
        // 1. Находим счет
        Invoice invoice = invoiceRepository.findById(payment.getInvoiceId())
                .orElseThrow(() -> new RuntimeException("Счет не найден: " + payment.getInvoiceId()));

        // 2. Находим клиента по имени из инвойса (обязательно для FinanceService)
        Client clientObj = clientRepository.findByName(invoice.getShopName())
                .orElseThrow(() -> new RuntimeException("Клиент не найден: " + invoice.getShopName()));

        // 3. Сохраняем информацию о платеже
        payment.setPaymentDate(LocalDateTime.now());
        paymentRepository.save(payment);

        // 4. Обновляем статус оплаты в инвойсе
        double currentPaid = (invoice.getPaidAmount() != null) ? invoice.getPaidAmount() : 0.0;
        invoice.setPaidAmount(currentPaid + payment.getAmount());

        // Проверяем, что разница между суммой счета и оплатой меньше 0.01
        if (Math.abs(invoice.getTotalAmount() - invoice.getPaidAmount()) < 0.01) {
            invoice.setStatus("PAID");
        } else if (invoice.getPaidAmount() >= invoice.getTotalAmount()) {
            invoice.setStatus("PAID"); // На случай переплаты
        } else {
            invoice.setStatus("PARTIAL");
        }

        invoiceRepository.save(invoice);

        // 5. ВЫЗЫВАЕМ ФИНАНСОВЫЙ СЕРВИС (Вместо старого блока ifPresent)
        // Он сам обновит client.debt и создаст запись в Transaction
        financeService.registerOperation(
                clientObj.getId(),
                "PAYMENT",
                payment.getAmount(),
                payment.getId(),
                "Оплата по счету " + invoice.getInvoiceNumber()
        );

        return ResponseEntity.ok(Map.of(
                "message", "Платеж успешно зарегистрирован и проведен по учету",
                "newInvoiceStatus", invoice.getStatus()
        ));
    }

}

