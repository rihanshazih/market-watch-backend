package com.eve.marketwatch;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.model.Market;
import com.eve.marketwatch.model.MarketRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Optional;

public class StructureDetailsHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private static final Logger LOG = LogManager.getLogger(StructureDetailsHandler.class);

	private final MarketRepository marketRepository = MarketRepository.getInstance();

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
		LOG.info("received: {}", input);

		if ("serverless-plugin-warmup".equals(input.get("source"))) {
			LOG.info("WarmUp event.");
			return ApiGatewayResponse.builder()
					.setStatusCode(200)
					.build();
		}

		final String term = InputExtractor.getQueryParam("term", input);
		if (term == null || term.length() <= 5) {
			return ApiGatewayResponse.builder()
					.setStatusCode(404)
					.build();
		}

		final Optional<Market> market = marketRepository.find(term);
		if (market.isPresent()) {
			return ApiGatewayResponse.builder()
					.setStatusCode(200)
					.setRawBody(String.valueOf(market.get().getLocationId()))
					.build();
		} else {
			return ApiGatewayResponse.builder()
					.setStatusCode(404)
					.build();
		}
	}
}
