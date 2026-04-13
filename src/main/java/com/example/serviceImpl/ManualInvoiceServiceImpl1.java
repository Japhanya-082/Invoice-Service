package com.example.serviceImpl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.example.DTO.ConsultantDTO;
import com.example.DTO.InvoiceSortingRequestDTO;
import com.example.DTO.VendorDTO;
import com.example.client.ConsultantFeignClient;
import com.example.client.VendorFeignClient;
import com.example.entity.InvoiceItem;
import com.example.entity.ManualInvoice;
import com.example.repository.ManualInvoiceRepository;
import com.example.service.ManualInvoiceService1;

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
	private VendorFeignClient vendorFeignClient;

	@Autowired
	private ConsultantFeignClient consultantFeignClient;

	@Autowired
	private InvoiceEmailService invoiceEmailService;

	@Override
	@Transactional
	public ManualInvoice saveInvoice(ManualInvoice request){

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
		    if (poNumber != null &&
		        invoiceRepository.existsByPoNumberAndConsultantIdNotAndIdNot(
		                poNumber,
		                request.getConsultantId(),
		                invoice.getId())) {

		        throw new RuntimeException("PO Number already used by another consultant");
		    }

		    invoice.clearItems();

		} else {

		    // CREATE validation
		    if (poNumber != null &&
		        invoiceRepository.existsByPoNumberAndConsultantIdNot(
		                poNumber,
		                request.getConsultantId())) {

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

		        if (request.getAdminId() != null && !consultant.getAdminId().equals(request.getAdminId())) {
		            throw new RuntimeException("Unauthorized consultant access");
		        }

		        invoice.setConsultantId(consultant.getId());
		        invoice.setConsultantName(consultant.getFullName());
		        invoice.setAdminId(consultant.getAdminId());

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

		// ===== Vendor Lookup =====
		if (request.getCustomer() != null && !request.getCustomer().isBlank()) {

			List<VendorDTO> vendors = vendorFeignClient.searchVendors(request.getCustomer());

			if (!vendors.isEmpty()) {

				VendorDTO vendor = vendors.get(0);

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

			        invoice.setInvoiceNumber("INV-" + year + consultant + invoiceId);
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

		double subtotal = 0.0;
		double totalHours = 0.0;

		if (invoice.getItems() != null) {
			for (InvoiceItem item : invoice.getItems()) {

				double hours = item.getHours() != null ? item.getHours() : 0.0;
				double rate = item.getRate() != null ? item.getRate() : 0.0;

				double amount = hours * rate;
				item.setAmount(amount);

				subtotal += amount;
				totalHours += hours;
			}
		}

		invoice.setSubtotal(subtotal);
		invoice.setTotalHours(totalHours);

		double tax = invoice.getTax() != null ? invoice.getTax() : 0.0;
		invoice.setTotal(subtotal + tax);

		double credit = invoice.getCredit() != null ? invoice.getCredit() : 0.0;
		invoice.setAmountDue(invoice.getTotal() - credit);

		// Calculate due date
//            if (invoice.getInvoiceDate() != null && invoice.getPaymentTerms() != null) {
//                try {
//                    int days = Integer.parseInt(invoice.getPaymentTerms().replaceAll("[^0-9]", ""));
//                    invoice.setDueDate(invoice.getInvoiceDate().plusDays(days));
//                } catch (Exception e) {
//                    invoice.setDueDate(invoice.getInvoiceDate().plusDays(30));
//                }
//            }
	}

	// ✅ New method: fetch by invoiceNumber
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
		ManualInvoice invoice = invoiceRepository.findById(id)
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
						System.err.println("⚠️ Failed to delete file: " + fileName);
					}

				} catch (Exception e) {
					System.err.println("⚠️ File delete error: " + e.getMessage());
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
		existingInvoice.setStatus(request.getStatus());
		existingInvoice.setCurrency(request.getCurrency());

		// 6️⃣ Vendor enrichment via Feign
		if (customerChanged || existingInvoice.getCustomerEmail() == null
				|| existingInvoice.getCustomerEmail().isBlank() || existingInvoice.getCustomerPhone() == null
				|| existingInvoice.getCustomerPhone().isBlank()) {

			if (existingInvoice.getCustomerVendorId() != null) {
				try {
					VendorDTO vendor = vendorFeignClient.getVendorById(existingInvoice.getCustomerVendorId());

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
		File file = new File(uploadDir, filename);
		if (!file.exists())
			throw new FileNotFoundException("File not found: " + filename);
		return new UrlResource(file.toURI());
	}

	// commented by vasim
//	public Page<ManualInvoice> getAllInvoicesWithPaginationAndSearch(int page, int size, String sortField,
//			String sortDir, String keyword) {
//
//		// Fallback safety
//		if (!"asc".equalsIgnoreCase(sortDir) && !"desc".equalsIgnoreCase(sortDir)) {
//			sortDir = "asc";
//		}
//
//		Sort sort = "desc".equalsIgnoreCase(sortDir) ? Sort.by(sortField).descending() : Sort.by(sortField).ascending();
//
//		Pageable pageable = PageRequest.of(page, size, sort);
//
//		Page<ManualInvoice> invoicePage = invoiceRepository.searchInvoices(keyword, pageable);
//
//		// Enrich invoice response
//		invoicePage.getContent().forEach(this::enrichFromVendorService);
//
//		return invoicePage;
//	}

	private void enrichFromVendorService(ManualInvoice invoice) {

		// Skip if customer name missing
		if (!StringUtils.hasText(invoice.getCustomer())) {
			return;
		}

		List<VendorDTO> vendors = vendorFeignClient.searchVendors(invoice.getCustomer());

		if (vendors == null || vendors.isEmpty()) {
			return;
		}

		VendorDTO vendor = vendors.get(0);

		// ✅ Set billing address if missing
		if (invoice.getBillingAddress() == null || invoice.getBillingAddress().getStreet() == null) {

			invoice.setBillingAddress(vendor.getVendorAddress());
		}

		// ✅ Sync contact info
		invoice.setCustomerEmail(vendor.getEmail());
		invoice.setCustomerPhone(vendor.getPhoneNumber());
	}

	@Override
	@Transactional
	public ManualInvoice updateManualInvoice(Long id, ManualInvoice request) {

	    ManualInvoice invoice = invoiceRepository.findById(id)
	            .orElseThrow(() -> new RuntimeException("Invoice not found with id: " + id));
	    String poNumber = request.getPoNumber();

	    poNumber = (poNumber != null && !poNumber.trim().isEmpty()) ? poNumber.trim() : null;

	    if (poNumber != null &&
	        invoiceRepository.existsByPoNumberAndConsultantIdNot(
	            poNumber, request.getConsultantId())) {

	        throw new RuntimeException("PO Number already used by another consultant");
	    }
	    
	    // ===== Basic Fields =====
	    invoice.setCustomer(request.getCustomer());
		//invoice.setCustomerVendorId(request.getCustomerVendorId());

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
	    invoice.setStatus(request.getStatus());
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
	    // ===== Update Items =====
	    invoice.clearItems();

	    if (request.getItems() != null) {
	        for (InvoiceItem item : request.getItems()) {
	            invoice.addItem(item);
	        }
	    }
	    
	  //Bhargav 21-03-26

		if (request.getCustomer() != null && !request.getCustomer().isBlank()) {

			List<VendorDTO> vendors = vendorFeignClient.searchVendors(request.getCustomer());

			if (!vendors.isEmpty()) {

				VendorDTO vendor = vendors.get(0);

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
//Bhargav 21-03-26
		
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

	// vasim/03/03
	@Override
	public Page<ManualInvoice> getAllInvoicesWithPaginationAndSearch(int page, int size, String sortField,
	        String sortDir, String keyword, Long adminId) {

	    // ✅ Validate sort direction
	    if (!"asc".equalsIgnoreCase(sortDir) && !"desc".equalsIgnoreCase(sortDir)) {
	        sortDir = "asc";
	    }

	    // ✅ Default sort field
	    if (sortField == null || sortField.isBlank()) {
	        sortField = "createdAt";
	    }

	    // ✅ Sorting
	    Sort sort = "desc".equalsIgnoreCase(sortDir)
	            ? Sort.by(sortField).descending()
	            : Sort.by(sortField).ascending();

	    Pageable pageable = PageRequest.of(page, size, sort);

	    // ✅ FIX: Avoid NULL (PostgreSQL issue)
	    if (keyword == null || keyword.trim().isEmpty()) {
	        keyword = "";   // 🔥 IMPORTANT FIX
	    } else {
	        keyword = "%" + keyword.trim().toLowerCase() + "%";
	    }

	    // 🔐 ADMIN FILTER
	    Page<ManualInvoice> invoicePage = invoiceRepository.searchInvoices(keyword, adminId, pageable);

	    // ✅ Enrich data
	    invoicePage.getContent().forEach(this::enrichFromVendorService);

	    return invoicePage;
	}
	
//11-04-26
//	public void sendInvoiceMail(String invoiceNumber, Long adminId) {
//
//		System.out.println("Sending invoice mail to: " + invoiceNumber);
//
//		ManualInvoice invoice = invoiceRepository.findByInvoiceNumberAndAdminId(invoiceNumber, adminId)
//				.orElseThrow(() -> new RuntimeException("Invoice not found or unauthorized access"));
//
//		ConsultantDTO consultant = consultantFeignClient.getConsultant(invoice.getConsultantId());
//
//		String email = consultant.getInvoiceMail();
//
//		invoiceEmailService.sendInvoiceMail(email, invoice);
//	}
	
	public void sendInvoiceMail(String invoiceNumber, Long adminId) {

	    System.out.println("Sending invoice mail to: " + invoiceNumber);

	    ManualInvoice invoice = invoiceRepository
	            .findByInvoiceNumberAndAdminId(invoiceNumber, adminId)
	            .orElseThrow(() -> new RuntimeException("Invoice not found or unauthorized access"));

	    ConsultantDTO consultant = consultantFeignClient.getConsultant(invoice.getConsultantId());

	    String email = consultant.getInvoiceMail();

	    // ✅ Convert String → List
	    List<String> emails = Arrays.stream(email.split(","))
	            .map(String::trim)
	            .filter(e -> !e.isEmpty())
	            .toList();

	    // ✅ Now correct
	    invoiceEmailService.sendInvoiceMail(emails, invoice);
	}
	
	
	// Bhargav 17-03-26
	@Override
	public List<ManualInvoice> getInvoicesByConsultantId(Long consultantId) {
		return invoiceRepository.findByConsultantId(consultantId);
	}
	// Bhargav 17-03-26

	// Bhargav 20-03-26 
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
	    if (pageNo == null || pageNo < 0) pageNo = 0;
	    int zeroBasedPageNo = (pageNo > 0) ? pageNo - 1 : pageNo;

	    if (pageSize == null || pageSize <= 0) pageSize = 10;

	    if (sortBy == null || sortBy.trim().isEmpty()) sortBy = "invoiceDate";
	    if (sortDir == null || sortDir.trim().isEmpty()) sortDir = "desc";

	    // ✅ Mapping (same as your style)
	    switch (sortBy.toLowerCase()) {
	        case "consultantname": sortBy = "consultantName"; break;
	        case "customer": sortBy = "customer"; break;
	        case "invoicenumber": sortBy = "invoiceNumber"; break;
	        case "invoicedate": sortBy = "invoiceDate"; break;
	        case "duedate": sortBy = "dueDate"; break;
	        case "paymentdate": sortBy = "paymentDate"; break;
	        case "paymentamount": sortBy = "paymentAmount"; break;
	        case "status": sortBy = "status"; break;
	        case "totalhours": sortBy = "totalHours"; break;
	        case "total": sortBy = "total"; break;
	        default: sortBy = "invoiceDate";
	    }

	    Sort.Direction direction = sortDir.equalsIgnoreCase("desc")
	            ? Sort.Direction.DESC
	            : Sort.Direction.ASC;

	    Pageable pageable = PageRequest.of(zeroBasedPageNo, pageSize, Sort.by(direction, sortBy));

	    // ✅ FIXED STATUS (case safe)
	    List<String> statuses = List.of("pending", "partially received");

	    boolean hasSearch = search != null && !search.trim().isEmpty();

	    if (hasSearch) {
	        return invoiceRepository.searchInvoicesByAdmin(
	                adminId,
	                statuses,
	                search.toLowerCase().trim(), // ✅ important
	                pageable
	        );
	    }

	    return invoiceRepository.findByAdminIdAndStatusInIgnoreCase(adminId, statuses, pageable);
	}

// Bhargav 20-03-26 
	
	@Override
	public Page<ManualInvoice> getInvoicesByAdminAndVendorType(
	        InvoiceSortingRequestDTO requestDTO) {

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

	    if (pageNo == null || pageNo < 0) pageNo = 0;
	    int zeroBasedPageNo = (pageNo > 0) ? pageNo - 1 : pageNo;

	    if (pageSize == null || pageSize <= 0) pageSize = 10;

	    if (sortBy == null || sortBy.trim().isEmpty()) sortBy = "invoiceDate";
	    if (sortDir == null || sortDir.trim().isEmpty()) sortDir = "desc";

	    switch (sortBy.toLowerCase()) {
	        case "consultantname": sortBy = "consultantName"; break;
	        case "customer": sortBy = "customer"; break;
	        case "invoicenumber": sortBy = "invoiceNumber"; break;
	        case "invoicedate": sortBy = "invoiceDate"; break;
	        case "duedate": sortBy = "dueDate"; break;
	        case "paymentdate": sortBy = "paymentDate"; break;
	        case "paymentamount": sortBy = "paymentAmount"; break;
	        case "paidAmount": sortBy = "paidAmount"; break;
	        case "paidDate": sortBy = "paidDate"; break;
	        case "vendorType": sortBy = "vendorType"; break;
	        case "status": sortBy = "status"; break;
	        case "total": sortBy = "total"; break;
	        default: sortBy = "invoiceDate";
	    }

	    Sort.Direction direction = sortDir.equalsIgnoreCase("desc")
	            ? Sort.Direction.DESC
	            : Sort.Direction.ASC;

	    Pageable pageable = PageRequest.of(zeroBasedPageNo, pageSize, Sort.by(direction, sortBy));

	    boolean hasSearch = search != null && !search.trim().isEmpty();

	    if (hasSearch) {
	        return invoiceRepository.searchInvoicesByAdminAndVendorType(
	                adminId,
	                vendorType.toLowerCase().trim(),
	                search.toLowerCase().trim(),
	                pageable
	        );
	    }

	    return invoiceRepository.findByAdminIdAndVendorTypeIgnoreCase(
	            adminId,
	            vendorType,
	            pageable
	    );
	}

	@Override
	public void sendInvoiceMails(String invoiceNumber, Long adminId) {

	    try {
	        ManualInvoice invoice = invoiceRepository
	                .findByInvoiceNumberAndAdminId(invoiceNumber, adminId)
	                .orElseThrow(() -> new RuntimeException("Invoice not found or unauthorized access"));

	        String emailsString = invoice.getCustomerEmail();

	        if (emailsString == null || emailsString.trim().isEmpty()) {
	            throw new RuntimeException("No email addresses found");
	        }

	        // ✅ Convert comma-separated string → List
	        List<String> emails = Arrays.stream(emailsString.split(","))
	                .map(String::trim) // remove spaces
	                .filter(email -> !email.isEmpty())
	                .toList();

	        if (emails.isEmpty()) {
	            throw new RuntimeException("No valid email addresses found");
	        }

	        // ✅ Send mail
	        invoiceEmailService.sendInvoiceMail(emails, invoice);

	        // ✅ Update status
	        invoice.setStatus("Pending");
	        invoiceRepository.save(invoice);

	    } catch (Exception e) {
	        throw new RuntimeException("Failed to send invoice mail: " + e.getMessage());
	    }
	}

	
	
	@Override
	public Page<ManualInvoice> getInvoiceByAdminAndVendorType(
	        InvoiceSortingRequestDTO requestDTO) {

	    String search = requestDTO.getSearch();
	    String sortBy = requestDTO.getSortField();
	    String sortDir = requestDTO.getSortOrder();
	    Integer pageNo = requestDTO.getPageNumber();
	    Integer pageSize = requestDTO.getPageSize();
	    Long adminId = requestDTO.getAdminId();

	    // ✅ FORCE RECEIVABLE ONLY (main change)
	    String vendorType = "Receivable";

	    // ✅ Pagination
	    if (pageNo == null || pageNo < 0) pageNo = 0;
	    int zeroBasedPageNo = (pageNo > 0) ? pageNo - 1 : pageNo;

	    if (pageSize == null || pageSize <= 0) pageSize = 10;

	    // ✅ Sorting defaults
	    if (sortBy == null || sortBy.trim().isEmpty()) sortBy = "invoiceDate";
	    if (sortDir == null || sortDir.trim().isEmpty()) sortDir = "desc";

	    // ✅ Sorting field mapping
	    switch (sortBy.toLowerCase()) {
	        case "consultantname": sortBy = "consultantName"; break;
	        case "customer": sortBy = "customer"; break;
	        case "invoicenumber": sortBy = "invoiceNumber"; break;
	        case "invoicedate": sortBy = "invoiceDate"; break;
	        case "duedate": sortBy = "dueDate"; break;
	        case "paymentdate": sortBy = "paymentDate"; break;
	        case "paymentamount": sortBy = "paymentAmount"; break;
	        case "paidamount": sortBy = "paidAmount"; break;
	        case "paiddate": sortBy = "paidDate"; break;
	        case "vendortype": sortBy = "vendorType"; break;
	        case "status": sortBy = "status"; break;
	        case "total": sortBy = "total"; break;
	        default: sortBy = "invoiceDate";
	    }

	    Sort.Direction direction = sortDir.equalsIgnoreCase("desc")
	            ? Sort.Direction.DESC
	            : Sort.Direction.ASC;

	    Pageable pageable = PageRequest.of(
	            zeroBasedPageNo,
	            pageSize,
	            Sort.by(direction, sortBy)
	    );

	    boolean hasSearch = search != null && !search.trim().isEmpty();

	    if (hasSearch) {
	        return invoiceRepository.searchInvoiceByAdminAndVendorType(
	                adminId,
	                vendorType,
	                search.trim().toLowerCase(),
	                pageable
	        );
	    }

	    return invoiceRepository.findByAdminIdAndVendorTypeIgnoreCase(
	            adminId,
	            vendorType,
	            pageable
	    );
	}
	
	
	

	@Override
	public Page<ManualInvoice> getInvoicesByAdminAndVendorTypestatus(
	        InvoiceSortingRequestDTO requestDTO) {

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
	    if (pageNo == null || pageNo < 0) pageNo = 0;
	    int zeroBasedPageNo = (pageNo > 0) ? pageNo - 1 : pageNo;

	    if (pageSize == null || pageSize <= 0) pageSize = 10;

	    // ✅ Sorting
	    if (sortBy == null || sortBy.trim().isEmpty()) sortBy = "invoiceDate";
	    if (sortDir == null || sortDir.trim().isEmpty()) sortDir = "desc";

	    Sort.Direction direction = sortDir.equalsIgnoreCase("desc")
	            ? Sort.Direction.DESC
	            : Sort.Direction.ASC;

	    Pageable pageable = PageRequest.of(zeroBasedPageNo, pageSize, Sort.by(direction, sortBy));

	    boolean hasSearch = search != null && !search.trim().isEmpty();
	    boolean hasVendorType = vendorType != null;
	    boolean hasStatus = status != null;

	    // ✅ CASE 1: ALL EMPTY → return ALL data
	    if (!hasVendorType && !hasStatus && !hasSearch) {
	        return invoiceRepository.findByAdminId(adminId, pageable);
	    }

	    // ✅ CASE 2: vendorType + status + search
	    if (hasVendorType && hasStatus && hasSearch) {
	        return invoiceRepository.searchInvoicesByAdminVendorTypeAndStatus(
	                adminId, vendorType.toLowerCase(), status.toLowerCase(),
	                search.toLowerCase(), pageable);
	    }

	    // ✅ CASE 3: vendorType + status
	    if (hasVendorType && hasStatus) {
	        return invoiceRepository.findByAdminIdAndVendorTypeAndStatusIgnoreCase(
	                adminId, vendorType, status, pageable);
	    }

	    // ✅ CASE 4: vendorType only
	    if (hasVendorType) {
	        return invoiceRepository.findByAdminIdAndVendorTypeIgnoreCase(
	                adminId, vendorType, pageable);
	    }

	    // ✅ CASE 5: status only
	    if (hasStatus) {
	        return invoiceRepository.findByAdminIdAndStatusIgnoreCase(
	                adminId, status, pageable);
	    }

	    // ✅ CASE 6: search only
	    if (hasSearch) {
	        return invoiceRepository.searchInvoicesByAdminOnly(
	                adminId, search.toLowerCase(), pageable);
	    }

	    return invoiceRepository.findByAdminId(adminId, pageable);
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
		    String vendorType = "Receivable";

		    // ✅ FIX: treat empty status as null
		    String status = (requestDTO.getStatus() != null && !requestDTO.getStatus().trim().isEmpty())
		            ? requestDTO.getStatus().trim()
		            : null;

		    // ✅ Pagination
		    if (pageNo == null || pageNo < 0) pageNo = 0;
		    int zeroBasedPageNo = (pageNo > 0) ? pageNo - 1 : pageNo;

		    if (pageSize == null || pageSize <= 0) pageSize = 10;

		    // ✅ Sorting
		    if (sortBy == null || sortBy.trim().isEmpty()) sortBy = "invoiceDate";
		    if (sortDir == null || sortDir.trim().isEmpty()) sortDir = "desc";

		    Sort.Direction direction = sortDir.equalsIgnoreCase("desc")
		            ? Sort.Direction.DESC
		            : Sort.Direction.ASC;

		    Pageable pageable = PageRequest.of(zeroBasedPageNo, pageSize, Sort.by(direction, sortBy));

		    boolean hasSearch = search != null && !search.trim().isEmpty();
		    boolean hasStatus = status != null;

		    // ✅ CASE 1: status + search
		    if (hasStatus && hasSearch) {
		        return invoiceRepository.searchReceivableByStatusAndSearch(
		                adminId,
		                vendorType,
		                status,
		                search.toLowerCase(),
		                pageable
		        );
		    }

		    // ✅ CASE 2: status only
		    if (hasStatus) {
		        return invoiceRepository.findReceivableByStatus(
		                adminId,
		                vendorType,
		                status,
		                pageable
		        );
		    }

		    // ✅ CASE 3: search only
		    if (hasSearch) {
		        return invoiceRepository.searchInvoiceByAdminAndVendorType(
		                adminId,
		                vendorType,
		                search.toLowerCase(),
		                pageable
		        );
		    }

		    // ✅ CASE 4: BOTH EMPTY → RETURN ALL RECEIVABLE DATA 🔥
		    return invoiceRepository.findByAdminIdAndVendorTypeIgnoreCase(
		            adminId,
		            vendorType,
		            pageable
		    );
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
	    if (pageNo == null || pageNo < 0) pageNo = 0;
	    int zeroBasedPageNo = (pageNo > 0) ? pageNo - 1 : pageNo;

	    if (pageSize == null || pageSize <= 0) pageSize = 10;

	    // ✅ Sorting
	    if (sortBy == null || sortBy.trim().isEmpty()) sortBy = "invoiceDate";
	    if (sortDir == null || sortDir.trim().isEmpty()) sortDir = "desc";

	    switch (sortBy.toLowerCase()) {
	        case "consultantname": sortBy = "consultantName"; break;
	        case "customer": sortBy = "customer"; break;
	        case "invoicenumber": sortBy = "invoiceNumber"; break;
	        case "invoicedate": sortBy = "invoiceDate"; break;
	        case "duedate": sortBy = "dueDate"; break;
	        case "paymentdate": sortBy = "paymentDate"; break;
	        case "paymentamount": sortBy = "paymentAmount"; break;
	        case "paidamount": sortBy = "paidAmount"; break;
	        case "paiddate": sortBy = "paidDate"; break;
	        case "vendortype": sortBy = "vendorType"; break;
	        case "status": sortBy = "status"; break;
	        case "total": sortBy = "total"; break;
	        default: sortBy = "invoiceDate";
	    }

	    Sort.Direction direction = sortDir.equalsIgnoreCase("desc")
	            ? Sort.Direction.DESC
	            : Sort.Direction.ASC;

	    Pageable pageable = PageRequest.of(zeroBasedPageNo, pageSize, Sort.by(direction, sortBy));

	    // ✅ Conditions
	    boolean hasSearch = search != null && !search.trim().isEmpty();
	    boolean hasStatus = status != null && !status.trim().isEmpty();

	    // ✅ FINAL FILTER LOGIC

	    if (hasSearch && hasStatus) {
	        return invoiceRepository.searchInvoicesByAdminVendorTypeAndStatus(
	                adminId,
	                vendorType.toLowerCase().trim(),
	                status.toLowerCase().trim(),
	                search.toLowerCase().trim(),
	                pageable
	        );
	    }

	    if (hasSearch) {
	        return invoiceRepository.searchInvoicesByAdminAndVendorType(
	                adminId,
	                vendorType.toLowerCase().trim(),
	                search.toLowerCase().trim(),
	                pageable
	        );
	    }

	    if (hasStatus) {
	        return invoiceRepository.findByAdminIdAndVendorTypeAndStatusIgnoreCase(
	                adminId,
	                vendorType,
	                status,
	                pageable
	        );
	    }

	    return invoiceRepository.findByAdminIdAndVendorTypeIgnoreCase(
	            adminId,
	            vendorType,
	            pageable
	    );
	}
	
	
	
	
	
	
}
