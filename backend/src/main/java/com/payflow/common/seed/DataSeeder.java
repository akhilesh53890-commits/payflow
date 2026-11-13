package com.payflow.common.seed;

import com.payflow.identity.domain.Role;
import com.payflow.identity.domain.User;
import com.payflow.identity.repository.UserRepository;
import com.payflow.ledger.domain.AccountType;
import com.payflow.ledger.service.LedgerService;
import com.payflow.merchant.domain.ApiKey;
import com.payflow.merchant.domain.Merchant;
import com.payflow.merchant.repository.ApiKeyRepository;
import com.payflow.merchant.repository.MerchantRepository;
import com.payflow.merchant.service.ApiKeyService;
import com.payflow.payment.dto.PaymentDtos.CreatePaymentRequest;
import com.payflow.payment.dto.PaymentDtos.PaymentDto;
import com.payflow.payment.service.PaymentService;
import com.payflow.refund.dto.RefundDtos.CreateRefundRequest;
import com.payflow.refund.service.RefundService;
import com.payflow.webhook.domain.WebhookEndpoint;
import com.payflow.webhook.repository.WebhookEndpointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final WebhookEndpointRepository webhookEndpointRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApiKeyService apiKeyService;
    private final LedgerService ledgerService;
    private final PaymentService paymentService;
    private final RefundService refundService;

    @Value("${payflow.seed.enabled:true}")
    private boolean seedEnabled;

    public DataSeeder(
            UserRepository userRepository,
            MerchantRepository merchantRepository,
            ApiKeyRepository apiKeyRepository,
            WebhookEndpointRepository webhookEndpointRepository,
            PasswordEncoder passwordEncoder,
            ApiKeyService apiKeyService,
            LedgerService ledgerService,
            PaymentService paymentService,
            RefundService refundService
    ) {
        this.userRepository = userRepository;
        this.merchantRepository = merchantRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.webhookEndpointRepository = webhookEndpointRepository;
        this.passwordEncoder = passwordEncoder;
        this.apiKeyService = apiKeyService;
        this.ledgerService = ledgerService;
        this.paymentService = paymentService;
        this.refundService = refundService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled || userRepository.existsByEmail("admin@payflow.local")) {
            log.info("Data seeding skipped (already initialized or disabled).");
            return;
        }

        log.info("=================================================================");
        log.info("PAYFLOW DEMO DATA SEEDING: Creating sandbox accounts & scenarios");
        log.info("=================================================================");

        // 1. Create Default Users
        User admin = new User("admin@payflow.local", passwordEncoder.encode("AdminPass123!"), Role.ADMIN);
        userRepository.save(admin);

        User merchantUser = new User("merchant@acme.local", passwordEncoder.encode("MerchantPass123!"), Role.MERCHANT);
        User savedMerchantUser = userRepository.save(merchantUser);

        User customer = new User("customer@buyer.local", passwordEncoder.encode("CustomerPass123!"), Role.CUSTOMER);
        userRepository.save(customer);

        // 2. Create Acme Merchant Profile
        Merchant merchant = new Merchant(savedMerchantUser.getId(), "Acme Corp eCommerce");
        merchant.setWebhookUrl("https://httpbin.org/post");
        Merchant savedMerchant = merchantRepository.save(merchant);

        // 3. Create Seeded API Key
        // Public: pk_test_acme_live_public_key, Secret: sk_test_acme_live_secret_key_12345
        String rawSecret = "sk_test_acme_live_secret_key_12345";
        String hashedSecret = apiKeyService.hashSecret(rawSecret);
        ApiKey apiKey = new ApiKey(
                savedMerchant.getId(),
                "pk_test_acme_live_public_key",
                hashedSecret,
                "Default Production Sandbox Key",
                Instant.now().plus(365, ChronoUnit.DAYS)
        );
        apiKeyRepository.save(apiKey);

        // 4. Register Webhook Endpoint
        WebhookEndpoint endpoint = new WebhookEndpoint(
                savedMerchant.getId(),
                "https://httpbin.org/post",
                savedMerchant.getWebhookSecret()
        );
        webhookEndpointRepository.save(endpoint);

        // 5. Setup Base Ledger Accounts
        ledgerService.getOrCreateAccount(null, "1010-CUST-FUNDS-USD", "Customer Transit Funds USD", AccountType.ASSET, "USD");
        ledgerService.getOrCreateAccount(savedMerchant.getId(), "2010-MERCHANT-" + savedMerchant.getId() + "-USD", "Merchant Settlement USD", AccountType.LIABILITY, "USD");
        ledgerService.getOrCreateAccount(null, "1010-CUST-FUNDS-EUR", "Customer Transit Funds EUR", AccountType.ASSET, "EUR");
        ledgerService.getOrCreateAccount(savedMerchant.getId(), "2010-MERCHANT-" + savedMerchant.getId() + "-EUR", "Merchant Settlement EUR", AccountType.LIABILITY, "EUR");

        // 6. Seed Demo Scenario Payments
        log.info("Seeding Payment Scenario 1: Standard Successful Payment ($150.00 USD)...");
        paymentService.processPayment(
                savedMerchant.getId(),
                "idem_seed_success_1",
                new CreatePaymentRequest(new BigDecimal("150.00"), "USD", "alice@example.com", "Order #1001", "CARD"),
                "seed-runner",
                "127.0.0.1"
        );

        log.info("Seeding Payment Scenario 2: Card Declined Payment ($49.99 USD ending in .99)...");
        paymentService.processPayment(
                savedMerchant.getId(),
                "idem_seed_decline_1",
                new CreatePaymentRequest(new BigDecimal("49.99"), "USD", "bob@example.com", "Order #1002 (Insufficient funds)", "CARD"),
                "seed-runner",
                "127.0.0.1"
        );

        log.info("Seeding Payment Scenario 3: Provider Ambiguous Timeout ($250.54 USD ending in .54)...");
        paymentService.processPayment(
                savedMerchant.getId(),
                "idem_seed_timeout_1",
                new CreatePaymentRequest(new BigDecimal("250.54"), "USD", "charlie@example.com", "Order #1003 (Gateway timeout)", "CARD"),
                "seed-runner",
                "127.0.0.1"
        );

        log.info("Seeding Payment Scenario 4: Partially Refunded Payment ($300.00 USD)...");
        PaymentDto p4 = paymentService.processPayment(
                savedMerchant.getId(),
                "idem_seed_refund_base",
                new CreatePaymentRequest(new BigDecimal("300.00"), "USD", "diana@example.com", "Order #1004 (Electronics)", "CARD"),
                "seed-runner",
                "127.0.0.1"
        );
        refundService.processRefund(
                savedMerchant.getId(),
                "idem_seed_refund_partial",
                new CreateRefundRequest(p4.id(), new BigDecimal("100.00"), "Customer returned accessory"),
                "seed-runner",
                "127.0.0.1"
        );

        log.info("Seeding Payment Scenario 5: High Amount Flagged for Review ($12,500.00 USD)...");
        paymentService.processPayment(
                savedMerchant.getId(),
                "idem_seed_review_1",
                new CreatePaymentRequest(new BigDecimal("12500.00"), "USD", "highroller@example.com", "B2B Bulk Order", "CARD"),
                "seed-runner",
                "127.0.0.1"
        );

        log.info("=================================================================");
        log.info("DEMO SEEDING COMPLETE!");
        log.info("Admin User:    admin@payflow.local / AdminPass123!");
        log.info("Merchant User: merchant@acme.local / MerchantPass123!");
        log.info("API Public:    pk_test_acme_live_public_key");
        log.info("API Secret:    sk_test_acme_live_secret_key_12345");
        log.info("=================================================================");
    }
}

