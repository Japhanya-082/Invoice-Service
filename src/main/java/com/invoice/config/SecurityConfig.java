package com.invoice.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.invoice.tenant.TenantFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	@Value("${cors.allowed-origins:http://localhost:4200}")
	private String allowedOrigins;

	
	private final TenantFilter tenantFilter;

	
	public SecurityConfig(TenantFilter tenantFilter) {
		this.tenantFilter = tenantFilter;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))
			.sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
				.requestMatchers(
						"/actuator/health",
						"/actuator/info"
				).permitAll()
				.anyRequest().authenticated())
			.addFilterBefore(tenantFilter, UsernamePasswordAuthenticationFilter.class)
			.formLogin(form -> form.disable())
			.httpBasic(b -> b.disable());
		return http.build();
	}

	
	@Bean
	public FilterRegistrationBean<TenantFilter> tenantFilterRegistration(TenantFilter filter) {
		FilterRegistrationBean<TenantFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	
	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		List<String> origins = Arrays.stream(allowedOrigins.split(","))
				.map(String::trim).filter(s -> !s.isEmpty()).toList();
		config.setAllowedOriginPatterns(origins);
		config.setAllowCredentials(true);
		config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
		config.setAllowedHeaders(Arrays.asList(
				"Authorization", "Content-Type", "Accept", "X-Requested-With", "X-Correlation-Id"));
		config.setExposedHeaders(Arrays.asList("Authorization", "Content-Disposition", "X-Correlation-Id"));
		config.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}
}

