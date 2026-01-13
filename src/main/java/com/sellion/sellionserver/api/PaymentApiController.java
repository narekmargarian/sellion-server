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
        // 1. Находим счет
        Invoice invoice = invoiceRepository.findById(payment.getInvoiceId())
                .orElseThrow(() -> new RuntimeException("Счет не найден"));

        // 2. Сохраняем платеж
        payment.setPaymentDate(LocalDateTime.now());
        paymentRepository.save(payment);

        // 3. Обновляем сумму оплаты в инвойсе
        double alreadyPaid = (invoice.getPaidAmount() != null) ? invoice.getPaidAmount() : 0.0;
        invoice.setPaidAmount(alreadyPaid + payment.getAmount());

        // 4. Обновляем статус инвойса
        if (invoice.getPaidAmount() >= invoice.getTotalAmount()) {
            invoice.setStatus("PAID");
        } else {
            invoice.setStatus("PARTIAL");
        }
        invoiceRepository.save(invoice);

        // 5. Уменьшаем долг клиента (если клиент привязан через магазин)
        // Ищем клиента по имени магазина из инвойса
        clientRepository.findAll().stream()
                .filter(c -> c.getName().equals(invoice.getShopName()))
                .findFirst()
                .ifPresent(client -> {
                    double currentDebt = (client.getDebt() != null) ? client.getDebt() : 0.0;
                    client.setDebt(Math.max(0, currentDebt - payment.getAmount()));
                    clientRepository.save(client);
                });

        return ResponseEntity.ok(Map.of("message", "Платеж принят", "newStatus", invoice.getStatus()));
    }
}
