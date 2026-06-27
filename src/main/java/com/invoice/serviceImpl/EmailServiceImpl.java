package com.invoice.serviceImpl;

import java.util.List;

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
	public void sendOverdueInvoiceEmail(UserDTO sender, ManualInvoice invoice, List<String> ccEmails) {

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

			if (ccEmails != null && !ccEmails.isEmpty()) {
				String[] cc = ccEmails.stream().filter(e -> e != null && !e.isBlank()).toArray(String[]::new);
				if (cc.length > 0) helper.setCc(cc);
			}
			String htmlContent = "<p>Hi Team,</p>"

					+ "<p>This is a reminder that the payment for the invoice generated for Consultant: " + "<b>"
					+ consultantName
					+ "</b> is due. We kindly request you to share the current payment status for the invoice.</p>"

					+ "<p><b>Below are the invoice details for your reference:</b></p>"

					+ "<p>" + "<b>Invoice Number :</b> " + invoice.getInvoiceNumber() + "<br>"
					+ "<b>Invoice Date :</b> " + invoice.getInvoiceDate() + "<br>" + "<b>Amount Due :</b> "
					+ (invoice.getAmountDue() == null ? "0" : invoice.getAmountDue().toPlainString()) + "<br>"
					+ "<b>Due Date :</b> " + invoice.getDueDate() + "</p>"

					+ "<p>If any payment has already been processed, please provide the remittance details so that we can update our records accordingly.</p>"

					+ "<p>Your prompt attention to this matter is appreciated.</p>"

					+ "<p>For any clarification or concerns, feel free to reach out to us.<br>"
					+ "Thank you for your cooperation.</p>"

					+ "<p style='color:#1f4fd8; font-weight:bold;'>"
					+ (sender.getFullName() != null ? sender.getFullName() + "<br>" : "")
					+ (sender.getRoleName() != null ? sender.getRoleName() + "<br>" : "")
					+ (sender.getCompanyName() != null && !sender.getCompanyName().isBlank()
							? sender.getCompanyName() + "<br>"
							: "")
					+ (sender.getCompanyAddress() != null && !sender.getCompanyAddress().isBlank()
							? sender.getCompanyAddress() + "<br>"
							: "")
					+ sender.getEmail()+"</p>"

					+ "<p style='color:red; font-weight:bold;'>" + EmailSignatureConstants.DISCLAIMER + "</p>";

			helper.setText(htmlContent, true);
			mailSender.send(message);
			

			log.info("Overdue invoice email sent. Invoice={}, From={}, To={}, CC={}",
					invoice.getInvoiceNumber(), sender.getEmail(), invoice.getCustomerEmail(), ccEmails);

		} catch (Exception e) {
			log.error("Failed to send overdue invoice email", e);
			throw new RuntimeException("Email sending failed");
		}
	}

	@Override
	public void sendPaymentReminderEmail(UserDTO sender, ManualInvoice invoice, int daysUntilDue, List<String> ccEmails) {
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

			String consultantName = "N/A";
			if (invoice.getItems() != null && !invoice.getItems().isEmpty()) {
				consultantName = invoice.getItems().get(0).getName();
			}

			helper.setSubject("Payment Reminder – Invoice " + invoice.getInvoiceNumber()
					+ " due in " + daysUntilDue + " day(s)");
			helper.setTo(invoice.getCustomerEmail());
			helper.setFrom(sender.getEmail());

			if (ccEmails != null && !ccEmails.isEmpty()) {
				String[] cc = ccEmails.stream().filter(e -> e != null && !e.isBlank()).toArray(String[]::new);
				if (cc.length > 0) helper.setCc(cc);
			}

			String htmlContent = "<p>Hi Team,</p>"
					+ "<p>This is a friendly reminder that the following invoice is due in <b>" + daysUntilDue
					+ " day(s)</b>.</p>"
					+ "<p><b>Invoice Details:</b></p>"
					+ "<p>"
					+ "<b>Invoice Number :</b> " + invoice.getInvoiceNumber() + "<br>"
					+ "<b>Consultant :</b> " + consultantName + "<br>"
					+ "<b>Invoice Date :</b> " + invoice.getInvoiceDate() + "<br>"
					+ "<b>Due Date :</b> " + invoice.getDueDate() + "<br>"
					+ "<b>Amount Due :</b> "
					+ (invoice.getAmountDue() == null ? "0" : invoice.getAmountDue().toPlainString())
					+ "</p>"
					+ "<p>Please ensure payment is made before the due date to avoid any delays.</p>"
					+ "<p>Thank you for your continued partnership.</p>"
					+ "<p style='color:#1f4fd8; font-weight:bold;'>"
					+ (sender.getFullName() != null ? sender.getFullName() + "<br>" : "")
					+ (sender.getRoleName() != null ? sender.getRoleName() + "<br>" : "")
					+ (sender.getCompanyName() != null && !sender.getCompanyName().isBlank() ? sender.getCompanyName() + "<br>" : "")
					+ (sender.getCompanyAddress() != null && !sender.getCompanyAddress().isBlank() ? sender.getCompanyAddress() + "<br>" : "")
					+ sender.getEmail() + "</p>"
					+ "<p style='color:red; font-weight:bold;'>"
					+ EmailSignatureConstants.DISCLAIMER + "</p>";

			helper.setText(htmlContent, true);
			mailSender.send(message);

			log.info("Payment reminder email sent. Invoice={}, DueIn={}d, To={}, CC={}",
					invoice.getInvoiceNumber(), daysUntilDue, invoice.getCustomerEmail(), ccEmails);

		} catch (Exception e) {
			log.error("Failed to send payment reminder email", e);
			throw new RuntimeException("Reminder email sending failed");
		}
	}
}
