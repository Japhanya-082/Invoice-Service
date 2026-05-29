package com.invoice.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Upload directory initialization is managed by InvoiceServiceImpl#init(). This
 * component is retained as a no-op to avoid removing the bean if it is
 * referenced elsewhere, but performs no directory creation itself.
 */
@Slf4j
@Component
public class UploadDirInitializer {

	@Value("${file.upload-dir}")
	private String uploadDir;

	@PostConstruct
	public void createUploadDir() {
		log.info("Upload directory '{}' is managed by InvoiceServiceImpl.", uploadDir);
	}
}
