
package com.invoice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.invoice.entity.Invoice;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
	/** Unscoped; Invoice entity is not tenant-aware. */
	@Deprecated(forRemoval = false)
	boolean existsByInvoiceNumber(String invoiceNumber);

	/** Unscoped; Invoice entity is not tenant-aware. */
	@Deprecated(forRemoval = false)
	public void deleteByInvoiceNumber(String invoiceNumber);

	/** Unscoped; Invoice entity is not tenant-aware. */
	@Deprecated(forRemoval = false)
	public boolean existsByConsultantId(Long consultantId);
}
