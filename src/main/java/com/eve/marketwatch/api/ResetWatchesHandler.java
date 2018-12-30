package com.eve.marketwatch.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.model.dao.ItemWatchRepository;
import com.eve.marketwatch.service.SecurityService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Optional;

public class ResetWatchesHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private static final Logger LOG = LogManager.getLogger(ResetWatchesHandler.class);

	private final ItemWatchRepository watchRepository = ItemWatchRepository.getInstance();
	private final SecurityService securityService = new SecurityService();

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
		LOG.info("received: {}", input);

		final String token = InputExtractor.getQueryParam("token", input);
		final Optional<Integer> optCharacterId = securityService.getCharacterId(token);
		if (optCharacterId.isPresent()) {
			final int characterId = optCharacterId.get();
			if (characterId != Integer.parseInt(System.getenv("ADMIN_CHARACTER_ID"))) {
				return ApiGatewayResponse.builder()
						.setStatusCode(403)
						.build();
			}
		} else {
			return ApiGatewayResponse.builder()
					.setStatusCode(401)
					.build();
		}


		final String charactersParam = InputExtractor.getQueryParam("characters", input);
		if (null != charactersParam && !charactersParam.isEmpty()) {
			final String[] split = charactersParam.split(",");
			if (split.length > 0) {
				for (String s : split) {
					final int characterId = Integer.parseInt(s);
					watchRepository.findByCharacterId(characterId).forEach(w -> {
						w.reset();
						watchRepository.save(w);
					});
				}

			}
		}

		return ApiGatewayResponse.builder()
				.setStatusCode(200)
				.build();
	}
}
