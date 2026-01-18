package com.sellion.sellionserver.services;


import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.entity.Transaction;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FinanceService {
    private final TransactionRepository transactionRepository;
    private final ClientRepository clientRepository;

    @Transactional
    public void registerOperation(Long clientId, String type, Double amount, Long refId, String comment) {
        Client client = clientRepository.findById(clientId).orElseThrow();

        double delta = type.equals("ORDER") ? amount : -amount;

        // ИСПРАВЛЕНО: Математически точное округление для 2026 года
        double newDebt = Math.round((client.getDebt() + delta) * 100.0) / 100.0;
        client.setDebt(newDebt);
        clientRepository.save(client);

        Transaction tx = Transaction.builder()
                .clientId(client.getId())
                .clientName(client.getName())
                .type(type)
                .referenceId(refId)
                .amount(amount)
                .balanceAfter(newDebt)
                .comment(comment)
                .timestamp(LocalDateTime.now())
                .build();

        transactionRepository.save(tx);
    }

}