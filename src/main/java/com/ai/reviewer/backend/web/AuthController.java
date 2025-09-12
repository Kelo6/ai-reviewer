package com.ai.reviewer.backend.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 认证控制器。
 */
@Controller
public class AuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    /**
     * 登录页面。
     */
    @GetMapping("/login")
    public String login(
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "logout", required = false) String logout,
            Model model) {
        
        logger.debug("Rendering login page: error={}, logout={}", error, logout);
        
        if (error != null) {
            model.addAttribute("error", "用户名或密码错误");
        }
        
        if (logout != null) {
            model.addAttribute("message", "您已成功退出登录");
        }
        
        return "login";
    }
}
