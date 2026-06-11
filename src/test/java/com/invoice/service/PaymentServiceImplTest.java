package com.invoice.service;

import com.invoice.DTO.PaymentRequest;
import com.invoice.entity.ManualInvoice;
import com.invoice.entity.Payment;
import com.invoice.repository.ManualInvoiceRepository;
import com.invoice.repository.PaymentRepository;
import com.invoice.serviceImpl.PaymentServiceImpl;
import com.invoice.tenant.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceImplTest {

    @Mock PaymentRepository paymentRepo;
    @Mock ManualInvoiceRepository invoiceRepo;
    @InjectMocks PaymentServiceImpl service;

    @BeforeEach
    void seedTenant() {
        TenantContext.setCurrentAdminId(100L);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsPaymentForOtherTenantInvoice() {
        when(invoiceRepo.findByIdAndAdminId(1L, 100L)).thenReturn(Optional.empty());

        PaymentRequest req = new PaymentRequest();
        req.setInvoiceId(1L);
        req.setAmount(new BigDecimal("100"));
        req.setPaymentDate(LocalDate.now());

        assertThrows(IllegalArgumentException.class, () -> service.recordPayment(req));
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void rejectsOverpayment() {
        ManualInvoice inv = new ManualInvoice();
        inv.setId(1L);
        inv.setAdminId(100L);
        inv.setTotal(new BigDecimal("500.00"));
        when(invoiceRepo.findByIdAndAdminId(1L, 100L)).thenReturn(Optional.of(inv));
        when(paymentRepo.sumPostedAmount(1L, 100L)).thenReturn(new BigDecimal("450.00"));

        PaymentRequest req = new PaymentRequest();
        req.setInvoiceId(1L);
        req.setAmount(new BigDecimal("100.00")); // 450 + 100 > 500
        req.setPaymentDate(LocalDate.now());

        assertThrows(IllegalArgumentException.class, () -> service.recordPayment(req));
    }

    @Test
    void acceptsPartialPayment() {
        ManualInvoice inv = new ManualInvoice();
        inv.setId(1L);
        inv.setAdminId(100L);
        inv.setTotal(new BigDecimal("500.00"));
        when(invoiceRepo.findByIdAndAdminId(1L, 100L)).thenReturn(Optional.of(inv));
        when(paymentRepo.sumPostedAmount(1L, 100L)).thenReturn(BigDecimal.ZERO);
        when(paymentRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        PaymentRequest req = new PaymentRequest();
        req.setInvoiceId(1L);
        req.setAmount(new BigDecimal("250.00"));
        req.setPaymentDate(LocalDate.now());

        Payment saved = service.recordPayment(req);
        assertEquals(100L, saved.getAdminId());
        assertEquals(0, saved.getAmount().compareTo(new BigDecimal("250.00")));
        assertEquals(Payment.Status.POSTED, saved.getStatus());
    }

    @Test
    void rejectsDuplicateReference() {
        ManualInvoice inv = new ManualInvoice();
        inv.setId(1L);
        inv.setAdminId(100L);
        inv.setTotal(new BigDecimal("500.00"));
        when(invoiceRepo.findByIdAndAdminId(1L, 100L)).thenReturn(Optional.of(inv));
        when(paymentRepo.existsByAdminIdAndPaymentReferenceIgnoreCase(100L, "WIRE-001")).thenReturn(true);

        PaymentRequest req = new PaymentRequest();
        req.setInvoiceId(1L);
        req.setAmount(new BigDecimal("100.00"));
        req.setPaymentDate(LocalDate.now());
        req.setPaymentReference("WIRE-001");

        assertThrows(IllegalArgumentException.class, () -> service.recordPayment(req));
    }

    @Test
    void balanceCalculatesCorrectly() {
        ManualInvoice inv = new ManualInvoice();
        inv.setId(1L);
        inv.setAdminId(100L);
        inv.setTotal(new BigDecimal("500.00"));
        when(invoiceRepo.findByIdAndAdminId(1L, 100L)).thenReturn(Optional.of(inv));
        when(paymentRepo.sumPostedAmount(1L, 100L)).thenReturn(new BigDecimal("125.50"));

        BigDecimal balance = service.getInvoiceBalance(1L);
        assertEquals(0, balance.compareTo(new BigDecimal("374.50")));
    }
}
