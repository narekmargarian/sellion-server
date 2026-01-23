package com.sellion.sellionserver.api;

import com.sellion.sellionserver.dto.ApiResponse;
import com.sellion.sellionserver.dto.CategoryGroupDto;
import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.entity.StockMovement;
import com.sellion.sellionserver.repository.AuditLogRepository;
import com.sellion.sellionserver.repository.ProductRepository;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<Product>> createProduct(@Valid @RequestBody Product newProduct) {
        // Убедимся, что все BigDecimal инициализированы нулем, если не указаны
        newProduct.setPurchasePrice(Optional.ofNullable(newProduct.getPurchasePrice()).orElse(BigDecimal.ZERO));
        newProduct.setPrice(Optional.ofNullable(newProduct.getPrice()).orElse(BigDecimal.ZERO));

        // ИДЕАЛЬНОЕ ИСПРАВЛЕНИЕ: Гарантируем, что isDeleted не NULL перед сохранением
        // Даже если клиент не прислал это поле в JSON, мы установим false.
        if (newProduct.getIsDeleted() == null) {
            newProduct.setIsDeleted(false);
        }

        Product savedProduct = productRepository.save(newProduct);
        log.info("Создан новый продукт: {}", savedProduct.getName());
        return ResponseEntity.ok(ApiResponse.ok("Товар успешно создан", savedProduct));
    }


    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/import")
    @Transactional // Транзакция на весь импорт: либо всё, либо ничего
    public ResponseEntity<ApiResponse<?>> importProducts(@RequestParam("file") MultipartFile file) {
        int updatedCount = 0;
        DataFormatter dataFormatter = new DataFormatter();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {
            // try-with-resources уже был, это отлично

            Sheet sheet = workbook.getSheetAt(0);

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Пропускаем заголовок

                Cell nameCell = row.getCell(0);
                Cell qtyCell = row.getCell(1);
                Cell purchaseCell = row.getCell(2);

                if (nameCell == null || nameCell.getStringCellValue().trim().isEmpty()) continue;
                String name = dataFormatter.formatCellValue(nameCell).trim();

                if (qtyCell == null) continue;

                int incomingQty = 0;
                try {
                    String qtyStr = dataFormatter.formatCellValue(qtyCell).replace(",", ".");
                    incomingQty = (int) Double.parseDouble(qtyStr); // Double.parseDouble используется только для парсинга из Excel
                } catch (NumberFormatException e) {
                    log.warn("Некорректное количество в строке {}: {}", row.getRowNum(), dataFormatter.formatCellValue(qtyCell));
                    continue;
                }

                final int finalQty = incomingQty;
                // ИДЕАЛЬНО: findByNameAndIsDeletedFalse использует индекс и фильтрует удаленные
                productRepository.findByNameAndIsDeletedFalse(name).ifPresent(product -> {
                    int currentStock = Optional.ofNullable(product.getStockQuantity()).orElse(0);
                    product.setStockQuantity(currentStock + finalQty);

                    if (purchaseCell != null) {
                        try {
                            String pPriceStr = dataFormatter.formatCellValue(purchaseCell).replace(",", ".");
                            // ИДЕАЛЬНО: парсим сразу в BigDecimal
                            BigDecimal pPrice = new BigDecimal(pPriceStr);
                            product.setPurchasePrice(pPrice);
                        } catch (Exception ignored) {
                            // ИДЕАЛЬНО: используем log.debug вместо ignored catch block
                            log.debug("Не удалось распарсить себестоимость для товара {}", name);
                        }
                    }

                    productRepository.save(product);
                    stockService.logMovement(name, finalQty, "INCOMING", "Импорт через Excel", "ADMIN");
                });
                updatedCount++;
            }

            log.info("Импорт завершен. Обновлено товаров: {}", updatedCount);

            return ResponseEntity.ok(ApiResponse.ok("Импортировано " + updatedCount + " товаров", Map.of("updatedCount", updatedCount)));

        } catch (Exception e) {
            log.error("Критические ошибка импорта Excel", e);
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
}
