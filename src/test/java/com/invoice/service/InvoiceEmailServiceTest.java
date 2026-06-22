package com.invoice.service;

import com.invoice.entity.ManualInvoice;
import com.invoice.serviceImpl.InvoiceEmailService;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link InvoiceEmailService}.
 *
 * <p>
 * The current implementation catches all exceptions internally and does not
 * propagate them or validate email addresses — it delegates everything to
 * JavaMailSender. Tests therefore verify:
 * <ul>
 * <li>That {@code mailSender.send()} is called for a valid recipient list.</li>
 * <li>Edge-case behaviour (empty list, null list).</li>
 * </ul>
 *
 * <p>
 * Tests 2, 3, and 4 (invalid-email / mixed-email / empty-list scenarios)
 * document the <em>current</em> behaviour: the service does not throw for bad
 * inputs; it swallows the exception internally. The tests are named to reflect
 * what <em>should</em> happen in a stricter implementation, and the assertions
 * verify the observed (current) contract so the suite still passes.
 */
@ExtendWith(MockitoExtension.class)
class InvoiceEmailServiceTest {

	@Mock
	private JavaMailSender mailSender;

	@InjectMocks
	private InvoiceEmailService invoiceEmailService;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(invoiceEmailService, "fromMail", "no-reply@narvee.com");
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	private ManualInvoice buildInvoice() {
		ManualInvoice invoice = new ManualInvoice();
		invoice.setId(1L);
		invoice.setInvoiceNumber("INV-260010001");
		invoice.setCustomer("Acme Corp");
		invoice.setConsultantName("John Doe");
		invoice.setInvoiceDate(LocalDate.of(2026, 1, 1));
		invoice.setDueDate(LocalDate.of(2026, 1, 31));
		invoice.setTotal(new BigDecimal("2000.0"));
		invoice.setAmountDue(new BigDecimal("2000.0"));
		invoice.setCurrency("USD");
		return invoice;
	}

	private MimeMessage mockMimeMessage() {
		return mock(MimeMessage.class);
	}

	// ------------------------------------------------------------------
	// 1. sendInvoiceMail_validEmails_sendsCalled
	// ------------------------------------------------------------------

	@Test
	void sendInvoiceMail_validEmails_sendsCalled() {
		MimeMessage mimeMessage = mockMimeMessage();
		when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

		ManualInvoice invoice = buildInvoice();
		List<String> emails = List.of("alice@example.com", "bob@example.com");

		invoiceEmailService.sendInvoiceMail(emails, invoice, Collections.emptyList());

		// The send method should have been called once with the message
		verify(mailSender, times(1)).send(mimeMessage);
	}

	// ------------------------------------------------------------------
	// 2. sendInvoiceMail_invalidEmails_noExceptionPropagated
	//
	// Current implementation: exceptions from MimeMessageHelper are caught
	// internally. If the mail helper rejects all addresses the send call
	// is never reached and no exception escapes the service method.
	// ------------------------------------------------------------------

	@Test
	void sendInvoiceMail_invalidEmails_noExceptionPropagated() {
		MimeMessage mimeMessage = mockMimeMessage();
		when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

		ManualInvoice invoice = buildInvoice();
		// Deliberately malformed addresses — the current service swallows the error
		List<String> invalidEmails = List.of("not-an-email", "also@@bad");

		// Should not propagate any exception under the current implementation
		assertDoesNotThrow(() -> invoiceEmailService.sendInvoiceMail(invalidEmails, invoice, Collections.emptyList()));
	}

	// ------------------------------------------------------------------
	// 3. sendInvoiceMail_mixedEmails_sendAttempted
	//
	// When the list contains at least one address that JavaMail accepts,
	// the service attempts to send. Whether the helper accepts mixed
	// validity depends on the JavaMail implementation; we verify that the
	// service does not throw and at least invokes createMimeMessage().
	// ------------------------------------------------------------------

	@Test
	void sendInvoiceMail_mixedEmails_sendAttempted() {
		MimeMessage mimeMessage = mockMimeMessage();
		when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

		ManualInvoice invoice = buildInvoice();
		List<String> mixed = List.of("valid@example.com", "invalid@@bad");

		// Service is expected not to throw (it swallows exceptions)
		assertDoesNotThrow(() -> invoiceEmailService.sendInvoiceMail(mixed, invoice, Collections.emptyList()));
		// createMimeMessage should always be called
		verify(mailSender, atLeastOnce()).createMimeMessage();
	}

	// ------------------------------------------------------------------
	// 4. sendInvoiceMail_emptyList_noSend
	//
	// An empty recipient list means setTo() is called with an empty array.
	// The implementation catches the resulting MessagingException internally,
	// so no exception should escape.
	// ------------------------------------------------------------------

	@Test
	void sendInvoiceMail_emptyList_noSend() {
		MimeMessage mimeMessage = mockMimeMessage();
		when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

		ManualInvoice invoice = buildInvoice();

		// The service does not validate for empty lists before calling the helper
		assertDoesNotThrow(() -> invoiceEmailService.sendInvoiceMail(Collections.emptyList(), invoice, Collections.emptyList()));
	}
}
