package com.eve.marketwatch;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.model.AccessTokenResponse;
import com.eve.marketwatch.model.Market;
import com.eve.marketwatch.model.MarketRepository;
import com.eve.marketwatch.model.SearchResponse;
import com.eve.marketwatch.model.StructureInfoResponse;
import com.eve.marketwatch.model.User;
import com.eve.marketwatch.model.UserRepository;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class StructureSearchHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private static final Logger LOG = LogManager.getLogger(StructureSearchHandler.class);

	private final javax.ws.rs.client.Client webClient = ClientBuilder.newClient();
	private final MarketRepository marketRepository = MarketRepository.getInstance();
	private final EsiAuthUtil esiAuthUtil = new EsiAuthUtil();
	private final UserRepository userRepository = UserRepository.getInstance();
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
		if (term == null || term.length() < 4) {
			return ApiGatewayResponse.builder()
					.setStatusCode(200)
					.setObjectBody(new ArrayList<>())
					.build();
		}

		final Optional<User> user = userRepository.find(characterId);
		if (!user.isPresent()) {
			LOG.warn("User not found for characterId " + characterId);
			return ApiGatewayResponse.builder()
					.setStatusCode(404)
					.build();
		}
		final AccessTokenResponse accessTokenResponse = esiAuthUtil.generateAccessToken(user.get().getRefreshToken());
		final Response searchResponse = webClient.target("https://esi.evetech.net")
				.path("/v3/characters/" + characterId + "/search/")
				.queryParam("categories", "structure")
				.queryParam("search", term)
				.queryParam("strict", false)
				.request()
				.header("Authorization", "Bearer " + accessTokenResponse.getAccessToken())
				.get();

		final String json = searchResponse.readEntity(String.class);
		LOG.info(json);
		if (searchResponse.getStatus() != 200) {
			LOG.info("Response from structure search was " + searchResponse.getStatus());
			return ApiGatewayResponse.builder()
					.setStatusCode(404)
					.build();
		}

		final SearchResponse typeIds = new GsonBuilder().create().fromJson(json, SearchResponse.class);
		if (typeIds == null || typeIds.getStructure() == null || typeIds.getStructure().length == 0) {
			LOG.info("No Structure was found for term " + term);
			return ApiGatewayResponse.builder()
					.setStatusCode(404)
					.build();
		}
		List<Long> structureIds = Arrays.asList(typeIds.getStructure());
		if (structureIds.size() > 10) {
			structureIds = structureIds.subList(0, 10);
		}

		final List<Market> markets = new ArrayList<>();
		for (final Long structureId : structureIds) {
			LOG.info("Resolving name for structureId " + structureId);
			final Response nameResponse = webClient.target("https://esi.evetech.net")
					.path("/v2/universe/structures/" + structureId + "/")
					.request()
					.header("Authorization", "Bearer " + accessTokenResponse.getAccessToken())
					.get();
			final String nameJson = nameResponse.readEntity(String.class);
			LOG.info(nameJson);
			if (nameResponse.getStatus() == 200) {
				final StructureInfoResponse structureInfo = new GsonBuilder().create().fromJson(nameJson, StructureInfoResponse.class);
				final Market market = new Market();
				market.setLocationId(structureId);
				market.setLocationName(structureInfo.getName());
				market.setTypeId(structureInfo.getTypeId());
				market.setAccessorCharacterId(characterId);
				markets.add(market);
			} else {
				LOG.warn(nameResponse.getStatus());
			}
		}

		markets.forEach(marketRepository::save);

		final List<String> names = markets.stream().map(Market::getLocationName).collect(Collectors.toList());

		return ApiGatewayResponse.builder()
				.setStatusCode(200)
				.setObjectBody(names)
				.build();
	}
}
