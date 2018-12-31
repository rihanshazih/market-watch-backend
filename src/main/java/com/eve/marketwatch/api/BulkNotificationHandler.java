package com.eve.marketwatch.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.model.api.BulkMailRequest;
import com.eve.marketwatch.model.dao.Mail;
import com.eve.marketwatch.model.dao.MailRepository;
import com.eve.marketwatch.model.dao.MailStatus;
import com.eve.marketwatch.model.dao.User;
import com.eve.marketwatch.model.dao.UserRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

import static com.eve.marketwatch.api.Util.parseBody;

public class BulkNotificationHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private static final Logger LOG = LogManager.getLogger(BulkNotificationHandler.class);

	private static final MailRepository mailRepository = MailRepository.getInstance();
	private static final UserRepository userRepository = UserRepository.getInstance();

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
		LOG.info("received: {}", input);

		final BulkMailRequest mailRequest = parseBody(BulkMailRequest.class, input);

		if (mailRequest.getRecipient() != null) {
			userRepository.find(mailRequest.getRecipient()).ifPresent(u -> saveMail(mailRequest, u));
		} else {
			userRepository.findAll().forEach(u -> saveMail(mailRequest, u));
		}

		return ApiGatewayResponse.builder()
				.setStatusCode(201)
				.build();
	}

	private void saveMail(BulkMailRequest mailRequest, User u) {
		final Mail mail = new Mail();
		mail.setPriority(1);
		mail.setRecipient(u.getCharacterId());
		mail.setMailStatus(MailStatus.NEW);
		mail.setSubject(mailRequest.getSubject());
		mail.setText(mailRequest.getText());
		mailRepository.save(mail);
	}
}
