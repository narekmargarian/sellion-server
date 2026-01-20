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
        // 1. Поиск клиента с защитой от ошибок
        Client client = (clientId != null)
                ? clientRepository.findById(clientId).orElseThrow(() -> new RuntimeException("Клиент с ID " + clientId + " не найден"))
                : clientRepository.findByName(shopName).orElseThrow(() -> new RuntimeException("Клиент с именем " + shopName + " не найден"));

        // 2. Безопасное получение текущего долга
        BigDecimal currentDebt = (client.getDebt() != null) ? client.getDebt() : BigDecimal.ZERO;

        // 3. Расчет дельты с обязательным округлением (Стандарт 2026 для валюты AMD)
        // Тип ORDER увеличивает долг (+), остальные (PAYMENT, RETURN) — уменьшают (-)
        BigDecimal delta = type.equals("ORDER") ? amount : amount.negate();

        // Важно: setScale(2) гарантирует отсутствие ArithmeticException при любых операциях
        BigDecimal newDebt = currentDebt.add(delta).setScale(2, RoundingMode.HALF_UP);

        // 4. Сохранение обновленного долга
        client.setDebt(newDebt);
        clientRepository.save(client);

        // 5. Логирование транзакции
        Transaction tx = Transaction.builder()
                .clientId(client.getId())
                .clientName(client.getName())
                .type(type)
                .referenceId(refId)
                .amount(amount.setScale(2, RoundingMode.HALF_UP)) // ИСПРАВЛЕНО
                .balanceAfter(newDebt.setScale(2, RoundingMode.HALF_UP)) // ИСПРАВЛЕНО
                .comment(comment)
                .timestamp(LocalDateTime.now())
                .build();

        transactionRepository.save(tx);
    }

}
