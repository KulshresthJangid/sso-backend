package com.sso.config;

import com.sso.auth.TenantAwareRegisteredClientRepository;
import com.sso.auth.TenantAwareUserDetailsService;
import com.sso.tenant.TenantResolutionFilter;
import com.sso.token.SSOTokenCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Filter chain 1 — OAuth2 Authorization Server endpoints.
     * Handles: /oauth2/authorize, /oauth2/token, /oauth2/jwks, etc.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authServerFilterChain(
            HttpSecurity http,
            TenantResolutionFilter tenantFilter) throws Exception {

        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);

        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .oidc(oidc -> {}); // Enable OIDC (UserInfo endpoint)

        http
                // Redirect to /login if not authenticated
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(
                                new LoginUrlAuthenticationEntryPoint("/login")))
                // Register tenant filter BEFORE the standard auth filters
                .addFilterBefore(tenantFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Filter chain 2 — default security (login page, form submit, API endpoints).
     */
    @Bean
    @Order(2)
    public SecurityFilterChain defaultFilterChain(
            HttpSecurity http,
            TenantAwareUserDetailsService userDetailsService,
            TenantResolutionFilter tenantFilter) throws Exception {

        http
                .addFilterBefore(tenantFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/orgs", "/api/orgs/**").permitAll() // public signup
                        .requestMatchers("/login", "/error").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                )
                .userDetailsService(userDetailsService)
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/**") // REST API is stateless
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        // Standard endpoints — tenant prefix is stripped by TenantResolutionFilter
        return AuthorizationServerSettings.builder().build();
    }

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer(SSOTokenCustomizer customizer) {
        return customizer;
    }
}
