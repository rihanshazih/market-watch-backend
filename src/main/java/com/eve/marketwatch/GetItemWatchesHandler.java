package com.eve.marketwatch;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.model.ItemWatch;
import com.eve.marketwatch.model.ItemWatchRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class GetItemWatchesHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private static final Logger LOG = LogManager.getLogger(GetItemWatchesHandler.class);

	private final ItemWatchRepository itemWatchRepository = ItemWatchRepository.getInstance();
	private final SecurityService securityService = new SecurityService();

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
		LOG.info("received: {}", input);

		if ("serverless-plugin-warmup".equals(input.get("source"))) {
			LOG.info("WarmUp event.");
			return ApiGatewayResponse.builder()
					.setStatusCode(200)
					.build();
		}

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

		final long structureId = Long.parseLong(InputExtractor.getQueryParam("structureId", input));

		final List<ItemWatch> itemWatches = itemWatchRepository.findByCharacterId(characterId)
				.stream().filter(w -> w.getLocationId() == structureId)
				.collect(Collectors.toList());

		return ApiGatewayResponse.builder()
				.setStatusCode(200)
				.setObjectBody(itemWatches)
				.build();
	}
}
