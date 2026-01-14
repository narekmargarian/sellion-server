package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.Invoice;
import com.sellion.sellionserver.entity.Payment;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.InvoiceRepository;
import com.sellion.sellionserver.repository.PaymentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentApiController {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final ClientRepository clientRepository;

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<?> registerPayment(@RequestBody Payment payment) {
        // 1. Находим счет по ID
        Invoice invoice = invoiceRepository.findById(payment.getInvoiceId())
                .orElseThrow(() -> new RuntimeException("Счет не найден: " + payment.getInvoiceId()));

        // 2. Сохраняем информацию о платеже
        payment.setPaymentDate(LocalDateTime.now());
        paymentRepository.save(payment);

        // 3. Обновляем статус оплаты в инвойсе
        double currentPaid = (invoice.getPaidAmount() != null) ? invoice.getPaidAmount() : 0.0;
        invoice.setPaidAmount(currentPaid + payment.getAmount());

        if (invoice.getPaidAmount() >= invoice.getTotalAmount()) {
            invoice.setStatus("PAID");
        } else {
            invoice.setStatus("PARTIAL");
        }
        invoiceRepository.save(invoice);

        // 4. Оптимизированное списание долга клиента (стандарт 2026)
        clientRepository.findByName(invoice.getShopName()).ifPresent(client -> {
            double currentDebt = (client.getDebt() != null) ? client.getDebt() : 0.0;
            // Долг не может быть меньше нуля
            client.setDebt(Math.max(0, currentDebt - payment.getAmount()));
            clientRepository.save(client);
        });

        return ResponseEntity.ok(Map.of(
                "message", "Платеж успешно зарегистрирован",
                "newInvoiceStatus", invoice.getStatus()
        ));
    }
}

