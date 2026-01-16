package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.AuditLog;
import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.entity.StockMovement;
import com.sellion.sellionserver.repository.AuditLogRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import com.sellion.sellionserver.repository.StockMovementRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products") // Именно этот путь ищет Android
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProductApiController {

    private final ProductRepository productRepository;
    private final AuditLogRepository auditLogRepository;
    private final StockMovementRepository movementRepository; // <-- Репозиторий добавлен



    @GetMapping
    public List<Product> getAllProducts() {
        // Отдаем все товары для загрузки в телефон
        return productRepository.findAll();
    }


    @PostMapping("/import")
    @Transactional
    public ResponseEntity<?> importProducts(@RequestParam("file") MultipartFile file) {
        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            int updatedCount = 0;

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;

                Cell nameCell = row.getCell(0);
                Cell qtyCell = row.getCell(1);

                if (nameCell == null || qtyCell == null) continue; // Пропуск пустых строк

                String name = nameCell.getStringCellValue();
                // Безопасное получение числа (даже если оно введено как текст)
                int incomingQty = (qtyCell.getCellType() == CellType.NUMERIC)
                        ? (int) qtyCell.getNumericCellValue()
                        : Integer.parseInt(qtyCell.getStringCellValue());

                productRepository.findByName(name).ifPresent(product -> {
                    int currentStock = (product.getStockQuantity() != null) ? product.getStockQuantity() : 0;
                    product.setStockQuantity(currentStock + incomingQty);
                    productRepository.save(product);
                });
                updatedCount++;
            }

            // Записываем действие в Аудит
            AuditLog log = new AuditLog();
            log.setUsername("ADMIN");
            log.setAction("Импорт поступления");
            log.setDetails("Обновлено товаров: " + updatedCount);
            auditLogRepository.save(log);

            return ResponseEntity.ok(Map.of("message", "Импортировано " + updatedCount));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Ошибка: " + e.getMessage());
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
}