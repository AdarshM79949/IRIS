package com.chatapp.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(
            @Value("${app.jwt.secret:vndfkgvbfdcfdsgnerughertuhfgvndvifvhuhgterfjerfvnrughughefenveogjrtugntlfvfnlkmvfeirgnrtnhyunbjgfnriejfrgrtjhnbgjlnvfilvkmevirgbnylnmibglkmfdvfjndmvsrogb}") String secret,
            @Value("${app.jwt.expiration-ms:2592000000}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String userId, String userName) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        Map<String, Object> claims = new HashMap<>();
        claims.put("nameid", userId);
        claims.put("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier", userId);
        claims.put("unique_name", userName);
        claims.put("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name", userName);
        claims.put("userName", userName);

        return Jwts.builder()
                .subject(userId)
                .claims(claims)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        String subject = claims.getSubject();
        if (subject != null && !subject.isEmpty()) {
            return subject;
        }
        Object nameid = claims.get("nameid");
        if (nameid != null) {
            return nameid.toString();
        }
        Object soapId = claims.get("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier");
        if (soapId != null) {
            return soapId.toString();
        }
        return null;
    }

    public String getUserNameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        Object uniqueName = claims.get("unique_name");
        if (uniqueName != null) {
            return uniqueName.toString();
        }
        Object userName = claims.get("userName");
        if (userName != null) {
            return userName.toString();
        }
        Object soapName = claims.get("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name");
        if (soapName != null) {
            return soapName.toString();
        }
        return claims.getSubject();
    }

    public String getTokenValidationError(String token) {
        if (token == null || token.isBlank()) return "Token is null or empty";
        try {
            Claims claims = getClaimsFromToken(token);
            if (claims.getExpiration().before(new Date())) {
                return "Token expired at " + claims.getExpiration() + " (current server time: " + new Date() + ")";
            }
            return null; // Valid!
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            return "Token is expired: " + e.getMessage();
        } catch (io.jsonwebtoken.security.SignatureException e) {
            return "Token signature does not match: " + e.getMessage();
        } catch (io.jsonwebtoken.MalformedJwtException e) {
            return "Token is malformed: " + e.getMessage();
        } catch (Exception e) {
            return "Token validation failed: " + e.getClass().getSimpleName() + " - " + e.getMessage();
        }
    }

    public boolean validateToken(String token) {
        return getTokenValidationError(token) == null;
    }
}
