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
import java.security.Principal;
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

    @GetMapping("/filter")
    public List<PromoAction> getPromosByPeriod(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            Principal principal) {

        String currentUser = (principal != null) ? principal.getName() : "ANONYMOUS";

        // ЭТИ СТРОКИ ПОЯВЯТСЯ В КОНСОЛИ ПРИ НАЖАТИИ КНОПКИ "ПОКАЗАТЬ"
        log.info("=== ЗАПРОС АКЦИЙ ===");
        log.info("Пользователь: [{}]", currentUser);
        log.info("Период: {} - {}", start, end);

        boolean isAdmin = currentUser.equalsIgnoreCase("ADMIN") ||
                currentUser.equalsIgnoreCase("admin") ||
                currentUser.equalsIgnoreCase("Офис");

        List<PromoAction> result;
        if (isAdmin) {
            log.info("Режим: АДМИНИСТРАТОР (поиск всех акций)");
            result = promoRepository.findByPeriod(start, end);
        } else {
            log.info("Режим: МЕНЕДЖЕР (поиск для ID: {})", currentUser);
            result = promoRepository.findByPeriodAndManager(start, end, currentUser);
        }

        log.info("Найдено акций: {}", result.size());
        log.info("====================");

        return result;
    }


    // 2. Создание новой акции
    @PostMapping("/create")
    @Transactional
    public ResponseEntity<?> createPromo(@RequestBody Map<String, Object> payload) {
        try {
            PromoAction promo = new PromoAction();

            promo.setTitle((String) payload.get("title"));
            promo.setManagerId((String) payload.get("managerId"));
            promo.setStartDate(LocalDate.parse((String) payload.get("startDate")));
            promo.setEndDate(LocalDate.parse((String) payload.get("endDate")));

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

            promo.setConfirmed(false);
            promo.setStatus("PENDING");
            promo.setCreatedAt(LocalDateTime.now());

            PromoAction saved = promoRepository.save(promo);
            log.info("Создана новая акция: {} (ID: {})", saved.getTitle(), saved.getId());

            return ResponseEntity.ok(Map.of(
                    "message", "Акция '" + saved.getTitle() + "' успешно создана",
                    "id", saved.getId()
            ));
        } catch (Exception e) {
            log.error("Ошибка при создании акции: ", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Ошибка сервера: " + e.getMessage()));
        }
    }

    // 3. Изменение (с проверкой прав доступа)
    @PutMapping("/{id}/edit")
    @Transactional
    public ResponseEntity<?> editPromo(@PathVariable Long id, @RequestBody Map<String, Object> payload, Principal principal) {
        PromoAction promo = promoRepository.findById(id).orElseThrow();
        String currentUser = principal.getName();
        boolean isAdmin = currentUser.equalsIgnoreCase("ADMIN") || currentUser.equalsIgnoreCase("Офис");

        // Защита: менеджер не может править чужую акцию
        if (!isAdmin && !promo.getManagerId().equals(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Доступ запрещен"));
        }

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

    // 4. ПОДТВЕРЖДЕНИЕ АКЦИИ
    @PostMapping("/{id}/confirm")
    @Transactional
    public ResponseEntity<?> confirmPromo(@PathVariable Long id) {
        PromoAction promo = promoRepository.findById(id).orElseThrow();
        promo.setConfirmed(true);

        LocalDate today = LocalDate.now();
        if (!promo.getStartDate().isAfter(today) && !promo.getEndDate().isBefore(today)) {
            promo.setStatus("ACTIVE");
        }

        promoRepository.save(promo);
        return ResponseEntity.ok(Map.of("message", "Акция подтверждена"));
    }

    @PostMapping("/check-active-for-items")
    public List<PromoAction> checkActiveForItems(@RequestBody Map<String, Object> payload) {
        LocalDate today = LocalDate.now();

        // Извлекаем данные из запроса
        List<Integer> productIdsInt = (List<Integer>) payload.get("productIds");
        List<Long> productIds = productIdsInt.stream().map(Long::valueOf).collect(Collectors.toList());
        String selectedManagerId = (String) payload.get("managerId");

        // Ищем акции ТОЛЬКО для того менеджера, которого выбрали в списке на экране
        List<PromoAction> managerPromos = promoRepository.findActivePromosForManager(today, selectedManagerId);

        // Фильтруем по товарам
        return managerPromos.stream()
                .filter(promo -> promo.getItems().keySet().stream().anyMatch(productIds::contains))
                .collect(Collectors.toList());
    }



    // 6. Удаление (с проверкой прав доступа)
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deletePromo(@PathVariable Long id, Principal principal) {
        PromoAction promo = promoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Акция не найдена"));

        String currentUser = principal.getName();
        boolean isAdmin = currentUser.equalsIgnoreCase("ADMIN") || currentUser.equalsIgnoreCase("Офис");

        if (!isAdmin && !promo.getManagerId().equals(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Доступ запрещен"));
        }

        if (promo.isConfirmed()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Нельзя удалить подтвержденную акцию!"));
        }

        promoRepository.delete(promo);
        return ResponseEntity.ok(Map.of("message", "Акция успешно удалена"));
    }
}
