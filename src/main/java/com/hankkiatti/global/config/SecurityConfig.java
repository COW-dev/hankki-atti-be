package com.hankkiatti.global.config;

import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.global.security.AccountJwtAuthenticationConverter;
import com.hankkiatti.global.security.AuthAuthorities;
import com.hankkiatti.global.security.AuthPaths;
import com.hankkiatti.global.security.AuthProperties;
import com.hankkiatti.global.security.JsonAccessDeniedHandler;
import com.hankkiatti.global.security.JsonAuthenticationEntryPoint;
import com.hankkiatti.global.security.PublicPathBearerTokenResolver;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_URLS = {
            "/actuator/health",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    private final AccountJwtAuthenticationConverter jwtAuthenticationConverter;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;
    private final AuthProperties authProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // 상태 없는 Bearer 인증이라 CSRF 토큰은 쓰지 않는다.
                // 쿠키로 인증하는 refresh·로그아웃은 CORS가 막는다 — 허용 목록에 없는 Origin의 요청은 CorsFilter가 컨트롤러 전에 403으로 거부한다.
                // bluerack.org는 여러 프로젝트가 나눠 쓰는 도메인이라 SameSite 쿠키만으로는 다른 서브도메인발 요청을 막지 못한다
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_URLS).permitAll()
                        .requestMatchers(AuthPaths.publicAuthEndpoints()).permitAll()
                        // 비밀번호 변경 필요 계정은 이 API만 쓸 수 있다
                        .requestMatchers(HttpMethod.PATCH, AuthPaths.USER_PASSWORD).hasAnyAuthority(
                                AuthAuthorities.role(AccountRole.STUDENT),
                                AuthAuthorities.role(AccountRole.HELPER),
                                AuthAuthorities.PASSWORD_CHANGE_ONLY)
                        .requestMatchers("/api/admin/**").hasRole(AccountRole.ADMIN.name())
                        .requestMatchers("/api/**").hasAnyRole(AccountRole.STUDENT.name(), AccountRole.HELPER.name())
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(new PublicPathBearerTokenResolver(AuthPaths.publicAuthEndpoints()))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 정확한 출처만 허용한다 (와일드카드 금지)
        configuration.setAllowedOrigins(authProperties.cors().allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        // refresh 토큰 쿠키를 주고받는다
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
