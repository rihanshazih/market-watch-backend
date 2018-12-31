package com.eve.marketwatch.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.model.dao.ItemWatchRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class DeleteItemWatchHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private static final Logger LOG = LogManager.getLogger(DeleteItemWatchHandler.class);

	private final ItemWatchRepository itemWatchRepository = ItemWatchRepository.getInstance();

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
		LOG.info("received: {}", input);

		if ("serverless-plugin-warmup".equals(input.get("source"))) {
			LOG.info("WarmUp event.");
			return ApiGatewayResponse.builder()
					.setStatusCode(200)
					.build();
		}

		final String id = InputExtractor.getPathParam("id", input);
		final int characterId = InputExtractor.getCharacterId(input);

		itemWatchRepository.findByCharacterId(characterId).stream().filter(w -> w.getId().equals(id))
				.findFirst().ifPresent(itemWatchRepository::delete);

		return ApiGatewayResponse.builder()
				.setStatusCode(200)
				.build();
	}
}
