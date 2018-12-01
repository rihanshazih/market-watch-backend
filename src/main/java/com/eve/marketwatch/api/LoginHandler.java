package com.eve.marketwatch.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.exceptions.EsiException;
import com.eve.marketwatch.service.EveAuthService;
import com.eve.marketwatch.service.SecurityService;
import com.eve.marketwatch.model.eveauth.AuthVerificationResponse;
import com.eve.marketwatch.model.eveauth.CharacterDetailsResponse;
import com.eve.marketwatch.model.dao.User;
import com.eve.marketwatch.model.dao.UserRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

public class LoginHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private static final Logger LOG = LogManager.getLogger(LoginHandler.class);

	private final EveAuthService eveAuthService;
	private final UserRepository userRepository;
	private final SecurityService securityService;

	public LoginHandler() {
		userRepository = UserRepository.getInstance();
		eveAuthService = new EveAuthService();
		securityService = new SecurityService();
	}

	LoginHandler(EveAuthService eveAuthService, UserRepository userRepository, SecurityService securityService) {
		this.eveAuthService = eveAuthService;
		this.userRepository = userRepository;
		this.securityService = securityService;
	}

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
		LOG.info("received: {}", input);

		if ("serverless-plugin-warmup".equals(input.get("source"))) {
			LOG.info("WarmUp event.");
			return ApiGatewayResponse.builder()
					.setStatusCode(200)
					.build();
		}

		final String code = InputExtractor.getQueryParam("code", input);
		final Instant beforeAuthentication = Instant.now();
		final AuthVerificationResponse authResponse;
		try {
			authResponse = eveAuthService.verifyAuthentication(code);
		} catch (EsiException e) {
			return ApiGatewayResponse.builder()
					.setStatusCode(400)
					.setRawBody(e.getError().getErrorDescription())
					.build();
		}
		final CharacterDetailsResponse charDetails = eveAuthService.getCharacterDetails(authResponse.getAccessToken());

		final User user = new User();
		user.setCharacterId(charDetails.getCharacterId());
		user.setRefreshToken(authResponse.getRefreshToken());
		user.setAccessToken(authResponse.getAccessToken());
		// 20-2 to allow for delay buffer without hitting an expired accessToken
		user.setAccessTokenExpiry(Date.from(beforeAuthentication.plus(18, ChronoUnit.MINUTES)));
		userRepository.save(user);

		final String jws = securityService.generateJws(user.getCharacterId());

		LOG.info("Login successful for user " + user.getCharacterId());

		return ApiGatewayResponse.builder()
				.setStatusCode(200)
				.setObjectBody(new JwtToken(jws))
				.build();
	}

	private class JwtToken {
		private final String token;

		public JwtToken(String token) {
			this.token = token;
		}

		public String getToken() {
			return token;
		}
	}
}
