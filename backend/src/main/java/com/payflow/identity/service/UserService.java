package com.payflow.identity.service;

import com.payflow.common.exception.ConflictException;
import com.payflow.common.exception.ResourceNotFoundException;
import com.payflow.common.exception.UnauthorizedException;
import com.payflow.identity.domain.RefreshToken;
import com.payflow.identity.domain.Role;
import com.payflow.identity.domain.User;
import com.payflow.identity.dto.*;
import com.payflow.identity.repository.RefreshTokenRepository;
import com.payflow.identity.repository.UserRepository;
import com.payflow.merchant.service.MerchantService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MerchantService merchantService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshTokenExpirationSeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            MerchantService merchantService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${payflow.security.jwt.refresh-token-expiration-seconds:604800}") long refreshTokenExpirationSeconds
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.merchantService = merchantService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenExpirationSeconds = refreshTokenExpirationSeconds;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("USER_EMAIL_EXISTS", "A user with email " + request.email() + " already exists.");
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = new User(request.email(), passwordHash, request.role());
        User savedUser = userRepository.save(user);

        String merchantId = null;
        if (request.role() == Role.MERCHANT) {
            String bName = (request.businessName() != null && !request.businessName().isBlank())
                    ? request.businessName()
                    : "Merchant " + request.email().split("@")[0];
            merchantId = merchantService.createMerchant(savedUser.getId(), bName).getId();
        }

        return issueTokens(savedUser, merchantId);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password.");
        }

        String merchantId = null;
        if (user.getRole() == Role.MERCHANT) {
            merchantId = merchantService.findByUserId(user.getId())
                    .map(m -> m.getId())
                    .orElse(null);
        }

        return issueTokens(user, merchantId);
    }

    @Transactional
    public TokenResponse refreshToken(String rawRefreshToken) {
        String tokenHash = hashToken(rawRefreshToken);
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token."));

        if (refreshToken.isRevoked() || refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token is expired or revoked.");
        }

        // Revoke the old token (Rotation)
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        User user = userRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found for token."));

        String merchantId = null;
        if (user.getRole() == Role.MERCHANT) {
            merchantId = merchantService.findByUserId(user.getId())
                    .map(m -> m.getId())
                    .orElse(null);
        }

        return issueTokens(user, merchantId);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String tokenHash = hashToken(rawRefreshToken);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
            });
        }
    }

    @Transactional(readOnly = true)
    public UserDto getUserDto(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        return UserDto.from(user);
    }

    private TokenResponse issueTokens(User user, String merchantId) {
        Map<String, Object> claims = new HashMap<>();
        if (merchantId != null) {
            claims.put("merchantId", merchantId);
        }

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name(), claims);

        // Generate high-entropy refresh token
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawRefreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        String tokenHash = hashToken(rawRefreshToken);
        Instant expiry = Instant.now().plusSeconds(refreshTokenExpirationSeconds);
        RefreshToken refreshTokenEntity = new RefreshToken(user.getId(), tokenHash, expiry);
        refreshTokenRepository.save(refreshTokenEntity);

        return TokenResponse.of(accessToken, rawRefreshToken, 900, UserDto.from(user));
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash refresh token", e);
        }
    }
}

