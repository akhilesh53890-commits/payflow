package com.payflow.webhook.security;

import com.payflow.common.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;

@Component
public class SsrfProtectionValidator {

    public void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new ValidationException("Webhook URL cannot be blank.");
        }

        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new ValidationException("Webhook URL scheme must be HTTP or HTTPS.");
            }

            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new ValidationException("Invalid webhook URL host: " + url);
            }

            // Resolve DNS
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                        || address.isAnyLocalAddress()) {
                    throw new ValidationException("SSRF Protection: Webhook destination resolves to private/loopback address: " + address.getHostAddress());
                }

                // Check cloud metadata service (169.254.169.254)
                byte[] bytes = address.getAddress();
                if (bytes.length == 4) {
                    int b0 = bytes[0] & 0xFF;
                    int b1 = bytes[1] & 0xFF;
                    if (b0 == 169 && b1 == 254) {
                        throw new ValidationException("SSRF Protection: Webhook destination resolves to link-local metadata address.");
                    }
                }
            }
        } catch (ValidationException ve) {
            throw ve;
        } catch (Exception ex) {
            throw new ValidationException("Failed to validate webhook URL host: " + ex.getMessage());
        }
    }
}

