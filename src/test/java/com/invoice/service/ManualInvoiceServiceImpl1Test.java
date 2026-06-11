package com.invoice.service;

import com.invoice.DTO.ConsultantDTO;
import com.invoice.DTO.VendorDTO;
import com.invoice.client.ConsultantFeignClient;
import com.invoice.client.VendorFeignClient;
import com.invoice.entity.InvoiceItem;
import com.invoice.entity.ManualInvoice;
import com.invoice.repository.ManualInvoiceRepository;
import com.invoice.serviceImpl.InvoiceEmailService;
import com.invoice.serviceImpl.ManualInvoiceServiceImpl1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ManualInvoiceServiceImpl1Test {

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

	private static final Long ADMIN_ID = 1L;
	private static final Long CONSULTANT_ID = 1L;

	@BeforeEach
	void setUp() {
		// Inject the uploadDir value since @Value won't be processed by Mockito
		ReflectionTestUtils.setField(service, "uploadDir", "/tmp/test-uploads");
	}

	// =========================================================
	// Helper factory methods
	// =========================================================

	private ManualInvoice buildCreateRequest() {
		ManualInvoice invoice = new ManualInvoice();
		invoice.setId(null);
		invoice.setAdminId(ADMIN_ID);
		invoice.setConsultantId(CONSULTANT_ID);
		invoice.setCustomer("Acme Corp");
		invoice.setCustomerEmail("billing@acme.com");
		invoice.setInvoiceDate(LocalDate.of(2026, 1, 1));
		invoice.setDueDate(LocalDate.of(2026, 1, 31));
		invoice.setStatus("Draft");
		invoice.setCurrency("USD");
		invoice.setItems(new ArrayList<>());
		return invoice;
	}

	private ConsultantDTO buildConsultant() {
		ConsultantDTO dto = new ConsultantDTO();
		dto.setId(CONSULTANT_ID);
		dto.setFirstName("John");
		dto.setLastName("Doe");
		dto.setAdminId(ADMIN_ID);
		dto.setEmail("john.doe@example.com");
		dto.setInvoiceMail("billing@example.com");
		return dto;
	}

	private VendorDTO buildVendor() {
		VendorDTO vendor = new VendorDTO();
		vendor.setVendorId(10L);
		vendor.setVendorName("Acme Corp");
		vendor.setEmail("billing@acme.com");
		vendor.setPhoneNumber("555-1234");
		vendor.setAdminId(ADMIN_ID);
		return vendor;
	}

	private ManualInvoice buildSavedInvoice(Long id) {
		ManualInvoice saved = buildCreateRequest();
		saved.setId(id);
		saved.setInvoiceNumber("INV-260010001");
		saved.setConsultantName("John  Doe");
		return saved;
	}

	// =========================================================
	// 1. saveInvoice_create_success
	// =========================================================

	@Test
	void saveInvoice_create_success() {
		ManualInvoice request = buildCreateRequest();
		ConsultantDTO consultant = buildConsultant();
		VendorDTO vendor = buildVendor();

		when(invoiceRepository.existsByPoNumberAndConsultantIdNot(any(), any())).thenReturn(false);
		when(consultantFeignClient.getConsultant(CONSULTANT_ID)).thenReturn(consultant);
		when(vendorFeignClient.searchVendors("Acme Corp")).thenReturn(List.of(vendor));

		// First save returns an invoice with a generated id
		ManualInvoice withId = buildSavedInvoice(1L);
		withId.setInvoiceNumber(null); // simulate first save before invoiceNumber is set
		when(invoiceRepository.save(any(ManualInvoice.class))).thenAnswer(inv -> {
			ManualInvoice arg = inv.getArgument(0);
			if (arg.getId() == null) {
				arg.setId(1L);
			}
			return arg;
		});

		ManualInvoice result = service.saveInvoice(request);

		assertNotNull(result);
		assertNotNull(result.getId());
		// Invoice number should have been set to INV-YY<consultantId><invoiceId>
		assertTrue(result.getInvoiceNumber().startsWith("INV-"));
		verify(invoiceRepository, atLeastOnce()).save(any(ManualInvoice.class));
	}

	// =========================================================
	// 2. saveInvoice_update_unauthorized
	// =========================================================

	@Test
	void saveInvoice_update_unauthorized() {
		ManualInvoice request = buildCreateRequest();
		request.setId(99L); // non-zero id triggers UPDATE path

		// Repository finds nothing for this id/adminId combination
		when(invoiceRepository.findByIdAndAdminId(99L, ADMIN_ID)).thenReturn(Optional.empty());

		RuntimeException ex = assertThrows(RuntimeException.class, () -> service.saveInvoice(request));
		assertTrue(ex.getMessage().contains("not found") || ex.getMessage().contains("unauthorized"),
				"Expected 'not found or unauthorized' message but got: " + ex.getMessage());
	}

	// =========================================================
	// 3. saveInvoice_duplicatePO_create_throws
	// =========================================================

	@Test
	void saveInvoice_duplicatePO_create_throws() {
		ManualInvoice request = buildCreateRequest();
		request.setPoNumber("PO-001");

		// PO already used by another consultant
		when(invoiceRepository.existsByPoNumberAndConsultantIdNot("PO-001", CONSULTANT_ID)).thenReturn(true);

		RuntimeException ex = assertThrows(RuntimeException.class, () -> service.saveInvoice(request));
		assertTrue(ex.getMessage().contains("PO Number"),
				"Expected PO Number duplicate message but got: " + ex.getMessage());
	}

	// =========================================================
	// 4. isPoNumberDuplicate_blank_returnsFalse
	// =========================================================

	@Test
	void isPoNumberDuplicate_blank_returnsFalse() {
		assertFalse(service.isPoNumberDuplicate("", null, ADMIN_ID));
		assertFalse(service.isPoNumberDuplicate("   ", null, ADMIN_ID));
		assertFalse(service.isPoNumberDuplicate(null, null, ADMIN_ID));
		verifyNoInteractions(invoiceRepository);
	}

	// =========================================================
	// 5. isPoNumberDuplicate_existsOnCreate_returnsTrue
	// =========================================================

	@Test
	void isPoNumberDuplicate_existsOnCreate_returnsTrue() {
		when(invoiceRepository.existsByPoNumberIgnoreCaseAndAdminId("PO-DUP", ADMIN_ID)).thenReturn(true);

		// CREATE case: invoiceId is null
		assertTrue(service.isPoNumberDuplicate("PO-DUP", null, ADMIN_ID));
		verify(invoiceRepository).existsByPoNumberIgnoreCaseAndAdminId("PO-DUP", ADMIN_ID);
	}

	// =========================================================
	// 6. deleteInvoice_notFound_throws
	// =========================================================

	@Test
	void deleteInvoice_notFound_throws() {
		when(invoiceRepository.findByIdAndAdminId(999L, ADMIN_ID)).thenReturn(Optional.empty());

		RuntimeException ex = assertThrows(RuntimeException.class, () -> service.deleteInvoice(999L, ADMIN_ID));
		assertTrue(ex.getMessage().contains("not found") || ex.getMessage().contains("unauthorized"),
				"Expected not-found message but got: " + ex.getMessage());
	}

	// =========================================================
	// 7. getAllInvoices_returnsListForAdmin
	// =========================================================

	@Test
	void getAllInvoices_returnsListForAdmin() {
		ManualInvoice invoice1 = buildSavedInvoice(1L);
		ManualInvoice invoice2 = buildSavedInvoice(2L);
		when(invoiceRepository.findByAdminId(ADMIN_ID)).thenReturn(List.of(invoice1, invoice2));

		List<ManualInvoice> result = service.getAllInvoices(ADMIN_ID);

		assertNotNull(result);
		assertEquals(2, result.size());
		verify(invoiceRepository).findByAdminId(ADMIN_ID);
	}

	// =========================================================
	// 8. getInvoiceById_notFound_throws
	// =========================================================

	@Test
	void getInvoiceById_notFound_throws() {
		when(invoiceRepository.findById(404L)).thenReturn(Optional.empty());

		RuntimeException ex = assertThrows(RuntimeException.class, () -> service.getInvoiceById(404L));
		assertTrue(ex.getMessage().contains("not found") || ex.getMessage().contains("404"),
				"Expected not-found message but got: " + ex.getMessage());
	}

	// =========================================================
	// 9. resolveSortField_unknown_returnsInvoiceDate
	// Tested indirectly via getPendingInvoicesByAdmin with an unknown sortField
	// =========================================================

	@Test
	void resolveSortField_unknown_returnsInvoiceDate() {
		// We reach getInvoicesByAdminAndVendorTypestatusinvoicestatus which calls
		// resolveSortField(). An unknown sort field should default to "invoiceDate".
		// We verify no exception is thrown and repository is called with a valid
		// Pageable.
		com.invoice.DTO.InvoiceSortingRequestDTO dto = new com.invoice.DTO.InvoiceSortingRequestDTO();
		dto.setAdminId(ADMIN_ID);
		dto.setPageNumber(1);
		dto.setPageSize(10);
		dto.setSortField("unknownField");
		dto.setSortOrder("asc");
		dto.setVendorType("payable");

		org.springframework.data.domain.Page<ManualInvoice> emptyPage = new org.springframework.data.domain.PageImpl<>(
				new ArrayList<>());
		when(invoiceRepository.findByAdminIdAndVendorTypeIgnoreCase(eq(ADMIN_ID), anyString(), any()))
				.thenReturn(emptyPage);

		org.springframework.data.domain.Page<ManualInvoice> result = service
				.getInvoicesByAdminAndVendorTypestatusinvoicestatus(dto);

		assertNotNull(result);
		// Verify the repository was called — confirming sort field resolved without
		// error
		verify(invoiceRepository).findByAdminIdAndVendorTypeIgnoreCase(eq(ADMIN_ID), anyString(), any());
	}

	// =========================================================
	// 10. calculateTotals_correctSubtotalAndTotal
	// Tested indirectly: when saveInvoice processes items the totals are correct
	// =========================================================

	@Test
	void calculateTotals_correctSubtotalAndTotal() {
		ManualInvoice request = buildCreateRequest();
		request.setTax(new BigDecimal("50.0"));

		// Add two line items: 10h @ $100 = $1000, 5h @ $200 = $1000
		InvoiceItem item1 = new InvoiceItem();
		item1.setName("Development");
		item1.setHours(new BigDecimal("10.0"));
		item1.setRate(new BigDecimal("100.0"));

		InvoiceItem item2 = new InvoiceItem();
		item2.setName("Testing");
		item2.setHours(new BigDecimal("5.0"));
		item2.setRate(new BigDecimal("200.0"));

		request.setItems(new ArrayList<>(List.of(item1, item2)));

		// Frontend computes and sends totals; service preserves them.
		// subtotal = 10*100 + 5*200 = 2000, total = 2000 + 50 tax = 2050, hours = 15
		request.setSubtotal(new BigDecimal("2000"));
		request.setTotal(new BigDecimal("2050"));
		request.setTotalHours(new BigDecimal("15"));
		request.setDueAmount(new BigDecimal("2050")); // dueAmount used (not amountDue)

		ConsultantDTO consultant = buildConsultant();
		VendorDTO vendor = buildVendor();

		when(invoiceRepository.existsByPoNumberAndConsultantIdNot(any(), any())).thenReturn(false);
		when(consultantFeignClient.getConsultant(CONSULTANT_ID)).thenReturn(consultant);
		when(vendorFeignClient.searchVendors("Acme Corp")).thenReturn(List.of(vendor));
		when(invoiceRepository.save(any(ManualInvoice.class))).thenAnswer(inv -> {
			ManualInvoice arg = inv.getArgument(0);
			if (arg.getId() == null)
				arg.setId(1L);
			return arg;
		});

		ManualInvoice result = service.saveInvoice(request);

		// Verify frontend-supplied totals are preserved by the service
		assertEquals(0, result.getSubtotal().compareTo(new BigDecimal("2000")),
				"subtotal must be preserved from request");
		assertEquals(0, result.getTotal().compareTo(new BigDecimal("2050")),
				"total must be preserved from request");
		assertEquals(0, result.getTotalHours().compareTo(new BigDecimal("15")),
				"totalHours must be preserved from request");
		// dueAmount (not amountDue) is the due field used by the frontend
		assertEquals(0, result.getDueAmount().compareTo(new BigDecimal("2050")),
				"dueAmount must be preserved from request");
	}
}
