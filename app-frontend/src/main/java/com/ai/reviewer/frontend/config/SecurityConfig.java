package com.ai.reviewer.frontend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * Spring Security 配置。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Value("${app.security.enabled:true}")
    private boolean securityEnabled;
    
    @Value("${app.security.default-user:admin}")
    private String defaultUsername;
    
    @Value("${app.security.default-password:password}")
    private String defaultPassword;
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        if (!securityEnabled) {
            // 如果安全功能被禁用，允许所有请求
            http.authorizeHttpRequests(authz -> authz.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
            return http.build();
        }
        
        http.authorizeHttpRequests(authz -> authz
                // 静态资源和健康检查端点不需要认证
                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**", "/actuator/health").permitAll()
                // 登录页面不需要认证
                .requestMatchers("/login", "/logout").permitAll()
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
                .maxSessionsPreventsLogins(false)
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/download/**") // 下载请求不需要CSRF
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
