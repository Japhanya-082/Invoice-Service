package com.invoice.tenant;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Component
@Order(1)
@Slf4j
public class TenantFilter extends OncePerRequestFilter {

	@Value("${jwt.secret}")
	private String jwtSecret;

    /** Expected token issuer. Not a secret; verified on every request. */
    @Value("${jwt.issuer}")
    private String jwtIssuer;

    /** Expected token audience. Not a secret; verified on every request. */
    @Value("${jwt.audience}")
    private String jwtAudience;

    /** Bounded tolerance for clock drift between issuer and verifier. */
    @Value("${jwt.clock-skew-seconds:30}")
    private long jwtClockSkewSeconds;


	@Value("${internal.api-key:}")
	private String internalApiKey;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
			chain.doFilter(request, response);
			return;
		}

		String path = request.getRequestURI();
		if (isExempt(path)) {
			chain.doFilter(request, response);
			return;
		}

		String authHeader = request.getHeader("Authorization");
		try {
			if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
				String token = authHeader.substring(7).trim();
				Claims claims = parseClaims(token);
				applyClaims(claims);
			} else if (isTrustedInternalCall(request)) {
				applyInternalHeaders(request);
			}
		} catch (io.jsonwebtoken.security.SecurityException e) {
			// Signature mismatch means the token was tampered → reject immediately.
			log.warn("Rejected request to {} — tampered JWT: {}", path, e.getMessage());
			writeUnauthorized(response, "Invalid or expired token");
			return;
		} catch (JwtException | IllegalArgumentException e) {
			// Expired, malformed, unsupported — pass through.
			// Spring Security enforces authentication on protected endpoints.
			log.warn("Rejected request to {} — invalid JWT: {}", path, e.getMessage());
		}

		try {
			chain.doFilter(request, response);
		} finally {
			TenantContext.clear();
			SecurityContextHolder.clearContext();
		}
	}

	private boolean isExempt(String path) {
		return path.startsWith("/actuator/health") || path.startsWith("/actuator/info")
				|| path.startsWith("/internal/provision-schema/");
	}

	private boolean isTrustedInternalCall(HttpServletRequest request) {
		if (!StringUtils.hasText(internalApiKey))
			return false;
		return constantTimeEquals(internalApiKey, request.getHeader("X-Internal-Api-Key"));
	}

	private void applyClaims(Claims claims) {
		// Set tenant from companyDomain first — needed for DB schema routing
		// even if adminId is absent (e.g. service-to-service tokens).
		String companyDomain = (String) claims.get("companyDomain");
		if (StringUtils.hasText(companyDomain)) {
			TenantContext.setCurrentTenant(TenantContext.toSchemaName(companyDomain));
		}

		Long adminId = coerceLong(claims.get("adminId"));
		if (adminId == null) {
			log.warn("JWT for {} missing adminId claim — skipping auth context setup", claims.getSubject());
			return;
		}
		TenantContext.setCurrentAdminId(adminId);

		Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();
		Object roles = claims.get("roles");
		if (roles instanceof List<?> roleList) {
			for (Object r : roleList)
				if (r != null)
					authorities.add(new SimpleGrantedAuthority("ROLE_" + r.toString().toUpperCase()));
		}
		Object privileges = claims.get("privileges");
		if (privileges instanceof Collection<?> privCol) {
			for (Object p : privCol)
				if (p != null)
					authorities.add(new SimpleGrantedAuthority(p.toString().toUpperCase()));
		}
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(claims.getSubject(), null,
				authorities);
		auth.setDetails(adminId);
		SecurityContextHolder.getContext().setAuthentication(auth);
	}

	private void applyInternalHeaders(HttpServletRequest request) {
		Long adminId = coerceLong(request.getHeader("X-Admin-Id"));
		if (adminId == null)
			return;
		TenantContext.setCurrentAdminId(adminId);
		String tenantHeader = request.getHeader("X-Tenant-Id");
		if (StringUtils.hasText(tenantHeader))
			TenantContext.setCurrentTenant(tenantHeader.trim());
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("internal-service", null,
				List.of(new SimpleGrantedAuthority("ROLE_INTERNAL")));
		auth.setDetails(adminId);
		SecurityContextHolder.getContext().setAuthentication(auth);
	}

	private Claims parseClaims(String token) {
		Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
		return Jwts.parserBuilder().setSigningKey(key)
				.requireIssuer(jwtIssuer)
				.requireAudience(jwtAudience)
				.setAllowedClockSkewSeconds(jwtClockSkewSeconds)
				.build().parseClaimsJws(token).getBody();
	}

	private Long coerceLong(Object value) {
		if (value == null)
			return null;
		if (value instanceof Number n)
			return n.longValue();
		try {
			return Long.parseLong(value.toString().trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType("application/json");
		response.getWriter().write("{\"status\":\"failed\",\"message\":\"" + message + "\"}");
	}

	/**
	 * Comparing a shared secret with String.equals short-circuits on the first
	 * differing byte, which leaks its length and prefix to a timing observer.
	 * MessageDigest.isEqual is constant-time for equal-length inputs.
	 */
	private static boolean constantTimeEquals(String expected, String supplied) {
		if (expected == null || supplied == null) {
			return false;
		}
		return java.security.MessageDigest.isEqual(
				expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
				supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	/**
	 * jjwt treats requireIssuer(null)/requireAudience(null) as "no requirement" and
	 * silently accepts the token — verified empirically against jjwt 0.11.5. A blank
	 * jwt.issuer or jwt.audience would therefore disable claim validation with no
	 * error at all. Refuse to start instead: a service that cannot validate claims
	 * must not serve traffic.
	 */
	@jakarta.annotation.PostConstruct
	void assertClaimValidationIsConfigured() {
		if (jwtIssuer == null || jwtIssuer.isBlank()) {
			throw new IllegalStateException(
					"jwt.issuer must be set — a blank value silently disables issuer validation.");
		}
		if (jwtAudience == null || jwtAudience.isBlank()) {
			throw new IllegalStateException(
					"jwt.audience must be set — a blank value silently disables audience validation.");
		}
		if (jwtSecret != null
				&& jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException(
					"jwt.secret must be at least 32 bytes (256 bits) for HS256.");
		}
	}
}
