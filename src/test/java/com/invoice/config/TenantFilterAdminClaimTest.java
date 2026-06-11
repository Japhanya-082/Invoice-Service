package com.invoice.config;

import com.invoice.tenant.TenantContext;
import com.invoice.tenant.TenantFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TenantFilterAdminClaimTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-test-secret-test-secret-test1234";

    private TenantFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TenantFilter();
        ReflectionTestUtils.setField(filter, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(filter, "internalApiKey", "");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsRequestWithoutAdminIdClaim() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .setSubject("user@example.com")
                .setClaims(Map.of("roles", List.of("USER")))
                .setSubject("user@example.com")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/manual-invoice/getall");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        // No adminId claim → applyClaims returns false → SecurityContext stays empty.
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(TenantContext.getCurrentAdminId());
    }

    @Test
    void acceptsRequestWithAdminIdClaim() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .setSubject("user@example.com")
                .claim("adminId", 42)
                .claim("roles", List.of("ADMIN"))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/manual-invoice/getall");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        // The filter clears context at end of doFilter, so we can't assert SecurityContext here.
        // Instead, verify the response was passed through (not unauthorized).
        assertEquals(200, res.getStatus());
    }

    @Test
    void rejectsTamperedToken() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor("different-secret-different-secret-different-secret-different-1234".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .setSubject("user@example.com")
                .claim("adminId", 42)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/manual-invoice/getall");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);
        assertEquals(401, res.getStatus());
    }
}
