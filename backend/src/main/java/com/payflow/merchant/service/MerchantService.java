package com.payflow.merchant.service;

import com.payflow.common.exception.ResourceNotFoundException;
import com.payflow.merchant.domain.Merchant;
import com.payflow.merchant.dto.MerchantDto;
import com.payflow.merchant.repository.MerchantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class MerchantService {

    private final MerchantRepository merchantRepository;

    public MerchantService(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
    }

    @Transactional
    public Merchant createMerchant(String userId, String businessName) {
        Merchant merchant = new Merchant(userId, businessName);
        return merchantRepository.save(merchant);
    }

    @Transactional(readOnly = true)
    public MerchantDto getMerchantDto(String merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found: " + merchantId));
        return MerchantDto.from(merchant);
    }

    @Transactional(readOnly = true)
    public Optional<Merchant> findByUserId(String userId) {
        return merchantRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Merchant getById(String merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found: " + merchantId));
    }

    @Transactional
    public MerchantDto updateWebhookConfig(String merchantId, String webhookUrl) {
        Merchant merchant = getById(merchantId);
        merchant.setWebhookUrl(webhookUrl);
        merchant.setUpdatedAt(Instant.now());
        return MerchantDto.from(merchantRepository.save(merchant));
    }

    @Transactional
    public MerchantDto toggleSandboxMode(String merchantId) {
        Merchant merchant = getById(merchantId);
        merchant.setSandboxMode(!merchant.isSandboxMode());
        merchant.setUpdatedAt(Instant.now());
        return MerchantDto.from(merchantRepository.save(merchant));
    }
}

