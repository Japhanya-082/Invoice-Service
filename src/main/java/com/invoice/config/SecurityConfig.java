package com.invoice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

//    @Bean
//    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
//        http
//            .csrf(csrf -> csrf.disable())
//            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
//            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
//        return http.build();
//    }

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

		http.csrf(csrf -> csrf.disable())
				.sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth.requestMatchers("/invoice/**", "/upload/**", "/invoice/getData/**",
						"/invoice/{invoiceId}/**", "/manual-invoice/**", "/send-overdue-email/{invoiceNumber}",
						"/invoice/save", "/exists/{poNumber}", "/invoices/count-by-vendor/{vendorId}", "/upload/{id}",
						"/view/{filename}", "/manual-invoice/{id}", "/getall", "/searchAndSort", "/count",
						"/today-overdue-count", "/today-overdue-invoices", "/update-status/{invoiceNumber}", "/{id}",
						"/update/{id}", "/manual-invoice/update/{id}", "/manual-invoice/upload/{id}",
						"/manual-invoice/view/{filename}", "/invoices/update-vendor",
						"/consultant/{consultantId}/exists", "/send-mail/{invoiceNumber}", "/consultant/{consultantId}",
						"/pending-invoices/{adminId}", "/pending-invoices/searchAndsorting",
						"/invoices/searchAndSorting", "/send-mails/{invoiceNumber}",
						"/vendortype-receivable/searchAndSorting", "/vendortype-receivablestatus/searchAndSorting",
						"/invoicestatus/searchAndSorting", "/status-count/{adminId}", "/internal",
						"/provision-schema/{schemaName}"

				).permitAll().anyRequest().authenticated()).formLogin(form -> form.disable())
				.httpBasic(httpBasic -> httpBasic.disable());

		return http.build();
	}
}
