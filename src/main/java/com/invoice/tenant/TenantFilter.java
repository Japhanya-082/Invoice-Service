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
        return path.startsWith("/actuator/health")
                || path.startsWith("/actuator/info")
                || path.startsWith("/internal/provision-schema/");
    }

    private boolean isTrustedInternalCall(HttpServletRequest request) {
        if (!StringUtils.hasText(internalApiKey)) return false;
        return internalApiKey.equals(request.getHeader("X-Internal-Api-Key"));
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
            for (Object r : roleList) if (r != null) authorities.add(new SimpleGrantedAuthority("ROLE_" + r.toString().toUpperCase()));
        }
        Object privileges = claims.get("privileges");
        if (privileges instanceof Collection<?> privCol) {
            for (Object p : privCol) if (p != null) authorities.add(new SimpleGrantedAuthority(p.toString().toUpperCase()));
        }
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
        auth.setDetails(adminId);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void applyInternalHeaders(HttpServletRequest request) {
        Long adminId = coerceLong(request.getHeader("X-Admin-Id"));
        if (adminId == null) return;
        TenantContext.setCurrentAdminId(adminId);
        String tenantHeader = request.getHeader("X-Tenant-Id");
        if (StringUtils.hasText(tenantHeader)) TenantContext.setCurrentTenant(tenantHeader.trim());
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "internal-service", null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL")));
        auth.setDetails(adminId);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Claims parseClaims(String token) {
        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }

    private Long coerceLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(value.toString().trim()); } catch (NumberFormatException e) { return null; }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":\"failed\",\"message\":\"" + message + "\"}");
    }
}
