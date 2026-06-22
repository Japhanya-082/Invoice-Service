package com.invoice.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoice.DTO.DashboardRawDataDTO;
import com.invoice.DTO.InvoiceSnapshotDTO;
import com.invoice.DTO.KpiRawDTO;
import com.invoice.DTO.PaymentSnapshotDTO;
import com.invoice.DTO.InvoiceSortingRequestDTO;
import com.invoice.DTO.VendorAddressDTO;
import com.invoice.DTO.VendorDTO;
import com.invoice.client.VendorFeignClient;
import com.invoice.common.RestAPIResponse;
import com.invoice.entity.InvoiceItem;
import com.invoice.entity.ManualInvoice;
import com.invoice.entity.Payment;
import com.invoice.repository.InvoiceRepository;
import com.invoice.repository.ManualInvoiceRepository;
import com.invoice.repository.PaymentRepository;
import com.invoice.service.VendorClientService;
import com.invoice.serviceImpl.ManualInvoiceServiceImpl1;
import com.invoice.tenant.SecurityUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/manual-invoice")
public class ManualInvoiceController1 {

	@Autowired
	private ManualInvoiceServiceImpl1 serviceImpl1;

	@Autowired
	private ManualInvoiceRepository manualInvoiceRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private VendorClientService vendorClientService;

	@Autowired
	private VendorFeignClient vendorFeignClient;

	@Autowired
	private InvoiceRepository invoiceRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@PostMapping("/save")
	public ResponseEntity<RestAPIResponse> saveInvoice(@RequestBody Map<String, Object> payload) {

		try {
			Long authAdminId = SecurityUtils.getCurrentAdminId();
			ManualInvoice invoice = objectMapper.convertValue(payload, ManualInvoice.class);
			invoice.setAdminId(authAdminId);
			
			System.err.println(invoice.getItems());
			System.err.println(invoice.getItems() == null ? "null" : invoice.getItems().size());
			
			// FIX frontend bug: id = ""
			if (payload.get("id") == null || payload.get("id").toString().isBlank()) {
				invoice.setId(null);
			}

			// ✅ FIX vendorType mapping
			if (payload.get("vendorType") != null) {
				invoice.setVendorType(payload.get("vendorType").toString());
			}

			// Shipping address
			Object shippingObj = payload.get("shippingAddress");
			if (shippingObj instanceof String) {
				invoice.setShippingAddress(new VendorAddressDTO((String) shippingObj));
			} else if (shippingObj instanceof Map) {
				invoice.setShippingAddress(objectMapper.convertValue(shippingObj, VendorAddressDTO.class));
			}

			// Billing address
			Object billingObj = payload.get("billingAddress");
			if (billingObj instanceof Map) {
				invoice.setBillingAddress(objectMapper.convertValue(billingObj, VendorAddressDTO.class));
			}

			// Items
			List<Map<String, Object>> itemsMap = (List<Map<String, Object>>) payload.get("items");
			List<InvoiceItem> items = new ArrayList<>();

			if (itemsMap != null) {
				for (Map<String, Object> m : itemsMap) {
					InvoiceItem item = new InvoiceItem();
					item.setId(null);
					item.setName((String) m.get("name"));
					item.setDescription((String) m.get("description"));
					item.setHours(new java.math.BigDecimal(m.get("hours").toString()));
					item.setRate(new java.math.BigDecimal(m.get("rate").toString()));
					items.add(item);
				}
			}

			invoice.setItems(items);
	
			ManualInvoice saved = serviceImpl1.saveInvoice(invoice);

			return ResponseEntity.ok(new RestAPIResponse("Success", "Invoice saved successfully", saved));

		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(new RestAPIResponse("Error", e.getMessage(), null));
		}
	}

//	@GetMapping("/consultant/{consultantId}/exists")
//	public boolean consultantHasInvoice(@PathVariable Long consultantId) {
//		return manualInvoiceRepository.existsByConsultantId(consultantId);
//	}

	@GetMapping("/exists/{poNumber}")
	public ResponseEntity<Map<String, Object>> checkPoNumberDuplicate(@PathVariable String poNumber,
			@RequestParam(required = false) Long invoiceId, @RequestParam(required = false) Long adminId) {

		Long authAdminId = SecurityUtils.getCurrentAdminId();
		boolean exists = serviceImpl1.isPoNumberDuplicate(poNumber, invoiceId, authAdminId);

		Map<String, Object> response = new HashMap<>();
		response.put("field", "poNumber");
		response.put("value", poNumber);
		response.put("exists", exists);
		response.put("message", exists ? "PO Number already exists" : "PO Number is available");

		return ResponseEntity.ok(response);
	}

	@GetMapping("/invoices/count-by-vendor/{vendorId}")
	public ResponseEntity<Long> countInvoicesByVendor(@PathVariable Long vendorId) {
		Long authAdminId = SecurityUtils.getCurrentAdminId();
		long count = manualInvoiceRepository.findByCustomerVendorIdAndAdminId(vendorId, authAdminId).size();
		return ResponseEntity.ok(count);
	}

	// Upload files and attach to invoice
	@PostMapping(value = "/upload/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Transactional
	public ResponseEntity<RestAPIResponse> uploadFiles(@PathVariable Long id,
			@RequestParam("files") MultipartFile[] files, HttpServletRequest request) {

		try {
			ManualInvoice invoice = serviceImpl1.getInvoiceById(id);
			if (invoice == null) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(new RestAPIResponse("Error", "Invoice not found", null));
			}

			List<String> uploadedFiles = serviceImpl1.storeMultipleFiles(files);

			// Merge uploaded files
			List<String> currentFiles = invoice.getUploadedFileNames();
			if (currentFiles == null)
				currentFiles = new ArrayList<>();
			currentFiles.addAll(uploadedFiles);
			invoice.setUploadedFileNames(currentFiles);

			// Save files only (no item validation)
			serviceImpl1.updateUploadedFilesOnly(invoice);

			// Generate download URLs
			String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
			List<String> fileUrls = uploadedFiles.stream().map(f -> baseUrl + "/manual-invoice/view/" + f)
					.collect(Collectors.toList());

			Map<String, Object> responseData = new HashMap<>();
			responseData.put("uploadedFiles", uploadedFiles);
			responseData.put("fileDownloadUrls", fileUrls);

			return ResponseEntity.ok(new RestAPIResponse("Success", "Files uploaded successfully", responseData));

		} catch (Exception e) {
			log.error("Failed to upload files for invoice id={}", id, e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new RestAPIResponse("Error", "Failed to upload files: " + e.getMessage(), null));
		}
	}

	// View single file
	@GetMapping("/view/{filename}")
	public ResponseEntity<Resource> viewFile(@PathVariable String filename) {
		try {
			Resource resource = serviceImpl1.loadFileAsResource(filename);

			// Determine content type based on file extension
			String contentType = "application/octet-stream";
			if (filename.endsWith(".pdf"))
				contentType = "application/pdf";
			else if (filename.endsWith(".csv"))
				contentType = "text/csv";
			else if (filename.endsWith(".docx"))
				contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

			return ResponseEntity.ok()
					.header("Content-Disposition", "inline; filename=\"" + resource.getFilename() + "\"")
					.contentType(MediaType.parseMediaType(contentType)).body(resource);

		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
	}

	// Get invoice by ID + uploaded file URLs
	@GetMapping("/{id}")
	public ResponseEntity<RestAPIResponse> getInvoiceById(@PathVariable Long id, HttpServletRequest request) {
		try {
			ManualInvoice invoice = serviceImpl1.getInvoiceById(id);
			if (invoice == null) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(new RestAPIResponse("Error", "Invoice not found", null));
			}

			String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
			List<String> fileUrls = invoice.getUploadedFileNames().stream()
					.map(fileName -> baseUrl + "/manual-invoice/view/" + fileName).collect(Collectors.toList());

			Map<String, Object> responseData = new HashMap<>();
			responseData.put("invoice", invoice);
			responseData.put("fileDownloadUrls", fileUrls);

			return ResponseEntity.ok(new RestAPIResponse("Success", "Invoice Retrieved Successfully", responseData));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new RestAPIResponse("Error", "Failed to retrieve invoice: " + e.getMessage(), null));
		}
	}

	// Get all invoices
	@GetMapping("/getall")
	public ResponseEntity<RestAPIResponse> getAllInvoices(@RequestParam Long adminId) {
		try {
			Long authAdminId = SecurityUtils.getCurrentAdminId();
			return ResponseEntity
					.ok(new RestAPIResponse("Success", "All Invoices Retrieved", serviceImpl1.getAllInvoices(authAdminId)));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new RestAPIResponse("Error", "Failed to retrieve invoices: " + e.getMessage(), null));
		}
	}

	@GetMapping("/searchAndSort")
	public ResponseEntity<RestAPIResponse> getManualInvoices(@RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
			@RequestParam(defaultValue = "id") String sortField, @RequestParam(defaultValue = "asc") String sortDir,
			@RequestParam Long adminId) {

		try {
			if (size > 100) size = 100;
			if (size < 1) size = 20;
			if (page < 0) page = 0;

			Long authAdminId = SecurityUtils.getCurrentAdminId();
			Page<ManualInvoice> invoicePage = serviceImpl1.getAllInvoicesWithPaginationAndSearch(page, size, sortField,
					sortDir, keyword, authAdminId);

			Map<String, Object> response = new HashMap<>();
			response.put("invoices", invoicePage.getContent());
			response.put("currentPage", invoicePage.getNumber());
			response.put("totalItems", invoicePage.getTotalElements());
			response.put("totalPages", invoicePage.getTotalPages());
			response.put("sortField", sortField);
			response.put("sortDir", sortDir);
			response.put("keyword", keyword);

			return ResponseEntity.ok(new RestAPIResponse("Success", "Invoices retrieved successfully", response));

		} catch (Exception e) {

			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new RestAPIResponse("Error", "Failed to fetch Invoices: " + e.getMessage(), null));
		}
	}

	@GetMapping("/count")
	public ResponseEntity<RestAPIResponse> getInvoiceCounts() {
		Long authAdminId = SecurityUtils.getCurrentAdminId();
		List<ManualInvoice> tenantInvoices = manualInvoiceRepository.findByAdminId(authAdminId);
		Map<String, Long> counts = new HashMap<>();
		counts.put("total", (long) tenantInvoices.size());
		counts.put("paid", tenantInvoices.stream()
				.filter(i -> i.getStatus() != null && "paid".equalsIgnoreCase(i.getStatus())).count());
		counts.put("pending", tenantInvoices.stream()
				.filter(i -> i.getStatus() != null && "pending".equalsIgnoreCase(i.getStatus())).count());
		counts.put("OverDue", tenantInvoices.stream()
				.filter(i -> i.getStatus() != null && "overdue".equalsIgnoreCase(i.getStatus())).count());
		return ResponseEntity.ok(new RestAPIResponse("success", "Invoice counts fetched", counts));
	}

	// ---------------- Today's overdue count ----------------
	@GetMapping("/today-overdue-count")
	public ResponseEntity<RestAPIResponse> getTodayOverdueCount() {
		Long authAdminId = SecurityUtils.getCurrentAdminId();
		java.time.LocalDate today = java.time.LocalDate.now();
		long count = manualInvoiceRepository.findByAdminId(authAdminId).stream()
				.filter(i -> i.getStatus() != null && "overdue".equalsIgnoreCase(i.getStatus()))
				.filter(i -> today.equals(i.getDueDate()))
				.count();
		return ResponseEntity.ok(new RestAPIResponse("Success", "Today's overdue count fetched", count));
	}

	// ---------------- Today's overdue invoices for popup ----------------
	@GetMapping("/today-overdue-invoices")
	public ResponseEntity<RestAPIResponse> getTodayOverdueInvoices() {
		Long authAdminId = SecurityUtils.getCurrentAdminId();
		java.time.LocalDate today = java.time.LocalDate.now();
		List<ManualInvoice> invoices = manualInvoiceRepository.findByAdminId(authAdminId).stream()
				.filter(i -> i.getStatus() != null && "overdue".equalsIgnoreCase(i.getStatus()))
				.filter(i -> today.equals(i.getDueDate()))
				.collect(Collectors.toList());
		return ResponseEntity.ok(new RestAPIResponse("Success", "Today's overdue invoices fetched", invoices));
	}

	@PutMapping("/update-status/{invoiceNumber}")
	public ResponseEntity<String> updateInvoiceStatus(@PathVariable String invoiceNumber,
			@RequestBody Map<String, String> payload) {

		String status = payload.get("status");
		ManualInvoice invoice = manualInvoiceRepository.findByInvoiceNumber(invoiceNumber)
				.orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceNumber));

		invoice.setStatus(status);
		invoice.setUpdatedAt(LocalDateTime.now());
		manualInvoiceRepository.save(invoice);

		return ResponseEntity.ok("Invoice " + invoiceNumber + " status updated to " + status);
	}

	// Update invoice
	@PutMapping("/{id}")
	public ResponseEntity<RestAPIResponse> updateInvoice(@PathVariable Long id, @RequestBody ManualInvoice invoice) {
		try {
			invoice.setAdminId(SecurityUtils.getCurrentAdminId());
			ManualInvoice updatedInvoice = serviceImpl1.updateInvoice(id, invoice);
			if (updatedInvoice == null) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(new RestAPIResponse("Error", "Invoice not found", null));
			}
			return ResponseEntity.ok(new RestAPIResponse("Success", "Invoice Updated Successfully", updatedInvoice));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new RestAPIResponse("Error", "Failed to update invoice: " + e.getMessage(), null));
		}
	}

	@PutMapping("/update/{id}")
	public ResponseEntity<RestAPIResponse> updateManualInvoice(@PathVariable Long id,
			@RequestBody ManualInvoice invoice) {
		try {
			invoice.setAdminId(SecurityUtils.getCurrentAdminId());
			ManualInvoice updatedInvoice = serviceImpl1.updateManualInvoice(id, invoice);
			return ResponseEntity.ok(new RestAPIResponse("Success", "Invoice updated successfully", updatedInvoice));
		} catch (RuntimeException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new RestAPIResponse("Error", e.getMessage(), null));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new RestAPIResponse("Error", "Failed to update invoice: " + e.getMessage(), null));
		}
	}

	@PutMapping("/invoices/update-vendor")
	public ResponseEntity<Void> updateInvoicesByVendor(@RequestBody VendorDTO vendorDTO) {

		vendorDTO.setAdminId(SecurityUtils.getCurrentAdminId());
		List<ManualInvoice> invoices = manualInvoiceRepository.findByCustomerVendorIdAndAdminId(vendorDTO.getVendorId(),
				vendorDTO.getAdminId());

		for (ManualInvoice invoice : invoices) {

			// Vendor snapshot update
			invoice.setCustomer(vendorDTO.getVendorName());
			invoice.setCustomerEmail(vendorDTO.getEmail());

			// Address snapshot update
			invoice.setBillingAddress(vendorDTO.getVendorAddress());
			invoice.setShippingAddress(vendorDTO.getVendorAddress());
		}

		manualInvoiceRepository.saveAll(invoices);

		return ResponseEntity.ok().build();
	}

	// Delete invoice
	@DeleteMapping("/{id}")
	public ResponseEntity<RestAPIResponse> deleteInvoice(@PathVariable Long id, @RequestParam Long adminId) {

		try {

			Long authAdminId = SecurityUtils.getCurrentAdminId();
			serviceImpl1.deleteInvoice(id, authAdminId);

			return ResponseEntity.ok(new RestAPIResponse("Success", "Invoice Deleted Successfully", null));

		} catch (Exception e) {

			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new RestAPIResponse("Error", "Failed to delete invoice: " + e.getMessage(), null));
		}
	}

	@GetMapping("/consultant/{consultantId}/exists")
	public boolean hasInvoices(@PathVariable("consultantId") Long consultantId) {
		Long authAdminId = SecurityUtils.getCurrentAdminId();
		return manualInvoiceRepository.existsByConsultantIdAndAdminId(consultantId, authAdminId);
	}

//	@GetMapping("/consultant/{consultantId}/exists")
//	public boolean hasInvoices(@PathVariable("consultantId") Long consultantId, @RequestParam Long adminId) {
//
//		return manualInvoiceRepository.existsByConsultantIdAndAdminId(consultantId, adminId);
//	}

	@PostMapping("/send-mail/{invoiceNumber}")
	public ResponseEntity<RestAPIResponse> sendInvoiceMail(@PathVariable String invoiceNumber,
			@RequestParam Long adminId) {
		try {
			Long authAdminId = SecurityUtils.getCurrentAdminId();
			serviceImpl1.sendInvoiceMail(invoiceNumber, authAdminId);
			return ResponseEntity.ok(new RestAPIResponse("success", "Invoice mail sent successfully", null));
		} catch (RuntimeException e) {
			log.error("Failed to send invoice mail for {}", invoiceNumber, e);
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(new RestAPIResponse("fail", e.getMessage(), null));
		}
	}

	@GetMapping("/consultant/{consultantId}")
	public ResponseEntity<?> getInvoicesByConsultant(@PathVariable Long consultantId) {
		List<ManualInvoice> invoices = serviceImpl1.getInvoicesByConsultantId(consultantId);
		return ResponseEntity.ok(new RestAPIResponse("Success", "Invoices fetched successfully", invoices));
	}

	@GetMapping("/pending-invoices/{adminId}")
	public ResponseEntity<RestAPIResponse> getPendingInvoices(@PathVariable Long adminId) {

		Long authAdminId = SecurityUtils.getCurrentAdminId();
		List<ManualInvoice> invoices = serviceImpl1.getPendingInvoicesByAdmin(authAdminId);

		return ResponseEntity
				.ok(new RestAPIResponse("Success", "Pending & Partially Paid invoices fetched successfully", invoices));
	}

	@PostMapping("/pending-invoices/searchAndsorting")
	public ResponseEntity<RestAPIResponse> getPendingInvoices(@RequestBody InvoiceSortingRequestDTO requestDTO) {

		requestDTO.setAdminId(SecurityUtils.getCurrentAdminId());
		Page<ManualInvoice> invoices = serviceImpl1.getPendingInvoicesByAdmin(requestDTO);

		return ResponseEntity.ok(new RestAPIResponse("Success",
				"Pending & Partially received invoices fetched successfully", invoices.getContent()));
	}

	@PostMapping("/invoices/searchAndSorting")
	public ResponseEntity<RestAPIResponse> getInvoicesByAdminAndVendorType(
			@RequestBody InvoiceSortingRequestDTO requestDTO) {

		requestDTO.setAdminId(SecurityUtils.getCurrentAdminId());
		Page<ManualInvoice> invoices = serviceImpl1.getInvoicesByAdminAndVendorType(requestDTO);

		return ResponseEntity
				.ok(new RestAPIResponse("Success", "Invoices fetched successfully", invoices.getContent()));
	}

	@PostMapping("/send-mails/{invoiceNumber}")
	public ResponseEntity<RestAPIResponse> sendInvoiceMails(@PathVariable String invoiceNumber,
			@RequestParam Long adminId) {
		try {
			Long authAdminId = SecurityUtils.getCurrentAdminId();
			serviceImpl1.sendInvoiceMails(invoiceNumber, authAdminId);
			return ResponseEntity.ok(new RestAPIResponse("success", "Invoice mail sent successfully", null));
		} catch (RuntimeException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(new RestAPIResponse("fail", e.getMessage(), null));
		}
	}

	@PostMapping("/vendortype-receivable/searchAndSorting")
	public ResponseEntity<RestAPIResponse> getInvoiceByAdminAndVendorType(
			@RequestBody InvoiceSortingRequestDTO requestDTO) {
		requestDTO.setAdminId(SecurityUtils.getCurrentAdminId());
		Page<ManualInvoice> invoices = serviceImpl1.getInvoiceByAdminAndVendorType(requestDTO);
		return ResponseEntity
				.ok(new RestAPIResponse("Success", "Invoices fetched successfully", invoices.getContent()));
	}

	@PostMapping("/vendortype-receivablestatus/searchAndSorting")
	public ResponseEntity<RestAPIResponse> getInvoiceByAdminAndVendorTypestatus(
			@RequestBody InvoiceSortingRequestDTO requestDTO) {

		requestDTO.setAdminId(SecurityUtils.getCurrentAdminId());
		Page<ManualInvoice> invoices = serviceImpl1.getInvoiceByAdminAndVendorTypereceivablestatus(requestDTO);

		Map<String, Object> responseData = new HashMap<>();

		responseData.put("data", invoices.getContent());

		// Custom pageable response without duplicate sort
		Map<String, Object> pageableData = new HashMap<>();
		pageableData.put("pageNumber", invoices.getPageable().getPageNumber());
		pageableData.put("pageSize", invoices.getPageable().getPageSize());
		pageableData.put("offset", invoices.getPageable().getOffset());
		pageableData.put("paged", invoices.getPageable().isPaged());
		pageableData.put("unpaged", invoices.getPageable().isUnpaged());
		responseData.put("pageable", pageableData);
		// Keep sort only one time here
		responseData.put("sort", invoices.getSort());
		responseData.put("last", invoices.isLast());
		responseData.put("totalPages", invoices.getTotalPages());
		responseData.put("totalElements", invoices.getTotalElements());
		responseData.put("first", invoices.isFirst());
		responseData.put("size", invoices.getSize());
		responseData.put("number", invoices.getNumber());
		responseData.put("numberOfElements", invoices.getNumberOfElements());
		responseData.put("empty", invoices.isEmpty());
		return ResponseEntity.ok(new RestAPIResponse("Success", "Invoices fetched successfully", responseData));
	}

	@PostMapping("/invoicestatus/searchAndSorting")
	public ResponseEntity<RestAPIResponse> getInvoicesByAdminAndVendorTypestatus(
			@RequestBody InvoiceSortingRequestDTO requestDTO) {

		requestDTO.setAdminId(SecurityUtils.getCurrentAdminId());
		Page<ManualInvoice> invoices = serviceImpl1.getInvoicesByAdminAndVendorTypestatusinvoicestatus(requestDTO);

		Map<String, Object> responseData = new HashMap<>();
		responseData.put("data", invoices.getContent());
		// Custom pageable response
		Map<String, Object> pageableData = new HashMap<>();
		pageableData.put("pageNumber", invoices.getPageable().getPageNumber());
		pageableData.put("pageSize", invoices.getPageable().getPageSize());
		pageableData.put("offset", invoices.getPageable().getOffset());
		pageableData.put("paged", invoices.getPageable().isPaged());
		pageableData.put("unpaged", invoices.getPageable().isUnpaged());
		responseData.put("pageable", pageableData);
		// Sort only one time
		responseData.put("sort", invoices.getSort());
		responseData.put("last", invoices.isLast());
		responseData.put("totalPages", invoices.getTotalPages());
		responseData.put("totalElements", invoices.getTotalElements());
		responseData.put("first", invoices.isFirst());
		responseData.put("size", invoices.getSize());
		responseData.put("number", invoices.getNumber());
		responseData.put("numberOfElements", invoices.getNumberOfElements());
		responseData.put("empty", invoices.isEmpty());

		return ResponseEntity.ok(new RestAPIResponse("Success", "Invoices fetched successfully", responseData));
	}

	@PostMapping("/find-invoice-page")
	public ResponseEntity<RestAPIResponse> getInvoicePage(@RequestBody InvoiceSortingRequestDTO requestDTO) {
		Long adminId = SecurityUtils.getCurrentAdminId();
		int pageSize = (requestDTO.getPageSize() != null && requestDTO.getPageSize() > 0) ? requestDTO.getPageSize() : 20;
		int pageNumber = serviceImpl1.getInvoicePage(
				requestDTO.getInvoiceId(), requestDTO.getVendorType(), requestDTO.getStatus(), pageSize, adminId);
		return ResponseEntity.ok(new RestAPIResponse("Success", "Invoice page found", Map.of("pageNumber", pageNumber)));
	}

	@GetMapping("/status-count/{adminId}")
	public ResponseEntity<?> getInvoiceStatusCounts(@PathVariable Long adminId) {
		Long authAdminId = SecurityUtils.getCurrentAdminId();
		Map<String, Object> data = serviceImpl1.getInvoiceStatusCounts(authAdminId);
		Map<String, Object> response = new LinkedHashMap<>();

		response.put("message", "Invoice status counts fetched successfully");
		response.put("status", "success");
		response.put("data", data);
		return ResponseEntity.ok(response);
	}

	@GetMapping("/check-employment/{employmentId}")
	public ResponseEntity<Boolean> checkEmploymentInvoices(@PathVariable Long employmentId) {
		return serviceImpl1.checkEmploymentInvoices(employmentId);
	}

	@GetMapping("/internal/dashboard-raw")
	public ResponseEntity<RestAPIResponse> getInternalDashboardRaw(
			@org.springframework.web.bind.annotation.RequestParam(required = false) Integer daysRange,
			@org.springframework.web.bind.annotation.RequestParam(required = false) Integer panelItems) {
		Long adminId = SecurityUtils.getCurrentAdminId();
		java.time.LocalDate today = java.time.LocalDate.now();
		int year = today.getYear();
		int month = today.getMonthValue();
		int effectiveDays = (daysRange != null && daysRange > 0) ? daysRange : 14;
		int effectiveItems = (panelItems != null && panelItems > 0) ? panelItems : 20;

		KpiRawDTO kpi = KpiRawDTO.builder()
				.arOutstanding(safe(manualInvoiceRepository.sumArOutstanding(adminId)))
				.arOutstandingCount(safe(manualInvoiceRepository.countArOutstanding(adminId)))
				.apOutstanding(safe(manualInvoiceRepository.sumApOutstanding(adminId)))
				.apOutstandingCount(safe(manualInvoiceRepository.countApOutstanding(adminId)))
				.overdueAmount(safe(manualInvoiceRepository.sumOverdueAmount(adminId)))
				.overdueCount(safe(manualInvoiceRepository.countOverdue(adminId)))
				.arOverdueAmount(safe(manualInvoiceRepository.sumArOverdueAmount(adminId)))
				.apOverdueAmount(safe(manualInvoiceRepository.sumApOverdueAmount(adminId)))
				.collectedThisMonth(safe(manualInvoiceRepository.sumCollectedThisMonth(adminId, year, month)))
				.collectedThisMonthCount(safe(manualInvoiceRepository.countCollectedThisMonth(adminId, year, month)))
				.paidThisMonth(safe(manualInvoiceRepository.sumPaidThisMonth(adminId, year, month)))
				.paidThisMonthCount(safe(manualInvoiceRepository.countPaidThisMonth(adminId, year, month)))
				.build();

		List<InvoiceSnapshotDTO> upcoming = manualInvoiceRepository
				.findUpcomingAndOverdue(adminId, today.plusDays(effectiveDays), PageRequest.of(0, effectiveItems))
				.stream().map(this::toInvoiceSnapshot).collect(Collectors.toList());

		List<InvoiceSnapshotDTO> recentInvoices = manualInvoiceRepository
				.findRecentlyUpdated(adminId, PageRequest.of(0, 20))
				.stream().map(this::toInvoiceSnapshot).collect(Collectors.toList());

		List<PaymentSnapshotDTO> recentPayments = paymentRepository
				.findRecentPayments(adminId, PageRequest.of(0, 10))
				.stream().map(pay -> toPaymentSnapshot(pay, adminId)).filter(p -> p != null)
				.collect(Collectors.toList());

		DashboardRawDataDTO raw = DashboardRawDataDTO.builder()
				.kpiData(kpi)
				.upcomingInvoices(upcoming)
				.recentInvoices(recentInvoices)
				.recentPayments(recentPayments)
				.build();

		return ResponseEntity.ok(new RestAPIResponse("success", "Internal dashboard data", raw));
	}

	private InvoiceSnapshotDTO toInvoiceSnapshot(ManualInvoice inv) {
		return InvoiceSnapshotDTO.builder()
				.id(inv.getId())
				.invoiceNumber(inv.getInvoiceNumber())
				.customer(inv.getCustomer())
				.vendorType(inv.getVendorType())
				.status(inv.getStatus())
				.invoiceDate(inv.getInvoiceDate())
				.dueDate(inv.getDueDate())
				.amountDue(inv.getAmountDue())
				.total(inv.getTotal())
				.createdAt(inv.getCreatedAt())
				.updatedAt(inv.getUpdatedAt())
				.build();
	}

	private PaymentSnapshotDTO toPaymentSnapshot(Payment pay, Long adminId) {
		try {
			ManualInvoice inv = manualInvoiceRepository.findById(pay.getInvoiceId()).orElse(null);
			if (inv == null || !adminId.equals(inv.getAdminId())) return null;
			return PaymentSnapshotDTO.builder()
					.paymentId(pay.getPaymentId())
					.invoiceId(pay.getInvoiceId())
					.amount(pay.getAmount())
					.createdAt(pay.getCreatedAt())
					.invoiceNumber(inv.getInvoiceNumber())
					.customer(inv.getCustomer())
					.vendorType(inv.getVendorType())
					.build();
		} catch (Exception e) {
			return null;
		}
	}

	private java.math.BigDecimal safe(java.math.BigDecimal v) { return v != null ? v : java.math.BigDecimal.ZERO; }
	private Long safe(Long v) { return v != null ? v : 0L; }

}
