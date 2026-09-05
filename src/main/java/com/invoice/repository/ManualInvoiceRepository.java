package com.invoice.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.invoice.entity.ManualInvoice;

@Repository
public interface ManualInvoiceRepository
		extends JpaRepository<ManualInvoice, Long>, JpaSpecificationExecutor<ManualInvoice> {

	/** Unscoped; use {@code existsByInvoiceNumberAndAdminId} instead. */
	@Deprecated(forRemoval = false)
	boolean existsByInvoiceNumber(String invoiceNumber);

	/** Unscoped; use {@code findByCustomerVendorIdAndAdminId}. */
	@Deprecated(forRemoval = false)
	List<ManualInvoice> findByCustomerVendorId(Long vendorId);

	/** Unscoped. */
	@Deprecated(forRemoval = false)
	List<ManualInvoice> findByConsultantId(Long consultantId);

	List<ManualInvoice> findByCustomerVendorIdAndAdminId(Long vendorId, Long adminId);

	@Query("SELECT m FROM ManualInvoice m\r\n" + "			WHERE\r\n"
			+ "			    :keyword IS NULL OR :keyword = '' OR (\r\n" + "\r\n" + "\r\n"
			+ "			        LOWER(COALESCE(m.customer, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))\r\n"
			+ "			        OR LOWER(COALESCE(m.customerEmail, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))\r\n"
			+ "			        OR LOWER(COALESCE(m.customerPhone, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))\r\n"
			+ "			        OR LOWER(COALESCE(m.invoiceNumber, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))\r\n"
			+ "			        OR LOWER(COALESCE(m.paymentTerms, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))\r\n"
			+ "			        OR LOWER(COALESCE(m.currency, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))\r\n"
			+ "			        OR LOWER(COALESCE(m.poNumber, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))\r\n"
			+ "			        OR LOWER(COALESCE(m.salesRep, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))\r\n"
			+ "			        OR LOWER(COALESCE(m.status, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))\r\n"
			+ "			        OR LOWER(COALESCE(m.issuedBy, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))\r\n"
			+ "			        OR LOWER(COALESCE(m.notes, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))\r\n"
			+ "			        OR LOWER(COALESCE(m.termsAndConditions, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))\r\n"
			+ "\r\n" + "\r\n"
			+ "			        OR CAST(m.invoiceDate AS string) LIKE CONCAT('%', :keyword, '%')\r\n"
			+ "			        OR CAST(m.dueDate AS string) LIKE CONCAT('%', :keyword, '%')\r\n"
			+ "			        OR CAST(m.createdAt AS string) LIKE CONCAT('%', :keyword, '%')\r\n"
			+ "			        OR CAST(m.updatedAt AS string) LIKE CONCAT('%', :keyword, '%')\r\n" + "\r\n" + "\r\n"
			+ "			        OR CAST(m.total AS string) LIKE CONCAT('%', :keyword, '%')\r\n"
			+ "			        OR CAST(m.subtotal AS string) LIKE CONCAT('%', :keyword, '%')\r\n"
			+ "			        OR CAST(m.tax AS string) LIKE CONCAT('%', :keyword, '%')\r\n"
			+ "			        OR CAST(m.amountDue AS string) LIKE CONCAT('%', :keyword, '%')\r\n"
			+ "			        OR CAST(m.credit AS string) LIKE CONCAT('%', :keyword, '%')\r\n"
			+ "			        OR CAST(m.totalHours AS string) LIKE CONCAT('%', :keyword, '%')\r\n" + "\r\n" + "\r\n"
			+ "			        OR LOWER(COALESCE(m.billingAddress.street, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))\r\n"
			+ "			        OR LOWER(COALESCE(m.billingAddress.city, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))\r\n"
			+ "			        OR LOWER(COALESCE(m.billingAddress.state, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))\r\n"
			+ "			        OR LOWER(COALESCE(m.billingAddress.zipCode, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))\r\n"
			+ "\r\n" + "\r\n"
			+ "			        OR LOWER(COALESCE(m.shippingAddress.street, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))\r\n"
			+ "			        OR LOWER(COALESCE(m.shippingAddress.city, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))\r\n"
			+ "			        OR LOWER(COALESCE(m.shippingAddress.state, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))\r\n"
			+ "			        OR LOWER(COALESCE(m.shippingAddress.zipCode, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))\r\n"
			+ "			    )")
	/** Unscoped; prefer the variant with adminId. */
	@Deprecated(forRemoval = false)
	Page<ManualInvoice> searchInvoices(@Param("keyword") String keyword, Pageable pageable);

	/** Unscoped. */
	@Deprecated(forRemoval = false)
	long countByCustomerVendorId(Long vendorId);

	boolean existsByPoNumber(String poNumber);

	boolean existsByPoNumberAndIdNot(String poNumber, Long id);

	Optional<ManualInvoice> findByInvoiceNumber(String invoiceNumber);

	boolean existsByPoNumberIgnoreCaseAndIdNot(String poNumber, Long id);

	boolean existsByPoNumberIgnoreCase(String poNumber);

	/** Unscoped; global count across all tenants. */
	@Deprecated(forRemoval = false)
	@Query("SELECT COUNT(i) FROM ManualInvoice i")
	Long getTotalInvoiceCount();

	/** Unscoped; global count across all tenants. */
	@Deprecated(forRemoval = false)
	@Query("SELECT COUNT(i) FROM ManualInvoice i WHERE LOWER(i.status) = 'paid'")
	Long getPaidInvoiceCount();

	/** Unscoped; global count across all tenants. */
	@Deprecated(forRemoval = false)
	@Query("SELECT COUNT(i) FROM ManualInvoice i WHERE LOWER(i.status) = 'pending'")
	Long getPendingInvoiceCount();

	/** Unscoped; global count across all tenants. */
	@Deprecated(forRemoval = false)
	@Query("SELECT COUNT(i) FROM ManualInvoice i WHERE LOWER(i.status) = 'overdue'")
	Long getOverdueInvoiceCount();

	@Query("SELECT COUNT(i) FROM ManualInvoice i WHERE LOWER(i.status) = 'overdue' AND i.dueDate = :today")
	Long countOverdueInvoicesForToday(@Param("today") LocalDate today);

	@Query("SELECT i FROM ManualInvoice i LEFT JOIN FETCH i.items WHERE LOWER(i.status) = 'overdue' AND i.dueDate = :today")
	List<ManualInvoice> findOverdueInvoicesForToday(@Param("today") LocalDate today);

	// Scheduler queries — per-admin overdue and upcoming-due AR (receivable)
	// invoices only
	// Skips invoices snoozed until a future date
	@Query("SELECT i FROM ManualInvoice i LEFT JOIN FETCH i.items "
			+ "WHERE i.adminId = :adminId AND i.dueDate < :today "
			+ "AND LOWER(i.status) IN ('pending', 'partially_received', 'partially received') "
			+ "AND LOWER(i.vendorType) = 'receivable' " + "AND i.deletedAt IS NULL "
			+ "AND (i.reminderSnoozedUntil IS NULL OR i.reminderSnoozedUntil < :today)")
	List<ManualInvoice> findOverdueByAdmin(@Param("adminId") Long adminId, @Param("today") LocalDate today);

	@Query("SELECT i FROM ManualInvoice i LEFT JOIN FETCH i.items "
			+ "WHERE i.adminId = :adminId AND i.dueDate = :dueOn " + "AND LOWER(i.vendorType) = 'receivable' "
			+ "AND LOWER(i.status) IN ('pending', 'partially_received', 'partially received') "
			+ "AND i.deletedAt IS NULL " + "AND (i.reminderSnoozedUntil IS NULL OR i.reminderSnoozedUntil < :dueOn)")
	List<ManualInvoice> findDueOnByAdmin(@Param("adminId") Long adminId, @Param("dueOn") LocalDate dueOn);

	@Modifying
	@Query("UPDATE ManualInvoice i SET i.status = 'OVERDUE' "
			+ "WHERE i.dueDate < :today AND LOWER(i.status) IN ('pending', 'partially_paid', 'partially paid')")
	int markOverdueInvoices(@Param("today") LocalDate today);

	boolean existsByConsultantId(Long consultantId);

	boolean existsByConsultantIdAndAdminId(Long consultantId, Long adminId);

	Optional<ManualInvoice> findByIdAndAdminId(Long id, Long adminId);

	boolean existsByPoNumberIgnoreCaseAndAdminId(String poNumber, Long adminId);

	boolean existsByPoNumberIgnoreCaseAndAdminIdAndIdNot(String poNumber, Long adminId, Long id);

	List<ManualInvoice> findByAdminId(Long adminId);

	@Query("SELECT mi FROM ManualInvoice mi WHERE mi.adminId = :adminId AND " + "LOWER(mi.status) = 'draft' AND "
			+ "(:keyword = '' OR " + "LOWER(mi.customer) LIKE :keyword OR " + "LOWER(mi.poNumber) LIKE :keyword OR "
			+ "LOWER(mi.invoiceNumber) LIKE :keyword OR " + "LOWER(mi.consultantName) LIKE :keyword OR "
			+ "CAST(mi.totalHours AS string) LIKE :keyword OR " + "CAST(mi.dueDate AS string) LIKE :keyword OR "
			+ "CAST(mi.invoiceDate AS string) LIKE :keyword OR " + "CAST(mi.dueAmount AS string) LIKE :keyword OR "
			+ "CAST(mi.paymentAmount AS string) LIKE :keyword OR " + "CAST(mi.createdAt AS string) LIKE :keyword OR "
			+ "LOWER(mi.status) LIKE :keyword)")
	Page<ManualInvoice> searchInvoices(@Param("keyword") String keyword, @Param("adminId") Long adminId,
			Pageable pageable);

	Optional<ManualInvoice> findByInvoiceNumberAndAdminId(String invoiceNumber, Long adminId);

	List<ManualInvoice> findByAdminIdAndStatusInIgnoreCase(Long adminId, List<String> statuses);

	Page<ManualInvoice> findByAdminIdAndStatusInIgnoreCase(Long adminId, List<String> statuses, Pageable pageable);

	@Query("SELECT m FROM ManualInvoice m\r\n" + "			WHERE m.adminId = :adminId\r\n"
			+ "			AND LOWER(m.status) IN :statuses\r\n" + "			AND (\r\n"
			+ "			    LOWER(m.consultantName) LIKE CONCAT('%', :search, '%')\r\n"
			+ "			    OR LOWER(m.customer) LIKE CONCAT('%', :search, '%')\r\n"
			+ "			    OR LOWER(m.invoiceNumber) LIKE CONCAT('%', :search, '%')\r\n" + "			)")
	Page<ManualInvoice> searchInvoicesByAdmin(Long adminId, List<String> statuses, String search, Pageable pageable);

	boolean existsByPoNumberAndConsultantIdNot(String poNumber, Long consultantId);

	boolean existsByPoNumberAndConsultantIdNotAndIdNot(String poNumber, Long consultantId, Long id);

	@Query("SELECT i FROM ManualInvoice i\r\n" + "WHERE i.adminId = :adminId\r\n"
			+ "			AND LOWER(i.vendorType) = LOWER(:vendorType)\r\n" + "			AND (\r\n"
			+ "			LOWER(i.consultantName) LIKE %:search%\r\n" + "			OR LOWER(i.customer) LIKE %:search%\r\n"
			+ "			OR LOWER(i.invoiceNumber) LIKE %:search%\r\n" + "			)")
	Page<ManualInvoice> searchInvoicesByAdminAndVendorType(Long adminId, String vendorType, String search,
			Pageable pageable);

	Page<ManualInvoice> findByAdminIdAndVendorTypeIgnoreCase(Long adminId, String vendorType, Pageable pageable);

	@Query(" SELECT i FROM ManualInvoice i\r\n" + "		    WHERE i.adminId = :adminId\r\n"
			+ "		    AND LOWER(i.vendorType) = LOWER(:vendorType)\r\n" + "		    AND (\r\n"
			+ "		        LOWER(i.consultantName) LIKE %:search%\r\n"
			+ "		        OR LOWER(i.customer) LIKE %:search%\r\n"
			+ "		        OR LOWER(i.invoiceNumber) LIKE %:search%\r\n" + "		    )")
	Page<ManualInvoice> searchInvoiceByAdminAndVendorType(Long adminId, String vendorType, String search,
			Pageable pageable);

	// ✅ CASE 1: ALL DATA (adminId only)
	Page<ManualInvoice> findByAdminId(Long adminId, Pageable pageable);

	// ✅ CASE 2: vendorType + status + search
	@Query("SELECT i FROM ManualInvoice i\r\n" + "    	    WHERE i.adminId = :adminId\r\n"
			+ "    	    AND LOWER(i.vendorType) = LOWER(:vendorType)\r\n"
			+ "    	    AND LOWER(REPLACE(i.status, '_', ' ')) = LOWER(REPLACE(:status, '_', ' '))\r\n" + "    	    AND (\r\n"
			+ "    	        :search IS NULL OR :search = '' OR (\r\n"
			+ "    	            LOWER(i.consultantName) LIKE LOWER(CONCAT('%', :search, '%'))\r\n"
			+ "    	            OR LOWER(i.customer) LIKE LOWER(CONCAT('%', :search, '%'))\r\n"
			+ "    	            OR LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :search, '%'))\r\n"
			+ "    	            OR LOWER(i.customerEmail) LIKE LOWER(CONCAT('%', :search, '%'))\r\n"
			+ "    	            OR LOWER(i.customerPhone) LIKE LOWER(CONCAT('%', :search, '%'))\r\n"
			+ "    	            OR LOWER(i.poNumber) LIKE LOWER(CONCAT('%', :search, '%'))\r\n"
			+ "    	            OR LOWER(i.status) LIKE LOWER(CONCAT('%', :search, '%'))\r\n"
			+ "    	            OR LOWER(i.paymentTerms) LIKE LOWER(CONCAT('%', :search, '%'))\r\n"
			+ "    	            OR LOWER(i.currency) LIKE LOWER(CONCAT('%', :search, '%'))\r\n"
			+ "    	            OR CAST(i.total AS string) LIKE CONCAT('%', :search, '%')\r\n"
			+ "    	            OR CAST(i.subtotal AS string) LIKE CONCAT('%', :search, '%')\r\n"
			+ "    	            OR CAST(i.amountDue AS string) LIKE CONCAT('%', :search, '%')\r\n"
			+ "    	            OR CAST(i.paidAmount AS string) LIKE CONCAT('%', :search, '%')\r\n"
			+ "    	            OR CAST(i.dueAmount AS string) LIKE CONCAT('%', :search, '%')\r\n"
			+ "    	            OR CAST(i.periodend AS string) LIKE CONCAT('%', :search, '%')\r\n"
			+ "    	            OR CAST(i.periodStart AS string) LIKE CONCAT('%', :search, '%')\r\n"
			+ "    	            OR CAST(i.vendorType AS string) LIKE CONCAT('%', :search, '%')\r\n"
			+ "    	            OR CAST(i.totalHours AS string) LIKE CONCAT('%', :search, '%')\r\n"
			+ "    	            OR CAST(i.invoiceDate AS string) LIKE CONCAT('%', :search, '%')\r\n"
			+ "    	            OR CAST(i.dueDate AS string) LIKE CONCAT('%', :search, '%')\r\n"
			+ "    	            OR CAST(i.paymentAmount AS string) LIKE CONCAT('%', :search, '%')\r\n"
			+ "    	            OR CAST(i.paymentDate AS string) LIKE CONCAT('%', :search, '%')\r\n"
			+ "    	            OR CAST(i.paidDate AS string) LIKE CONCAT('%', :search, '%')\r\n"
			+ "    	        )\r\n" + "    	    )")
	Page<ManualInvoice> searchInvoicesByAdminVendorTypeAndStatus(Long adminId, String vendorType, String status,
			String search, Pageable pageable);

	/**
	 * Status filters compare {@code lower(replace(status,'_',' '))} on both sides.
	 *
	 * <p>The table holds two vocabularies: legacy rows in title case with spaces
	 * ("Partially Received", 13 of them on UAT) and rows written since the status
	 * normalisation in upper snake case ("PARTIALLY_RECEIVED"). Both front ends
	 * send the snake form, so an exact case-insensitive match showed the AP and
	 * AR "Partially ..." and "Excess ..." tabs as empty for every legacy row.
	 * Equality after folding case and the separator matches both.
	 */
	// ✅ CASE 3: vendorType + status
	@Query("SELECT i FROM ManualInvoice i WHERE i.adminId = :adminId "
			+ "AND LOWER(i.vendorType) = LOWER(:vendorType) "
			+ "AND LOWER(REPLACE(i.status, '_', ' ')) = LOWER(REPLACE(:status, '_', ' '))")
	Page<ManualInvoice> findByAdminIdAndVendorTypeAndStatusIgnoreCase(@Param("adminId") Long adminId,
			@Param("vendorType") String vendorType, @Param("status") String status, Pageable pageable);

	// ✅ CASE 5: status only
	@Query("SELECT i FROM ManualInvoice i WHERE i.adminId = :adminId "
			+ "AND LOWER(REPLACE(i.status, '_', ' ')) = LOWER(REPLACE(:status, '_', ' '))")
	Page<ManualInvoice> findByAdminIdAndStatusIgnoreCase(@Param("adminId") Long adminId, @Param("status") String status,
			Pageable pageable);

	// ✅ CASE 6: search only
	@Query("  SELECT i FROM ManualInvoice i\r\n" + "			    WHERE i.adminId = :adminId\r\n"
			+ "			    AND (\r\n"
			+ "			        LOWER(i.consultantName) LIKE LOWER(CONCAT('%', :search, '%'))\r\n"
			+ "			        OR LOWER(i.customer) LIKE LOWER(CONCAT('%', :search, '%'))\r\n"
			+ "			        OR LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :search, '%'))\r\n"
			+ "			    )")
	Page<ManualInvoice> searchInvoicesByAdminOnly(Long adminId, String search, Pageable pageable);

	// Receivable
	@Query("SELECT i FROM ManualInvoice i\r\n" + "    WHERE i.adminId = :adminId\r\n"
			+ "    AND LOWER(i.vendorType) = LOWER(:vendorType)\r\n" + "    AND LOWER(REPLACE(i.status, '_', ' ')) = LOWER(REPLACE(:status, '_', ' '))\r\n"
			+ "    AND (\r\n" + "        :search IS NULL OR :search = '' OR (\r\n"
			+ "            LOWER(i.consultantName) LIKE LOWER(CONCAT('%', :search, '%'))\r\n"
			+ "            OR LOWER(i.customer) LIKE LOWER(CONCAT('%', :search, '%'))\r\n"
			+ "            OR LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :search, '%'))\r\n"
			+ "            OR LOWER(i.customerEmail) LIKE LOWER(CONCAT('%', :search, '%'))\r\n"
			+ "            OR LOWER(i.customerPhone) LIKE LOWER(CONCAT('%', :search, '%'))\r\n"
			+ "            OR LOWER(i.poNumber) LIKE LOWER(CONCAT('%', :search, '%'))\r\n"
			+ "            OR LOWER(i.status) LIKE LOWER(CONCAT('%', :search, '%'))\r\n"
			+ "            OR LOWER(i.paymentTerms) LIKE LOWER(CONCAT('%', :search, '%'))\r\n"
			+ "            OR LOWER(i.currency) LIKE LOWER(CONCAT('%', :search, '%'))\r\n"
			+ "            OR CAST(i.total AS string) LIKE CONCAT('%', :search, '%')\r\n"
			+ "            OR CAST(i.subtotal AS string) LIKE CONCAT('%', :search, '%')\r\n"
			+ "            OR CAST(i.amountDue AS string) LIKE CONCAT('%', :search, '%')\r\n"
			+ "            OR CAST(i.paidAmount AS string) LIKE CONCAT('%', :search, '%')\r\n"
			+ "            OR CAST(i.dueAmount AS string) LIKE CONCAT('%', :search, '%')\r\n"
			+ "            OR CAST(i.periodend AS string) LIKE CONCAT('%', :search, '%')\r\n"
			+ "            OR CAST(i.periodStart AS string) LIKE CONCAT('%', :search, '%')\r\n"
			+ "            OR CAST(i.vendorType AS string) LIKE CONCAT('%', :search, '%')\r\n"
			+ "            OR CAST(i.totalHours AS string) LIKE CONCAT('%', :search, '%')\r\n"
			+ "            OR CAST(i.invoiceDate AS string) LIKE CONCAT('%', :search, '%')\r\n"
			+ "            OR CAST(i.dueDate AS string) LIKE CONCAT('%', :search, '%')\r\n"
			+ "            OR CAST(i.paymentAmount AS string) LIKE CONCAT('%', :search, '%')\r\n"
			+ "            OR CAST(i.paymentDate AS string) LIKE CONCAT('%', :search, '%')\r\n"
			+ "            OR CAST(i.paidDate AS string) LIKE CONCAT('%', :search, '%')\r\n" + "        )\r\n"
			+ "    )")
	Page<ManualInvoice> searchReceivableByStatusAndSearch(Long adminId, String vendorType, String status, String search,
			Pageable pageable);
	// Receivable

	@Query(value = "SELECT i FROM ManualInvoice i\r\n" + "			    WHERE i.adminId = :adminId\r\n"
			+ "			    AND LOWER(i.vendorType) = LOWER(:vendorType)\r\n"
			+ "			    AND LOWER(REPLACE(i.status, '_', ' ')) = LOWER(REPLACE(:status, '_', ' '))")
	Page<ManualInvoice> findReceivableByStatus(Long adminId, String vendorType, String status, Pageable pageable);

	@Query("SELECT COUNT(i) FROM ManualInvoice i WHERE i.adminId = :adminId AND LOWER(i.vendorType) = LOWER(:vendorType) AND LOWER(REPLACE(i.status, '_', ' ')) = LOWER(REPLACE(:status, '_', ' ')) AND i.id < :invoiceId")
	long countBeforeIdWithFilters(@Param("adminId") Long adminId, @Param("vendorType") String vendorType,
			@Param("status") String status, @Param("invoiceId") Long invoiceId);

	@Query(value = "SELECT\r\n" + "			    COUNT(CASE WHEN LOWER(status) = 'paid' THEN 1 END) AS paid_count,\r\n"
			+ "			    COUNT(CASE WHEN LOWER(status) = 'pending' THEN 1 END) AS pending_count,\r\n"
			+ "			    COUNT(CASE WHEN LOWER(status) = 'received' THEN 1 END) AS received_count,\r\n"
			+ "			    COUNT(*) AS total_count\r\n" + "			FROM invoice.manual_invoices\r\n"
			+ "			WHERE admin_id = :adminId", nativeQuery = true)
	Object getInvoiceStatusCounts(@Param("adminId") Long adminId);

	boolean existsByEmploymentId(Long employmentId);

	// ─── Dashboard: AR/AP Outstanding ────────────────────────────────────────

	@Query("SELECT SUM(i.amountDue) FROM ManualInvoice i "
			+ "WHERE i.adminId = :adminId AND LOWER(i.vendorType) = 'receivable' "
			+ "AND UPPER(i.status) IN ('PENDING','PARTIALLY_RECEIVED','OVERDUE') " + "AND i.deletedAt IS NULL")
	BigDecimal sumArOutstanding(@Param("adminId") Long adminId);

	@Query("SELECT COUNT(i) FROM ManualInvoice i "
			+ "WHERE i.adminId = :adminId AND LOWER(i.vendorType) = 'receivable' "
			+ "AND UPPER(i.status) IN ('PENDING','PARTIALLY_RECEIVED','OVERDUE') " + "AND i.deletedAt IS NULL")
	Long countArOutstanding(@Param("adminId") Long adminId);

	@Query("SELECT SUM(i.amountDue) FROM ManualInvoice i "
			+ "WHERE i.adminId = :adminId AND LOWER(i.vendorType) = 'payable' "
			+ "AND UPPER(i.status) IN ('PENDING','PARTIALLY_PAID','OVERDUE') " + "AND i.deletedAt IS NULL")
	BigDecimal sumApOutstanding(@Param("adminId") Long adminId);

	@Query("SELECT COUNT(i) FROM ManualInvoice i " + "WHERE i.adminId = :adminId AND LOWER(i.vendorType) = 'payable' "
			+ "AND UPPER(i.status) IN ('PENDING','PARTIALLY_PAID','OVERDUE') " + "AND i.deletedAt IS NULL")
	Long countApOutstanding(@Param("adminId") Long adminId);

	// ─── Dashboard: Overdue ───────────────────────────────────────────────────

	@Query("SELECT SUM(i.amountDue) FROM ManualInvoice i "
			+ "WHERE i.adminId = :adminId AND i.dueDate IS NOT NULL AND i.dueDate < CURRENT_DATE "
			+ "AND UPPER(i.status) NOT IN ('PAID','RECEIVED','CANCELLED','DRAFT','EXCESS_PAID','EXCESS_RECEIVED') "
			+ "AND i.deletedAt IS NULL")
	BigDecimal sumOverdueAmount(@Param("adminId") Long adminId);

	@Query("SELECT COUNT(i) FROM ManualInvoice i "
			+ "WHERE i.adminId = :adminId AND i.dueDate IS NOT NULL AND i.dueDate < CURRENT_DATE "
			+ "AND UPPER(i.status) NOT IN ('PAID','RECEIVED','CANCELLED','DRAFT','EXCESS_PAID','EXCESS_RECEIVED') "
			+ "AND i.deletedAt IS NULL")
	Long countOverdue(@Param("adminId") Long adminId);

	@Query("SELECT SUM(i.amountDue) FROM ManualInvoice i "
			+ "WHERE i.adminId = :adminId AND LOWER(i.vendorType) = 'receivable' "
			+ "AND i.dueDate IS NOT NULL AND i.dueDate < CURRENT_DATE "
			+ "AND UPPER(i.status) NOT IN ('RECEIVED','CANCELLED','DRAFT','EXCESS_RECEIVED') "
			+ "AND i.deletedAt IS NULL")
	BigDecimal sumArOverdueAmount(@Param("adminId") Long adminId);

	@Query("SELECT SUM(i.amountDue) FROM ManualInvoice i "
			+ "WHERE i.adminId = :adminId AND LOWER(i.vendorType) = 'payable' "
			+ "AND i.dueDate IS NOT NULL AND i.dueDate < CURRENT_DATE "
			+ "AND UPPER(i.status) NOT IN ('PAID','CANCELLED','DRAFT','EXCESS_PAID') " + "AND i.deletedAt IS NULL")
	BigDecimal sumApOverdueAmount(@Param("adminId") Long adminId);

	// ─── Dashboard: Upcoming Due Dates ────────────────────────────────────────

	@Query("SELECT i FROM ManualInvoice i " + "WHERE i.adminId = :adminId AND i.dueDate IS NOT NULL "
			+ "AND i.dueDate <= :endDate "
			+ "AND UPPER(i.status) NOT IN ('PAID','RECEIVED','CANCELLED','EXCESS_PAID','EXCESS_RECEIVED','DRAFT') "
			+ "AND i.deletedAt IS NULL " + "ORDER BY i.dueDate ASC")
	List<ManualInvoice> findUpcomingAndOverdue(@Param("adminId") Long adminId,
			@Param("endDate") java.time.LocalDate endDate, org.springframework.data.domain.Pageable pageable);

	// ─── Dashboard: Recent Activity ───────────────────────────────────────────

	@Query("SELECT i FROM ManualInvoice i " + "WHERE i.adminId = :adminId AND i.deletedAt IS NULL "
			+ "ORDER BY i.updatedAt DESC")
	List<ManualInvoice> findRecentlyUpdated(@Param("adminId") Long adminId,
			org.springframework.data.domain.Pageable pageable);

	// ─── Dashboard: Collected / Paid this month (from invoice status) ─────────

	@Query("SELECT SUM(i.total) FROM ManualInvoice i "
			+ "WHERE i.adminId = :adminId AND LOWER(i.vendorType) = 'receivable' "
			+ "AND UPPER(i.status) IN ('RECEIVED','PARTIALLY_RECEIVED','EXCESS_RECEIVED') " + "AND i.deletedAt IS NULL "
			+ "AND YEAR(i.invoiceDate) = :year AND MONTH(i.invoiceDate) = :month")
	BigDecimal sumCollectedThisMonth(@Param("adminId") Long adminId, @Param("year") int year,
			@Param("month") int month);

	@Query("SELECT COUNT(i) FROM ManualInvoice i "
			+ "WHERE i.adminId = :adminId AND LOWER(i.vendorType) = 'receivable' "
			+ "AND UPPER(i.status) IN ('RECEIVED','PARTIALLY_RECEIVED','EXCESS_RECEIVED') " + "AND i.deletedAt IS NULL "
			+ "AND YEAR(i.invoiceDate) = :year AND MONTH(i.invoiceDate) = :month")
	Long countCollectedThisMonth(@Param("adminId") Long adminId, @Param("year") int year, @Param("month") int month);

	@Query("SELECT SUM(i.total) FROM ManualInvoice i "
			+ "WHERE i.adminId = :adminId AND LOWER(i.vendorType) = 'payable' "
			+ "AND UPPER(i.status) IN ('PAID','PARTIALLY_PAID','EXCESS_PAID') " + "AND i.deletedAt IS NULL "
			+ "AND YEAR(i.invoiceDate) = :year AND MONTH(i.invoiceDate) = :month")
	BigDecimal sumPaidThisMonth(@Param("adminId") Long adminId, @Param("year") int year, @Param("month") int month);

	@Query("SELECT COUNT(i) FROM ManualInvoice i " + "WHERE i.adminId = :adminId AND LOWER(i.vendorType) = 'payable' "
			+ "AND UPPER(i.status) IN ('PAID','PARTIALLY_PAID','EXCESS_PAID') " + "AND i.deletedAt IS NULL "
			+ "AND YEAR(i.invoiceDate) = :year AND MONTH(i.invoiceDate) = :month")
	Long countPaidThisMonth(@Param("adminId") Long adminId, @Param("year") int year, @Param("month") int month);


	// Tenant-scoped variants for the call sites that were still using the
	// unscoped ones. The unscoped methods on this interface already carry
	// "Unscoped; use ... instead" comments -- the derivations existed, three
	// service methods just never moved over.
	java.util.List<ManualInvoice> findByConsultantIdAndAdminId(Long consultantId, Long adminId);

	/**
	 * Whether the caller's own tenant has an invoice referencing this uploaded
	 * file. uploadedFileNames is an @ElementCollection, so this needs an
	 * explicit query rather than a derivation.
	 */
	@org.springframework.data.jpa.repository.Query("""
			SELECT COUNT(i) > 0 FROM ManualInvoice i JOIN i.uploadedFileNames f
			WHERE f = :fileName AND i.adminId = :adminId
			""")
	boolean existsUploadedFileForTenant(
			@org.springframework.data.repository.query.Param("fileName") String fileName,
			@org.springframework.data.repository.query.Param("adminId") Long adminId);
}
