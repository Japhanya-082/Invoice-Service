package com.invoice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoice.client.VendorFeignClient;
import com.invoice.common.RestAPIResponse;
import com.invoice.entity.ManualInvoice;
import com.invoice.repository.InvoiceRepository;
import com.invoice.repository.ManualInvoiceRepository;
import com.invoice.service.DashboardService;
import com.invoice.service.VendorClientService;
import com.invoice.serviceImpl.ManualInvoiceServiceImpl1;
import com.invoice.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for {@link ManualInvoiceController1}.
 *
 * <p>
 * Uses {@code @WebMvcTest} to load only the web layer. All service and
 * repository beans are replaced with Mockito mocks. The security config permits
 * all {@code /manual-invoice/**} requests so no authentication header is
 * required.
 */
@WebMvcTest(ManualInvoiceController1.class)
@AutoConfigureMockMvc(addFilters = false)
class ManualInvoiceControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	// --- All beans that the controller @Autowires ---

	@BeforeEach
	void setUp() {
		// SecurityUtils.getCurrentAdminId() reads TenantContext first.
		// Set it up here so controller methods don't throw SecurityIntegrityException.
		TenantContext.setCurrentAdminId(1L);
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
	}

	@MockBean
	private ManualInvoiceServiceImpl1 serviceImpl1;

	@MockBean
	private DashboardService dashboardService;

	@MockBean
	private ManualInvoiceRepository manualInvoiceRepository;

	@MockBean
	private VendorClientService vendorClientService;

	@MockBean
	private VendorFeignClient vendorFeignClient;

	@MockBean
	private InvoiceRepository invoiceRepository;

	// =========================================================
	// 1. getInvoiceById_notFound_returns404
	// The controller maps service exceptions to 500, but if the service
	// returns null the controller returns 404. We simulate the null path.
	// =========================================================

	@Test
	void getInvoiceById_notFound_returns404() throws Exception {
		// Service returns null → controller sends 404
		when(serviceImpl1.getInvoiceById(999L)).thenReturn(null);

		mockMvc.perform(get("/manual-invoice/999")).andExpect(status().isNotFound());
	}

	// =========================================================
	// 2. deleteInvoice_success_returns200
	// =========================================================

	@Test
	void deleteInvoice_success_returns200() throws Exception {
		doNothing().when(serviceImpl1).deleteInvoice(1L, 1L);

		mockMvc.perform(delete("/manual-invoice/1").param("adminId", "1")).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("Success"));
	}

	// =========================================================
	// 3. getAll_returns200
	// =========================================================

	@Test
	void getAll_returns200() throws Exception {
		when(serviceImpl1.getAllInvoices(1L)).thenReturn(Collections.emptyList());

		mockMvc.perform(get("/manual-invoice/getall").param("adminId", "1")).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("Success"));
	}

	// =========================================================
	// 4. saveInvoice_invalidPayload_returns400
	// The controller's /save endpoint catches all exceptions and returns
	// 400 BAD_REQUEST. We simulate a RuntimeException from the service.
	// =========================================================

	@Test
	void saveInvoice_invalidPayload_returns400() throws Exception {
		// Payload that causes an exception inside the controller (missing required
		// items)
		when(serviceImpl1.saveInvoice(any(ManualInvoice.class)))
				.thenThrow(new RuntimeException("Vendor not found for customer: Unknown"));

		Map<String, Object> payload = new HashMap<>();
		payload.put("customer", "Unknown");
		payload.put("adminId", 1);
		payload.put("consultantId", 1);
		payload.put("items", Collections.emptyList());

		mockMvc.perform(post("/manual-invoice/save").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(payload))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value("Error"));
	}
}
