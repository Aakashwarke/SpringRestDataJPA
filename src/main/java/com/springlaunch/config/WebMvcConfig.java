package com.springlaunch.config;

import com.springlaunch.usage.EntitlementService;
import com.springlaunch.usage.UsageMeteringInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final EntitlementService entitlementService;

    public WebMvcConfig(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UsageMeteringInterceptor(entitlementService))
                .addPathPatterns("/api/v1/**")
                // Sign-in, billing and the tenant's own usage dashboard are never billable:
                // a customer who has hit their cap must still be able to log in and upgrade.
                .excludePathPatterns(
                        "/api/v1/auth/**",
                        "/api/v1/billing/**",
                        "/api/v1/usage/**",
                        "/api/v1/plans");
    }
}
