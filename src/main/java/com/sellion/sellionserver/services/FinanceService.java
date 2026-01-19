package com.sellion.sellionserver.services;


import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.entity.Transaction;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
@Service
@RequiredArgsConstructor
public class FinanceService {
    private final TransactionRepository transactionRepository;
    private final ClientRepository clientRepository;

    @Transactional
    public void registerOperation(Long clientId, String type, BigDecimal amount, Long refId, String comment, String shopName) {
        // Если ID не передан, ищем по имени магазина
        Client client = (clientId != null)
                ? clientRepository.findById(clientId).orElseThrow()
                : clientRepository.findByName(shopName).orElseThrow(() -> new RuntimeException("Клиент не найден: " + shopName));

        BigDecimal currentDebt = client.getDebt() != null ? client.getDebt() : BigDecimal.ZERO;

        // Тип ORDER увеличивает долг, остальные (PAYMENT, RETURN) — уменьшают
        BigDecimal delta = type.equals("ORDER") ? amount : amount.negate();
        BigDecimal newDebt = currentDebt.add(delta).setScale(2, RoundingMode.HALF_UP);

        client.setDebt(newDebt);
        clientRepository.save(client);

        Transaction tx = Transaction.builder()
                .clientId(client.getId())
                .clientName(client.getName())
                .type(type)
                .referenceId(refId)
                .amount(amount.doubleValue()) // Для совместимости с БД, если там double
                .balanceAfter(newDebt.doubleValue())
                .comment(comment)
                .timestamp(LocalDateTime.now())
                .build();

        transactionRepository.save(tx);
    }
}
