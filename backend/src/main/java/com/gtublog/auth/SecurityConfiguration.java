package com.gtublog.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            JwtDecoder jwtDecoder,
            ObjectMapper objectMapper) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/api/v1/internal/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/session").authenticated()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .headers(headers -> headers
                        .contentSecurityPolicy(policy -> policy.policyDirectives(
                                "default-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"))
                        .referrerPolicy(referrer -> referrer.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", "geolocation=(), microphone=(), camera=()")))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(problemAccessDeniedHandler(objectMapper)));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AuthProperties authProperties) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(authProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-CSRF-Token"));
        configuration.setExposedHeaders(List.of("Set-Cookie"));
        configuration.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        var provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    UserDetailsService userDetailsService(AdminUserRepository adminUserRepository) {
        return username -> adminUserRepository.findByUsername(username)
                .filter(AdminUser::isActive)
                .map(admin -> User.withUsername(admin.getUsername())
                        .password(admin.getPasswordHash())
                        .roles("ADMIN")
                        .build())
                .orElseThrow(AuthExceptions::invalidCredentials);
    }

    @Bean
    JwtEncoder jwtEncoder(AuthProperties authProperties, PemKeyLoader pemKeyLoader) {
        var privateKey = pemKeyLoader.readPrivateKey(authProperties.keys().privateKeyPem());
        var publicKey = pemKeyLoader.readPublicKey(authProperties.keys().publicKeyPem());
        var rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("gtublog-active").build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
    }

    @Bean
    JwtDecoder jwtDecoder(AuthProperties authProperties, PemKeyLoader pemKeyLoader) {
        var publicKey = pemKeyLoader.readPublicKey(authProperties.keys().publicKeyPem());
        var decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(authProperties.issuer()),
                audienceValidator(authProperties.audience())));
        return decoder;
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    private AccessDeniedHandler problemAccessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, exception) -> {
            response.setStatus(403);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            objectMapper.writeValue(
                    response.getWriter(),
                    Map.of(
                            "type", "https://gtublog.dev/problems/403",
                            "title", "Access denied",
                            "status", 403,
                            "detail", "You do not have permission to access this resource."));
        };
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        return converter;
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        var roles = jwt.getClaimAsStringList("roles");
        if (roles == null) {
            return List.of();
        }
        return roles.stream().map(SimpleGrantedAuthority::new).map(GrantedAuthority.class::cast).toList();
    }

    private OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
        return jwt -> jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience.", null));
    }
}
