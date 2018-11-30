package com.eve.marketwatch.service;

import com.eve.marketwatch.service.SecurityService;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityServiceTest {

    private final SecurityService sut = new SecurityService(Keys.secretKeyFor(SignatureAlgorithm.HS256));

    @Test
    void jwtWorks() {
        final String jws = sut.generateJws(12345);
        final Optional<Integer> characterId = sut.getCharacterId(jws);
        assertTrue(characterId.isPresent());
        assertEquals(12345, characterId.get().intValue());
    }
}
