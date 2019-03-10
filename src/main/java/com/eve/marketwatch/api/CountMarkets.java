package com.eve.marketwatch.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.model.dao.ItemWatch;
import com.eve.marketwatch.model.dao.ItemWatchRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class CountMarkets implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private static final Logger LOG = LogManager.getLogger(CountMarkets.class);

	private final ItemWatchRepository watchRepository = ItemWatchRepository.getInstance();

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
		LOG.info("received: {}", input);

		final long count = watchRepository.findAll().stream()
				.map(ItemWatch::getLocationId)
				.distinct()
				.count();

		return ApiGatewayResponse.builder()
				.setStatusCode(200)
				.setRawBody(String.valueOf(count))
				.build();
	}
}
