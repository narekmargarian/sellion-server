package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.repository.ClientRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clients")
public class ClientApiController {

    private final ClientRepository clientRepository;

    public ClientApiController(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @GetMapping
    public List<Client> getAllClients() {
        // Возвращаем всех клиентов, отсортированных по имени для удобства в JS
        return clientRepository.findAll()
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
}