package com.sellion.sellionserver.services;

import com.sellion.sellionserver.entity.PromoAction;
import com.sellion.sellionserver.repository.PromoActionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PromoTaskService {
    private final PromoActionRepository promoRepository;

    @Scheduled(cron = "0 0 20 * * *") // Каждый день в 20:00
    @Transactional
    public void closeExpiredPromos() {
        LocalDate today = LocalDate.now();
        List<PromoAction> promos = promoRepository.findAll();
        for (PromoAction p : promos) {
            if (p.getEndDate().isBefore(today)) {
                p.setStatus("FINISHED");
                p.setConfirmed(true);
            } else if (!p.getStartDate().isAfter(today) && !p.getEndDate().isBefore(today) && p.isConfirmed()) {
                p.setStatus("ACTIVE");
            }
        }
        promoRepository.saveAll(promos);
    }
}
