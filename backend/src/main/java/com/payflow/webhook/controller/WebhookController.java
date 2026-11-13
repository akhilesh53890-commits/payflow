package com.payflow.webhook.controller;

import com.payflow.common.PageResponse;
import com.payflow.common.exception.ResourceNotFoundException;
import com.payflow.webhook.domain.WebhookDelivery;
import com.payflow.webhook.domain.WebhookEndpoint;
import com.payflow.webhook.repository.WebhookDeliveryRepository;
import com.payflow.webhook.repository.WebhookEndpointRepository;
import com.payflow.webhook.service.WebhookDispatcher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Webhooks", description = "Endpoints for merchant outbound webhook monitoring and manual redeliveries")
public class WebhookController {

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDispatcher dispatcher;

    public WebhookController(
            WebhookDeliveryRepository deliveryRepository,
            WebhookEndpointRepository endpointRepository,
            WebhookDispatcher dispatcher
    ) {
        this.deliveryRepository = deliveryRepository;
        this.endpointRepository = endpointRepository;
        this.dispatcher = dispatcher;
    }

    @GetMapping("/deliveries")
    @Operation(summary = "List paginated webhook delivery attempt records")
    public ResponseEntity<PageResponse<WebhookDelivery>> listDeliveries(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(deliveryRepository.findAllByOrderByCreatedAtDesc(pageable)));
    }

    @PostMapping("/deliveries/{id}/redeliver")
    @Transactional
    @Operation(summary = "Manually trigger redelivery of an event")
    public ResponseEntity<WebhookDelivery> redeliver(@PathVariable String id) {
        WebhookDelivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook delivery not found: " + id));

        WebhookEndpoint endpoint = endpointRepository.findById(delivery.getWebhookEndpointId())
                .orElseThrow(() -> new ResourceNotFoundException("Webhook endpoint not found: " + delivery.getWebhookEndpointId()));

        delivery.setAttemptNumber(delivery.getAttemptNumber() + 1);
        dispatcher.executeDelivery(delivery, endpoint.getUrl(), delivery.getSignature());

        return ResponseEntity.ok(delivery);
    }
}

