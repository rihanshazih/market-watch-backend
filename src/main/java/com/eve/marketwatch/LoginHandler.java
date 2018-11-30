package com.eve.marketwatch;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.model.AuthVerificationResponse;
import com.eve.marketwatch.model.CharacterDetailsResponse;
import com.eve.marketwatch.model.User;
import com.eve.marketwatch.model.UserRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

public class LoginHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private static final Logger LOG = LogManager.getLogger(LoginHandler.class);

	private final EsiAuthUtil esiAuthUtil;
	private final UserRepository userRepository;
	private final SecurityService securityService;

	public LoginHandler() {
		userRepository = UserRepository.getInstance();
		esiAuthUtil = new EsiAuthUtil();
		securityService = new SecurityService();
	}

	LoginHandler(EsiAuthUtil esiAuthUtil, UserRepository userRepository, SecurityService securityService) {
		this.esiAuthUtil = esiAuthUtil;
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
		final AuthVerificationResponse authResponse = esiAuthUtil.verifyAuthentication(code);
		final CharacterDetailsResponse charDetails = esiAuthUtil.getCharacterDetails(authResponse.getAccessToken());

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
