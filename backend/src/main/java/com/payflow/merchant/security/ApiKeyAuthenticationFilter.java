package com.payflow.merchant.security;

import com.payflow.identity.security.PayflowUserPrincipal;
import com.payflow.merchant.domain.Merchant;
import com.payflow.merchant.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Only authenticate if not already authenticated
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String apiKeyHeader = request.getHeader("X-API-Key");
            if (apiKeyHeader == null || apiKeyHeader.isBlank()) {
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("ApiKey ")) {
                    apiKeyHeader = authHeader.substring(7).trim();
                }
            }

            if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
                Optional<Merchant> optMerchant = apiKeyService.authenticateApiKey(apiKeyHeader);
                if (optMerchant.isPresent()) {
                    Merchant merchant = optMerchant.get();
                    PayflowUserPrincipal principal = new PayflowUserPrincipal(
                            merchant.getUserId(),
                            merchant.getId() + "@merchant.payflow.internal",
                            "MERCHANT",
                            merchant.getId()
                    );
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}

