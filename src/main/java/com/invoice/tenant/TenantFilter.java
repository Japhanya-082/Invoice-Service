package com.invoice.tenant;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;

@Component
@Order(1)
@Slf4j
public class TenantFilter implements Filter {

	@Value("${jwt.secret}")
	private String jwtSecret;

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		String authHeader = httpRequest.getHeader("Authorization");
		boolean tenantResolved = false;
		try {
			if (authHeader != null && authHeader.startsWith("Bearer ")) {
				String token = authHeader.substring(7).trim();
				Claims claims = parseClaims(token);
				String companyDomain = (String) claims.get("companyDomain");
				if (companyDomain != null && !companyDomain.isBlank()) {
					TenantContext.setCurrentTenant(TenantContext.toSchemaName(companyDomain));
					tenantResolved = true;
				}
			}
		} catch (Exception e) {
			log.debug("Tenant extraction from JWT skipped: {}", e.getMessage());
		}

		// Fallback: honour X-Tenant-Id / X-Company-Domain when JWT didn't set tenant.
		// Used by service-to-service Feign calls (e.g. Reports-Service) that already
		// resolved the tenant upstream.
		if (!tenantResolved) {
			String tenantHeader = httpRequest.getHeader("X-Tenant-Id");
			if (tenantHeader != null && !tenantHeader.isBlank()) {
				TenantContext.setCurrentTenant(tenantHeader.trim());
				tenantResolved = true;
			} else {
				String companyHeader = httpRequest.getHeader("X-Company-Domain");
				if (companyHeader != null && !companyHeader.isBlank()) {
					TenantContext.setCurrentTenant(TenantContext.toSchemaName(companyHeader.trim()));
				}
			}
		}

		try {
			chain.doFilter(request, response);
		} finally {
			TenantContext.clear();
		}
	}

	private Claims parseClaims(String token) {
		Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
		return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
	}
}
