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

    @Value("${security.jwt.secret}")
    private String secret;

    @Value("${security.jwt.expiration-seconds:28800}")
    private long expirationSeconds;

    @PostConstruct
    public void validateConfiguration() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("security.jwt.secret 必须至少包含32字节");
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
                .withIssuedAt(issuedAt)
                .withExpiresAt(expiresAt)
                .sign(algorithm());
    }

    public Integer verifyAndGetUserId(String token) {
        JWTVerifier verifier = JWT.require(algorithm())
                .withIssuer(ISSUER)
                .build();
        DecodedJWT jwt = verifier.verify(token);
        return Integer.valueOf(jwt.getSubject());
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    private Algorithm algorithm() {
        return Algorithm.HMAC256(secret);
    }
}
