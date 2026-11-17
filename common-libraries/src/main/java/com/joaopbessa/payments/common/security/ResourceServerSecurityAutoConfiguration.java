package com.joaopbessa.payments.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Secures a servlet application as an OAuth2 resource server that validates JWTs (the {@code JwtDecoder} comes from
 * the {@code spring.security.oauth2.resourceserver.jwt.*} properties).
 *
 * <ul>
 * <li>Authorization of each endpoint is declared with {@code @PreAuthorize} on the controller methods; the roles come
 * from the {@link TokenClaims#ROLES} claim, mapped to {@code ROLE_*} authorities.</li>
 * <li>{@code /actuator/health} and {@code /error} are open; {@code /actuator/prometheus} requires
 * {@link CommonRoles#METRICS_READ}; every other request must be authenticated.</li>
 * <li>No session is created. CSRF protection stays on; Spring Security exempts requests that carry a Bearer token,
 * because browsers do not send those on their own.</li>
 * </ul>
 *
 * Backs off when the application declares its own {@link SecurityFilterChain}.
 */
@AutoConfiguration(beforeName = {
        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration" })
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass({ SecurityFilterChain.class, BearerTokenAuthenticationFilter.class })
@ConditionalOnMissingBean(SecurityFilterChain.class)
@EnableWebSecurity
@EnableMethodSecurity
public class ResourceServerSecurityAutoConfiguration {

    private static final String AUTHORITY_PREFIX = "ROLE_";

    @Bean
    SecurityFilterChain resourceServerSecurityFilterChain(HttpSecurity http) {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
                        .requestMatchers("/actuator/prometheus").hasRole(CommonRoles.METRICS_READ)
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(rolesClaimConverter())))
                .build();
    }

    private static JwtAuthenticationConverter rolesClaimConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(TokenClaims.ROLES);
        authorities.setAuthorityPrefix(AUTHORITY_PREFIX);

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

}
