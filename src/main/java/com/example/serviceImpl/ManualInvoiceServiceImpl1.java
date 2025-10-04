package com.example.serviceImpl;

import com.example.entity.InvoiceItem;
import com.example.entity.ManualInvoice;
import com.example.repository.ManualInvoiceRepository;
import com.example.service.ManualInvoiceService1;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ManualInvoiceServiceImpl1 implements ManualInvoiceService1 {

    private static final String UPLOAD_DIR = "D:/Invoicing Application/Invoice-Service/uploaded_files/";

    private final ManualInvoiceRepository invoiceRepository;

    public ManualInvoiceServiceImpl1(ManualInvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Override
    public ManualInvoice saveInvoice(ManualInvoice invoice) {
        if (invoiceRepository.existsByInvoiceNumber(invoice.getInvoiceNumber())) {
            invoice.setInvoiceNumber("INV-" + System.currentTimeMillis());
        }
        calculateTotalsAndDueDate(invoice);
        return invoiceRepository.save(invoice);
    }

    private void calculateTotalsAndDueDate(ManualInvoice invoice) {
        double subtotal = 0.0;
        double totalHours = 0.0;

        if (invoice.getItems() != null) {
            for (InvoiceItem item : invoice.getItems()) {
                double hours = item.getHours() != null ? item.getHours() : 0.0;
                double rate = item.getRate() != null ? item.getRate() : 0.0;
                double amount = hours * rate;
                item.setAmount(amount);
                subtotal += amount;
                totalHours += hours;
                item.setManualInvoice(invoice);
            }
        }

        invoice.setSubtotal(subtotal);
        invoice.setTotalHours(totalHours);

        double tax = invoice.getTax() != null ? invoice.getTax() : 0.0;
        double total = subtotal + tax;
        invoice.setTotal(total);

        double credit = invoice.getCredit() != null ? invoice.getCredit() : 0.0;
        invoice.setAmountDue(total - credit);

        if (invoice.getInvoiceDate() != null && invoice.getPaymentTerms() != null) {
            try {
                int days = Integer.parseInt(invoice.getPaymentTerms().replaceAll("[^0-9]", ""));
                invoice.setDueDate(invoice.getInvoiceDate().plusDays(days));
            } catch (NumberFormatException e) {
                invoice.setDueDate(invoice.getInvoiceDate().plusDays(30));
            }
        }
    }

    @Override
    public ManualInvoice getInvoiceById(Long id) {
        return invoiceRepository.findById(id).orElse(null);
    }

    @Override
    public List<ManualInvoice> getAllInvoices() {
        return invoiceRepository.findAll();
    }

    @Override
    public Page<ManualInvoice> searchInvoices(String keyword, Pageable pageable) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return invoiceRepository.findAll(pageable);
        }
        return invoiceRepository.searchInvoices(keyword, pageable);
    }

    @Override
    public void deleteInvoice(Long id) {
        invoiceRepository.deleteById(id);
    }

    @Override
    public ManualInvoice updateInvoice(Long id, ManualInvoice invoice) {
        ManualInvoice existingInvoice = invoiceRepository.findById(id).orElse(null);
        if (existingInvoice == null) return null;

        // Copy all fields from the new invoice
        existingInvoice.updateFrom(invoice);

        // Set the parent reference for items
        existingInvoice.getItems().clear();
        if (invoice.getItems() != null) {
            for (InvoiceItem item : invoice.getItems()) {
                item.setManualInvoice(existingInvoice);
                existingInvoice.getItems().add(item);
            }
        }

        return invoiceRepository.save(existingInvoice);
    }

    @Override
    public String storeFile(MultipartFile file) throws IOException {
        File dir = new File(UPLOAD_DIR);
        if (!dir.exists()) dir.mkdirs();

        String uniqueFilename = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        File destFile = new File(dir, uniqueFilename);
        file.transferTo(destFile);
        return uniqueFilename;
    }

    @Override
    public List<String> getAllTemplates() {
        File dir = new File(UPLOAD_DIR);
        if (!dir.exists() || dir.listFiles() == null) return List.of();

        return Arrays.stream(dir.listFiles())
                .filter(File::isFile)
                .map(File::getName)
                .collect(Collectors.toList());
    }

    @Override
    public Resource loadFileAsResource(String filename) throws Exception {
        File file = new File(UPLOAD_DIR + filename);
        if (!file.exists()) throw new FileNotFoundException("File Not Found: " + filename);

        return new UrlResource(file.toURI());
    }
}
