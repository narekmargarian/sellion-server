package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.repository.ClientRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/clients")
public class ClientWebController {

    private final ClientRepository clientRepository;

    public ClientWebController(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @GetMapping
    public String listClients(Model model) {
        model.addAttribute("clients", clientRepository.findAll());
        return "clients-list";
    }

    @PostMapping("/update")
    public String updateClient(@RequestParam("id") Long id,
                               @RequestParam("debt") Double debt,
                               @RequestParam("routeDay") String routeDay) {
        clientRepository.findById(id).ifPresent(c -> {
            c.setDebt(debt);
            c.setRouteDay(routeDay);
            clientRepository.save(c);
        });
        return "redirect:/admin/clients";
    }
}