package com.eve.marketwatch.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.model.dao.Structure;
import com.eve.marketwatch.service.EveAuthService;
import com.eve.marketwatch.service.SecurityService;
import com.eve.marketwatch.exceptions.BadRequestException;
import com.eve.marketwatch.model.eveauth.AccessTokenResponse;
import com.eve.marketwatch.model.dao.StructureRepository;
import com.eve.marketwatch.model.esi.SearchResponse;
import com.eve.marketwatch.model.esi.StructureInfoResponse;
import com.eve.marketwatch.model.dao.User;
import com.eve.marketwatch.model.dao.UserRepository;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

public class StructureSearchHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

    private static final Logger LOG = LogManager.getLogger(StructureSearchHandler.class);
    private static final int MAX_THREADS = 50;
    private final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(MAX_THREADS);

    private static final List<Integer> IGNORED_STRUCTURE_TYPES = Arrays.asList(
    		35825, // raitaru
			35835, // athanor
			35836, // tatara
			35841, // ansiblex jump gate
			35840, // pharolux cyno beacon
			37534, // cerebrex cyno jammer
			27674); // cynosural system jammer

    private final javax.ws.rs.client.Client webClient = ClientBuilder.newClient();
    private final StructureRepository structureRepository = StructureRepository.getInstance();
    private final EveAuthService eveAuthService = new EveAuthService();
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

		final Optional<User> optUser = userRepository.find(characterId);
		if (!optUser.isPresent()) {
			LOG.warn("User not found for characterId " + characterId);
			return ApiGatewayResponse.builder()
					.setStatusCode(404)
					.build();
		}
		final AccessTokenResponse accessTokenResponse;
		final User user = optUser.get();
		try {
			accessTokenResponse = eveAuthService.generateAccessToken(user.getRefreshToken());
		} catch (BadRequestException e) {
			LOG.warn("Failed to get access token for characterId " + user.getCharacterId());
			return ApiGatewayResponse.builder()
					.setStatusCode(403)
					.build();
		}
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

		final SearchResponse search = new GsonBuilder().create().fromJson(json, SearchResponse.class);
		if (search == null || search.getStructure() == null || search.getStructure().length == 0) {
			LOG.info("No Structure was found for term " + term);
			return ApiGatewayResponse.builder()
					.setStatusCode(404)
					.build();
		}

		final List<String> names = getMarkets(accessTokenResponse.getAccessToken(), search).stream()
				.map(Structure::getStructureName).collect(Collectors.toList());

		return ApiGatewayResponse.builder()
				.setStatusCode(200)
				.setObjectBody(names)
				.build();
	}

	private List<Structure> getMarkets(String accessToken, SearchResponse search) {
        final List<Structure> allKnownStructures = structureRepository.findAll();
        final List<Long> allKnownStructureIds = allKnownStructures.stream().map(Structure::getStructureId).collect(Collectors.toList());
        final List<Long> knownNonMarketStructureIds = allKnownStructures.stream()
				.filter(m -> IGNORED_STRUCTURE_TYPES.contains(m.getTypeId()))
				.map(Structure::getStructureId)
				.collect(Collectors.toList());

		final List<Long> searchedStructureIds = Arrays.stream(search.getStructure())
				.filter(id -> !knownNonMarketStructureIds.contains(id))
				.limit(MAX_THREADS)
				.collect(Collectors.toList());
		LOG.info("After filtering " + search.getStructure().length + " structures we now have " + searchedStructureIds.size());

		// don't resolve the structure ids from the database, as a user might not have access to those structures
        // the ACL is verified by the structure resolution call
        final List<Future<Structure>> futures = searchedStructureIds.stream().map(structureId -> {
            final Callable<Structure> callable = new StructureResolution(structureId, accessToken);
            LOG.info("Submitting callable " + callable);
            return executor.submit(callable);
        }).collect(Collectors.toList());

		final List<Structure> resolvedStructures = new ArrayList<>();
        for (final Future<Structure> future : futures) {
            try {
                final Structure m = future.get();
                if (m != null) {
                    resolvedStructures.add(m);
                }
            } catch (final InterruptedException | ExecutionException e) {
                LOG.error(e);
            }
        }

        resolvedStructures.stream()
                .filter(s -> !allKnownStructureIds.contains(s.getStructureId()))
                .forEach(structureRepository::save);

        return resolvedStructures.stream()
                .filter(s -> !IGNORED_STRUCTURE_TYPES.contains(s.getTypeId()))
				.sorted((o1, o2) -> o1.getStructureName().compareToIgnoreCase(o2.getStructureName()))
                .limit(10)
                .collect(Collectors.toList());
	}

	private class StructureResolution implements Callable<Structure> {

        private final long structureId;
        private final String accessToken;

        private StructureResolution(long structureId, String accessToken) {
            this.structureId = structureId;
            this.accessToken = accessToken;
        }

        @Override
        public Structure call() throws Exception {
            LOG.info("Resolving name for structureId " + structureId);
            final Response nameResponse = webClient.target("https://esi.evetech.net")
                    .path("/v2/universe/structures/" + structureId + "/")
                    .request()
                    .header("Authorization", "Bearer " + accessToken)
                    .get();
            final String nameJson = nameResponse.readEntity(String.class);
            LOG.info(nameJson);
            if (nameResponse.getStatus() == 200) {
                final StructureInfoResponse structureInfo = new GsonBuilder().create().fromJson(nameJson, StructureInfoResponse.class);
                final Structure structure = new Structure();
                structure.setStructureId(structureId);
                structure.setStructureName(structureInfo.getName());
                structure.setTypeId(structureInfo.getTypeId());
                return structure;
            } else {
                LOG.warn(nameResponse.getStatus());
                return null;
            }
        }

        @Override
        public String toString() {
            return "StructureResolution{" +
                    "structureId=" + structureId +
                    '}';
        }
    }
}
