package com.insidergame.insider_api.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    // Secret key สำหรับ sign JWT (ควรเก็บใน environment variable ในระบบจริง)
    private static final String SECRET_KEY = "InsiderBoardGameSecretKeyForJWTTokenGeneration2024SecureKey123456";
    private static final long EXPIRATION_TIME = 86400000; // 24 hours in milliseconds

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes());
    }

    public String generateToken(String uuid, String playerName) {
        return Jwts.builder()
                .claim("uuid", uuid)
                .claim("playerName", playerName)
                .subject(uuid)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractUuid(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractPlayerName(String token) {
        return extractAllClaims(token).get("playerName", String.class);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

