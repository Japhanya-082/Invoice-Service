package com.invoice.serviceImpl;

import java.util.List;
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
import com.invoice.repository.ManageUserRepository;
import com.invoice.service.EmailService;
import com.invoice.service.InvoiceEmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceEmailServiceImpl implements InvoiceEmailService {

	private final RestTemplate restTemplate;
	private final ManualInvoiceServiceImpl1 invoiceService;
	private final EmailService emailService;
	private final ManageUserRepository manageUserRepository;

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
				(String) userData.get("organizationName"), roleName, null);

		// 4️Fetch invoice
		ManualInvoice invoice = invoiceService.getInvoiceByNumber(invoiceNumber);
		if (invoice == null) {
			throw new RuntimeException("Invoice not found with number: " + invoiceNumber);
		}

		// 5️ Resolve sender (ACCOUNTANT) and CC (ADMIN + HR) from manage_users
		Long authAdminId = invoice.getAdminId();

		String companyAddress = manageUserRepository.findAdminAndHrByAdminId(authAdminId).stream()
				.filter(u -> "ADMIN".equalsIgnoreCase(u.getRoleName())).findFirst().map(u -> u.getFormattedAddress())
				.orElse("");

		UserDTO sender = manageUserRepository.findAccountantsByAdminId(authAdminId).stream().findFirst()
				.map(a -> new UserDTO(a.getPrimaryEmail(), loggedInUser.getFullName(), null,
						loggedInUser.getCompanyName(), null, "Accountant", companyAddress))
				.orElseGet(() -> new UserDTO(loggedInUser.getEmail(), loggedInUser.getFullName(), null,
						loggedInUser.getCompanyName(), null, loggedInUser.getRoleName(), companyAddress)); // fall back
																											// to
																											// logged-in
																											// user if
																											// no
																											// accountant
																											// exists

		List<String> cc = manageUserRepository.findAdminAndHrByAdminId(authAdminId).stream()
				.map(u -> u.getPrimaryEmail()).filter(e -> e != null && !e.isBlank()).distinct()
				.collect(java.util.stream.Collectors.toList());

		// 6️ Send email
		emailService.sendOverdueInvoiceEmail(sender, invoice, cc);

		log.info("Overdue email sent. Invoice={}, From={}, Role={}, CC={}", invoice.getInvoiceNumber(),
				sender.getEmail(), roleName, cc);
	}

}
