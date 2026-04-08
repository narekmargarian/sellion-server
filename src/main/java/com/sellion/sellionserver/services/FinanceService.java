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
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceService {
    private final TransactionRepository transactionRepository;
    private final ClientRepository clientRepository;

    /**
     * Регистрация финансовой операции.
     * Исправлено: добавлена нормализация имен и принудительное сохранение баланса.
     */
    @Transactional(rollbackFor = Exception.class)
    public void registerOperation(Long clientId, String type, BigDecimal amount, Long refId, String comment, String shopName) {

        // 1. ПОИСК КЛИЕНТА (Нормализация)
        // Убираем возможные пробелы в начале и конце, которые часто бывают при ручном вводе
        String cleanShopName = (shopName != null) ? shopName.trim() : "";

        Client client;
        if (clientId != null) {
            client = clientRepository.findById(clientId)
                    .orElseThrow(() -> new RuntimeException("Клиент с ID " + clientId + " не найден"));
        } else {

            client = clientRepository.findByNameIgnoreCase(cleanShopName)
                    .orElseThrow(() -> new RuntimeException("Критическая ошибка: Магазин '" + cleanShopName +
                            "' не найден в справочнике! Проверьте, что в разделе 'Клиенты' название совпадает до буквы."));
//            // Ищем по очищенному имени
//            client = clientRepository.findByName(cleanShopName)
//                    .orElseThrow(() -> new RuntimeException("Клиент '" + cleanShopName + "' не найден в справочнике! Проверьте название магазина."));
        }

        // 2. РАСЧЕТ НОВОГО ДОЛГА
        BigDecimal currentDebt = Optional.ofNullable(client.getDebt()).orElse(BigDecimal.ZERO);

        // Масштабируем сумму до 2 знаков (стандарт для валют)
        BigDecimal delta = amount.setScale(2, RoundingMode.HALF_UP);

        // ORDER увеличивает долг (+), PAYMENT и RETURN уменьшают долг (-)
        if (!"ORDER".equals(type)) {
            delta = delta.negate();
        }

        BigDecimal newDebt = currentDebt.add(delta);

        // 3. ОБНОВЛЕНИЕ КЛИЕНТА
        client.setDebt(newDebt);
        // Используем saveAndFlush, чтобы баланс обновился в БД немедленно
        clientRepository.saveAndFlush(client);

        // 4. ЗАПИСЬ ТРАНЗАКЦИИ (Аудит)
        Transaction tx = Transaction.builder()
                .clientId(client.getId())
                .clientName(client.getName())
                .type(type)
                .referenceId(refId)
                .amount(amount.setScale(2, RoundingMode.HALF_UP))
                .balanceAfter(newDebt.setScale(2, RoundingMode.HALF_UP))
                .comment(comment != null ? comment : "Автоматическая операция")
                .timestamp(LocalDateTime.now())
                .build();

        transactionRepository.save(tx);

        log.info("Финансы [{}]: Магазин [{}], Операция [{}], Итог долга [{}]",
                type, client.getName(), amount, newDebt);
    }
}
