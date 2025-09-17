package com.example.serviceImpl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
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
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

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
                case "docx":
                    invoices = parseDocxFile(targetLocation, fileName);
                    break;
                case "jpg":
                case "jpeg":
                case "png":
                    Invoice invoiceFromImage = parseImageToInvoice(targetLocation.toFile(), fileName);
                    invoices = new ArrayList<>();
                    invoices.add(invoiceFromImage);
                    break;
                default:
                    throw new FileStorageException("Only CSV, Excel (.xls/.xlsx), PDF, DOCX or Image files are supported. Found: " + ext);
            }

            return invoiceRepository.saveAll(invoices);

        } catch (IOException e) {
            throw new FileStorageException("Could not store file: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new FileStorageException("Failed to process file", e);
        }
    }
    
    /** Extract Client Name from text (used internally by core parser) */
    private String extractClientName(String text) {
        String clientName = "Unknown";

        // Regex pattern for Client Name variants
        Pattern clientPattern = Pattern.compile("(?i)(client\\s*name\\s*:?|clientname\\s*:?|client\\s*:?)\\s*(.*)");
        Matcher matcher = clientPattern.matcher(text);
        if (matcher.find()) {
            clientName = matcher.group(2).split("\\n")[0].trim();
        } else {
            // Fallback: line-by-line scan
            for (String line : text.split("\\n")) {
                String lower = line.toLowerCase();
                if (lower.startsWith("client name") || lower.startsWith("clientname") || lower.startsWith("client")) {
                    if (line.contains(":")) {
                        clientName = line.split(":", 2)[1].trim();
                    } else {
                        clientName = line.trim();
                    }
                    break;
                }
            }
        }
        return clientName;
    }

    /** Core parser for text-based extraction (works for CSV, Excel, DOCX, PDF, Image) */
    private List<Invoice> parseTextToInvoice(String text, String fileName) {
        List<Invoice> invoices = new ArrayList<>();

        Pattern invoicePattern = Pattern.compile("Invoice Number[:\\s]*([\\w-]+)", Pattern.CASE_INSENSITIVE);
        Pattern clientPattern = Pattern.compile("Client Name[:\\s]*(.+)", Pattern.CASE_INSENSITIVE);
        Pattern customerPattern = Pattern.compile("Customer[:\\s]*(.+)", Pattern.CASE_INSENSITIVE);
        Pattern totalHoursPattern = Pattern.compile("Total Hours[:\\s]*(\\d+)", Pattern.CASE_INSENSITIVE);
        Pattern totalAmountPattern = Pattern.compile("Total Amount[:\\s]*([0-9.,]+)", Pattern.CASE_INSENSITIVE);
        Pattern weekPattern = Pattern.compile("Week[:\\s]*(.+)", Pattern.CASE_INSENSITIVE);
        Pattern datePattern = Pattern.compile("Date[:\\s]*(\\d{4}-\\d{2}-\\d{2}|\\d{2}/\\d{2}/\\d{4})", Pattern.CASE_INSENSITIVE);
        Pattern dueDatePattern = Pattern.compile("Due Date[:\\s]*(\\d{4}-\\d{2}-\\d{2}|\\d{2}/\\d{2}/\\d{4})", Pattern.CASE_INSENSITIVE);
        Pattern statusPattern = Pattern.compile("Status[:\\s]*(\\w+)", Pattern.CASE_INSENSITIVE);

        Invoice invoice = new Invoice();
        invoice.setFileName(fileName);

        Matcher matcher = invoicePattern.matcher(text);
        invoice.setInvoiceNumber(matcher.find() ? matcher.group(1) : "INV-" + System.currentTimeMillis());

        // Client Name extraction (robust)
        String clientName = extractClientName(text);
        invoice.setClientName(clientName);

        matcher = customerPattern.matcher(text);
        invoice.setCustomer(matcher.find() ? matcher.group(1).trim() : null);

        // Total Hours → Amount (100 per hour)
        matcher = totalHoursPattern.matcher(text);
        int totalHours = matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;

        matcher = totalAmountPattern.matcher(text);
        BigDecimal totalAmount = matcher.find()
                ? new BigDecimal(matcher.group(1).replaceAll(",", ""))
                : BigDecimal.valueOf(totalHours * 100L);
        invoice.setTotalAmount(totalAmount);

        // Week → start date & due date
        matcher = weekPattern.matcher(text);
        if (matcher.find()) {
            try {
                String weekStr = matcher.group(1).trim();
                String[] dates = weekStr.split("to");
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMM yyyy");
                LocalDate startDate = LocalDate.parse(dates[0].trim() + " " + dates[1].trim().split(" ")[1], formatter);
                invoice.setDate(startDate);
                invoice.setDueDate(startDate.plusDays(30));
            } catch (Exception e) {
                invoice.setDate(LocalDate.now());
                invoice.setDueDate(LocalDate.now().plusDays(30));
            }
        } else {
            matcher = datePattern.matcher(text);
            invoice.setDate(matcher.find() ? parseDateString(matcher.group(1)) : LocalDate.now());

            matcher = dueDatePattern.matcher(text);
            invoice.setDueDate(matcher.find() ? parseDateString(matcher.group(1)) : invoice.getDate().plusDays(30));
        }

        matcher = statusPattern.matcher(text);
        invoice.setStatus(matcher.find() ? matcher.group(1) : "GENERATED");

        invoices.add(invoice);
        return invoices;
    }

    private LocalDate parseDateString(String dateStr) {
        try {
            if (dateStr.contains("/")) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
                return LocalDate.parse(dateStr, formatter);
            } else {
                return LocalDate.parse(dateStr);
            }
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    /** CSV Parsing */
    private List<Invoice> parseCsvFile(Path path, String fileName) throws IOException {
        List<Invoice> invoices = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(path)) {
            String headerLine = br.readLine(); // First row is header
            String dataLine = br.readLine();   // Next row contains data (assuming 1 invoice per file)

            if (headerLine != null && dataLine != null) {
                StringBuilder text = new StringBuilder();
                text.append(headerLine).append("\n").append(dataLine);
                invoices.addAll(parseTextToInvoice(text.toString(), fileName));
            }
        }
        return invoices;
    }

    /** Excel Parsing */
    private List<Invoice> parseExcelFile(Path path, String fileName) throws IOException {
        List<Invoice> invoices = new ArrayList<>();
        try (Workbook workbook = fileName.toLowerCase().endsWith("xlsx") ?
                new XSSFWorkbook(Files.newInputStream(path)) :
                new HSSFWorkbook(Files.newInputStream(path))) {

            Sheet sheet = workbook.getSheetAt(0);
            StringBuilder text = new StringBuilder();

            for (Row row : sheet) {
                for (Cell cell : row) {
                    text.append(getCellValueAsString(cell)).append(" ");
                }
                text.append("\n");
            }

            invoices.addAll(parseTextToInvoice(text.toString(), fileName));
        }
        return invoices;
    }

    /** DOCX Parsing */
    private List<Invoice> parseDocxFile(Path path, String fileName) throws IOException {
        List<Invoice> invoices = new ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(path))) {
            StringBuilder text = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                text.append(p.getText()).append("\n");
            }
            invoices.addAll(parseTextToInvoice(text.toString(), fileName));
        }
        return invoices;
    }

    /** PDF Parsing */
    private List<Invoice> parsePdfFile(Path path, String fileName) throws IOException {
        List<Invoice> invoices = new ArrayList<>();
        try (PDDocument document = PDDocument.load(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            invoices.addAll(parseTextToInvoice(text, fileName));
        }
        return invoices;
    }

    /** Image Parsing using Tesseract */
    public Invoice parseImageToInvoice(File file, String fileName) throws TesseractException {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(new File("src/main/resources/tessdata").getAbsolutePath());
        tesseract.setLanguage("eng");

        String text = tesseract.doOCR(file);
        List<Invoice> invoices = parseTextToInvoice(text, fileName);
        return invoices.get(0); // Single invoice per image
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue().trim();
            case NUMERIC: return DateUtil.isCellDateFormatted(cell) ?
                    cell.getLocalDateTimeCellValue().toLocalDate().toString() :
                    String.valueOf(cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try { return String.valueOf(cell.getNumericCellValue()); }
                catch (Exception e) { return cell.getCellFormula(); }
            default: return "";
        }
    }

    @Override
    public List<Invoice> getAll() {
        return invoiceRepository.findAll();
    }

    @Override
    public void deleteByInvoiceNumber(Long invoiceId) {
        invoiceRepository.deleteById(invoiceId);
    }
}
