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
	/**
	 * Authorities that may NEVER originate from a token claim.
	 *
	 * ROLE_INTERNAL denotes "this call came from another service inside the
	 * platform" and is granted only after the shared X-Internal-Api-Key has been
	 * verified. Mapping it from the roles claim let any bearer of a tenant token
	 * containing roles:["INTERNAL"] invoke internal-only endpoints — verified
	 * against the live security chain, which returned 200 for exactly that token.
	 */
	private static final java.util.Set<String> RESERVED_AUTHORITIES =
			java.util.Set.of("ROLE_INTERNAL");


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

	// Defaults to true: a token that authenticated as a tenant user but carries
	// no companyDomain is refused here rather than reaching the fail-open router
	// (G-57). Matches Customer-Service.
	@Value("${tenant.require-tenant:true}")
	private boolean requireTenant;

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

		// Fail closed: a tenant user with no resolvable schema is refused here,
		// before the request reaches the router (G-57). Belt-and-suspenders with
		// TenantRoutingDataSource, which also refuses — this just gives a clean
		// 503 instead of a 500 from a thrown TenantNotResolvedException.
		if (requireTenant && TenantContext.isTenantRequiredButUnresolved()) {
			log.error("Refusing {} — authenticated as adminId={} but the token carries no "
					+ "companyDomain, so no tenant schema can be selected. Serving it from the "
					+ "default datasource would expose other tenants' invoices.",
					path, TenantContext.getCurrentAdminId());
			TenantContext.clear();
			SecurityContextHolder.clearContext();
			writeTenantUnavailable(response);
			return;
		}

		try {
			chain.doFilter(request, response);
		} finally {
			TenantContext.clear();
			SecurityContextHolder.clearContext();
		}
	}

	private boolean isExempt(String path) {
		// /internal/provision-schema/ is deliberately NOT exempt: exempting it returned
		// before isTrustedInternalCall() could run, so the internal-API-key check never
		// executed and the DDL endpoint was reachable anonymously.
		return path.startsWith("/actuator/health") || path.startsWith("/actuator/info");
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
		boolean tenantResolved = StringUtils.hasText(companyDomain);
		if (tenantResolved) {
			TenantContext.setCurrentTenant(TenantContext.toSchemaName(companyDomain));
		}

		Long adminId = coerceLong(claims.get("adminId"));
		if (adminId == null) {
			log.warn("JWT for {} missing adminId claim — skipping auth context setup", claims.getSubject());
			return;
		}
		TenantContext.setCurrentAdminId(adminId);

		if (!tenantResolved) {
			// Authenticated as a tenant user, but no schema can be selected.
			// Previously this fell through and the router served the request from
			// the shared default pool (G-57) — and the invoice table has no
			// admin_id column, so findAll() then returned every tenant's rows.
			TenantContext.markTenantRequiredButUnresolved();
		}

		Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();
		Object roles = claims.get("roles");
		if (roles instanceof List<?> roleList) {
			for (Object r : roleList)
				if (r != null) {
					String authority = "ROLE_" + r.toString().toUpperCase();
					if (RESERVED_AUTHORITIES.contains(authority)) {
						log.warn("Ignoring reserved authority {} claimed by token subject — "
								+ "internal authority is granted only via the internal API key", authority);
						continue;
					}
					authorities.add(new SimpleGrantedAuthority(authority));
				}
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
		// Do NOT require X-Admin-Id: provisioning runs before the tenant exists, so
		// demanding an adminId would reject the very call that creates it. The shared
		// internal key has already been verified.
		if (adminId != null) {
			TenantContext.setCurrentAdminId(adminId);
		}
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

	private void writeTenantUnavailable(HttpServletResponse response) throws IOException {
		response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
		response.setContentType("application/json");
		response.getWriter().write("{\"status\":\"FAIL\",\"message\":\"This session is not "
				+ "associated with a company. Sign in again; if it persists, contact your "
				+ "administrator.\"}");
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
