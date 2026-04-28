package com.minibank.backend.auth.service;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

	private final JwtEncoder jwtEncoder;
	private final String issuer;
	private final long accessTokenTtlSeconds;

	public JwtTokenService(
		JwtEncoder jwtEncoder,
		@Value("${app.jwt.issuer:minibank}") String issuer,
		@Value("${app.jwt.access-token-ttl-seconds:86400}") long accessTokenTtlSeconds
	) {
		this.jwtEncoder = jwtEncoder;
		this.issuer = issuer;
		this.accessTokenTtlSeconds = accessTokenTtlSeconds;
	}

	public IssuedToken issueAccessToken(Long userId, String type, String subject, List<String> roles) {
		Instant now = Instant.now();
		Instant expiresAt = now.plusSeconds(accessTokenTtlSeconds);

		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(issuer)
			.issuedAt(now)
			.expiresAt(expiresAt)
			.subject(subject)
			.claim("uid", userId)
			.claim("type", type)
			.claim("roles", roles)
			.build();

		String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedToken(tokenValue, accessTokenTtlSeconds);
	}

	public record IssuedToken(String token, long expiresInSeconds) {}
}
