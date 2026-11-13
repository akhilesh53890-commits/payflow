package com.payflow.idempotency.service;

import com.payflow.common.exception.ConflictException;
import com.payflow.idempotency.domain.IdempotencyRecord;
import com.payflow.idempotency.repository.IdempotencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRepository idempotencyRepository;
    private final RequestHasher requestHasher;

    public IdempotencyService(IdempotencyRepository idempotencyRepository, RequestHasher requestHasher) {
        this.idempotencyRepository = idempotencyRepository;
        this.requestHasher = requestHasher;
    }

    public record CachedResponse(int statusCode, String responseBody) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<CachedResponse> startOrCheck(String merchantId, String idempotencyKey, Object payload) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }

        String requestHash = requestHasher.computeHash(payload);

        Optional<IdempotencyRecord> existing = idempotencyRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (!record.getRequestHash().equals(requestHash)) {
                log.warn("Idempotency conflict: key={} storedHash={} incomingHash={}",
                        idempotencyKey, record.getRequestHash(), requestHash);
                throw new ConflictException("IDEMPOTENCY_KEY_PAYLOAD_MISMATCH",
                        "Idempotency key was previously used with a different request payload.");
            }

            if ("COMPLETED".equals(record.getStatus())) {
                log.info("Idempotency HIT: returning cached response for key={}", idempotencyKey);
                return Optional.of(new CachedResponse(record.getResponseCode(), record.getResponseBody()));
            }

            if ("IN_PROGRESS".equals(record.getStatus())) {
                throw new ConflictException("CONCURRENT_REQUEST",
                        "A request with this idempotency key is currently in progress. Please retry shortly.");
            }
        }

        try {
            Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
            IdempotencyRecord newRecord = new IdempotencyRecord(merchantId, idempotencyKey, requestHash, expiresAt);
            idempotencyRepository.saveAndFlush(newRecord);
            return Optional.empty();
        } catch (DataIntegrityViolationException ex) {
            // Concurrency catch: another thread inserted the key simultaneously
            IdempotencyRecord raceRecord = idempotencyRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey)
                    .orElseThrow(() -> new ConflictException("CONCURRENT_REQUEST", "Concurrent request detected."));
            if (!raceRecord.getRequestHash().equals(requestHash)) {
                throw new ConflictException("IDEMPOTENCY_KEY_PAYLOAD_MISMATCH",
                        "Idempotency key was previously used with a different request payload.");
            }
            if ("COMPLETED".equals(raceRecord.getStatus())) {
                return Optional.of(new CachedResponse(raceRecord.getResponseCode(), raceRecord.getResponseBody()));
            }
            throw new ConflictException("CONCURRENT_REQUEST",
                    "A concurrent request with this idempotency key is in progress.");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String merchantId, String idempotencyKey, int statusCode, String responseBody) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }

        idempotencyRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey).ifPresent(record -> {
            record.setStatus("COMPLETED");
            record.setResponseCode(statusCode);
            record.setResponseBody(responseBody);
            idempotencyRepository.save(record);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(String merchantId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }

        idempotencyRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey).ifPresent(record -> {
            record.setStatus("FAILED");
            idempotencyRepository.save(record);
        });
    }
}

