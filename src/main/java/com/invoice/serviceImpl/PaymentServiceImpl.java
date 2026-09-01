package com.invoice.serviceImpl;

import com.invoice.DTO.PaymentRequest;
import com.invoice.entity.ManualInvoice;
import com.invoice.entity.Payment;
import com.invoice.repository.ManualInvoiceRepository;
import com.invoice.repository.PaymentRepository;
import com.invoice.service.PaymentService;
import com.invoice.tenant.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

	private final PaymentRepository paymentRepository;
	private final ManualInvoiceRepository invoiceRepository;

	@Override
	@Transactional
	public Payment recordPayment(PaymentRequest request) {
		Long adminId = SecurityUtils.getCurrentAdminId();

		ManualInvoice invoice = invoiceRepository.findByIdAndAdminId(request.getInvoiceId(), adminId).orElseThrow(
				() -> new IllegalArgumentException("Invoice " + request.getInvoiceId() + " not found in this tenant"));

		if (request.getPaymentReference() != null && !request.getPaymentReference().isBlank() && paymentRepository
				.existsByAdminIdAndPaymentReferenceIgnoreCase(adminId, request.getPaymentReference())) {
			throw new IllegalArgumentException(
					"Payment with reference '" + request.getPaymentReference() + "' already exists for this tenant");
		}

		BigDecimal alreadyPaid = paymentRepository.sumPostedAmount(invoice.getId(), adminId);
		BigDecimal invoiceTotal = invoice.getTotal() == null ? BigDecimal.ZERO : invoice.getTotal();
		BigDecimal remaining = invoiceTotal.subtract(alreadyPaid == null ? BigDecimal.ZERO : alreadyPaid);
		if (request.getAmount().compareTo(remaining.add(new BigDecimal("0.0001"))) > 0) {
			throw new IllegalArgumentException(
					"Payment amount " + request.getAmount() + " exceeds outstanding balance " + remaining);
		}

		Payment payment = Payment.builder().invoiceId(invoice.getId()).adminId(adminId).amount(request.getAmount())
				.paymentDate(request.getPaymentDate()).paymentReference(request.getPaymentReference())
				.paymentMethod(request.getPaymentMethod()).remarks(request.getRemarks()).status(Payment.Status.POSTED)
				.build();

		Payment saved = paymentRepository.save(payment);
		log.info("Payment recorded: id={} invoice={} amount={} adminId={}", saved.getPaymentId(), invoice.getId(),
				saved.getAmount(), adminId);
		return saved;
	}

	@Override
	@Transactional
	public Payment voidPayment(Long paymentId, String reason) {
		Long adminId = SecurityUtils.getCurrentAdminId();
		Payment payment = paymentRepository.findByPaymentIdAndAdminId(paymentId, adminId)
				.orElseThrow(() -> new IllegalArgumentException("Payment " + paymentId + " not found in this tenant"));

		if (payment.getStatus() == Payment.Status.VOIDED) {
			return payment;
		}
		payment.setStatus(Payment.Status.VOIDED);
		payment.setRemarks((payment.getRemarks() == null ? "" : payment.getRemarks() + " | ") + "Voided: "
				+ (reason == null ? "(no reason)" : reason));
		Payment saved = paymentRepository.save(payment);
		log.info("Payment voided: id={} invoice={} adminId={}", saved.getPaymentId(), saved.getInvoiceId(), adminId);
		return saved;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Payment> getPaymentsForInvoice(Long invoiceId) {
		Long adminId = SecurityUtils.getCurrentAdminId();
		return paymentRepository.findByInvoiceIdAndAdminIdAndDeletedAtIsNullOrderByPaymentDateAsc(invoiceId, adminId);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<Payment> listPayments(Pageable pageable) {
		Long adminId = SecurityUtils.getCurrentAdminId();
		return paymentRepository.findByAdminIdAndDeletedAtIsNull(adminId, capped(pageable));
	}

	@Override
	@Transactional(readOnly = true)
	public Page<Payment> listPaymentsByDateRange(LocalDate from, LocalDate to, Pageable pageable) {
		Long adminId = SecurityUtils.getCurrentAdminId();
		return paymentRepository.findByAdminIdAndDateRange(adminId, from, to, capped(pageable));
	}

	@Override
	@Transactional(readOnly = true)
	public BigDecimal getInvoiceBalance(Long invoiceId) {
		Long adminId = SecurityUtils.getCurrentAdminId();
		ManualInvoice invoice = invoiceRepository.findByIdAndAdminId(invoiceId, adminId)
				.orElseThrow(() -> new IllegalArgumentException("Invoice " + invoiceId + " not found in this tenant"));
		BigDecimal paid = paymentRepository.sumPostedAmount(invoiceId, adminId);
		BigDecimal total = invoice.getTotal() == null ? BigDecimal.ZERO : invoice.getTotal();
		BigDecimal balance = total.subtract(paid == null ? BigDecimal.ZERO : paid);
		return balance.signum() < 0 ? BigDecimal.ZERO : balance;
	}

	private Pageable capped(Pageable in) {
		if (in.getPageSize() > 200) {
			return org.springframework.data.domain.PageRequest.of(in.getPageNumber(), 200, in.getSort());
		}
		return in;
	}
}
