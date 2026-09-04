package com.invoice.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import com.invoice.repository.ManualInvoiceRepository;
import com.invoice.serviceImpl.ManualInvoiceServiceImpl1;
import com.invoice.tenant.TenantContext;

/**
 * Ownership and path containment for {@code GET /manual-invoice/view/{filename}}.
 *
 * <p>The loader was:
 *
 * <pre>
 * File file = new File(uploadDir, filename);
 * if (!file.exists()) throw new FileNotFoundException(...);
 * return new UrlResource(file.toURI());
 * </pre>
 *
 * — no ownership check and <strong>no containment check at all</strong>, so
 * {@code ../../..} escaped the upload directory. This is the fourth endpoint of
 * this exact shape found in this audit; the other three at least blocked
 * traversal.
 *
 * <p>The two controls are tested separately and the containment one is tested
 * with ownership <em>satisfied</em>. Otherwise the ownership check refuses the
 * traversing name first and the containment check is never reached — the
 * ordering problem that made the equivalent test in Invoice-Login pass for the
 * wrong reason (G-45).
 */
class InvoiceFileAccessTest {

	private static final long TENANT = 97001L;
	private static final String OWNED = "owned-invoice-20260904.pdf";
	private static final String FOREIGN = "foreign-invoice-20260904.pdf";

	private ManualInvoiceRepository repository;
	private ManualInvoiceServiceImpl1 service;
	private Path uploads;
	private Path outside;

	@BeforeEach
	void setUp() throws Exception {
		repository = Mockito.mock(ManualInvoiceRepository.class);
		service = new ManualInvoiceServiceImpl1();
		ReflectionTestUtils.setField(service, "invoiceRepository", repository);
		ReflectionTestUtils.setField(service, "uploadDir", "target/test-uploads-20260904");

		uploads = Paths.get("target/test-uploads-20260904").toAbsolutePath();
		Files.createDirectories(uploads);
		Files.writeString(uploads.resolve(OWNED), "OWNED-INVOICE-20260904");
		Files.writeString(uploads.resolve(FOREIGN), "FOREIGN-INVOICE-20260904");

		// A real file outside the upload directory, so a successful traversal
		// would actually return something.
		outside = Paths.get("target", "outside-probe-20260904.txt").toAbsolutePath();
		Files.writeString(outside, "OUTSIDE-20260904");

		TenantContext.setCurrentAdminId(TENANT);
	}

	@AfterEach
	void tearDown() throws Exception {
		TenantContext.clear();
		Files.deleteIfExists(uploads.resolve(OWNED));
		Files.deleteIfExists(uploads.resolve(FOREIGN));
		Files.deleteIfExists(outside);
	}

	@Test
	@DisplayName("positive control: a file the caller's tenant references is served")
	void ownedFileServed() throws Exception {
		Mockito.when(repository.existsUploadedFileForTenant(OWNED, TENANT)).thenReturn(true);

		Resource resource = service.loadFileAsResource(OWNED);
		assertTrue(resource.exists());
		assertEquals("OWNED-INVOICE-20260904",
				new String(resource.getInputStream().readAllBytes()).trim());
	}

	@Test
	@DisplayName("a file no invoice of the caller's references is refused, though it exists")
	void foreignFileRefused() {
		// The file is on disk and readable. The only thing that should stop it
		// is that this tenant has no invoice referencing it.
		Mockito.when(repository.existsUploadedFileForTenant(FOREIGN, TENANT)).thenReturn(false);

		assertThrows(Exception.class, () -> service.loadFileAsResource(FOREIGN),
				"another tenant's invoice attachment was served");
	}

	@Test
	@DisplayName("path traversal is refused even when ownership is satisfied")
	void traversalRefusedWithOwnershipSatisfied() {
		// Ownership deliberately returns true for ANY name here. That isolates
		// the containment check: if it is removed, this test is the one that
		// fails. With ownership stubbed false, the traversal would be refused
		// by the wrong control and the test would prove nothing.
		Mockito.when(repository.existsUploadedFileForTenant(anyString(), anyLong())).thenReturn(true);

		for (String probe : new String[] {
				"../outside-probe-20260904.txt",
				"../../pom.xml",
				"./../outside-probe-20260904.txt",
				"sub/../../pom.xml",
		}) {
			assertThrows(Exception.class, () -> service.loadFileAsResource(probe),
					"traversal succeeded for " + probe);
		}
	}

	@Test
	@DisplayName("the ownership check is asked about the caller's tenant, not a supplied one")
	void ownershipUsesTheTokenTenant() throws Exception {
		Mockito.when(repository.existsUploadedFileForTenant(anyString(), anyLong())).thenReturn(true);
		service.loadFileAsResource(OWNED);
		Mockito.verify(repository).existsUploadedFileForTenant(eq(OWNED), eq(TENANT));
	}

	@Test
	@DisplayName("a missing file inside the directory is a not-found, not a traversal")
	void missingFileIsNotFound() {
		Mockito.when(repository.existsUploadedFileForTenant(anyString(), anyLong())).thenReturn(true);
		assertThrows(Exception.class, () -> service.loadFileAsResource("no-such-20260904.pdf"));
	}
}
