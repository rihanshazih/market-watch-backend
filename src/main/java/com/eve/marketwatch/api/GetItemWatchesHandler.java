package com.eve.marketwatch.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.model.dao.ItemWatch;
import com.eve.marketwatch.model.dao.ItemWatchRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GetItemWatchesHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private static final Logger LOG = LogManager.getLogger(GetItemWatchesHandler.class);

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


		final long structureId = Long.parseLong(InputExtractor.getQueryParam("structureId", input));
		final int characterId = InputExtractor.getCharacterId(input);

		final List<ItemWatch> itemWatches = itemWatchRepository.findByCharacterId(characterId)
				.stream().filter(w -> w.getLocationId() == structureId)
				.sorted((o1, o2) -> o1.getTypeName().compareToIgnoreCase(o2.getTypeName()))
				.collect(Collectors.toList());

		return ApiGatewayResponse.builder()
				.setStatusCode(200)
				.setObjectBody(itemWatches)
				.build();
	}
}
