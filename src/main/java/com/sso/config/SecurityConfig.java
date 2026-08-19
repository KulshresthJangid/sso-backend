package com.sso.config;

import com.sso.auth.TenantAwareUserDetailsService;
import com.sso.tenant.TenantResolutionFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;


@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.issuer-base-url:http://localhost:9000}")
    private String issuerBaseUrl;

    /**
     * Filter chain 1 — OAuth2 Authorization Server endpoints.
     * Handles: /oauth2/authorize, /oauth2/token, /oauth2/jwks, etc.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authServerFilterChain(
            HttpSecurity http,
            TenantResolutionFilter tenantFilter,
            TenantAwareUserDetailsService userDetailsService) throws Exception {

        // Apply default Spring AS security — this also sets a securityMatcher
        // limited to bare /oauth2/** paths. We extend it to also capture
        // tenant-prefixed paths like /{slug}/oauth2/** so filter chain selection
        // happens before TenantResolutionFilter strips the slug.
        OAuth2AuthorizationServerConfigurer authServerConfigurer =
                new OAuth2AuthorizationServerConfigurer();
        authServerConfigurer.oidc(oidc -> {});
        http.apply(authServerConfigurer);

        RequestMatcher endpointsMatcher = authServerConfigurer.getEndpointsMatcher();
        RequestMatcher tenantOAuthMatcher = new AntPathRequestMatcher("/*/oauth2/**");
        RequestMatcher tenantScopedMatcher = new OrRequestMatcher(
                endpointsMatcher,
                tenantOAuthMatcher,
                new AntPathRequestMatcher("/*/login"),
                new AntPathRequestMatcher("/.well-known/**")
        );
        http.securityMatcher(tenantScopedMatcher);

        http
                .cors(Customizer.withDefaults())
                // Exempt tenant-prefixed OAuth2 paths and login posts from CSRF.
                // The React login form submits without a CSRF token so /*/login must be exempt.
                .csrf(csrf -> csrf.ignoringRequestMatchers(tenantOAuthMatcher,
                        new AntPathRequestMatcher("/*/login")))
                // Redirect unauthenticated users to /{slug}/login so TenantResolutionFilter
                // can set TenantContext before the login form is processed.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new TenantAwareAuthenticationEntryPoint()))
                // Default RequestCache saves the post-tenantFilter (slug-stripped) request,
                // so the post-login redirect loses the /{slug} prefix — see
                // TenantAwareRequestCache for why.
                .requestCache(cache -> cache.requestCache(new TenantAwareRequestCache()))
                // Must run AFTER CsrfFilter (so CSRF sees original /slug/oauth2/** path
                // and the exemption matches) but BEFORE any OAuth2 AS endpoint filters
                // so they see the stripped /oauth2/... path.
                .addFilterAfter(tenantFilter, CsrfFilter.class)
                // Process login form submissions for /{slug}/login (stripped to /login by
                // TenantResolutionFilter so UsernamePasswordAuthenticationFilter matches).
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/dashboard", false)
                        // Default failure handler redirects to a hardcoded bare
                        // /login?error, losing the tenant slug — see
                        // TenantAwareAuthenticationFailureHandler.
                        .failureHandler(new TenantAwareAuthenticationFailureHandler())
                        .permitAll()
                )
                .userDetailsService(userDetailsService)
                // AuthorizationFilter blocks unauthenticated requests to /oauth2/authorize.
                // ExceptionTranslationFilter catches InsufficientAuthenticationException
                // and calls TenantAwareAuthenticationEntryPoint → redirects to /{slug}/login.
                // /login itself must be permitted so the login page is accessible.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(new AntPathRequestMatcher("/login")).permitAll()
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    /**
     * Filter chain 2 — resource server for /api/me/** and /api/admin/** endpoints.
     * These accept Bearer JWTs issued by this SSO; the Lumen frontend and backend
     * call them directly with the user's or service's access token.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain resourceServerFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/me/**", "/api/admin/**")
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwkSetUri(issuerBaseUrl + "/oauth2/jwks"))
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                );
        return http.build();
    }

    /**
     * Filter chain 3 — default security (login page, form submit, admin API endpoints).
     */
    @Bean
    @Order(3)
    public SecurityFilterChain defaultFilterChain(
            HttpSecurity http,
            TenantAwareUserDetailsService userDetailsService,
            TenantResolutionFilter tenantFilter) throws Exception {

        http
                .cors(Customizer.withDefaults())
                .addFilterBefore(tenantFilter, UsernamePasswordAuthenticationFilter.class)
                // Same slug-loss issue as chain 1 — see TenantAwareRequestCache.
                .requestCache(cache -> cache.requestCache(new TenantAwareRequestCache()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/orgs").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/signup").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/signup/claim").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/orgs/*").permitAll()
                        // Frontend fetches this at runtime (no session yet) to skin itself
                        // per-brand — see BrandController.getConfig().
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/brands/*/config").permitAll()
                        .requestMatchers("/login", "/error").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/dashboard", false)
                        // Default failure handler redirects to a hardcoded bare
                        // /login?error, losing the tenant slug — see
                        // TenantAwareAuthenticationFailureHandler.
                        .failureHandler(new TenantAwareAuthenticationFailureHandler())
                        .permitAll()
                )
                .userDetailsService(userDetailsService)
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/**", "/login", "/*/login", "/oauth2/**", "/*/oauth2/**") // REST API and stateless routes
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
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*")); // Allow all origins
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}
