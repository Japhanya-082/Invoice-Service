package com.example.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.entity.ManualInvoice;

@Repository
public interface ManualInvoiceRepository
		extends JpaRepository<ManualInvoice, Long>, JpaSpecificationExecutor<ManualInvoice> {

	// Check if an invoice with the given number exists
	boolean existsByInvoiceNumber(String invoiceNumber);

	List<ManualInvoice> findByCustomerVendorId(Long vendorId);

	// Bhargav 17-03-26
	List<ManualInvoice> findByConsultantId(Long consultantId);
	// Bhargav 17-03-26

	// Search invoices by keyword in multiple fields
//    @Query("SELECT m FROM ManualInvoice m " +
//           "WHERE LOWER(m.companyName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
//           "OR LOWER(m.customer) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
//           "OR LOWER(m.clientEmail) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
//           "OR LOWER(m.clientPhone) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
//           "OR LOWER(m.billingAddress) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
//           "OR LOWER(m.invoiceNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
//           "OR LOWER(m.currency) LIKE LOWER(CONCAT('%', :keyword, '%'))")
//    Page<ManualInvoice> searchInvoices(@Param("keyword") String keyword, Pageable pageable);
//    

	List<ManualInvoice> findByCustomerVendorIdAndAdminId(Long vendorId, Long adminId);

	// vasim/03/03
	@Query("""
			SELECT m FROM ManualInvoice m
			WHERE
			    :keyword IS NULL OR :keyword = '' OR (


			        LOWER(COALESCE(m.customer, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
			        OR LOWER(COALESCE(m.customerEmail, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
			        OR LOWER(COALESCE(m.customerPhone, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
			        OR LOWER(COALESCE(m.invoiceNumber, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
			        OR LOWER(COALESCE(m.paymentTerms, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
			        OR LOWER(COALESCE(m.currency, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
			        OR LOWER(COALESCE(m.poNumber, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
			        OR LOWER(COALESCE(m.salesRep, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
			        OR LOWER(COALESCE(m.status, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
			        OR LOWER(COALESCE(m.issuedBy, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
			        OR LOWER(COALESCE(m.notes, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
			        OR LOWER(COALESCE(m.termsAndConditions, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))


			        OR STR(m.invoiceDate) LIKE CONCAT('%', :keyword, '%')
			        OR STR(m.dueDate) LIKE CONCAT('%', :keyword, '%')
			        OR STR(m.createdAt) LIKE CONCAT('%', :keyword, '%')
			        OR STR(m.updatedAt) LIKE CONCAT('%', :keyword, '%')


			        OR STR(m.total) LIKE CONCAT('%', :keyword, '%')
			        OR STR(m.subtotal) LIKE CONCAT('%', :keyword, '%')
			        OR STR(m.tax) LIKE CONCAT('%', :keyword, '%')
			        OR STR(m.amountDue) LIKE CONCAT('%', :keyword, '%')
			        OR STR(m.credit) LIKE CONCAT('%', :keyword, '%')
			        OR STR(m.totalHours) LIKE CONCAT('%', :keyword, '%')


			        OR LOWER(COALESCE(m.billingAddress.street, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
			        OR LOWER(COALESCE(m.billingAddress.city, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
			        OR LOWER(COALESCE(m.billingAddress.state, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
			        OR LOWER(COALESCE(m.billingAddress.zipCode, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))


			        OR LOWER(COALESCE(m.shippingAddress.street, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
			        OR LOWER(COALESCE(m.shippingAddress.city, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
			        OR LOWER(COALESCE(m.shippingAddress.state, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
			        OR LOWER(COALESCE(m.shippingAddress.zipCode, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
			    )
			""")
	Page<ManualInvoice> searchInvoices(@Param("keyword") String keyword, Pageable pageable);

	long countByCustomerVendorId(Long vendorId);

	boolean existsByPoNumber(String poNumber);

	boolean existsByPoNumberAndIdNot(String poNumber, Long id);

	Optional<ManualInvoice> findByInvoiceNumber(String invoiceNumber);

	boolean existsByPoNumberIgnoreCaseAndIdNot(String poNumber, Long id);

	boolean existsByPoNumberIgnoreCase(String poNumber);

	@Query("SELECT COUNT(i) FROM ManualInvoice i")
	Long getTotalInvoiceCount();

	@Query("SELECT COUNT(i) FROM ManualInvoice i WHERE LOWER(i.status) = 'paid'")
	Long getPaidInvoiceCount();

	@Query("SELECT COUNT(i) FROM ManualInvoice i WHERE LOWER(i.status) = 'pending'")
	Long getPendingInvoiceCount();

	@Query("SELECT COUNT(i) FROM ManualInvoice i WHERE LOWER(i.status) = 'overdue'")
	Long getOverdueInvoiceCount();

	// Count today's overdue invoices (case-insensitive)
	@Query("SELECT COUNT(i) FROM ManualInvoice i " + "WHERE LOWER(i.status) = 'overdue' AND i.dueDate = :today")
	Long countOverdueInvoicesForToday(@Param("today") LocalDate today);

	// Fetch today's overdue invoices for popup (case-insensitive)
	@Query("SELECT i FROM ManualInvoice i LEFT JOIN FETCH i.items "
			+ "WHERE LOWER(i.status) = 'overdue' AND i.dueDate = :today")
	List<ManualInvoice> findOverdueInvoicesForToday(@Param("today") LocalDate today);

	boolean existsByConsultantId(Long consultantId);

	boolean existsByConsultantIdAndAdminId(Long consultantId, Long adminId);
//	boolean existsByPoNumber(String poNumber);
//
//	boolean existsByPoNumberAndIdNot(String poNumber, Long id);
//
//	Optional<ManualInvoice> findByInvoiceNumber(String invoiceNumber);

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

	// Bhargav 20-03-26
	List<ManualInvoice> findByAdminIdAndStatusInIgnoreCase(Long adminId, List<String> statuses);

	Page<ManualInvoice> findByAdminIdAndStatusInIgnoreCase(Long adminId, List<String> statuses, Pageable pageable);

	@Query("""
			SELECT m FROM ManualInvoice m
			WHERE m.adminId = :adminId
			AND LOWER(m.status) IN :statuses
			AND (
			    LOWER(m.consultantName) LIKE CONCAT('%', :search, '%')
			    OR LOWER(m.customer) LIKE CONCAT('%', :search, '%')
			    OR LOWER(m.invoiceNumber) LIKE CONCAT('%', :search, '%')
			)
			""")
	Page<ManualInvoice> searchInvoicesByAdmin(Long adminId, List<String> statuses, String search, Pageable pageable);
	// Bhargav 20-03-26

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

	// payable
	// ✅ CASE 2: vendorType + status + search
	@Query("SELECT i FROM ManualInvoice i\r\n" + "    	    WHERE i.adminId = :adminId\r\n"
			+ "    	    AND LOWER(i.vendorType) = LOWER(:vendorType)\r\n"
			+ "    	    AND LOWER(i.status) = LOWER(:status)\r\n" + "    	    AND (\r\n"
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
	// payable

	// ✅ CASE 3: vendorType + status
	Page<ManualInvoice> findByAdminIdAndVendorTypeAndStatusIgnoreCase(Long adminId, String vendorType, String status,
			Pageable pageable);

//    // ✅ CASE 4: vendorType only
//    Page<ManualInvoice> findByAdminIdAndVendorTypeIgnoreCase(
//            Long adminId,
//            String vendorType,
//            Pageable pageable
//    );

	// ✅ CASE 5: status only
	Page<ManualInvoice> findByAdminIdAndStatusIgnoreCase(Long adminId, String status, Pageable pageable);

	// ✅ CASE 6: search only
	@Query("""
			    SELECT i FROM ManualInvoice i
			    WHERE i.adminId = :adminId
			    AND (
			        LOWER(i.consultantName) LIKE LOWER(CONCAT('%', :search, '%'))
			        OR LOWER(i.customer) LIKE LOWER(CONCAT('%', :search, '%'))
			        OR LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :search, '%'))
			    )
			""")
	Page<ManualInvoice> searchInvoicesByAdminOnly(Long adminId, String search, Pageable pageable);

	// Receivable
	@Query("SELECT i FROM ManualInvoice i\r\n" + "    WHERE i.adminId = :adminId\r\n"
			+ "    AND LOWER(i.vendorType) = LOWER(:vendorType)\r\n" + "    AND LOWER(i.status) = LOWER(:status)\r\n"
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

	@Query("""
			    SELECT i FROM ManualInvoice i
			    WHERE i.adminId = :adminId
			    AND LOWER(i.vendorType) = LOWER(:vendorType)
			    AND LOWER(i.status) = LOWER(:status)
			""")
	Page<ManualInvoice> findReceivableByStatus(Long adminId, String vendorType, String status, Pageable pageable);

	@Query(value = """
			SELECT
			    COUNT(CASE WHEN LOWER(status) = 'paid' THEN 1 END) AS paid_count,
			    COUNT(CASE WHEN LOWER(status) = 'pending' THEN 1 END) AS pending_count,
			    COUNT(CASE WHEN LOWER(status) = 'received' THEN 1 END) AS received_count,
			    COUNT(*) AS total_count
			FROM invoice.manual_invoices
			WHERE admin_id = :adminId
			""", nativeQuery = true)
	Object getInvoiceStatusCounts(@Param("adminId") Long adminId);

}
