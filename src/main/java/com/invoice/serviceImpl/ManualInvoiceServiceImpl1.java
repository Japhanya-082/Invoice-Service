package com.invoice.serviceImpl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.invoice.DTO.ConsultantDTO;
import com.invoice.DTO.InvoiceSortingRequestDTO;
import com.invoice.DTO.VendorDTO;
import com.invoice.DTO.VendorEnvelope;
import com.invoice.client.ConsultantFeignClient;
import com.invoice.client.VendorFeignClient;
import com.invoice.entity.InvoiceItem;
import com.invoice.entity.AdminSettings;
import com.invoice.entity.ManualInvoice;
import com.invoice.tenant.SecurityUtils;
import com.invoice.repository.AdminSettingsRepository;
import com.invoice.repository.ManualInvoiceRepository;
import com.invoice.service.ManualInvoiceService1;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ManualInvoiceServiceImpl1 implements ManualInvoiceService1 {

	@Value("${file.upload-dir}")
	private String uploadDir;

	@Autowired
	private ManualInvoiceRepository invoiceRepository;

	@Autowired
	private AdminSettingsRepository adminSettingsRepository;

	@Autowired
	private com.invoice.repository.ManageUserRepository manageUserRepository;

	@Autowired
	private VendorFeignClient vendorFeignClient;

	@Autowired
	private ConsultantFeignClient consultantFeignClient;

	@Autowired
	private InvoiceEmailService invoiceEmailService;

	@Override
	@Transactional
	public ManualInvoice saveInvoice(ManualInvoice request) {

		ManualInvoice invoice;
		String poNumber = request.getPoNumber() != null ? request.getPoNumber().trim() : null;

		// convert empty to null
		if (poNumber != null && poNumber.isEmpty()) {
			poNumber = null;
		}

		// ===== CREATE vs UPDATE =====
		if (request.getId() != null && request.getId() > 0) {

			invoice = invoiceRepository.findByIdAndAdminId(request.getId(), request.getAdminId())
					.orElseThrow(() -> new RuntimeException("Invoice not found or unauthorized access"));

			// UPDATE validation
			if (poNumber != null && invoiceRepository.existsByPoNumberAndConsultantIdNotAndIdNot(poNumber,
					request.getConsultantId(), invoice.getId())) {

				throw new RuntimeException("PO Number already used by another consultant");
			}

			invoice.clearItems();

			if (request.getItems() != null) {
				for (InvoiceItem item : request.getItems()) {
					item.setManualInvoice(invoice); // parent mapping
					invoice.getItems().add(item);
				}
			}
		} else {

			// CREATE validation
			if (poNumber != null
					&& invoiceRepository.existsByPoNumberAndConsultantIdNot(poNumber, request.getConsultantId())) {

				throw new RuntimeException("PO Number already used by another consultant");
			}

			invoice = new ManualInvoice();
			invoice.setCreatedAt(LocalDateTime.now());
		}

		invoice.setPoNumber(poNumber);
		// ===== Validate Consultant =====
		if (request.getConsultantId() != null) {

			try {

				ConsultantDTO consultant = consultantFeignClient.getConsultant(request.getConsultantId());

				if (consultant == null) {
					throw new RuntimeException("Consultant not found with id: " + request.getConsultantId());
				}

				// Fail closed, not with a NullPointerException: when Customer-Service
				// answers without the consultant's tenant, the comparison below
				// cannot be made, and the old code then stamped the invoice with a
				// null adminId -- an invoice owned by nobody (E-5).
				if (consultant.getAdminId() == null) {
					throw new RuntimeException("Unable to verify the consultant's company. Please try again.");
				}
				if (request.getAdminId() != null && !consultant.getAdminId().equals(request.getAdminId())) {
					throw new RuntimeException("Unauthorized consultant access");
				}

				invoice.setConsultantId(consultant.getId());
				invoice.setConsultantName(consultant.getFullName());
				// The caller's tenant, from the token; the consultant's is only ever confirmed equal.
				invoice.setAdminId(request.getAdminId() != null ? request.getAdminId() : consultant.getAdminId());

			} catch (feign.FeignException.Unauthorized e) {

				throw new RuntimeException("Invalid or expired token. Please login again.");

			} catch (feign.FeignException.NotFound e) {

				throw new RuntimeException("Consultant not found with id: " + request.getConsultantId());

			} catch (feign.FeignException e) {

				throw new RuntimeException("Unable to fetch consultant details. Please try again.");

			}
		}

		// ===== Basic Fields =====
		invoice.setCustomer(request.getCustomer());
		invoice.setCustomerEmail(request.getCustomerEmail());
		invoice.setCustomerPhone(request.getCustomerPhone());
		invoice.setInvoiceDate(request.getInvoiceDate());
		invoice.setDueDate(request.getDueDate());
		invoice.setPaymentTerms(request.getPaymentTerms());
		invoice.setNotes(request.getNotes());
		invoice.setTax(request.getTax());
		invoice.setCredit(request.getCredit());
		invoice.setBillingAddress(request.getBillingAddress());
		invoice.setShippingAddress(request.getShippingAddress());
		invoice.setSalesRep(request.getSalesRep());
		invoice.setPoNumber(poNumber);
		invoice.setTemplate(request.getTemplate());
		invoice.setTermsAndConditions(request.getTermsAndConditions());
		invoice.setStatus(request.getStatus());
		invoice.setCurrency(request.getCurrency());
		invoice.setVendorType(request.getVendorType());

		// ===== New Fields =====
		invoice.setUploadedFileNames(request.getUploadedFileNames());
		invoice.setIssuedBy(request.getIssuedBy());
		invoice.setPaymentAmount(request.getPaymentAmount());
		invoice.setPaymentDate(request.getPaymentDate());
		invoice.setDueAmount(request.getDueAmount());
		invoice.setRemarks(request.getRemarks());
		invoice.setPeriodend(request.getPeriodend());
		invoice.setPeriodStart(request.getPeriodStart());
		invoice.setDiscount(request.getDiscount());
		invoice.setPaidDate(request.getPaidDate());
		invoice.setPaidAmount(request.getPaidAmount());
		invoice.setPeriod(request.getPeriod());
		invoice.setEmploymentId(request.getEmploymentId());
		// Financial totals are computed by the frontend and sent in the payload
		invoice.setSubtotal(request.getSubtotal());
		invoice.setTotal(request.getTotal());
		invoice.setTotalHours(request.getTotalHours());
		// ===== Vendor Lookup =====
		if (request.getCustomerVendorId() != null
				|| (request.getCustomer() != null && !request.getCustomer().isBlank())) {

			VendorDTO vendor = resolveVendor(request.getCustomerVendorId(), request.getCustomer());

			if (vendor != null) {

				invoice.setCustomerVendorId(vendor.getVendorId());
				invoice.setCustomer(vendor.getVendorName());
				invoice.setVendorType(invoice.getVendorType());

				if (request.getCustomerEmail() != null && !request.getCustomerEmail().isBlank()) {
					invoice.setCustomerEmail(request.getCustomerEmail());
				} else {
					invoice.setCustomerEmail(vendor.getEmail());
				}

				if (request.getCustomerPhone() != null && !request.getCustomerPhone().isBlank()) {
					invoice.setCustomerPhone(request.getCustomerPhone());
				} else {
					invoice.setCustomerPhone(vendor.getPhoneNumber());
				}

			} else {
				throw new RuntimeException("Vendor not found for customer: " + request.getCustomer());
			}
		}

		// ===== Items =====
		if (request.getItems() != null) {
			for (InvoiceItem item : request.getItems()) {
				invoice.addItem(item);
			}
		}

		// ===== Calculations =====
		// Recompute item amounts (hours * rate) and subtotal/total from items.
		// dueAmount is set from the request above and is not touched here.
		calculateTotalsAndDueDate(invoice);

		invoice.setUpdatedAt(LocalDateTime.now());

		// ===== Generate Invoice Number =====
		try {

			if (invoice.getInvoiceNumber() == null) {

				LocalDate today = LocalDate.now();
				String year = String.valueOf(today.getYear()).substring(2);

				Long consultantId = invoice.getConsultantId() != null ? invoice.getConsultantId() : 0L;
				String consultant = String.format("%03d", consultantId);

				invoice = invoiceRepository.save(invoice);

				String invoiceId = String.format("%03d", invoice.getId());

				String prefix = resolveInvoicePrefix(invoice.getAdminId());
				invoice.setInvoiceNumber(prefix + year + consultant + invoiceId);
			}

			return invoiceRepository.save(invoice);

		} catch (org.springframework.dao.DataIntegrityViolationException e) {

			String error = e.getMessage();

			if (error != null && error.contains("po_number")) {
				throw new RuntimeException("PO Number already exists.");
			}

			if (error != null && error.contains("manual_invoices_pkey")) {
				throw new RuntimeException("Invoice already exists. Please refresh and try again.");
			}

			throw new RuntimeException("Database error while saving invoice.");
		}
	}

	private void calculateTotalsAndDueDate(ManualInvoice invoice) {

		BigDecimal subtotal = BigDecimal.ZERO;
		BigDecimal totalHours = BigDecimal.ZERO;

		if (invoice.getItems() != null) {
			for (InvoiceItem item : invoice.getItems()) {

				BigDecimal hours = item.getHours() != null ? item.getHours() : BigDecimal.ZERO;
				BigDecimal rate = item.getRate() != null ? item.getRate() : BigDecimal.ZERO;

				BigDecimal amount = hours.multiply(rate).setScale(4, RoundingMode.HALF_UP);
				item.setAmount(amount);

				subtotal = subtotal.add(amount);
				totalHours = totalHours.add(hours);
			}
		}

		invoice.setSubtotal(subtotal.setScale(4, RoundingMode.HALF_UP));
		invoice.setTotalHours(totalHours.setScale(4, RoundingMode.HALF_UP));

		BigDecimal tax = invoice.getTax() != null ? invoice.getTax() : BigDecimal.ZERO;
		invoice.setTotal(subtotal.add(tax).setScale(4, RoundingMode.HALF_UP));

		BigDecimal credit = invoice.getCredit() != null ? invoice.getCredit() : BigDecimal.ZERO;
		invoice.setAmountDue(invoice.getTotal().subtract(credit).setScale(4, RoundingMode.HALF_UP));

	}

	@Override
	public ManualInvoice getInvoiceByNumber(String invoiceNumber) {
		return invoiceRepository.findByInvoiceNumber(invoiceNumber)
				.orElseThrow(() -> new RuntimeException("Invoice not found with number: " + invoiceNumber));
	}

	@Override
	public boolean isPoNumberDuplicate(String poNumber, Long invoiceId, Long adminId) {

		if (poNumber == null || poNumber.isBlank()) {
			return false;
		}

		// UPDATE case
		if (invoiceId != null) {
			return invoiceRepository.existsByPoNumberIgnoreCaseAndAdminIdAndIdNot(poNumber, adminId, invoiceId);
		}

		// CREATE case
		return invoiceRepository.existsByPoNumberIgnoreCaseAndAdminId(poNumber, adminId);
	}

	@Override
	public ManualInvoice getInvoiceById(Long id) {
		// Scoped. findByIdAndAdminId already existed on the repository -- this
		// call site simply never moved over, so any tenant's invoice was
		// readable by id.
		ManualInvoice invoice = invoiceRepository.findByIdAndAdminId(id, SecurityUtils.getCurrentAdminId())
				.orElseThrow(() -> new RuntimeException("Invoice not found with id: " + id));

		if (invoice.getUploadedFileNames() == null) {
			invoice.setUploadedFileNames(new ArrayList<>());
		}

		List<String> existingFiles = new ArrayList<>();
		for (String fileName : invoice.getUploadedFileNames()) {
			File file = new File(uploadDir, fileName);
			if (file.exists()) {
				existingFiles.add(fileName);
			}
		}
		invoice.setUploadedFileNames(existingFiles);
		return invoice;
	}

	@Override
	public List<ManualInvoice> getAllInvoices(Long adminId) {
		return invoiceRepository.findByAdminId(adminId);
	}

	@Override
	public Page<ManualInvoice> searchInvoices(String keyword, Pageable pageable) {
		if (keyword == null || keyword.trim().isEmpty())
			return invoiceRepository.findAll(pageable);
		keyword = keyword.trim();
		return invoiceRepository.searchInvoices(keyword, pageable);
	}

	@Transactional
	@Override
	public void deleteInvoice(Long id, Long adminId) {

		ManualInvoice invoice = invoiceRepository.findByIdAndAdminId(id, adminId)
				.orElseThrow(() -> new RuntimeException("Invoice not found or unauthorized"));

		// Delete uploaded files
		if (invoice.getUploadedFileNames() != null) {

			for (String fileName : invoice.getUploadedFileNames()) {

				try {

					File file = new File(uploadDir, fileName);

					if (file.exists() && !file.delete()) {
						log.warn("Failed to delete file: {}", fileName);
					}

				} catch (Exception e) {
					log.warn("File delete error for {}: {}", fileName, e.getMessage());
				}
			}
		}

		invoiceRepository.delete(invoice);
	}

	@Override
	@Transactional
	public ManualInvoice updateInvoice(Long id, ManualInvoice request) {

		// 1️⃣ Fetch existing invoice
		ManualInvoice existingInvoice = invoiceRepository.findById(id)
				.orElseThrow(() -> new RuntimeException("Invoice not found with id: " + id));

		// 2️⃣ Clean PO Number
		String poNumber = request.getPoNumber() != null ? request.getPoNumber().trim() : null;

		// 3️⃣ PO Number uniqueness check (only if PO provided)
		if (poNumber != null && !poNumber.isEmpty()) {
			if (invoiceRepository.existsByPoNumberAndIdNot(poNumber, id)) {
				throw new RuntimeException("PO Number already exists");
			}
		}

		// 4️⃣ Detect if customer/vendor has changed
		boolean customerChanged = request.getCustomerVendorId() != null
				&& !request.getCustomerVendorId().equals(existingInvoice.getCustomerVendorId());

		// 5️⃣ Update basic fields
		existingInvoice.setCustomer(request.getCustomer());
		existingInvoice.setCustomerEmail(request.getCustomerEmail());
		existingInvoice.setCustomerPhone(request.getCustomerPhone());
		existingInvoice.setInvoiceDate(request.getInvoiceDate());
		existingInvoice.setDueDate(request.getDueDate());
		existingInvoice.setPaymentTerms(request.getPaymentTerms());
		existingInvoice.setNotes(request.getNotes());
		existingInvoice.setCustomerVendorId(request.getCustomerVendorId());
		existingInvoice.setTax(request.getTax());
		existingInvoice.setCredit(request.getCredit());
		existingInvoice.setBillingAddress(request.getBillingAddress());
		existingInvoice.setShippingAddress(request.getShippingAddress());
		existingInvoice.setSalesRep(request.getSalesRep());
		existingInvoice.setPoNumber(poNumber);
		existingInvoice.setTemplate(request.getTemplate());
		existingInvoice.setTermsAndConditions(request.getTermsAndConditions());
		existingInvoice.setStatus(normalizeStatusForDb(request.getStatus(), existingInvoice.getStatus()));
		existingInvoice.setCurrency(request.getCurrency());

		// 6️⃣ Vendor enrichment via Feign
		if (customerChanged || existingInvoice.getCustomerEmail() == null
				|| existingInvoice.getCustomerEmail().isBlank() || existingInvoice.getCustomerPhone() == null
				|| existingInvoice.getCustomerPhone().isBlank()) {

			if (existingInvoice.getCustomerVendorId() != null) {
				try {
					VendorEnvelope envelope = vendorFeignClient.getVendorById(existingInvoice.getCustomerVendorId());
					VendorDTO vendor = envelope != null ? envelope.getData() : null;

					if (vendor != null) {
						existingInvoice.setCustomer(vendor.getVendorName());
						existingInvoice.setCustomerEmail(vendor.getEmail());
						existingInvoice.setCustomerPhone(vendor.getPhoneNumber());
						existingInvoice.setBillingAddress(vendor.getVendorAddress());
						existingInvoice.setShippingAddress(vendor.getVendorAddress());
					}

				} catch (Exception e) {
					log.warn("Vendor enrichment failed for vendorId {}: {}", existingInvoice.getCustomerVendorId(),
							e.getMessage());
				}
			}
		}

		// 7️⃣ Update invoice items
		existingInvoice.getItems().clear();

		if (request.getItems() != null) {
			for (InvoiceItem item : request.getItems()) {
				item.setId(null);
				item.setManualInvoice(existingInvoice);
				existingInvoice.getItems().add(item);
			}
		}

		// 8️⃣ Update uploaded files
		if (request.getUploadedFileNames() != null) {
			existingInvoice.setUploadedFileNames(request.getUploadedFileNames());
		}

		// 9️⃣ Recalculate totals
		calculateTotalsAndDueDate(existingInvoice);

		// 🔟 Update timestamp
		existingInvoice.setUpdatedAt(LocalDateTime.now());

		return invoiceRepository.save(existingInvoice);
	}

	@Override
	public String storeFile(MultipartFile file) throws IOException {
		if (file.isEmpty()) {
			throw new IOException("Uploaded file is empty");
		}

		Path uploadPath = Paths.get(uploadDir).normalize();
		Files.createDirectories(uploadPath);

		String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
		if (originalFileName.contains("..")) {
			throw new IOException("Invalid filename: " + originalFileName);
		}

		String uniqueFileName = System.currentTimeMillis() + "_" + originalFileName.replaceAll("\\s+", "_");
		Path targetLocation = uploadPath.resolve(uniqueFileName);

		Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

		return uniqueFileName;
	}

	@Override
	public List<String> storeMultipleFiles(MultipartFile[] files) throws IOException {
		List<String> savedFiles = new ArrayList<>();
		if (files == null || files.length == 0)
			throw new IOException("No files provided!");
		for (MultipartFile file : files) {
			if (!file.isEmpty()) {
				String savedFilename = storeFile(file);
				savedFiles.add(savedFilename);
			}
		}
		if (savedFiles.isEmpty())
			throw new IOException("All files were empty!");
		return savedFiles;
	}

	@Transactional
	@Override
	public ManualInvoice updateUploadedFilesOnly(ManualInvoice invoice) {
		return invoiceRepository.save(invoice);
	}

	@Override
	public List<String> getAllTemplates() {
		Path dirPath = Paths.get(uploadDir);
		if (!Files.exists(dirPath))
			return List.of();

		try {
			return Files.list(dirPath).filter(Files::isRegularFile).map(p -> p.getFileName().toString())
					.collect(Collectors.toList());
		} catch (IOException e) {
			return List.of();
		}
	}

	@Override
	public Resource loadFileAsResource(String filename) throws Exception {
		// Ownership first: the caller's own tenant must have an invoice
		// referencing this file. Nothing checked that, so any authenticated
		// caller with a file name got any tenant's invoice attachment.
		if (!invoiceRepository.existsUploadedFileForTenant(filename, SecurityUtils.getCurrentAdminId())) {
			throw new FileNotFoundException("File not found: " + filename);
		}

		java.nio.file.Path base = java.nio.file.Paths.get(uploadDir).toAbsolutePath().normalize();
		java.nio.file.Path filePath = base.resolve(filename).normalize();

		// Containment: this was `new File(uploadDir, filename)` with no check at
		// all, so "../.." escaped the upload directory entirely. The ownership
		// check above happens to refuse a traversing name too, but a boundary
		// that only holds because another check runs first is not a boundary --
		// see the same ordering problem in Invoice-Login (G-45).
		if (!filePath.startsWith(base)) {
			throw new FileNotFoundException("File not found: " + filename);
		}

		File file = filePath.toFile();
		if (!file.exists())
			throw new FileNotFoundException("File not found: " + filename);
		return new UrlResource(file.toURI());
	}

	/** Statuses permitted by the DB check constraint ck_manual_invoices_status. */
	private static final java.util.Set<String> ALLOWED_DB_STATUSES = java.util.Set.of(

			"DRAFT", "PENDING", "RECEIVED", "PARTIALLY_RECEIVED", "PARTIALLY_PAID", "PAID", "OVERDUE", "CANCELLED",
			"EXCESS_RECEIVED", "EXCESS_PAID");

	/**
	 * Converts a UI status (e.g. "Pending", "Partially Paid") to the DB enum form
	 * (UPPERCASE_UNDERSCORE) required by ck_manual_invoices_status. If the
	 * requested value is blank or not a recognized DB status, the existing value is
	 * kept so the update can't violate the constraint.
	 */
//	private String normalizeStatusForDb(String requested, String fallback) {
//		if (requested == null || requested.isBlank()) {
//			return fallback;
//		}
//		String normalized = requested.trim().toUpperCase().replaceAll("\\s+", "_");
//		if (ALLOWED_DB_STATUSES.contains(normalized)) {
//			return normalized;
//		}
//		return fallback != null ? fallback : "PENDING";
//	}

	private String normalizeStatusForDb(String requested, String fallback) {
		if (requested == null || requested.isBlank()) {
			return fallback;
		}

		String normalized = requested.trim().toUpperCase().replaceAll("\\s+", "_");

		if ("SENT".equals(normalized)) {
			normalized = "PENDING";
		}

		return ALLOWED_DB_STATUSES.contains(normalized) ? normalized : (fallback != null ? fallback : "PENDING");
	}

	/**
	 * The vendor an invoice belongs to, resolved by id in preference to name.
	 *
	 * <p>Both save and update used to resolve it as
	 * {@code searchVendors(customer).get(0)}. That call is a LIKE search
	 * (`findByVendorNameContainingIgnoreCaseAndAdminId`), tenant-scoped and
	 * sorted exact-first, so it picked the right vendor while the stored name
	 * still matched one. When it did not — a renamed or deleted vendor — it had
	 * two bad outcomes, both reproduced against a running stack:
	 *
	 * <ul>
	 *   <li>nothing contained the stored name: the update threw
	 *       {@code Vendor not found for customer}, so the invoice could not be
	 *       settled at all;</li>
	 *   <li>something else contained it — "Acme" against a surviving
	 *       "Acme Holdings" — and the settlement silently adopted that vendor.
	 *       Because the update then takes {@code vendorType} from the vendor,
	 *       and the entity normalises status into the matching AR/AP family, a
	 *       payable invoice paid through the Payments screen was stored as a
	 *       <em>receivable</em> marked RECEIVED. HTTP 200, no warning.</li>
	 * </ul>
	 *
	 * <p>The invoice already carries {@code customerVendorId}, a foreign key
	 * that cannot drift when a name changes, so that is what is used. The name
	 * remains a fallback for rows written before the column was populated, but
	 * it now requires an <strong>exact</strong> match: a LIKE hit that is not
	 * the vendor named is not evidence of anything, and silently substituting
	 * one is worse than refusing.
	 *
	 * <p>Both paths stay inside the caller's tenant: the by-id endpoint is
	 * scoped in Customer-Service and the by-name search carries the admin id.
	 *
	 * @return the vendor, or {@code null} when there is nothing to resolve from
	 */
	private VendorDTO resolveVendor(Long vendorId, String customerName) {
		if (vendorId != null) {
			VendorEnvelope envelope = vendorFeignClient.getVendorById(vendorId);
			VendorDTO vendor = envelope != null ? envelope.getData() : null;
			if (vendor == null || vendor.getVendorId() == null) {
				throw new RuntimeException("Vendor not found for id: " + vendorId);
			}
			return vendor;
		}

		if (customerName == null || customerName.isBlank()) {
			return null;
		}

		String wanted = customerName.trim();
		return vendorFeignClient.searchVendors(wanted).stream()
				.filter(v -> v.getVendorName() != null && wanted.equalsIgnoreCase(v.getVendorName().trim()))
				.findFirst()
				.orElseThrow(() -> new RuntimeException("Vendor not found for customer: " + customerName));
	}

	@Override
	@Transactional
	public ManualInvoice updateManualInvoice(Long id, ManualInvoice request) {

		// Scoped, as above. Unscoped, this let one tenant rewrite another's
		// invoice -- amounts, status, dates.
		ManualInvoice invoice = invoiceRepository.findByIdAndAdminId(id, SecurityUtils.getCurrentAdminId())
				.orElseThrow(() -> new RuntimeException("Invoice not found with id: " + id));
		String poNumber = request.getPoNumber();

		poNumber = (poNumber != null && !poNumber.trim().isEmpty()) ? poNumber.trim() : null;

		if (poNumber != null
				&& invoiceRepository.existsByPoNumberAndConsultantIdNot(poNumber, request.getConsultantId())) {

			throw new RuntimeException("PO Number already used by another consultant");
		}

		// ===== Basic Fields =====
		invoice.setCustomer(request.getCustomer());
		invoice.setCustomerEmail(request.getCustomerEmail());
		invoice.setCustomerPhone(request.getCustomerPhone());

		invoice.setConsultantId(request.getConsultantId());
		invoice.setConsultantName(request.getConsultantName());
		invoice.setAdminId(request.getAdminId());

		invoice.setInvoiceDate(request.getInvoiceDate());
		invoice.setDueDate(request.getDueDate());
		invoice.setPaymentTerms(request.getPaymentTerms());

		invoice.setNotes(request.getNotes());
		invoice.setTax(request.getTax());
		invoice.setCredit(request.getCredit());

		invoice.setBillingAddress(request.getBillingAddress());
		invoice.setShippingAddress(request.getShippingAddress());

		invoice.setSalesRep(request.getSalesRep());
		invoice.setPoNumber(request.getPoNumber());
		invoice.setTemplate(request.getTemplate());
		invoice.setTermsAndConditions(request.getTermsAndConditions());
		invoice.setStatus(normalizeStatusForDb(request.getStatus(), invoice.getStatus()));
		invoice.setCurrency(request.getCurrency());

		// ===== New Fields =====
		invoice.setUploadedFileNames(request.getUploadedFileNames());
		invoice.setIssuedBy(request.getIssuedBy());
		invoice.setPaymentAmount(request.getPaymentAmount());
		invoice.setPaymentDate(request.getPaymentDate());
		invoice.setDueAmount(request.getDueAmount());
		invoice.setRemarks(request.getRemarks());
		invoice.setPeriodend(request.getPeriodend());
		invoice.setPeriodStart(request.getPeriodStart());
		invoice.setDiscount(request.getDiscount());
		invoice.setTotalHours(request.getTotalHours());
		invoice.setSubtotal(request.getSubtotal());
		invoice.setTotal(request.getTotal());
		invoice.setAmountDue(request.getAmountDue());
		invoice.setPaidDate(request.getPaidDate());
		invoice.setPaidAmount(request.getPaidAmount());
		invoice.setVendorType(request.getVendorType());
		invoice.setPeriod(request.getPeriod());
		invoice.setEmploymentId(request.getEmploymentId());
		// ===== Update Items =====
		invoice.clearItems();

		if (request.getItems() != null) {
			for (InvoiceItem item : request.getItems()) {
				invoice.addItem(item);
			}
		}

		if (request.getCustomerVendorId() != null
				|| (request.getCustomer() != null && !request.getCustomer().isBlank())) {

			VendorDTO vendor = resolveVendor(request.getCustomerVendorId(), request.getCustomer());

			if (vendor != null) {

				invoice.setCustomerVendorId(vendor.getVendorId());
				invoice.setVendorType(vendor.getVendorType());
				invoice.setCustomer(vendor.getVendorName());

				// Vendor Type Fix
				if (vendor.getVendorType() != null && !vendor.getVendorType().isBlank()) {
					invoice.setVendorType(vendor.getVendorType());
				} else if (request.getVendorType() != null && !request.getVendorType().isBlank()) {
					invoice.setVendorType(request.getVendorType());
				}

				if (request.getCustomerEmail() != null && !request.getCustomerEmail().isBlank()) {
					invoice.setCustomerEmail(request.getCustomerEmail());
				} else {
					invoice.setCustomerEmail(vendor.getEmail());
				}

				if (request.getCustomerPhone() != null && !request.getCustomerPhone().isBlank()) {
					invoice.setCustomerPhone(request.getCustomerPhone());
				} else {
					invoice.setCustomerPhone(vendor.getPhoneNumber());
				}

			} else {
				throw new RuntimeException("Vendor not found for customer: " + request.getCustomer());
			}
		}

		// ===== Recalculate Totals =====
		calculateTotalsAndDueDate(invoice);

		invoice.setUpdatedAt(LocalDateTime.now());
		return invoiceRepository.save(invoice);
	}

	@Override
	public Map<String, Long> getInvoiceCounts() {
		Map<String, Long> counts = new HashMap<>();
		counts.put("total", invoiceRepository.getTotalInvoiceCount());
		counts.put("paid", invoiceRepository.getPaidInvoiceCount());
		counts.put("pending", invoiceRepository.getPendingInvoiceCount());
		counts.put("OverDue", invoiceRepository.getOverdueInvoiceCount());
		return counts;
	}

	@Override
	public Long getTodayOverdueCount() {
		return invoiceRepository.countOverdueInvoicesForToday(LocalDate.now());
	}

	@Override
	public List<ManualInvoice> getTodayOverdueInvoices() {
		return invoiceRepository.findOverdueInvoicesForToday(LocalDate.now());
	}

	@Override
	public Page<ManualInvoice> getAllInvoicesWithPaginationAndSearch(int page, int size, String sortField,
			String sortDir, String keyword, Long adminId) {

		if (!"asc".equalsIgnoreCase(sortDir) && !"desc".equalsIgnoreCase(sortDir)) {
			sortDir = "asc";
		}
		if (sortField == null || sortField.isBlank()) {
			sortField = "createdAt";
		}

		Sort sort = "desc".equalsIgnoreCase(sortDir) ? Sort.by(sortField).descending() : Sort.by(sortField).ascending();
		Pageable pageable = PageRequest.of(page, size, sort);

		if (keyword == null || keyword.trim().isEmpty()) {
			keyword = "";
		} else {
			keyword = "%" + keyword.trim().toLowerCase() + "%";
		}

		return invoiceRepository.searchInvoices(keyword, adminId, pageable);
	}

	@Override
	public void sendInvoiceMail(String invoiceNumber, Long adminId) {

		log.info("Sending invoice mail for invoiceNumber: {}", invoiceNumber);

		ManualInvoice invoice = invoiceRepository.findByInvoiceNumberAndAdminId(invoiceNumber, adminId)
				.orElseThrow(() -> new RuntimeException("Invoice not found or unauthorized access"));

		ConsultantDTO consultant = consultantFeignClient.getConsultant(invoice.getConsultantId());

		String email = consultant.getInvoiceMail();

		List<String> emails = Arrays.stream(email.split(",")).map(String::trim).filter(e -> !e.isEmpty()).toList();
		invoiceEmailService.sendInvoiceMail(emails, invoice, buildCcList(adminId));
		invoice.setStatus("PENDING");
		invoice.setUpdatedAt(java.time.LocalDateTime.now());
		invoiceRepository.save(invoice);
	}

	@Override
	public List<ManualInvoice> getInvoicesByConsultantId(Long consultantId) {
		return invoiceRepository.findByConsultantIdAndAdminId(consultantId,
				SecurityUtils.getCurrentAdminId());
	}

	@Override
	public List<ManualInvoice> getPendingInvoicesByAdmin(Long adminId) {

		List<String> statuses = List.of("Pending", "partially received");

		return invoiceRepository.findByAdminIdAndStatusInIgnoreCase(adminId, statuses);
	}

	@Override
	public Page<ManualInvoice> getPendingInvoicesByAdmin(InvoiceSortingRequestDTO requestDTO) {

		String search = requestDTO.getSearch();
		String sortBy = requestDTO.getSortField();
		String sortDir = requestDTO.getSortOrder();
		Integer pageNo = requestDTO.getPageNumber();
		Integer pageSize = requestDTO.getPageSize();
		Long adminId = requestDTO.getAdminId();

		// ✅ Default handling
		if (pageNo == null || pageNo < 0)
			pageNo = 0;
		int zeroBasedPageNo = (pageNo > 0) ? pageNo - 1 : pageNo;

		if (pageSize == null || pageSize <= 0)
			pageSize = 10;

		sortBy = resolveSortField(sortBy);
		if (sortDir == null || sortDir.trim().isEmpty())
			sortDir = "desc";

		Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;

		Pageable pageable = PageRequest.of(zeroBasedPageNo, pageSize, Sort.by(direction, sortBy));

		List<String> statuses = List.of("pending", "partially received");

		boolean hasSearch = search != null && !search.trim().isEmpty();

		if (hasSearch) {
			return invoiceRepository.searchInvoicesByAdmin(adminId, statuses, search.toLowerCase().trim(), pageable);
		}

		return invoiceRepository.findByAdminIdAndStatusInIgnoreCase(adminId, statuses, pageable);
	}

	@Override
	public Page<ManualInvoice> getInvoicesByAdminAndVendorType(InvoiceSortingRequestDTO requestDTO) {

		String search = requestDTO.getSearch();
		String sortBy = requestDTO.getSortField();
		String sortDir = requestDTO.getSortOrder();
		Integer pageNo = requestDTO.getPageNumber();
		Integer pageSize = requestDTO.getPageSize();
		Long adminId = requestDTO.getAdminId();
		String vendorType = requestDTO.getVendorType();

		// ✅ Default vendorType (only payable if not passed)
		if (vendorType == null || vendorType.trim().isEmpty()) {
			vendorType = "payable";
		}

		if (pageNo == null || pageNo < 0)
			pageNo = 0;
		int zeroBasedPageNo = (pageNo > 0) ? pageNo - 1 : pageNo;

		if (pageSize == null || pageSize <= 0)
			pageSize = 10;

		sortBy = resolveSortField(sortBy);
		if (sortDir == null || sortDir.trim().isEmpty())
			sortDir = "desc";

		Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;

		Pageable pageable = PageRequest.of(zeroBasedPageNo, pageSize, Sort.by(direction, sortBy));

		boolean hasSearch = search != null && !search.trim().isEmpty();

		if (hasSearch) {
			return invoiceRepository.searchInvoicesByAdminAndVendorType(adminId, vendorType.toLowerCase().trim(),
					search.toLowerCase().trim(), pageable);
		}

		return invoiceRepository.findByAdminIdAndVendorTypeIgnoreCase(adminId, vendorType, pageable);
	}

	@Override
	public void sendInvoiceMails(String invoiceNumber, Long adminId) {
		try {
			ManualInvoice invoice = invoiceRepository.findByInvoiceNumberAndAdminId(invoiceNumber, adminId)
					.orElseThrow(() -> new RuntimeException("Invoice not found or unauthorized access"));
			String emailsString = invoice.getCustomerEmail();
			if (emailsString == null || emailsString.trim().isEmpty()) {
				throw new RuntimeException("No email addresses found");
			}
			List<String> emails = Arrays.stream(emailsString.split(",")).map(String::trim)
					.filter(email -> !email.isEmpty()).toList();
			if (emails.isEmpty()) {
				throw new RuntimeException("No valid email addresses found");
			}
			invoiceEmailService.sendInvoiceMail(emails, invoice, buildCcList(adminId));
			// Must match ck_manual_invoices_status (uppercase enum), not title case.
			invoice.setStatus("PENDING");
			invoiceRepository.save(invoice);
		} catch (Exception e) {
			throw new RuntimeException("Failed to send invoice mail: " + e.getMessage());
		}
	}

	@Override
	public Page<ManualInvoice> getInvoiceByAdminAndVendorType(InvoiceSortingRequestDTO requestDTO) {
		String search = requestDTO.getSearch();
		String sortBy = requestDTO.getSortField();
		String sortDir = requestDTO.getSortOrder();
		Integer pageNo = requestDTO.getPageNumber();
		Integer pageSize = requestDTO.getPageSize();
		Long adminId = requestDTO.getAdminId();
		// ✅ FORCE RECEIVABLE ONLY (main change)
		String vendorType = "Receivable";
		// ✅ Pagination
		if (pageNo == null || pageNo < 0)
			pageNo = 0;
		int zeroBasedPageNo = (pageNo > 0) ? pageNo - 1 : pageNo;
		if (pageSize == null || pageSize <= 0)
			pageSize = 10;
		sortBy = resolveSortField(sortBy);
		if (sortDir == null || sortDir.trim().isEmpty())
			sortDir = "desc";
		Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;

		Pageable pageable = PageRequest.of(zeroBasedPageNo, pageSize, Sort.by(direction, sortBy));

		boolean hasSearch = search != null && !search.trim().isEmpty();
		if (hasSearch) {
			return invoiceRepository.searchInvoiceByAdminAndVendorType(adminId, vendorType, search.trim().toLowerCase(),
					pageable);
		}
		return invoiceRepository.findByAdminIdAndVendorTypeIgnoreCase(adminId, vendorType, pageable);
	}

	@Override
	public Page<ManualInvoice> getInvoicesByAdminAndVendorTypestatus(InvoiceSortingRequestDTO requestDTO) {

		String search = requestDTO.getSearch();
		String sortBy = requestDTO.getSortField();
		String sortDir = requestDTO.getSortOrder();
		Integer pageNo = requestDTO.getPageNumber();
		Integer pageSize = requestDTO.getPageSize();
		Long adminId = requestDTO.getAdminId();

		// ✅ FIX: treat empty as null
		String vendorType = (requestDTO.getVendorType() != null && !requestDTO.getVendorType().trim().isEmpty())
				? requestDTO.getVendorType().trim()
				: null;

		String status = (requestDTO.getStatus() != null && !requestDTO.getStatus().trim().isEmpty())
				? requestDTO.getStatus().trim()
				: null;

		// ✅ Pagination
		if (pageNo == null || pageNo < 0)
			pageNo = 0;
		int zeroBasedPageNo = (pageNo > 0) ? pageNo - 1 : pageNo;

		if (pageSize == null || pageSize <= 0)
			pageSize = 10;

		sortBy = resolveSortField(sortBy);
		if (sortDir == null || sortDir.trim().isEmpty())
			sortDir = "desc";

		Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
		Pageable pageable = PageRequest.of(zeroBasedPageNo, pageSize, Sort.by(direction, sortBy));

		boolean hasSearch = search != null && !search.trim().isEmpty();
		boolean hasVendorType = vendorType != null;
		boolean hasStatus = status != null;

		if (!hasVendorType && !hasStatus && !hasSearch) {
			return invoiceRepository.findByAdminId(adminId, pageable);
		}

		// ✅ CASE 2: vendorType + status + search
		if (hasVendorType && hasStatus && hasSearch) {
			return invoiceRepository.searchInvoicesByAdminVendorTypeAndStatus(adminId, vendorType.toLowerCase(),
					status.toLowerCase(), search.toLowerCase(), pageable);
		}

		// ✅ CASE 3: vendorType + status
		if (hasVendorType && hasStatus) {
			return invoiceRepository.findByAdminIdAndVendorTypeAndStatusIgnoreCase(adminId, vendorType, status,
					pageable);
		}

		// ✅ CASE 4: vendorType only
		if (hasVendorType) {
			return invoiceRepository.findByAdminIdAndVendorTypeIgnoreCase(adminId, vendorType, pageable);
		}

		// ✅ CASE 5: status only
		if (hasStatus) {
			return invoiceRepository.findByAdminIdAndStatusIgnoreCase(adminId, status, pageable);
		}

		// ✅ CASE 6: search only
		if (hasSearch) {
			return invoiceRepository.searchInvoicesByAdminOnly(adminId, search.toLowerCase(), pageable);
		}

		return invoiceRepository.findByAdminId(adminId, pageable);
	}

	/**
	 * Maps AR receivable status labels to the status actually stored on the
	 * invoice. "Received" → "Paid", "Partially Received" → "Partially Paid",
	 * "Excess Received" → "Excess Paid". Other values pass through unchanged. The
	 * downstream queries compare with LOWER(...), so the returned casing doesn't
	 * matter.
	 */
	private String mapReceivableStatusToStored(String status) {
		// Statuses are stored exactly as the frontend sends them (title case, e.g.
		// "Partially Received"), and AR uses received-side labels while AP uses
		// paid-side.
		// No folding/format change — the query compares case-insensitively.
		if (status == null || status.isBlank()) {
			return null;
		}
		return status.trim();
	}

	@Override
	public Page<ManualInvoice> getInvoiceByAdminAndVendorTypereceivablestatus(InvoiceSortingRequestDTO requestDTO) {
		String search = requestDTO.getSearch();
		String sortBy = requestDTO.getSortField();
		String sortDir = requestDTO.getSortOrder();
		Integer pageNo = requestDTO.getPageNumber();
		Integer pageSize = requestDTO.getPageSize();
		Long adminId = requestDTO.getAdminId();

		// ✅ ALWAYS RECEIVABLE
		String vendorType = "receivable";

		// ✅ FIX: treat empty status as null
		String status = (requestDTO.getStatus() != null && !requestDTO.getStatus().trim().isEmpty())
				? requestDTO.getStatus().trim()
				: null;

		// Receivable invoices are stored with the payment statuses (PAID /
		// PARTIALLY_PAID),
		// while the AR UI labels them "Received" / "Partially Received". Translate the
		// UI
		// label to the stored status (and DB enum format) so the tab matches rows.
		status = mapReceivableStatusToStored(status);

		// ✅ Pagination
		if (pageNo == null || pageNo < 0)
			pageNo = 0;
		int zeroBasedPageNo = (pageNo > 0) ? pageNo - 1 : pageNo;

		if (pageSize == null || pageSize <= 0)
			pageSize = 10;

		// ✅ Sorting
		if (sortBy == null || sortBy.trim().isEmpty())
			sortBy = "invoiceDate";
		if (sortDir == null || sortDir.trim().isEmpty())
			sortDir = "desc";

		Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;

		Pageable pageable = PageRequest.of(zeroBasedPageNo, pageSize, Sort.by(direction, sortBy));

		boolean hasSearch = search != null && !search.trim().isEmpty();
		boolean hasStatus = status != null;

		// ✅ CASE 1: status + search
		if (hasStatus && hasSearch) {
			return invoiceRepository.searchReceivableByStatusAndSearch(adminId, vendorType, status,
					search.toLowerCase(), pageable);
		}

		// ✅ CASE 2: status only
		if (hasStatus) {
			return invoiceRepository.findReceivableByStatus(adminId, vendorType, status, pageable);
		}

		// ✅ CASE 4: BOTH EMPTY → RETURN ALL RECEIVABLE DATA 🔥
		return invoiceRepository.findByAdminIdAndVendorTypeIgnoreCase(adminId, vendorType, pageable);
	}

	@Override
	public Page<ManualInvoice> getInvoicesByAdminAndVendorTypestatusinvoicestatus(InvoiceSortingRequestDTO requestDTO) {
		String search = requestDTO.getSearch();
		String sortBy = requestDTO.getSortField();
		String sortDir = requestDTO.getSortOrder();
		Integer pageNo = requestDTO.getPageNumber();
		Integer pageSize = requestDTO.getPageSize();
		Long adminId = requestDTO.getAdminId();
		String vendorType = requestDTO.getVendorType();
		String status = requestDTO.getStatus(); // ✅ NEW

		// ✅ Default vendorType
		if (vendorType == null || vendorType.trim().isEmpty()) {
			vendorType = "payable";
		}

		// ✅ Pagination
		if (pageNo == null || pageNo < 0)
			pageNo = 0;
		int zeroBasedPageNo = (pageNo > 0) ? pageNo - 1 : pageNo;

		if (pageSize == null || pageSize <= 0)
			pageSize = 10;

		sortBy = resolveSortField(sortBy);
		if (sortDir == null || sortDir.trim().isEmpty())
			sortDir = "desc";

		Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
		Pageable pageable = PageRequest.of(zeroBasedPageNo, pageSize, Sort.by(direction, sortBy));

		boolean hasSearch = search != null && !search.trim().isEmpty();
		boolean hasStatus = status != null && !status.trim().isEmpty();

		// ✅ FINAL FILTER LOGIC

		if (hasSearch && hasStatus) {
			return invoiceRepository.searchInvoicesByAdminVendorTypeAndStatus(adminId, vendorType.toLowerCase().trim(),
					status.toLowerCase().trim(), search.toLowerCase().trim(), pageable);
		}

		if (hasStatus) {
			return invoiceRepository.findByAdminIdAndVendorTypeAndStatusIgnoreCase(adminId, vendorType, status,
					pageable);
		}

		return invoiceRepository.findByAdminIdAndVendorTypeIgnoreCase(adminId, vendorType, pageable);
	}

	@Override
	public int getInvoicePage(Long invoiceId, String vendorType, String status, int pageSize, Long adminId) {
		long countBefore = invoiceRepository.countBeforeIdWithFilters(adminId, vendorType, status, invoiceId);
		return (int) (countBefore / pageSize) + 1;
	}

	private String resolveInvoicePrefix(Long adminId) {
		try {
			String email = SecurityUtils.getCurrentUserEmail();
			if (email != null) {
				return adminSettingsRepository.findByEmailIgnoreCase(email).map(AdminSettings::getInvoicePrefix)
						.filter(p -> p != null && !p.trim().isEmpty())
						.map(p -> p.trim().endsWith("-") ? p.trim() : p.trim() + "-").orElse("INV-");
			}
		} catch (Exception ignored) {
		}
		return "INV-";
	}

	private List<String> buildCcList(Long adminId) {
		return manageUserRepository.findAdminAndHrByAdminId(adminId).stream().map(u -> u.getPrimaryEmail())
				.filter(e -> e != null && !e.isBlank()).distinct().collect(java.util.stream.Collectors.toList());
	}

	private String resolveSortField(String sortBy) {
		if (sortBy == null || sortBy.trim().isEmpty()) {
			return "invoiceDate";
		}
		switch (sortBy.toLowerCase()) {
		case "consultantname":
			return "consultantName";
		case "customer":
			return "customer";
		case "invoicenumber":
			return "invoiceNumber";
		case "invoicedate":
			return "invoiceDate";
		case "duedate":
			return "dueDate";
		case "paymentdate":
			return "paymentDate";
		case "paymentamount":
			return "paymentAmount";
		case "paidamount":
			return "paidAmount";
		case "paiddate":
			return "paidDate";
		case "vendortype":
			return "vendorType";
		case "status":
			return "status";
		case "total":
			return "total";
		case "totalhours":
			return "totalHours";
		default:
			return "invoiceDate";
		}
	}

	@Override
	public Map<String, Object> getInvoiceStatusCounts(Long adminId) {
		Object[] result = (Object[]) invoiceRepository.getInvoiceStatusCounts(adminId);
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("pendingCount", ((Number) result[1]).intValue());
		data.put("adminId", adminId);
		data.put("paidCount", ((Number) result[0]).intValue());
		data.put("receivedCount", ((Number) result[2]).intValue());
		data.put("totalCount", ((Number) result[3]).intValue());
		return data;
	}

	@Override
	public ResponseEntity<Boolean> checkEmploymentInvoices(Long employmentId) {

		boolean exists = invoiceRepository.existsByEmploymentId(employmentId);

		return ResponseEntity.ok(exists);
	}

}
