package com.ai.reviewer.backend.web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置 for web interface.
 */
@Configuration
@EnableWebSecurity
public class WebSecurityConfig {
    
    @Value("${app.security.enabled:false}")
    private boolean securityEnabled;
    
    @Value("${app.security.default-user:admin}")
    private String defaultUsername;
    
    @Value("${app.security.default-password:password}")
    private String defaultPassword;
    
    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        if (!securityEnabled) {
            // 如果安全功能被禁用，允许所有API请求
            http.securityMatcher("/api/**")
                .authorizeHttpRequests(authz -> authz.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
            return http.build();
        }
        
        // API endpoints (REST API) - separate security config
        http.securityMatcher("/api/**")
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/health").permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic(basic -> {})
            .csrf(csrf -> csrf.disable());
        
        return http.build();
    }
    
    @Bean
    @Order(2) 
    public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
        if (!securityEnabled) {
            // 如果安全功能被禁用，允许所有请求
            http.securityMatcher("/**")
                .authorizeHttpRequests(authz -> authz.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
            return http.build();
        }
        
        http.securityMatcher("/**")
            .authorizeHttpRequests(authz -> authz
                // 静态资源和健康检查端点不需要认证
                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico", "/actuator/health").permitAll()
                // 登录页面不需要认证
                .requestMatchers("/login", "/logout").permitAll()
                // API endpoints handled by different filter chain
                .requestMatchers("/api/**").permitAll()
                // 其他所有请求需要认证
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .sessionManagement(session -> session
                .maximumSessions(1)
                .maxSessionsPreventsLogin(false)
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/download/**", "/api/**", "/config/scm/**") // 下载请求、API和配置不需要CSRF
            );
        
        return http.build();
    }
    
    @Bean
    public UserDetailsService userDetailsService() {
        if (!securityEnabled) {
            // 如果安全功能被禁用，返回空的用户服务
            return new InMemoryUserDetailsManager();
        }
        
        UserDetails admin = User.builder()
            .username(defaultUsername)
            .password(passwordEncoder().encode(defaultPassword))
            .roles("ADMIN", "USER")
            .build();
        
        UserDetails user = User.builder()
            .username("user")
            .password(passwordEncoder().encode("user123"))
            .roles("USER")
            .build();
        
        return new InMemoryUserDetailsManager(admin, user);
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
