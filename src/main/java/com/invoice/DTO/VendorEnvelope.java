package com.invoice.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

/**
 * Customer-Service's {@code GET /vendor/{vendorId}} answers a
 * {@code RestAPIResponse} — {@code {status, message, data, ...}} — not a bare
 * vendor, unlike {@code /vendor/by-name} which answers a plain list.
 *
 * <p>The Feign client declared the by-id call as returning {@code VendorDTO}
 * directly. That could not have worked: Jackson matched none of the envelope's
 * fields onto the DTO and, since Boot leaves {@code FAIL_ON_UNKNOWN_PROPERTIES}
 * off, produced a non-null DTO with every field null instead of throwing. The
 * one caller took `vendor != null` as success — so the "enrichment" it performs
 * would blank the invoice's customer, email, phone and addresses, and its
 * try/catch never fired because nothing was thrown. This type is what the
 * endpoint actually returns.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VendorEnvelope {
	private String status;
	private String message;
	private VendorDTO data;
}
