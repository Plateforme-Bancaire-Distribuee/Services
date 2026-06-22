// java
package com.banking.customer_service.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

@Configuration
public class FeignConfig {

    /**
     * Propage le JWT sortant vers les appels Feign inter-services.
     * - priorité à SecurityContextHolder si le token est présent.
     * - fallback : copie l'en-tête Authorization de la requête HTTP courante.
     */
    @Bean
    public RequestInterceptor jwtFeignInterceptor() {
        return requestTemplate -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getCredentials() instanceof String token && token != null && !token.isBlank()) {
                requestTemplate.header("Authorization", "Bearer " + token);
                return;
            }

            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return;
            HttpServletRequest request = attrs.getRequest();
            if (request == null) return;
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && !authHeader.isBlank()) {
                requestTemplate.header("Authorization", authHeader);
            }
        };
    }
}
