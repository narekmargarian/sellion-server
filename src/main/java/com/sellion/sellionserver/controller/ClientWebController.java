package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;


@Controller
@RequestMapping("/admin/clients")
@RequiredArgsConstructor
public class ClientWebController {
    private final ClientRepository clientRepository;

    @PostMapping("/create")
    // Используем @ModelAttribute Client client для автоматического связывания полей
    public String createClient(@ModelAttribute Client client) {
        // Здесь Spring Security автоматически заполнит поля объекта client
        // если имена полей в HTML совпадают с именами в сущности Client
        clientRepository.save(client);
        return "redirect:/admin?activeTab=tab-clients";
    }


}