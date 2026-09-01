package com.invoice.controller;

import com.invoice.DTO.PaymentRequest;
import com.invoice.common.RestAPIResponse;
import com.invoice.entity.Payment;
import com.invoice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Payment lifecycle endpoints. Every operation is implicitly tenant-scoped via
 * {@code SecurityUtils.getCurrentAdminId()} inside {@link PaymentService} — the
 * caller's JWT determines which invoices are visible. No {@code adminId} is
 * accepted in path, query, or body.
 */
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class PaymentController {

    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<RestAPIResponse> recordPayment(@Valid @RequestBody PaymentRequest request) {
        Payment saved = paymentService.recordPayment(request);
        return ResponseEntity.ok(new RestAPIResponse("success", "Payment recorded", saved));
    }

    @PostMapping("/{paymentId}/void")
    public ResponseEntity<RestAPIResponse> voidPayment(@PathVariable Long paymentId,
                                                      @RequestParam(required = false) String reason) {
        Payment voided = paymentService.voidPayment(paymentId, reason);
        return ResponseEntity.ok(new RestAPIResponse("success", "Payment voided", voided));
    }

    @GetMapping("/invoice/{invoiceId}")
    public ResponseEntity<RestAPIResponse> getPaymentsForInvoice(@PathVariable Long invoiceId) {
        List<Payment> payments = paymentService.getPaymentsForInvoice(invoiceId);
        return ResponseEntity.ok(new RestAPIResponse("success", "Payments retrieved", payments));
    }

    
    @GetMapping("/invoice/{invoiceId}/balance")
    public ResponseEntity<RestAPIResponse> getBalance(@PathVariable Long invoiceId) {
        BigDecimal balance = paymentService.getInvoiceBalance(invoiceId);
        return ResponseEntity.ok(new RestAPIResponse("success", "Balance retrieved", balance));
    }

    
    @GetMapping
    public ResponseEntity<RestAPIResponse> listPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "paymentDate") String sortBy,
            @RequestParam(defaultValue = "desc") String dir,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        if (size > MAX_PAGE_SIZE) size = MAX_PAGE_SIZE;
        Sort sort = "asc".equalsIgnoreCase(dir) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Payment> result = (from != null && to != null)
                ? paymentService.listPaymentsByDateRange(from, to, pageable)
                : paymentService.listPayments(pageable);
        return ResponseEntity.ok(new RestAPIResponse("success", "Payments page", result));
    }
}

