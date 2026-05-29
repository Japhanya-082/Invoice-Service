package com.invoice.serviceImpl;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.invoice.DTO.UserDTO;
import com.invoice.constant.EmailSignatureConstants;
import com.invoice.entity.ManualInvoice;
import com.invoice.service.EmailService;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

	private final JavaMailSender mailSender;

	@Override
	public void sendOverdueInvoiceEmail(UserDTO sender, ManualInvoice invoice) {

		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

			// Consultant name from InvoiceItem
			String consultantName = "N/A";
			if (invoice.getItems() != null && !invoice.getItems().isEmpty()) {
				consultantName = invoice.getItems().get(0).getName();
			}

			// Email Subject
			helper.setSubject("Pending Payment – Consultant Invoice " + invoice.getInvoiceNumber());

			// TO & FROM
			helper.setTo(invoice.getCustomerEmail());
			helper.setFrom(sender.getEmail());

			// HTML Email Body
			String htmlContent = "<p>Hi Team,</p>" +

					"<p>We would like to inform you that the invoice generated for Consultant: " + "<b>"
					+ consultantName + "</b> is still pending and has not been cleared.</p>" +

					"<p><b>Below are the invoice details for your reference:</b></p>" +

					"<p>" + "<b>Invoice Number :</b> " + invoice.getInvoiceNumber() + "<br>" + "<b>Invoice Date :</b> "
					+ invoice.getInvoiceDate() + "<br>" + "<b>Amount Due :</b> " + invoice.getAmountDue() + "<br>"
					+ "<b>Due Date :</b> " + invoice.getDueDate() + "</p>" +

					"<p>"
					+ "We kindly request you to prioritize this payment and complete it at the earliest to avoid any follow-ups.<br>"
					+ "If the payment has already been initiated, please share the transaction reference for verification."
					+ "</p>" +

					"<p>For any clarification or concerns, feel free to reach out to us.<br>"
					+ "Thank you for your cooperation.</p>" +

					"<p style='color:#1f4fd8; font-weight:bold;'>" + sender.getFullName() + "<br>"
					+ sender.getRoleName() + "<br>" + sender.getCompanyName() + "<br>" + invoice.getShippingAddress()
					+ "<br>" + sender.getEmail() + "<br>" + EmailSignatureConstants.TAGLINE + "</p>" +

					"<p style='color:red; font-weight:bold;'>" + EmailSignatureConstants.DISCLAIMER + "</p>";

			// Send as HTML
			helper.setText(htmlContent, true);

			mailSender.send(message);

			log.info("Overdue invoice email sent. Invoice={}, From={}, To={}", invoice.getInvoiceNumber(),
					sender.getEmail(), invoice.getCustomerEmail());

		} catch (Exception e) {
			log.error("Failed to send overdue invoice email", e);
			throw new RuntimeException("Email sending failed");
		}
	}
}
