package com.invoice.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import com.invoice.entity.ManualInvoice;

/**
 * The AP and AR status tabs against the two status vocabularies the table
 * actually holds: title case with spaces (legacy rows, e.g. "Partially
 * Received") and upper snake case (everything written since normalisation).
 * Both front ends send the snake form. Before this, the "Partially ..." and
 * "Excess ..." tabs matched only the rows written after the change.
 */
@SpringBootTest
@ActiveProfiles("test")
class ManualInvoiceStatusFilterTest {

	private static final long TENANT = 77_001L;
	private static final long OTHER = 77_002L;

	@Autowired
	private ManualInvoiceRepository repository;

	@BeforeEach
	void seed() {
		repository.deleteAll(repository.findByAdminId(TENANT));
		repository.deleteAll(repository.findByAdminId(OTHER));
		repository.saveAll(List.of(
				invoice(TENANT, "payable", "Partially Paid", "AP-LEGACY-1"),
				invoice(TENANT, "payable", "PARTIALLY_PAID", "AP-SNAKE-1"),
				invoice(TENANT, "payable", "Pending", "AP-PENDING-1"),
				invoice(TENANT, "receivable", "Partially Received", "AR-LEGACY-1"),
				invoice(TENANT, "receivable", "PARTIALLY_RECEIVED", "AR-SNAKE-1"),
				invoice(TENANT, "receivable", "Received", "AR-RECEIVED-1"),
				invoice(OTHER, "payable", "PARTIALLY_PAID", "AP-OTHER-TENANT")));
	}

	@AfterEach
	void clean() {
		repository.deleteAll(repository.findByAdminId(TENANT));
		repository.deleteAll(repository.findByAdminId(OTHER));
	}

	private static ManualInvoice invoice(long adminId, String vendorType, String status, String number) {
		ManualInvoice i = new ManualInvoice();
		i.setAdminId(adminId);
		i.setVendorType(vendorType);
		i.setStatus(status);
		i.setInvoiceNumber(number);
		i.setCustomer("Vendor " + number);
		i.setTotal(new BigDecimal("100.0000"));
		return i;
	}

	@Test
	@DisplayName("AP: the snake-case filter both front ends send matches legacy title-case rows too")
	void payableStatusMatchesBothVocabularies() {
		var page = repository.findByAdminIdAndVendorTypeAndStatusIgnoreCase(TENANT, "payable", "PARTIALLY_PAID",
				PageRequest.of(0, 10));
		assertEquals(2, page.getTotalElements(), "one legacy and one snake row, same tenant, same tab");
		// And the legacy spelling finds the same two.
		assertEquals(2, repository.findByAdminIdAndVendorTypeAndStatusIgnoreCase(TENANT, "payable", "Partially Paid",
				PageRequest.of(0, 10)).getTotalElements());
		// Another status stays out; another tenant stays out.
		assertEquals(1, repository.findByAdminIdAndVendorTypeAndStatusIgnoreCase(TENANT, "payable", "PENDING",
				PageRequest.of(0, 10)).getTotalElements());
	}

	@Test
	@DisplayName("AP with a search term: same tolerance")
	void payableStatusWithSearch() {
		assertEquals(2, repository.searchInvoicesByAdminVendorTypeAndStatus(TENANT, "payable", "partially_paid", "vendor",
				PageRequest.of(0, 10)).getTotalElements());
		assertEquals(1, repository.searchInvoicesByAdminVendorTypeAndStatus(TENANT, "payable", "partially_paid", "legacy",
				PageRequest.of(0, 10)).getTotalElements());
	}

	@Test
	@DisplayName("AR: received-side statuses, both spellings")
	void receivableStatusMatchesBothVocabularies() {
		assertEquals(2, repository.findReceivableByStatus(TENANT, "receivable", "PARTIALLY_RECEIVED", PageRequest.of(0, 10))
				.getTotalElements());
		assertEquals(2, repository.searchReceivableByStatusAndSearch(TENANT, "receivable", "Partially Received", "ar-",
				PageRequest.of(0, 10)).getTotalElements());
		assertEquals(1, repository.findReceivableByStatus(TENANT, "receivable", "RECEIVED", PageRequest.of(0, 10))
				.getTotalElements());
	}

	@Test
	@DisplayName("status-only filter: same tolerance")
	void statusOnly() {
		assertEquals(2, repository.findByAdminIdAndStatusIgnoreCase(TENANT, "Partially_Received", PageRequest.of(0, 10))
				.getTotalElements());
	}
}
