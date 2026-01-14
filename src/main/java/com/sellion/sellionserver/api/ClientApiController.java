package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.repository.ClientRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

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
}