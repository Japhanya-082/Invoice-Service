package com.invoice.config;

import com.invoice.tenant.TenantContext;
import com.invoice.tenant.TenantFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TenantFilter} — the JWT-parsing Servlet filter used in
 * this service.
 *
 * <p>
 * Note: In this microservice, JWT authentication/authorisation is handled by
 * the API-gateway / login service. The TenantFilter only extracts the
 * {@code companyDomain} claim from the token so that the correct PostgreSQL
 * schema is selected. It does <em>not</em> block requests for missing or
 * invalid tokens — those are simply skipped.
 */
class JwtAuthenticationFilterTest {

	/** Must be at least 256 bits (32 bytes) for HS256. */
	private static final String TEST_SECRET = "8f2c9a6d1e4b7c3f5a0d9e2b6c8f1a4e7d0c3b5a9f2e6d1c4b7a8e0f3d5c2b1";

	private TenantFilter filter;

	@BeforeEach
	void setUp() {
		filter = new TenantFilter();
		ReflectionTestUtils.setField(filter, "jwtSecret", TEST_SECRET);
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	private Key signingKey() {
		return Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
	}

	/** Build a valid, non-expired JWT containing a companyDomain and adminId claim. */
	private String buildValidToken(String companyDomain) {
		return Jwts.builder().setSubject("testuser")
				.claim("companyDomain", companyDomain)
				.claim("adminId", 1)
				.setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + 3_600_000L))
				.signWith(signingKey(), SignatureAlgorithm.HS256).compact();
	}

	/** Build a JWT that expired 1 second ago. */
	private String buildExpiredToken() {
		return Jwts.builder().setSubject("testuser").claim("companyDomain", "narvee.com")
				.setIssuedAt(new Date(System.currentTimeMillis() - 10_000L))
				.setExpiration(new Date(System.currentTimeMillis() - 1_000L))
				.signWith(signingKey(), SignatureAlgorithm.HS256).compact();
	}

	// ------------------------------------------------------------------
	// 1. filter_noAuthHeader_passesThrough
	//
	// A request without an Authorization header should pass through the
	// filter chain without setting tenant context or writing any error.
	// ------------------------------------------------------------------

	@Test
	void filter_noAuthHeader_passesThrough() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		// Filter chain was invoked (request passed through)
		assertNotNull(chain.getRequest(), "Filter chain should have been called");
		// No tenant context was set
		assertNull(TenantContext.getCurrentTenant(), "TenantContext must be cleared after filter execution");
		// Response should be unmodified
		assertEquals(200, response.getStatus());
	}

	// ------------------------------------------------------------------
	// 2. filter_validJwt_setsTenantContext
	//
	// A valid JWT containing a companyDomain claim should cause the
	// TenantContext to be populated while the request is being processed.
	// After the filter returns, TenantContext is cleared (finally block).
	// We capture the schema by using a custom FilterChain stub.
	// ------------------------------------------------------------------

	@Test
	void filter_validJwt_setsTenantContext() throws Exception {
		String token = buildValidToken("Narvee.com");

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + token);

		MockHttpServletResponse response = new MockHttpServletResponse();

		// Capture the tenant that was active during the downstream processing
		final String[] capturedTenant = { null };
		MockFilterChain chain = new MockFilterChain() {
			@Override
			public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
					throws java.io.IOException, jakarta.servlet.ServletException {
				capturedTenant[0] = TenantContext.getCurrentTenant();
				super.doFilter(req, res);
			}
		};

		filter.doFilter(request, response, chain);

		// TenantContext.toSchemaName("Narvee.com") → "narvee_com"
		assertEquals("narvee_com", capturedTenant[0],
				"Tenant context should be set to the normalised schema name during processing");

		// Verify the context was cleared after the filter completed
		assertNull(TenantContext.getCurrentTenant(), "TenantContext should be cleared after filter execution");
	}

	// ------------------------------------------------------------------
	// 3. filter_expiredJwt_passesThrough
	//
	// The filter catches all exceptions from JWT parsing and merely logs
	// them. An expired token should NOT block the request — it passes
	// through without tenant context being set.
	// ------------------------------------------------------------------

	@Test
	void filter_expiredJwt_passesThrough() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + buildExpiredToken());

		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		// Should not throw — the filter silently skips bad tokens
		assertDoesNotThrow(() -> filter.doFilter(request, response, chain));

		// Request passed through to the next filter
		assertNotNull(chain.getRequest());
		// Tenant was NOT set due to the parsing error
		assertNull(TenantContext.getCurrentTenant(), "TenantContext must remain empty after an expired-token failure");
		// HTTP response is not modified to 401 by this filter
		assertEquals(200, response.getStatus(), "TenantFilter should not change the HTTP status for expired tokens");
	}

	// ------------------------------------------------------------------
	// 4. filter_malformedJwt_passesThrough
	//
	// Garbage token strings should be silently swallowed by the filter.
	// ------------------------------------------------------------------

	@Test
	void filter_malformedJwt_passesThrough() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer this.is.garbage.not.a.real.jwt");

		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		assertDoesNotThrow(() -> filter.doFilter(request, response, chain));

		assertNotNull(chain.getRequest(), "Filter chain should have been called");
		assertNull(TenantContext.getCurrentTenant(), "TenantContext must remain empty after a malformed-token failure");
		assertEquals(200, response.getStatus(), "TenantFilter should not change the HTTP status for malformed tokens");
	}
}
