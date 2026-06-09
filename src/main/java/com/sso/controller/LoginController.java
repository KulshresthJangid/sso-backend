package com.sso.controller;

import com.sso.tenant.TenantContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the login page rendered by Thymeleaf.
 * Spring Security handles the actual POST /login submission.
 * TenantResolutionFilter strips any /{slug} prefix before this runs,
 * but sets TenantContext so we can show the org slug on the page.
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String loginPage(Model model) {
        var org = TenantContext.get();
        if (org != null) {
            model.addAttribute("slug", org.getSlug());
        }
        return "login";
    }
}
