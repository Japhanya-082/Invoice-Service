package com.example.serviceImpl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.example.entity.Invoice;
import com.example.exception.FileStorageException;
import com.example.repository.InvoiceRepository;
import com.example.service.InvoiceService;

import jakarta.annotation.PostConstruct;

@Service
public class InvoiceServiceImpl implements InvoiceService {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    private Path fileStorageLocation;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @PostConstruct
    public void init() throws FileStorageException {
        try {
            this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(this.fileStorageLocation);
        } catch (IOException ex) {
            throw new FileStorageException("Could not create upload directory", ex);
        }
    }

    @Override
    public List<Invoice> uploadAndSaveInvoices(MultipartFile file) throws FileStorageException {
        try {
            if (file.isEmpty()) {
                throw new FileStorageException("No file selected");
            }

            String fileName = System.currentTimeMillis() + "_" + StringUtils.cleanPath(file.getOriginalFilename());
            Path targetLocation = this.fileStorageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            String ext = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
            List<Invoice> invoices;

            switch (ext) {
                case "csv":
                    invoices = parseCsvFile(targetLocation, fileName);
                    break;
                case "xls":
                case "xlsx":
                    invoices = parseExcelFile(targetLocation, fileName);
                    break;
                case "pdf":
                    invoices = parsePdfFile(targetLocation, fileName);
                    break;
                default:
                    throw new FileStorageException("Unsupported file format: " + ext);
            }

            return invoiceRepository.saveAll(invoices);

        } catch (Exception e) {
            throw new FileStorageException("Failed to process file", e);
        }
    }

    private List<Invoice> parseCsvFile(Path path, String fileName) throws IOException {
        List<Invoice> invoices = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line;
            boolean isHeader = true;
            while ((line = br.readLine()) != null) {
                if (isHeader) { isHeader = false; continue; } // skip header
                String[] data = line.split(",");
                if (data.length >= 4) {
                    invoices.add(buildInvoiceSafe(data[0], data[1], data[2], data[3], fileName));
                }
            }
        }
        return invoices;
    }

    private List<Invoice> parseExcelFile(Path path, String fileName) throws IOException {
        List<Invoice> invoices = new ArrayList<>();

        try (InputStream is = Files.newInputStream(path);
             Workbook workbook = fileName.endsWith("xlsx") ? new XSSFWorkbook(is) : new HSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // skip header
                String dateStr = getCellValueAsString(row.getCell(0));
                String dueDateStr = getCellValueAsString(row.getCell(1));
                String customer = getCellValueAsString(row.getCell(2));
                String amountStr = getCellValueAsString(row.getCell(3));

                if (dateStr.isEmpty() || dueDateStr.isEmpty() || customer.isEmpty() || amountStr.isEmpty()) continue;

                invoices.add(buildInvoiceSafe(dateStr, dueDateStr, customer, amountStr, fileName));
            }
        }

        return invoices;
    }

    private List<Invoice> parsePdfFile(Path path, String fileName) throws IOException {
        List<Invoice> invoices = new ArrayList<>();
        try (PDDocument document = PDDocument.load(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            boolean isFirstLine = true;
            for (String line : text.split("\n")) {
                if (isFirstLine) { isFirstLine = false; continue; } // skip header
                String[] data = line.split(",");
                if (data.length >= 4) {
                    invoices.add(buildInvoiceSafe(data[0], data[1], data[2], data[3], fileName));
                }
            }
        }
        return invoices;
    }

    // Safe builder that handles parsing errors
    private Invoice buildInvoiceSafe(String dateStr, String dueDateStr, String customer, String amountStr, String fileName) {
        Invoice invoice = new Invoice();
        try {
            invoice.setDate(LocalDate.parse(dateStr));
        } catch (DateTimeParseException e) {
            invoice.setDate(null); // or handle default date
        }

        try {
            invoice.setDueDate(LocalDate.parse(dueDateStr));
        } catch (DateTimeParseException e) {
            invoice.setDueDate(null);
        }

        invoice.setCustomer(customer);
        try {
            invoice.setAmount(new BigDecimal(amountStr));
        } catch (NumberFormatException e) {
            invoice.setAmount(BigDecimal.ZERO);
        }

        invoice.setStatus("GENERATED");
        invoice.setFileName(fileName);
        return invoice;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue().trim();
            case NUMERIC:
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate().toString();
                } else {
                    return BigDecimal.valueOf(cell.getNumericCellValue()).toPlainString();
                }
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA: return cell.getCellFormula();
            default: return "";
        }
    }

    @Override
    public List<Invoice> getAll() {
        return invoiceRepository.findAll();
    }
}
