package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.entity.Transaction;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.TransactionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;



@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientApiController {

    private final ClientRepository clientRepository;
    private final TransactionRepository transactionRepository;

    @GetMapping
    public List<Client> getAllClients(@RequestParam String managerId) {
        // Выдает всех активных клиентов только для того менеджера, который сделал запрос
        return clientRepository.findAllByManagerIdAndIsDeletedFalse(managerId)
                .stream()
                .sorted(Comparator.comparing(Client::getName))
                .toList();
    }





    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteClient(@PathVariable Long id) {
        clientRepository.softDeleteById(id);
        return ResponseEntity.ok(Map.of("message", "Клиент скрыт (мягко удален)"));
    }

    @GetMapping("/{id}/statement")
    public ResponseEntity<?> getClientStatement(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        Client client = clientRepository.findById(id).orElseThrow();
        List<Transaction> transactions = transactionRepository.findAllByClientIdOrderByTimestampAsc(id);

        List<Transaction> periodTransactions = transactions.stream()
                .filter(tx -> !tx.getTimestamp().toLocalDate().isBefore(start) &&
                        !tx.getTimestamp().toLocalDate().isAfter(end))
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("client", client);
        response.put("transactions", periodTransactions);
        response.put("startDate", start);
        response.put("endDate", end);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/transactions")
    public List<Transaction> getClientTransactions(@PathVariable Long id) {
        return transactionRepository.findAllByClientIdOrderByTimestampAsc(id);
    }


    @GetMapping("/search-fast")
    public List<Client> searchFast(@RequestParam String keyword) {
        // Увеличиваем размер страницы до 100 совпадений
        return clientRepository.searchClients(keyword, null, PageRequest.of(0, 100)).getContent();
    }




}
