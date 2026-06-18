package com.sellion.sellionserver.services;

import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.entity.Transaction;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceService {
    private final TransactionRepository transactionRepository;
    private final ClientRepository clientRepository;

    /**
     * Регистрация финансовой операции.
     * Исправлено: Защита от Race Condition (состояния гонки) через атомарный UPDATE на уровне БД.
     */
    @Transactional(rollbackFor = Exception.class)
    public void registerOperation(Long clientId, String type, BigDecimal amount, Long refId, String comment, String shopName) {

        // 1. ПОИСК КЛИЕНТА (Нормализация)
        String cleanShopName = (shopName != null) ? shopName.trim() : "";

        Client client;
        if (clientId != null) {
            client = clientRepository.findById(clientId)
                    .orElseThrow(() -> new RuntimeException("Клиент с ID " + clientId + " не найден"));
        } else {
            client = clientRepository.findByNameIgnoreCase(cleanShopName)
                    .orElseThrow(() -> new RuntimeException("Критическая ошибка: Магазин '" + cleanShopName +
                            "' не найден в справочнике! Проверьте, что в разделе 'Клиенты' название совпадает до буквы."));
        }

        // 2. РАСЧЕТ ИЗМЕНЕНИЯ (DELTA)
        // Масштабируем сумму до 2 знаков (стандарт для валют)
        BigDecimal delta = amount.setScale(2, RoundingMode.HALF_UP);

        // ORDER увеличивает долг (+), PAYMENT и RETURN уменьшают долг (-)
        if (!"ORDER".equals(type)) {
            delta = delta.negate();
        }

        // 3. АТОМАРНОЕ ОБНОВЛЕНИЕ В БД (Защита от Race Condition)
        // Изменение происходит внутри СУБД: debt = debt + delta
        clientRepository.updateDebt(client.getId(), delta);

        // 4. ПОЛУЧЕНИЕ АКТУАЛЬНОГО БАЛАНСА ДЛЯ АУДИТА
        // Запрашиваем свежий объект из БД, чтобы узнать точный balanceAfter, посчитанный базой
        Client updatedClient = clientRepository.findById(client.getId())
                .orElseThrow(() -> new RuntimeException("Ошибка при получении обновленных данных клиента"));

        BigDecimal newDebt = updatedClient.getDebt();

        // 5. ЗАПИСЬ ТРАНЗАКЦИИ (Аудит)
        Transaction tx = Transaction.builder()
                .clientId(updatedClient.getId())
                .clientName(updatedClient.getName())
                .type(type)
                .referenceId(refId)
                .amount(amount.setScale(2, RoundingMode.HALF_UP))
                .balanceAfter(newDebt.setScale(2, RoundingMode.HALF_UP))
                .comment(comment != null ? comment : "Автоматическая операция")
                .timestamp(LocalDateTime.now())
                .build();

        transactionRepository.save(tx);

        log.info("Финансы [{}]: Магазин [{}], Операция [{}], Итог долга [{}]",
                type, updatedClient.getName(), amount, newDebt);
    }
}
