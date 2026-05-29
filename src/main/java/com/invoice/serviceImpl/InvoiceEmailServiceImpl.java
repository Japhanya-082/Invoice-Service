package com.invoice.serviceImpl;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.invoice.DTO.UserDTO;
import com.invoice.entity.ManualInvoice;
import com.invoice.service.EmailService;
import com.invoice.service.InvoiceEmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceEmailServiceImpl implements InvoiceEmailService {

	private final RestTemplate restTemplate;
	private final ManualInvoiceServiceImpl1 invoiceService; // Your invoice fetch service
	private final EmailService emailService;

	@Value("${login.service.base-url}")
	private String loginServiceBaseUrl;

	@Override
	public void sendOverdueInvoiceEmail(String authHeader, String invoiceNumber) {

		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			throw new RuntimeException("Missing or invalid Authorization header");
		}

		String token = authHeader.substring(7).trim();

		// 1️ Fetch logged-in user info from Login Service
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);

		HttpEntity<Void> entity = new HttpEntity<>(headers);

		ResponseEntity<Map> response = restTemplate.exchange(loginServiceBaseUrl + "/auth/me", HttpMethod.GET, entity,
				Map.class);

		Map<String, Object> body = response.getBody();
		if (body == null || !body.containsKey("data")) {
			throw new RuntimeException("Login-service response missing required user info");
		}

		Map<String, Object> userData = (Map<String, Object>) body.get("data");

		// 2️ Extract role name safely
		Map<String, Object> roleData = (Map<String, Object>) userData.get("role");

		String roleName = roleData != null ? (String) roleData.get("roleName") : "Employee";

		// 3️ Build UserDTO
		UserDTO loggedInUser = new UserDTO((String) userData.get("email"), (String) userData.get("fullName"),
				(String) userData.get("mobileNumber"), (String) userData.get("companyName"),
				(String) userData.get("organizationName"), roleName);

		// 4️Fetch invoice
		ManualInvoice invoice = invoiceService.getInvoiceByNumber(invoiceNumber);
		if (invoice == null) {
			throw new RuntimeException("Invoice not found with number: " + invoiceNumber);
		}

		// 5️ Send email
		emailService.sendOverdueInvoiceEmail(loggedInUser, invoice);

		log.info("Overdue email sent. Invoice={}, From={}, Role={}", invoice.getInvoiceNumber(),
				loggedInUser.getEmail(), roleName);
	}

}
