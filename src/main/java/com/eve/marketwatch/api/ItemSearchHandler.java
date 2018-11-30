package com.eve.marketwatch.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.model.esi.SearchResponse;
import com.eve.marketwatch.model.esi.NameResponse;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ItemSearchHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private static final Logger LOG = LogManager.getLogger(ItemSearchHandler.class);

	private final javax.ws.rs.client.Client webClient = ClientBuilder.newClient();

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
		if (term == null || term.length() < 3) {
			return ApiGatewayResponse.builder()
					.setStatusCode(200)
					.setObjectBody(new ArrayList<>())
					.build();
		}

		final Response searchResponse = webClient.target("https://esi.evetech.net")
				.path("/v2/search/")
				.queryParam("categories", "inventory_type")
				.queryParam("language", "en-us")
				.queryParam("search", term)
				.request()
				.get();

		if (searchResponse.getStatus() != 200) {
			return ApiGatewayResponse.builder()
					.setStatusCode(404)
					.build();
		}

		final String json = searchResponse.readEntity(String.class);
		System.out.println(json);
		final SearchResponse typeIds = new GsonBuilder().create().fromJson(json, SearchResponse.class);

		final Response nameResponse = webClient.target("https://esi.evetech.net")
				.path("/v2/universe/names/")
				.request()
				.post(Entity.entity(typeIds.getInventoryTypes(), "application/json"));

		if (nameResponse.getStatus() != 200) {
			return ApiGatewayResponse.builder()
					.setStatusCode(404)
					.build();
		}

		final String json2 = nameResponse.readEntity(String.class);
		System.out.println(json2);
		final List<String> names = getNamesFromPayload(json2);
		return ApiGatewayResponse.builder()
				.setStatusCode(200)
				.setObjectBody(names)
				.build();
	}

	List<String> getNamesFromPayload(String json2) {
		return Arrays.stream(new GsonBuilder().create().fromJson(json2, NameResponse[].class))
					.map(NameResponse::getName)
					.sorted((o1, o2) -> compareTypeNames(o1, o2))
					.limit(10).collect(Collectors.toList());
	}

	private int compareTypeNames(String o1, String o2) {
		if (o1.length() != o2.length()) {
			return o1.length() - o2.length();
		} else {
			return o1.compareToIgnoreCase(o2);
		}
	}
}
