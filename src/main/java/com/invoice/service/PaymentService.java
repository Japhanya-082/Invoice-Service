package com.invoice.service;

import com.invoice.DTO.PaymentRequest;
import com.invoice.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface PaymentService {

	Payment recordPayment(PaymentRequest request);

	Payment voidPayment(Long paymentId, String reason);

	List<Payment> getPaymentsForInvoice(Long invoiceId);

	Page<Payment> listPayments(Pageable pageable);

	Page<Payment> listPaymentsByDateRange(LocalDate from, LocalDate to, Pageable pageable);

	BigDecimal getInvoiceBalance(Long invoiceId);
}
