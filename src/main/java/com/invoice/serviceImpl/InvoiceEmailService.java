package com.invoice.serviceImpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.invoice.entity.ManualInvoice;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class InvoiceEmailService {

	@Autowired
	private JavaMailSender mailSender;

	@Value("${spring.mail.username}")
	private String fromMail;

	public void sendInvoiceMail(List<String> toMail, ManualInvoice invoice) {

		try {

			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true);

			helper.setFrom(fromMail);
			// ✅ List → String[]
			helper.setTo(toMail.toArray(new String[0]));
			helper.setSubject("Invoice Generated - " + invoice.getInvoiceNumber());

			String body = "<html>"
					+ "<body style='margin:0;padding:0;background:#f0f4f8;font-family:Arial,sans-serif;'>" +

					"<table width='100%' cellpadding='0' cellspacing='0' bgcolor='#f0f4f8'>" + "<tr><td align='center'>"
					+

					"<table width='600' cellpadding='0' cellspacing='0' bgcolor='#ffffff' style='margin-top:20px;border:1px solid #ddd;'>"
					+

					/* ================= HEADER ================= */
					"<tr>" + "<td bgcolor='#2575fc' style='padding:20px;text-align:center;color:#ffffff;'>"
					+ "<h2 style='margin:0;'>Narvee</h2>" + "<p style='margin:5px 0 0;'>Invoice Notification</p>"
					+ "</td>" + "</tr>" +

					/* ================= BODY ================= */
					"<tr>" + "<td style='padding:20px;color:#333;font-size:14px;'>" +

					"<p>Hello <b>" + invoice.getCustomer() + "</b>,</p>"
					+ "<p>Your invoice has been generated successfully.</p>" +

					"<table width='100%' cellpadding='10' cellspacing='0' border='1' style='border-collapse:collapse;font-size:13px;'>"
					+

					"<tr bgcolor='#f2f2f2'>" + "<td><b>Invoice Number</b></td>" + "<td>" + invoice.getInvoiceNumber()
					+ "</td>" + "</tr>" +

					"<tr>" + "<td><b>Invoice Date</b></td>" + "<td>" + invoice.getInvoiceDate() + "</td>" + "</tr>" +

					"<tr bgcolor='#f2f2f2'>" + "<td><b>Due Date</b></td>" + "<td>" + invoice.getDueDate() + "</td>"
					+ "</tr>" +

					"<tr>" + "<td><b>Consultant</b></td>" + "<td>" + invoice.getConsultantName() + "</td>" + "</tr>" +

					"<tr bgcolor='#f2f2f2'>" + "<td><b>Total Amount</b></td>"
					+ "<td style='color:#27ae60;font-weight:bold;'>" + invoice.getTotal() + " " + invoice.getCurrency()
					+ "</td>" + "</tr>" +

					"<tr>" + "<td><b>Amount Due</b></td>" + "<td style='color:#e74c3c;font-weight:bold;'>"
					+ invoice.getAmountDue() + "</td>" + "</tr>" +

					"</table>" +

					/* ================= FOOTER (UPDATED DARK MODERN) ================= */
					"<tr>"
					+ "<td bgcolor='#4a6fa5' style='text-align:center;padding:10px;font-size:10px;color:#ffffff;'>" +

					"<span style='font-size:11px;color:#cfd8dc;'>© 2026 All Rights Reserved</span><br><br>" +

//					"<a href='mailto:no-reply@narvee.com' style='color:#4fc3f7;text-decoration:none;'>no-reply@narvee.com</a>" +

					"</td>" + "</tr>" +

					"</table>" +

					"</td></tr></table>" +

					"</body></html>";
			helper.setText(body, true);

			mailSender.send(message);

			log.info("Invoice mail sent successfully to: {}", toMail);

		} catch (Exception e) {
			log.error("Failed to send invoice mail to {}: {}", toMail, e.getMessage(), e);
		}
	}
}