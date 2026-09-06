package com.invoice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.invoice.DTO.VendorDTO;
import com.invoice.DTO.VendorEnvelope;
import com.invoice.client.ConsultantFeignClient;
import com.invoice.client.VendorFeignClient;
import com.invoice.entity.ManualInvoice;
import com.invoice.repository.ManualInvoiceRepository;
import com.invoice.serviceImpl.InvoiceEmailService;
import com.invoice.serviceImpl.ManualInvoiceServiceImpl1;

/**
 * How an update resolves the vendor an invoice belongs to.
 *
 * <p>The behaviour these cover was reproduced against a running stack first: a
 * payable invoice whose vendor had been renamed, settled through the Payments
 * screen, came back as a <em>receivable</em> marked RECEIVED with HTTP 200 and
 * no warning — because the vendor was resolved by a LIKE search on the stored
 * name, a differently-named survivor matched, and the invoice took that
 * vendor's type.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VendorResolutionTest {

	private static final Long ADMIN_ID = 1L;
	private static final Long INVOICE_ID = 77L;

	@Mock
	private ManualInvoiceRepository invoiceRepository;
	@Mock
	private VendorFeignClient vendorFeignClient;
	@Mock
	private ConsultantFeignClient consultantFeignClient;
	@Mock
	private InvoiceEmailService invoiceEmailService;

	@InjectMocks
	private ManualInvoiceServiceImpl1 service;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(service, "uploadDir", "/tmp/test-uploads");
		com.invoice.tenant.TenantContext.setCurrentAdminId(ADMIN_ID);
		when(invoiceRepository.findByIdAndAdminId(INVOICE_ID, ADMIN_ID)).thenReturn(Optional.of(storedInvoice()));
		when(invoiceRepository.save(any(ManualInvoice.class))).thenAnswer(i -> i.getArgument(0));
	}

	@AfterEach
	void clearTenant() {
		com.invoice.tenant.TenantContext.clear();
	}

	/** The invoice as stored: payable, naming a vendor by id and by name. */
	private ManualInvoice storedInvoice() {
		ManualInvoice invoice = new ManualInvoice();
		invoice.setId(INVOICE_ID);
		invoice.setAdminId(ADMIN_ID);
		invoice.setCustomer("Acme");
		invoice.setCustomerVendorId(10L);
		invoice.setVendorType("payable");
		invoice.setStatus("PENDING");
		invoice.setTotal(new BigDecimal("1000"));
		invoice.setInvoiceDate(LocalDate.of(2026, 3, 1));
		return invoice;
	}

	/** What a settlement sends: the row read back, with the money applied. */
	private ManualInvoice settlementFor(Long vendorId, String customer) {
		ManualInvoice request = storedInvoice();
		request.setCustomerVendorId(vendorId);
		request.setCustomer(customer);
		request.setStatus("PAID");
		request.setPaidAmount(new BigDecimal("1000"));
		return request;
	}

	private static VendorDTO vendor(Long id, String name, String type) {
		VendorDTO v = new VendorDTO();
		v.setVendorId(id);
		v.setVendorName(name);
		v.setAdminId(ADMIN_ID);
		v.setVendorType(type);
		return v;
	}

	private static VendorEnvelope envelope(VendorDTO vendor) {
		VendorEnvelope e = new VendorEnvelope();
		e.setStatus("Success");
		e.setData(vendor);
		return e;
	}

	@Test
	@DisplayName("the id decides, and the name is not searched at all")
	void resolvesByIdWithoutSearching() {
		when(vendorFeignClient.getVendorById(10L)).thenReturn(envelope(vendor(10L, "Acme Renamed Ltd", "payable")));

		ManualInvoice saved = service.updateManualInvoice(INVOICE_ID, settlementFor(10L, "Acme"));

		assertEquals("Acme Renamed Ltd", saved.getCustomer(), "the invoice takes the name its own vendor now has");
		assertEquals("payable", saved.getVendorType());
		verify(vendorFeignClient, never()).searchVendors(any());
	}

	@Test
	@DisplayName("a renamed vendor no longer lets a similarly-named one be substituted")
	void doesNotSubstituteASimilarlyNamedVendor() {
		// The exact reproduction: nothing is named "Acme" any more, and the only
		// LIKE hit is a *receivable* vendor whose name contains it.
		when(vendorFeignClient.searchVendors("Acme"))
				.thenReturn(List.of(vendor(99L, "Acme Holdings", "receivable")));

		// No id on the request, so the name is all there is to go on.
		RuntimeException thrown = assertThrows(RuntimeException.class,
				() -> service.updateManualInvoice(INVOICE_ID, settlementFor(null, "Acme")));
		assertEquals("Vendor not found for customer: Acme", thrown.getMessage(),
				"refusing is correct; adopting the other vendor is what moved invoices between ledgers");
	}

	@Test
	@DisplayName("an exact name still resolves, for rows written before the id column")
	void resolvesByExactNameWhenNoIdIsStored() {
		when(vendorFeignClient.searchVendors("Acme"))
				.thenReturn(List.of(vendor(99L, "Acme Holdings", "receivable"), vendor(10L, "Acme", "payable")));

		ManualInvoice saved = service.updateManualInvoice(INVOICE_ID, settlementFor(null, "Acme"));

		assertEquals("Acme", saved.getCustomer());
		assertEquals(10L, saved.getCustomerVendorId());
		assertEquals("payable", saved.getVendorType(), "the exact match wins wherever it sits in the list");
	}

	@Test
	@DisplayName("an id that resolves to nothing is refused, not silently ignored")
	void refusesAnUnresolvableId() {
		// Customer-Service answers 404 for another tenant's id exactly as for a
		// missing one, so an empty envelope must not fall through to the name.
		when(vendorFeignClient.getVendorById(10L)).thenReturn(envelope(null));

		RuntimeException thrown = assertThrows(RuntimeException.class,
				() -> service.updateManualInvoice(INVOICE_ID, settlementFor(10L, "Acme")));
		assertEquals("Vendor not found for id: 10", thrown.getMessage());
		verify(vendorFeignClient, never()).searchVendors(any());
	}

	@Test
	@DisplayName("the AP invoice stays on the AP ledger — the regression this fixes")
	void payableInvoiceStaysPayable() {
		when(vendorFeignClient.getVendorById(10L)).thenReturn(envelope(vendor(10L, "Acme Renamed Ltd", "payable")));

		ManualInvoice saved = service.updateManualInvoice(INVOICE_ID, settlementFor(10L, "Acme"));

		// Before the fix this came back "receivable" / RECEIVED: the substituted
		// vendor's type flipped it, and the entity normalised PAID into the
		// receivable family to match.
		assertEquals("payable", saved.getVendorType());
		assertEquals("PAID", saved.getStatus());
	}
}
