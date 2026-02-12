package com.sellion.sellionserver.api;

import com.sellion.sellionserver.dto.ApiResponse;
import com.sellion.sellionserver.dto.CategoryGroupDto;
import com.sellion.sellionserver.entity.AuditLog;
import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.entity.PromoAction;
import com.sellion.sellionserver.entity.StockMovement;
import com.sellion.sellionserver.repository.AuditLogRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import com.sellion.sellionserver.repository.PromoActionRepository;
import com.sellion.sellionserver.repository.StockMovementRepository;
import com.sellion.sellionserver.services.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j // Используем Lombok Logger
public class ProductApiController {

    private final ProductRepository productRepository;
    private final AuditLogRepository auditLogRepository;
    private final StockMovementRepository movementRepository;
    private final StockService stockService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Product>>> getAllProducts() {
        List<Product> products = productRepository.findAllActive();
        return ResponseEntity.ok(ApiResponse.ok("Список активных товаров", products));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/create") // Итоговый путь: /api/admin/products/create
    public ResponseEntity<?> createProduct(@RequestBody Product newProduct) {
        try {
            // 1. Инициализация финансовых полей
            if (newProduct.getPurchasePrice() == null) newProduct.setPurchasePrice(BigDecimal.ZERO);
            if (newProduct.getPrice() == null) newProduct.setPrice(BigDecimal.ZERO);

            // 2. Гарантируем статус активности (мягкое удаление)
            if (newProduct.getIsDeleted() == null) {
                newProduct.setIsDeleted(false);
            }

            // 3. Установка дефолтных значений для логистики и склада
            if (newProduct.getStockQuantity() == null) newProduct.setStockQuantity(0);
            if (newProduct.getItemsPerBox() == null) newProduct.setItemsPerBox(1);

            // 4. Сохранение в базу данных
            Product savedProduct = productRepository.save(newProduct);

            log.info("Создан новый продукт: {}", savedProduct.getName());

            // 5. Логирование движения в stockService (если подключен)
            if (stockService != null) {
                stockService.logMovement(
                        savedProduct.getName(),
                        savedProduct.getStockQuantity(),
                        "INITIAL",
                        "Начальный ввод остатков",
                        "ADMIN"
                );
            }

            // 6. Запись в глобальный аудит
            recordAudit(savedProduct.getId(), "PRODUCT", "СОЗДАНИЕ", "Добавлен товар: " + savedProduct.getName());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Товар успешно создан",
                    "data", savedProduct
            ));
        } catch (Exception e) {
            log.error("Ошибка при создании товара: ", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Ошибка сервера: " + e.getMessage()
            ));
        }
    }


    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/import")
    @Transactional
    public ResponseEntity<ApiResponse<?>> importProducts(@RequestParam("file") MultipartFile file) {
        int updatedCount = 0;
        DataFormatter dataFormatter = new DataFormatter();

        // 1. ОПТИМИЗАЦИЯ: Загружаем все активные товары в Map (Имя -> Товар) ОДНИМ запросом
        // Это исключает N+1 проблему и ускоряет импорт в десятки раз
        Map<String, Product> productCache = productRepository.findAllActive().stream()
                .collect(Collectors.toMap(Product::getName, p -> p, (existing, replacement) -> existing));

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);

            // Список для массового сохранения в конце, если вы захотите еще больше скорости
            // (но сохранение в цикле внутри @Transactional тоже допустимо)

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Пропускаем заголовок

                Cell nameCell = row.getCell(0);
                if (nameCell == null || nameCell.getStringCellValue().trim().isEmpty()) continue;
                String name = dataFormatter.formatCellValue(nameCell).trim();

                // 2. Ищем товар в кэше (в памяти), а не в БД
                Product product = productCache.get(name);
                if (product == null) continue; // Если товара нет в базе, пропускаем строку

                Cell qtyCell = row.getCell(1);
                Cell purchaseCell = row.getCell(2);

                if (qtyCell != null) {
                    try {
                        String qtyStr = dataFormatter.formatCellValue(qtyCell).replace(",", ".");
                        int incomingQty = (int) Double.parseDouble(qtyStr);

                        int currentStock = Optional.ofNullable(product.getStockQuantity()).orElse(0);
                        product.setStockQuantity(currentStock + incomingQty);

                        // Логируем движение товара
                        stockService.logMovement(name, incomingQty, "INCOMING", "Импорт через Excel", "ADMIN");
                    } catch (NumberFormatException e) {
                        log.warn("Ошибка количества в строке {}: {}", row.getRowNum(), dataFormatter.formatCellValue(qtyCell));
                    }
                }

                if (purchaseCell != null) {
                    try {
                        String pPriceStr = dataFormatter.formatCellValue(purchaseCell).replace(",", ".");
                        product.setPurchasePrice(new BigDecimal(pPriceStr));
                    } catch (Exception e) {
                        log.debug("Не удалось распарсить цену в строке {}: {}", row.getRowNum(), name);
                    }
                }

                productRepository.save(product);
                updatedCount++;
            }

            log.info("Импорт завершен успешно. Обновлено товаров: {}", updatedCount);
            return ResponseEntity.ok(ApiResponse.ok("Импортировано " + updatedCount + " товаров", Map.of("updatedCount", updatedCount)));

        } catch (Exception e) {
            log.error("Критическая ошибка импорта Excel", e);
            // Транзакция откатится автоматически
            throw new RuntimeException("Ошибка импорта: " + e.getMessage());
        }
    }


    @GetMapping("/{name}/history")
    public ResponseEntity<ApiResponse<List<StockMovement>>> getProductHistory(@PathVariable String name) {
        List<StockMovement> history = movementRepository.findAllByProductNameOrderByTimestampDesc(name);
        return ResponseEntity.ok(ApiResponse.ok("История движений товара " + name, history));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @Transactional
    // ИДЕАЛЬНО: Стандартизированный ответ ApiResponse
    public ResponseEntity<ApiResponse<?>> deleteProduct(@PathVariable Long id) {
        productRepository.softDeleteById(id);
        return ResponseEntity.ok(ApiResponse.ok("Товар скрыт (мягко удален)", Map.of("productId", id)));
    }

    @GetMapping("/catalog")
    public ResponseEntity<ApiResponse<List<CategoryGroupDto>>> getAndroidCatalog() {
        List<Product> allProducts = productRepository.findAllActive();

        Map<String, List<Product>> grouped = allProducts.stream()
                .collect(Collectors.groupingBy(p ->
                        (p.getCategory() == null || p.getCategory().isBlank()) ? "Прочее" : p.getCategory()
                ));

        List<CategoryGroupDto> result = grouped.entrySet().stream()
                .map(entry -> new CategoryGroupDto(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(CategoryGroupDto::getCategoryName))
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok("Каталог товаров для Android", result));
    }

    private void recordAudit(Long entityId, String type, String action, String details) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUsername("ADMIN");
        auditLog.setEntityId(entityId);
        auditLog.setEntityType(type);
        auditLog.setAction(action);
        auditLog.setDetails(details);
        auditLog.setTimestamp(LocalDateTime.now());
        auditLogRepository.save(auditLog);
    }
}
