package com.wms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.common.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @ConfigurationProperties(prefix = "app.cors")
    @Bean
    public CorsProps corsProps() {
        return new CorsProps();
    }

    public static class CorsProps {
        private List<String> allowedOriginPatterns = List.of("*");
        public List<String> getAllowedOriginPatterns() { return allowedOriginPatterns; }
        public void setAllowedOriginPatterns(List<String> v) { this.allowedOriginPatterns = v; }
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsProps corsProps) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource(corsProps)))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/interface/**").permitAll()
                        .requestMatchers("/api/esl/callback/**").permitAll()
                        .requestMatchers("/api/users/**").hasAuthority("system:user")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .headers(headers -> headers.frameOptions(frame -> frame.disable()));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    private AuthenticationEntryPoint authenticationEntryPoint() {
        // code=401：前端按 code 判断登录态失效，不依赖文案
        return (request, response, ex) -> writeJson(response, Result.fail(401, "未登录或登录已过期"));
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> writeJson(response, Result.fail(403, "无权限访问"));
    }

    private void writeJson(HttpServletResponse response, Result<?> result) throws IOException {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }

    private CorsConfigurationSource corsConfigurationSource(CorsProps corsProps) {
        CorsConfiguration cfg = new CorsConfiguration();
        // 兜底：Spring AntPathMatcher 对端口匹配和无端口 origin 处理不一致，
        // 直接硬编码 "*" 保证 IDEA 跑和 jar 跑都能通
        cfg.setAllowedOriginPatterns(List.of("*"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowedMethods(List.of("*"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
