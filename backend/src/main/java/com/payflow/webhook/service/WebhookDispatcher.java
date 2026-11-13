package com.payflow.webhook.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.eventing.service.OutboxPublisher.DomainEventNotification;
import com.payflow.webhook.domain.WebhookDelivery;
import com.payflow.webhook.domain.WebhookEndpoint;
import com.payflow.webhook.repository.WebhookDeliveryRepository;
import com.payflow.webhook.repository.WebhookEndpointRepository;
import com.payflow.webhook.security.SsrfProtectionValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookSignatureService signatureService;
    private final SsrfProtectionValidator ssrfValidator;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final int maxRetries;
    private final long backoffInitialDelayMs;
    private final double backoffMultiplier;

    public WebhookDispatcher(
            WebhookEndpointRepository endpointRepository,
            WebhookDeliveryRepository deliveryRepository,
            WebhookSignatureService signatureService,
            SsrfProtectionValidator ssrfValidator,
            ObjectMapper objectMapper,
            @Value("${payflow.webhook.timeout-ms:5000}") long timeoutMs,
            @Value("${payflow.webhook.max-retries:4}") int maxRetries,
            @Value("${payflow.webhook.backoff-initial-delay-ms:2000}") long backoffInitialDelayMs,
            @Value("${payflow.webhook.backoff-multiplier:2.0}") double backoffMultiplier
    ) {
        this.endpointRepository = endpointRepository;
        this.deliveryRepository = deliveryRepository;
        this.signatureService = signatureService;
        this.ssrfValidator = ssrfValidator;
        this.objectMapper = objectMapper;
        this.maxRetries = maxRetries;
        this.backoffInitialDelayMs = backoffInitialDelayMs;
        this.backoffMultiplier = backoffMultiplier;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    @EventListener
    @Async
    public void onDomainEvent(DomainEventNotification event) {
        try {
            JsonNode root = objectMapper.readTree(event.payload());
            String merchantId = root.has("merchantId") ? root.get("merchantId").asText() : null;
            if (merchantId == null) {
                return;
            }

            List<WebhookEndpoint> endpoints = endpointRepository.findByMerchantId(merchantId);
            for (WebhookEndpoint ep : endpoints) {
                if ("ACTIVE".equalsIgnoreCase(ep.getStatus())) {
                    dispatchToEndpoint(ep, event.eventId(), event.eventType(), event.payload());
                }
            }
        } catch (Exception ex) {
            log.error("Failed to process domain event for webhook dispatch: {}", ex.getMessage());
        }
    }

    @Transactional
    public void dispatchToEndpoint(WebhookEndpoint endpoint, String eventId, String eventType, String payload) {
        String signatureHeader = signatureService.computeSignatureHeader(payload, endpoint.getSecret());
        WebhookDelivery delivery = new WebhookDelivery(endpoint.getId(), eventId, eventType, payload, signatureHeader);
        deliveryRepository.save(delivery);

        executeDelivery(delivery, endpoint.getUrl(), signatureHeader);
    }

    public void executeDelivery(WebhookDelivery delivery, String targetUrl, String signatureHeader) {
        try {
            // SSRF Validation check before initiating outbound connection
            ssrfValidator.validateUrl(targetUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Content-Type", "application/json")
                    .header("X-PayFlow-Signature", signatureHeader)
                    .header("X-PayFlow-Event-Type", delivery.getEventType())
                    .POST(HttpRequest.BodyPublishers.ofString(delivery.getPayload()))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            delivery.setHttpStatus(response.statusCode());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                delivery.setStatus("SUCCESS");
                delivery.setDeliveredAt(Instant.now());
                log.info("Webhook delivered successfully deliveryId={} url={} status={}",
                        delivery.getId(), targetUrl, response.statusCode());
            } else {
                scheduleRetry(delivery, "HTTP " + response.statusCode() + " received from endpoint");
            }
        } catch (Exception ex) {
            scheduleRetry(delivery, "Delivery exception: " + ex.getMessage());
        }

        deliveryRepository.save(delivery);
    }

    private void scheduleRetry(WebhookDelivery delivery, String reason) {
        delivery.setErrorMessage(reason);
        log.warn("Webhook delivery failed id={} attempt={}/{}: {}",
                delivery.getId(), delivery.getAttemptNumber(), maxRetries, reason);

        if (delivery.getAttemptNumber() >= maxRetries) {
            delivery.setStatus("FAILED");
            log.error("Webhook delivery id={} exhausted all {} retries, marked FAILED", delivery.getId(), maxRetries);
        } else {
            delivery.setStatus("PENDING");
            long delay = (long) (backoffInitialDelayMs * Math.pow(backoffMultiplier, delivery.getAttemptNumber() - 1));
            delivery.setNextRetryAt(Instant.now().plusMillis(delay));
            log.info("Scheduled webhook retry for id={} in {} ms", delivery.getId(), delay);
        }
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void retryPendingDeliveries() {
        List<WebhookDelivery> retries = deliveryRepository.findPendingRetries(Instant.now(), PageRequest.of(0, 20));
        for (WebhookDelivery delivery : retries) {
            endpointRepository.findById(delivery.getWebhookEndpointId()).ifPresent(endpoint -> {
                delivery.setAttemptNumber(delivery.getAttemptNumber() + 1);
                executeDelivery(delivery, endpoint.getUrl(), delivery.getSignature());
            });
        }
    }
}

