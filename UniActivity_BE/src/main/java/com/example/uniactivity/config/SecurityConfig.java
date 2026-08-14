package com.example.uniactivity.config;

import com.example.uniactivity.security.CustomAuthenticationSuccessHandler;
import com.example.uniactivity.security.CustomOAuth2AuthenticationFailureHandler;
import com.example.uniactivity.security.CustomOAuth2UserService;
import com.example.uniactivity.security.CustomUserDetailsService;
import com.example.uniactivity.security.JwtAuthenticationFilter;
import com.example.uniactivity.security.RestAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;

    @Autowired
    private CustomOAuth2UserService customOAuth2UserService;

    @Autowired
    private CustomOAuth2AuthenticationFailureHandler customOAuth2FailureHandler;

    @Autowired
    private RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    // CORS config cho React frontend dev server (port 5173)
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Bật CORS cho React frontend
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf
                // Disable CSRF cho các API endpoints, auth endpoints (React FE), và SSE
                .ignoringRequestMatchers(
                    "/login",       // React frontend POST login
                    "/register",    // React frontend POST register
                    "/admin/faculties/api/**",
                    "/admin/academic-years/api/**",
                    "/admin/classes/api/**",
                    "/admin/semesters/api/**",
                    "/admin/users/api/**",
                    "/admin/activities/api/**",
                    "/admin/api/**",
                    "/manager/api/**",
                    "/student/api/**",
                    "/api/auth/**",
                    "/api/profile/**",
                    "/sse/**"
                )
            )
            // Cho phép nhiều session đồng thời (multi-tab, incognito)
            .sessionManagement(session -> session
                .maximumSessions(-1)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/register", "/login", "/css/**", "/js/**", "/images/**",
                    "/uploads/activities/**",
                    "/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/api/auth/oauth2/exchange",
                    "/api/auth/send-verification-email", "/api/auth/verify-email",
                    "/api/auth/forgot-password", "/api/auth/verify-reset-otp", "/api/auth/reset-password",
                    "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                    "/oauth2/**", "/error", "/terms"
                ).permitAll()
                .requestMatchers("/api/auth/logout-jwt", "/api/auth/me").authenticated()
                .requestMatchers("/sse/subscribe").permitAll()
                .requestMatchers("/sse/ticket", "/sse/status").authenticated()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/manager/**").hasRole("MANAGER")
                .requestMatchers("/student/**").hasRole("STUDENT")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(customAuthenticationSuccessHandler)
                .permitAll()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                )
                .successHandler(customAuthenticationSuccessHandler)
                // Khi đăng nhập Google thất bại, redirect về React frontend thay vì Thymeleaf
                .failureHandler(customOAuth2FailureHandler)
            )
            // Trả 401 JSON cho API requests thay vì redirect tới /login
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(restAuthenticationEntryPoint)
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            // === JWT Filter: chạy TRƯỚC UsernamePasswordAuthenticationFilter ===
            // Nếu request có header "Authorization: Bearer <token>", JWT filter sẽ
            // authenticate user và set SecurityContext TRƯỚC KHI session-based auth chạy.
            // Nếu không có JWT → bỏ qua, session-based auth vẫn hoạt động bình thường.
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
