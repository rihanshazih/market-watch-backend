package com.eve.marketwatch.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.Constants;
import com.eve.marketwatch.exceptions.BadRequestException;
import com.eve.marketwatch.exceptions.UnknownUserException;
import com.eve.marketwatch.model.dao.Structure;
import com.eve.marketwatch.model.dao.StructureRepository;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

public class GetStructureIdHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private static final Logger LOG = LogManager.getLogger(GetStructureIdHandler.class);

	private final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);

	private final javax.ws.rs.client.Client webClient = ClientBuilder.newClient();
	private final EveAuthService eveAuthService = new EveAuthService();
	private final SecurityService securityService = new SecurityService();
	private final StructureRepository structureRepository = StructureRepository.getInstance();

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
		LOG.info("received: {}", input);

		if ("serverless-plugin-warmup".equals(input.get("source"))) {
			LOG.info("WarmUp event.");
			return ApiGatewayResponse.builder()
					.setStatusCode(200)
					.build();
		}

		final Integer characterId = InputExtractor.getCharacterId(input);
		final String term = InputExtractor.getQueryParam("term", input);

		// try to find the structure in our known data first
		final Optional<Structure> structure = structureRepository.find(term);
		if (structure.isPresent()) {
			return ApiGatewayResponse.builder()
					.setObjectBody(structure.get().getStructureId())
					.setStatusCode(200)
					.build();
		}

		final Future<Long> stationCall = executor.submit(new StationResolver(term));
		final Future<Long> structureCall = executor.submit(new StructureResolver(characterId, term));

		try {
			final Long stationId = stationCall.get();
			if (null != stationId) {
				return ApiGatewayResponse.builder()
						.setObjectBody(stationId)
						.setStatusCode(200)
						.build();
			}
		} catch (final InterruptedException | ExecutionException e) {
			LOG.error(e);
		}

		try {
			final Long structureId = structureCall.get();
			if (null != structureId) {
				return ApiGatewayResponse.builder()
						.setObjectBody(structureId)
						.setStatusCode(200)
						.build();
			}
		} catch (final InterruptedException | ExecutionException e) {
			LOG.error(e);
		}

		return ApiGatewayResponse.builder()
				.setStatusCode(404)
				.build();
	}

	private class StationResolver implements Callable<Long> {

		private final String term;

		public StationResolver(String term) {
			this.term = term;
		}

		@Override
		public Long call() throws Exception {
			final Response searchResponse = webClient.target(Constants.ESI_BASE_URL)
					.path("/v2/search/")
					.queryParam("categories", "station")
					.queryParam("search", term)
					.queryParam("strict", true)
					.request()
					.get();

			if (searchResponse.getStatus() != 200) {
				return null;
			}

			final String json = searchResponse.readEntity(String.class);
			final SearchResponse typeIds = new GsonBuilder().create().fromJson(json, SearchResponse.class);
			final List<Long> stationIds = Arrays.asList(typeIds.getStation());
			if (stationIds.isEmpty()) {
				return null;
			} else {
				return stationIds.get(0);
			}
		}
	}

	private class StructureResolver implements Callable<Long> {

		private final int characterId;
		private final String term;

		public StructureResolver(int characterId, String term) {
			this.characterId = characterId;
			this.term = term;
		}

		@Override
		public Long call() throws Exception {
			final String accessToken;
			try {
				accessToken = eveAuthService.getAccessToken(characterId);
			} catch (BadRequestException | UnknownUserException e) {
				LOG.warn("Failed to get access token for characterId " + characterId);
				return null;
			}

			final Response searchResponse = webClient.target(Constants.ESI_BASE_URL)
					.path("/v3/characters/" + characterId + "/search/")
					.queryParam("categories", "structure")
					.queryParam("search", term)
					.queryParam("strict", true)
					.request()
					.header("Authorization", "Bearer " + accessToken)
					.get();

			if (searchResponse.getStatus() != 200) {
				return null;
			}

			final String json = searchResponse.readEntity(String.class);
			final SearchResponse typeIds = new GsonBuilder().create().fromJson(json, SearchResponse.class);
			final List<Long> structureIds = Arrays.asList(typeIds.getStructure());
			if (structureIds.isEmpty()) {
				return null;
			} else {
				return structureIds.get(0);
			}
		}
	}

}
