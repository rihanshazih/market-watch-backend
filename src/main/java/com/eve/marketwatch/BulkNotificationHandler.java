package com.eve.marketwatch;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.model.BulkMailRequest;
import com.eve.marketwatch.model.Mail;
import com.eve.marketwatch.model.MailRepository;
import com.eve.marketwatch.model.MailStatus;
import com.eve.marketwatch.model.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public class BulkNotificationHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private static final Logger LOG = LogManager.getLogger(BulkNotificationHandler.class);

	private static final MailRepository mailRepository = MailRepository.getInstance();
	private static final UserRepository userRepository = UserRepository.getInstance();
	private final SecurityService securityService = new SecurityService();

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
		LOG.info("received: {}", input);

		final String token = InputExtractor.getQueryParam("token", input);
		final Optional<Integer> optCharacterId = securityService.getCharacterId(token);
		final int characterId;
		if (optCharacterId.isPresent()) {
			characterId = optCharacterId.get();
		} else {
			return ApiGatewayResponse.builder()
					.setStatusCode(401)
					.build();
		}

		if (characterId != Integer.parseInt(System.getenv("ADMIN_CHARACTER_ID"))) {
			return ApiGatewayResponse.builder()
					.setStatusCode(400)
					.build();
		}

		final BulkMailRequest mailRequest;
		try {
			mailRequest = new ObjectMapper().readValue(input.get("body").toString(), BulkMailRequest.class);
		} catch (IOException e) {
			LOG.error("Failed to parse body", e);
			return ApiGatewayResponse.builder()
					.setStatusCode(400)
					.build();
		}

		userRepository.findAll().forEach(u -> {
			final Mail mail = new Mail();
			mail.setRecipient(u.getCharacterId());
			mail.setMailStatus(MailStatus.NEW);
			mail.setSubject(mailRequest.getSubject());
			mail.setText(mailRequest.getText());
			mailRepository.save(mail);
		});

		return ApiGatewayResponse.builder()
				.setStatusCode(201)
				.build();
	}
}
