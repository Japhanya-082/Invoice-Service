package com.invoice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "manual_invoice_upload")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManualInvoiceUpload {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "invoice_id")
	private Long invoiceId;

	@Column(name = "original_filename")
	private String originalFilename;

	@Column(name = "stored_filename")
	private String storedFilename;

	@Column(name = "file_path")
	private String filePath;

	@Column(name = "file_size")
	private Long fileSize;

	@Column(name = "content_type")
	private String contentType;

	@Column(name = "admin_id")
	private Long adminId;

	@Column(name = "uploaded_at")
	private LocalDateTime uploadedAt;

	@PrePersist
	public void prePersist() {
		this.uploadedAt = LocalDateTime.now();
	}
}

