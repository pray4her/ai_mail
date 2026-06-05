package com.github.mail.service.User;

import com.github.mail.config.JwtKeyProvider;
import com.github.mail.model.config.Properties.AccountAuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtKeyProvider jwtKeyProvider;
    private final AccountAuthProperties accountAuthProperties;

    public String createToken(final String username, final String role) {
        Instant now = Instant.now();
        Instant exp = now.plus(accountAuthProperties.getJwtExpirationDays(), ChronoUnit.DAYS);
        return Jwts.builder()
                .setSubject(username)
                .setId(UUID.randomUUID().toString())
                .setIssuer(accountAuthProperties.getJwtIssuer())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .claim("role", role)
                .signWith(Keys.hmacShaKeyFor(jwtKeyProvider.getSecretKeyBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parse(final String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(jwtKeyProvider.getSecretKeyBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
