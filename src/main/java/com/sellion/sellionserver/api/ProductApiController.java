package com.sellion.sellionserver.api;

import com.sellion.sellionserver.dto.CategoryGroupDto;
import com.sellion.sellionserver.entity.AuditLog;
import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.entity.StockMovement;
import com.sellion.sellionserver.repository.AuditLogRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import com.sellion.sellionserver.repository.StockMovementRepository;
import com.sellion.sellionserver.services.StockService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProductApiController {

    private final ProductRepository productRepository;
    private final AuditLogRepository auditLogRepository;
    private final StockMovementRepository movementRepository;
    private final StockService stockService;

    @GetMapping
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @PostMapping("/import")
    @Transactional
    public ResponseEntity<?> importProducts(@RequestParam("file") MultipartFile file) {
        int updatedCount = 0;
        DataFormatter dataFormatter = new DataFormatter();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;

                Cell nameCell = row.getCell(0);
                Cell qtyCell = row.getCell(1);
                Cell purchaseCell = row.getCell(2);

                if (nameCell == null || qtyCell == null) continue;

                String name = dataFormatter.formatCellValue(nameCell);
                if (name.trim().isEmpty()) continue; // Пропуск пустых строк по названию

                int rawQty = 0;
                try {
                    String qtyStr = dataFormatter.formatCellValue(qtyCell).replace(",", ".");
                    rawQty = (int) Double.parseDouble(qtyStr);
                } catch (Exception e) { continue; }

                final int incomingQty = rawQty;
                productRepository.findByName(name).ifPresent(product -> {
                    // 1. Обновляем остаток
                    int currentStock = (product.getStockQuantity() != null) ? product.getStockQuantity() : 0;
                    product.setStockQuantity(currentStock + incomingQty);

                    // 2. Обновляем цену закупки (себестоимость)
                    if (purchaseCell != null) {
                        try {
                            String pPriceStr = dataFormatter.formatCellValue(purchaseCell).replace(",", ".");
                            double pPrice = Double.parseDouble(pPriceStr);
                            product.setPurchasePrice(pPrice);
                        } catch (Exception ignored) {}
                    }

                    productRepository.save(product);

                    // 3. Логируем движение товара для карточки товара (Stock History)
                    stockService.logMovement(name, incomingQty, "INCOMING", "Импорт через Excel", "ADMIN");
                });
                updatedCount++;
            }

            // СОЗДАНИЕ ЛОГА АУДИТА ДЛЯ ГЛАВНОЙ СТРАНИЦЫ (Dashboard)
            AuditLog log = new AuditLog();
            log.setUsername("ADMIN");
            log.setAction("Импорт поступления");
            log.setDetails("Обновлено товаров: " + updatedCount + ". Проставлена себестоимость.");
            log.setTimestamp(LocalDateTime.now()); // Явно задаем время для 2026 года

            // Используем saveAndFlush, чтобы запись попала в БД до выхода из метода
            auditLogRepository.saveAndFlush(log);

            return ResponseEntity.ok(Map.of("message", "Импортировано " + updatedCount));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Ошибка импорта: " + e.getMessage());
        }
    }

    @GetMapping("/{name}/history")
    public List<StockMovement> getProductHistory(@PathVariable String name) {
        return movementRepository.findAllByProductNameOrderByTimestampDesc(name);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        productRepository.softDeleteById(id);
        return ResponseEntity.ok(Map.of("message", "Товар скрыт (мягко удален)"));
    }
//TODO AY ES HASCEIN BDI DIME ANDROID@ KATALGI HAMAR
    @GetMapping("/catalog")
    public List<CategoryGroupDto> getAndroidCatalog() {
        // 1. Получаем все активные товары
        List<Product> allProducts = productRepository.findAllActive();

        // 2. Группируем их по полю category
        Map<String, List<Product>> grouped = allProducts.stream()
                .collect(Collectors.groupingBy(p ->
                        (p.getCategory() == null || p.getCategory().isBlank()) ? "Прочее" : p.getCategory()
                ));

        // 3. Преобразуем в список DTO для Android
        return grouped.entrySet().stream()
                .map(entry -> new CategoryGroupDto(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(CategoryGroupDto::getCategoryName)) // Сортировка категорий по алфавиту
                .collect(Collectors.toList());
    }


}