package com.eve.marketwatch.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.exceptions.BadRequestException;
import com.eve.marketwatch.jobs.StationResolver;
import com.eve.marketwatch.jobs.StructureResolver;
import com.eve.marketwatch.model.dao.Structure;
import com.eve.marketwatch.model.dao.StructureRepository;
import com.eve.marketwatch.model.dao.User;
import com.eve.marketwatch.model.dao.UserRepository;
import com.eve.marketwatch.service.EveAuthService;
import com.eve.marketwatch.service.SecurityService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.ws.rs.client.ClientBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

public class StructureSearchHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

    private static final Logger LOG = LogManager.getLogger(StructureSearchHandler.class);
	private final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);

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
		final String accessToken;
		final User user = optUser.get();
		try {
			accessToken = eveAuthService.getAccessToken(user);
		} catch (BadRequestException e) {
			LOG.warn("Failed to get access token for characterId " + user.getCharacterId());
			return ApiGatewayResponse.builder()
					.setStatusCode(403)
					.build();
		}

		final List<Structure> allKnownStructures = structureRepository.findAll();

		final Future<List<String>> f1 = executor.submit(new StructureResolver(term, allKnownStructures, structureRepository, webClient, characterId, accessToken));
		final Future<List<String>> f2 = executor.submit(new StationResolver(term, allKnownStructures, structureRepository, webClient));

		List<String> names = new ArrayList<>();
		try {
			names.addAll(f1.get());
			names.addAll(f2.get());
		} catch (final InterruptedException | ExecutionException e) {
			LOG.error(e);
		}

		names = names.stream()
				.sorted(String::compareToIgnoreCase)
				.limit(10)
				.collect(Collectors.toList());

		if (names.isEmpty()) {
			return ApiGatewayResponse.builder()
					.setStatusCode(404)
					.build();
		}

		return ApiGatewayResponse.builder()
				.setStatusCode(200)
				.setObjectBody(names)
				.build();
	}

}
