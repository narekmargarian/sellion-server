package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.PromoAction;
import com.sellion.sellionserver.repository.ProductRepository;
import com.sellion.sellionserver.repository.PromoActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/promos")
@RequiredArgsConstructor
@Slf4j
public class PromoApiController {

    private final PromoActionRepository promoRepository;
    private final ProductRepository productRepository;

    // 1. Получение акций по периоду (для вкладки "Акции")
    @GetMapping("/filter")
    public List<PromoAction> getPromosByPeriod(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return promoRepository.findByPeriod(start, end);
    }

    // 2. Создание новой акции (Исправлено под Map payload для надежности)
    @PostMapping("/create")
    @Transactional
    public ResponseEntity<?> createPromo(@RequestBody Map<String, Object> payload) {
        try {
            PromoAction promo = new PromoAction();

            // Поля заголовка
            promo.setTitle((String) payload.get("title"));
            promo.setManagerId((String) payload.get("managerId"));

            // Даты
            promo.setStartDate(LocalDate.parse((String) payload.get("startDate")));
            promo.setEndDate(LocalDate.parse((String) payload.get("endDate")));

            // Обработка списка товаров (ID -> Процент)
            Map<Long, BigDecimal> promoItems = new HashMap<>();
            Object itemsRaw = payload.get("items");

            if (itemsRaw instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) itemsRaw;
                map.forEach((key, value) -> {
                    Long productId = Long.valueOf(key.toString());
                    BigDecimal percent = new BigDecimal(value.toString());
                    promoItems.put(productId, percent);
                });
            }
            promo.setItems(promoItems);

            // Начальные статусы
            promo.setConfirmed(false);
            promo.setStatus("PENDING");
            promo.setCreatedAt(LocalDateTime.now());

            PromoAction saved = promoRepository.save(promo);
            log.info("Создана новая акция: {} (ID: {})", saved.getTitle(), saved.getId());

            return ResponseEntity.ok(Map.of(
                    "message", "Акция '" + saved.getTitle() + "' успешно создана и ожидает подтверждения",
                    "id", saved.getId()
            ));
        } catch (Exception e) {
            log.error("Ошибка при создании акции: ", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Ошибка сервера: " + e.getMessage()));
        }
    }

    // 3. Изменение (только если не подтверждена)
    @PutMapping("/{id}/edit")
    @Transactional
    public ResponseEntity<?> editPromo(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        PromoAction promo = promoRepository.findById(id).orElseThrow();

        if (promo.isConfirmed()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Нельзя редактировать подтвержденную акцию"));
        }

        promo.setTitle((String) payload.get("title"));
        promo.setManagerId((String) payload.get("managerId"));
        promo.setStartDate(LocalDate.parse((String) payload.get("startDate")));
        promo.setEndDate(LocalDate.parse((String) payload.get("endDate")));

        Map<Long, BigDecimal> promoItems = new HashMap<>();
        Map<?, ?> map = (Map<?, ?>) payload.get("items");
        map.forEach((k, v) -> promoItems.put(Long.valueOf(k.toString()), new BigDecimal(v.toString())));
        promo.setItems(promoItems);

        return ResponseEntity.ok(promoRepository.save(promo));
    }

    // 4. ПОДТВЕРЖДЕНИЕ АКЦИИ (После этого нельзя менять)
    @PostMapping("/{id}/confirm")
    @Transactional
    public ResponseEntity<?> confirmPromo(@PathVariable Long id) {
        PromoAction promo = promoRepository.findById(id).orElseThrow();
        promo.setConfirmed(true);

        LocalDate today = LocalDate.now();
        // Если период акции уже наступил — активируем сразу
        if (!promo.getStartDate().isAfter(today) && !promo.getEndDate().isBefore(today)) {
            promo.setStatus("ACTIVE");
        }

        promoRepository.save(promo);
        return ResponseEntity.ok(Map.of("message", "Акция подтверждена и готова к запуску"));
    }

    // 5. Проверка активных акций для товаров (для модального окна в заказе)
    @PostMapping("/check-active-for-items")
    public List<PromoAction> checkActiveForItems(@RequestBody List<Long> productIds) {
        LocalDate today = LocalDate.now();
        // Находим все подтвержденные и актуальные по датам акции
        List<PromoAction> activePromos = promoRepository.findActivePromos(today);

        // Фильтруем те, в которых есть хотя бы один товар из корзины заказа
        return activePromos.stream()
                .filter(promo -> promo.getItems().keySet().stream().anyMatch(productIds::contains))
                .collect(Collectors.toList());
    }


    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deletePromo(@PathVariable Long id) {
        PromoAction promo = promoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Акция не найдена"));

        if (promo.isConfirmed()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Нельзя удалить подтвержденную акцию!"));
        }

        promoRepository.delete(promo);
        return ResponseEntity.ok(Map.of("message", "Акция успешно удалена"));
    }



}