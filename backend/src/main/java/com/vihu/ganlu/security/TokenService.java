package com.vihu.ganlu.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.vihu.ganlu.entitys.UserEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class TokenService {
    private static final String ISSUER = "ganlu-webpage";
    private static final String RETIRED_DEVELOPMENT_SECRET = "ganlu-local-development-secret-change-me-2026";

    @Value("${security.jwt.secret}")
    private String secret;

    @Value("${security.jwt.expiration-seconds:28800}")
    private long expirationSeconds;

    @PostConstruct
    public void validateConfiguration() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("security.jwt.secret 必须至少包含32字节");
        }
        if (RETIRED_DEVELOPMENT_SECRET.equals(secret)) {
            throw new IllegalStateException("security.jwt.secret 不能使用已废弃的开发密钥");
        }
        if (expirationSeconds <= 0) {
            throw new IllegalStateException("security.jwt.expiration-seconds 必须大于0");
        }
    }

    public String createToken(UserEntity user) {
        Date issuedAt = new Date();
        Date expiresAt = new Date(issuedAt.getTime() + expirationSeconds * 1000L);
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(String.valueOf(user.getId()))
                .withClaim("sv", user.getSessionVersion() == null ? 0 : user.getSessionVersion())
                .withIssuedAt(issuedAt)
                .withExpiresAt(expiresAt)
                .sign(algorithm());
    }

    public Integer verifyAndGetUserId(String token) {
        DecodedJWT jwt = verify(token);
        return Integer.valueOf(jwt.getSubject());
    }

    public boolean isTokenCurrent(String token, UserEntity user) {
        if (user == null) return false;
        Integer tokenSessionVersion = verify(token).getClaim("sv").asInt();
        int currentSessionVersion = user.getSessionVersion() == null ? 0 : user.getSessionVersion();
        return (tokenSessionVersion == null ? 0 : tokenSessionVersion) == currentSessionVersion;
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    private Algorithm algorithm() {
        return Algorithm.HMAC256(secret);
    }

    private DecodedJWT verify(String token) {
        JWTVerifier verifier = JWT.require(algorithm())
                .withIssuer(ISSUER)
                .build();
        return verifier.verify(token);
    }
}
