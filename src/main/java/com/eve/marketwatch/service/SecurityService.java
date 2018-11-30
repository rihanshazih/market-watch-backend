package com.eve.marketwatch.service;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

public class SecurityService {

    final Key key;

    public SecurityService() {
        final String jwtSecret = System.getenv("JWT_SECRET");
        key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    SecurityService(Key key) {
        this.key = key;
    }

    public String generateJws(final int characterId) {
        return Jwts.builder()
                .setIssuer("Eve Market Watch")
                .claim("characterId", characterId)
                .claim("scope", "user")
                .setIssuedAt(Date.from(Instant.now()))
                .setExpiration(Date.from(Instant.now().plus(14, ChronoUnit.DAYS)))
				.signWith(key)
                .compact();
    }

    public Optional<Integer> getCharacterId(final String jws) {
        try {
            final Integer characterId = Jwts.parser().setSigningKey(key)
                    .parseClaimsJws(jws).getBody().get("characterId", Integer.class);
            return Optional.of(characterId);
        } catch (JwtException ex) {
            return Optional.empty();
        }
    }
}
