package com.eve.marketwatch.jobs;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.Constants;
import com.eve.marketwatch.api.ApiGatewayResponse;
import com.eve.marketwatch.exceptions.MailFailed;
import com.eve.marketwatch.model.dao.UserRepository;
import com.eve.marketwatch.service.EveAuthService;
import com.eve.marketwatch.exceptions.BadRequestException;
import com.eve.marketwatch.model.eveauth.AccessTokenResponse;
import com.eve.marketwatch.model.dao.Mail;
import com.eve.marketwatch.model.esi.MailRecipient;
import com.eve.marketwatch.model.dao.MailRepository;
import com.eve.marketwatch.model.esi.MailRequest;
import com.eve.marketwatch.model.dao.MailStatus;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MailSender implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private static final Logger LOG = LogManager.getLogger(MailSender.class);

	private final javax.ws.rs.client.Client webClient = ClientBuilder.newClient();
	private final EveAuthService eveAuthService;
	private final MailRepository mailRepository;
	private final UserRepository userRepository;
	private final int mailCharacterId;
	private final String mailClientId;
	private final String mailSecret;
	private final String mailRefreshToken;

	public MailSender() {
		mailRepository = MailRepository.getInstance();
		mailCharacterId = Integer.parseInt(System.getenv("MAIL_CHARACTER_ID"));
		mailClientId = System.getenv("MAIL_CLIENT_ID");
		mailSecret = System.getenv("MAIL_CLIENT_SECRET");
		mailRefreshToken = System.getenv("MAIL_REFRESH_TOKEN");
		eveAuthService = new EveAuthService();
		userRepository = UserRepository.getInstance();
	}

	MailSender(EveAuthService eveAuthService, MailRepository mailRepository, UserRepository userRepository, int mailCharacterId, String mailClientId, String mailSecret, String mailRefreshToken) {
		this.eveAuthService = eveAuthService;
		this.mailRepository = mailRepository;
		this.userRepository = userRepository;
		this.mailCharacterId = mailCharacterId;
		this.mailClientId = mailClientId;
		this.mailSecret = mailSecret;
		this.mailRefreshToken = mailRefreshToken;
	}

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
		doSend();
		return ApiGatewayResponse.builder()
				.setStatusCode(200)
				.build();
	}

	void doSend() {
		final List<Mail> mails = mailRepository.findByStatus(MailStatus.NEW);
		if (mails.isEmpty()) {
			LOG.info("No new mails to be sent.");
		} else {
			final Mail mail = mails.stream()
					.sorted((o1, o2) -> o2.getPriority().compareTo(o1.getPriority()))
					.collect(Collectors.toList()).get(0);

			LOG.info("Processing mail " + mail.getId());

			final MailRequest mailRequest = createMailRequest(mail);
			try {
				submitMailRequest(mailRequest);
				mail.setMailStatus(MailStatus.SENT);
				mail.setCreated(new Date());
			} catch (final MailFailed mailFailed) {
				updateUserErrors(mailFailed);
				if (!mailFailed.isRetry()) {
					mail.setMailStatus(MailStatus.FAILED);
				}
			}
			mailRepository.save(mail);
		}
	}

	private void updateUserErrors(MailFailed mailFailed) {
		userRepository.find(mailFailed.getRecipientId()).ifPresent(user -> {
			user.incrementErrorCount();
			userRepository.save(user);
			if (user.getErrorCount() >= 5) {
				userRepository.delete(user);
				LOG.warn(user.getCharacterId() + " has been deleted due to too many client errors.");
			}
		});
	}

	private MailRequest createMailRequest(final Mail mail) {
		final MailRequest mailRequest = new MailRequest();
		mailRequest.setRecipients(Collections.singletonList(new MailRecipient(mail.getRecipient())));
		mailRequest.setSubject(mail.getSubject());
		mailRequest.setBody(mail.getText());
		return mailRequest;
	}

	private void submitMailRequest(final MailRequest mailRequest) throws MailFailed {
		final AccessTokenResponse accessTokenResponse;
		try {
			accessTokenResponse = eveAuthService.getAccessToken(mailRefreshToken, mailClientId, mailSecret);
		} catch (BadRequestException e) {
			throw new RuntimeException("Failed to generate access token for mail sending.");
		}
		final String accessToken = accessTokenResponse.getAccessToken();
		final String payload = new GsonBuilder().create().toJson(mailRequest);
		final Response mailResponse = webClient.target(Constants.ESI_BASE_URL)
				.path("/v1/characters/" + mailCharacterId + "/mail/")
				.request()
				.header("Authorization", "Bearer " + accessToken)
				.post(Entity.entity(payload, "application/json"));
		final String json = mailResponse.readEntity(String.class);
		LOG.info(mailResponse.getStatus());
		if (mailResponse.getStatus() != 201) {
		    LOG.info(json);
			throw new MailFailed(mailResponse.getStatus(),
					mailRequest.getRecipients().get(0).getRecipientId(),
					// if the mail fails during downtime, retry it
					mailResponse.getStatus() == 502 || mailResponse.getStatus() == 503);
		}
	}
}
