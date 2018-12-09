package com.eve.marketwatch.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.exceptions.BadRequestException;
import com.eve.marketwatch.exceptions.UnknownUserException;
import com.eve.marketwatch.model.dao.UserRepository;
import com.eve.marketwatch.model.esi.SearchResponse;
import com.eve.marketwatch.service.EveAuthService;
import com.eve.marketwatch.service.SecurityService;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GetStructureIdHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private static final Logger LOG = LogManager.getLogger(GetStructureIdHandler.class);

	private final javax.ws.rs.client.Client webClient = ClientBuilder.newClient();
	private final UserRepository userRepository = UserRepository.getInstance();
	private final EveAuthService eveAuthService = new EveAuthService();
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

		final String term = InputExtractor.getQueryParam("term", input);

		final String accessToken;
		try {
			accessToken = eveAuthService.getAccessToken(characterId);
		} catch (BadRequestException | UnknownUserException e) {
			LOG.warn("Failed to get access token for characterId " + characterId);
			return ApiGatewayResponse.builder()
					.setStatusCode(403)
					.build();
		}
		final Response searchResponse = webClient.target("https://esi.evetech.net")
				.path("/v3/characters/" + characterId + "/search/")
				.queryParam("categories", "structure")
				.queryParam("search", term)
				.queryParam("strict", true)
				.request()
				.header("Authorization", "Bearer " + accessToken)
				.get();

		if (searchResponse.getStatus() != 200) {
			return ApiGatewayResponse.builder()
					.setStatusCode(404)
					.build();
		}

		final String json = searchResponse.readEntity(String.class);
		final SearchResponse typeIds = new GsonBuilder().create().fromJson(json, SearchResponse.class);
		List<Long> structureIds = Arrays.asList(typeIds.getStructure());
		if (structureIds.isEmpty()) {
			return ApiGatewayResponse.builder()
					.setStatusCode(404)
					.build();
		} else {
			return ApiGatewayResponse.builder()
					.setObjectBody(structureIds.get(0))
					.setStatusCode(200)
					.build();
		}
	}
}
