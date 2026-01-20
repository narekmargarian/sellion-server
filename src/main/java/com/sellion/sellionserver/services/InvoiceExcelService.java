package com.sellion.sellionserver.services;

import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.entity.ReturnOrder;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvoiceExcelService {
    private final ProductRepository productRepository;
    private final ClientRepository clientRepository;
    private final CompanySettings companySettings;
    private static final Logger log = LoggerFactory.getLogger(InvoiceExcelService.class);

    public Workbook generateExcel(Order order) {
        return generateExcel(List.of(order), null, "Հաշիվ №" + order.getId());
    }

    public Workbook generateExcel(List<Order> orders, List<ReturnOrder> returns, String title) {
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        Map<String, String> seller = companySettings.getSellerData();

        if (orders != null && !orders.isEmpty()) {
            fillSheetData(workbook, "Վաճառք", orders, null, seller);
        }
        if (returns != null && !returns.isEmpty()) {
            fillSheetData(workbook, "Վերադարձ", null, returns, seller);
        }
        return workbook;
    }

    private void fillSheetData(Workbook workbook, String sheetBaseName, List<Order> orders, List<ReturnOrder> returns, Map<String, String> seller) {
        Map<String, Client> clients = clientRepository.findAll().stream()
                .collect(Collectors.toMap(Client::getName, Function.identity(), (existing, replacement) -> existing));

        // ՓՈՓՈԽՈՒԹՅՈՒՆ 1: Ապրանքները քարտեզագրում ենք ըստ ID-ի
        Map<Long, Product> products = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (existing, replacement) -> existing));

        if (orders != null) {
            for (Order o : orders) {
                Client c = clients.getOrDefault(o.getShopName(), new Client());
                Sheet sheet = workbook.createSheet(sheetBaseName + " " + (c.getName() != null ? c.getName() : o.getId()));
                int rowIdx = 0;

                rowIdx = addSellerHeader(sheet, rowIdx, seller);

                addRow(sheet, rowIdx++, "ԳՆՈՐԴԻ ՏՎՅԱԼՆԵՐ", "");
                addRow(sheet, rowIdx++, "Անվանում:", c.getName());
                addRow(sheet, rowIdx++, "ՀՎՀՀ:", c.getInn());
                addRow(sheet, rowIdx++, "Հաշվեհամար (բանկ):", c.getBankAccount());
                addRow(sheet, rowIdx++, "Մենեջեր:", o.getManagerId() != null ? o.getManagerId() : "");
                rowIdx++;

                // Կանչում ենք թարմացված մեթոդը
                rowIdx = fillItemsTable(sheet, rowIdx, o.getItems(), products);
            }
        }

        if (returns != null) {
            for (ReturnOrder r : returns) {
                Client c = clients.getOrDefault(r.getShopName(), new Client());
                Sheet sheet = workbook.createSheet(sheetBaseName + " " + (c.getName() != null ? c.getName() : r.getId()));
                int rowIdx = 0;

                rowIdx = addSellerHeader(sheet, rowIdx, seller);

                addRow(sheet, rowIdx++, "ԳՆՈՐԴԻ ՏՎՅԱԼՆԵՐ", "");
                addRow(sheet, rowIdx++, "Անվանում:", c.getName());
                addRow(sheet, rowIdx++, "ՀՎՀՀ:", c.getInn());
                addRow(sheet, rowIdx++, "Հաշվեհամար (բանկ):", c.getBankAccount());
                addRow(sheet, rowIdx++, "Մենեջեր:", r.getManagerId() != null ? r.getManagerId() : "");
                rowIdx++;

                rowIdx = fillItemsTable(sheet, rowIdx, r.getItems(), products);
            }
        }
    }

    private int addSellerHeader(Sheet sheet, int rowIdx, Map<String, String> seller) {
        addRow(sheet, rowIdx++, "ՎԱՃԱՌՈՂԻ ՏՎՅԱԼՆԵՐ", "");
        addRow(sheet, rowIdx++, "Անվանում:", seller.get("name"));
        addRow(sheet, rowIdx++, "ՀՎՀՀ:", seller.get("inn"));
        addRow(sheet, rowIdx++, "Իրավաբանական հասցե:", seller.get("address"));
        addRow(sheet, rowIdx++, "Բանկի անվանում:", seller.get("bank"));
        addRow(sheet, rowIdx++, "Հաշվեհամար (IBAN):", seller.get("iban"));
        addRow(sheet, rowIdx++, "ԱԱՀ վճարող է:", seller.get("isVatPayer"));
        return ++rowIdx;
    }

    // ՓՈՓՈԽՈՒԹՅՈՒՆ 2: Մեթոդն այժմ ընդունում է Map<Long, Integer> items և Map<Long, Product> products
    private int fillItemsTable(Sheet sheet, int rowIdx, Map<Long, Integer> items, Map<Long, Product> products) {
        Row header = sheet.createRow(rowIdx++);
        String[] cols = {"Ապրանք", "Կոդ (ԱՏԳ)", "Միավոր", "Քանակ", "Գին (առանց ԱԱՀ)", "ԱԱՀ գումար", "Ընդհանուր գումար"};
        for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);

        BigDecimal totalAmount = BigDecimal.ZERO;
        if (items != null) {
            for (Map.Entry<Long, Integer> item : items.entrySet()) {
                Product p = products.get(item.getKey());
                if (p == null) {
                    log.warn("Ապրանքը ID-ով {} չի գտնվել բազայում Excel-ի համար", item.getKey());
                    continue;
                }

                BigDecimal qty = BigDecimal.valueOf(item.getValue());
                BigDecimal itemTotal = (p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO).multiply(qty);
                totalAmount = totalAmount.add(itemTotal);

                writeProductRow(sheet.createRow(rowIdx++), p, item.getValue());
            }
        }

        rowIdx++;
        Row totalRow = sheet.createRow(rowIdx++);
        totalRow.createCell(5).setCellValue("Ընդհանուր վճարվող գումար:");
        totalRow.createCell(6).setCellValue(totalAmount.setScale(2, RoundingMode.HALF_UP).doubleValue());
        return rowIdx;
    }

    private void writeProductRow(Row row, Product p, int qty) {
        BigDecimal priceWithVat = (p.getPrice() != null) ? p.getPrice() : BigDecimal.ZERO;
        BigDecimal divisor = new BigDecimal("1.2");

        BigDecimal priceNoVat = priceWithVat.divide(divisor, 2, RoundingMode.HALF_UP);
        BigDecimal totalLine = priceWithVat.multiply(BigDecimal.valueOf(qty));
        BigDecimal totalVatLine = totalLine.subtract(priceNoVat.multiply(BigDecimal.valueOf(qty)));

        int cellIdx = 0;
        row.createCell(cellIdx++).setCellValue(p.getName());
        row.createCell(cellIdx++).setCellValue(p.getHsnCode() != null ? p.getHsnCode() : "");
        row.createCell(cellIdx++).setCellValue(p.getUnit() != null ? p.getUnit() : "հատ");
        row.createCell(cellIdx++).setCellValue(qty);
        row.createCell(cellIdx++).setCellValue(priceNoVat.doubleValue());
        row.createCell(cellIdx++).setCellValue(totalVatLine.doubleValue());
        row.createCell(cellIdx++).setCellValue(totalLine.doubleValue());
    }

    private void addRow(Sheet sheet, int rowIdx, String label, String value) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value != null ? value : "");
    }
}

