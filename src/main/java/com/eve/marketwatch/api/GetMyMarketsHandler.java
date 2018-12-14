package com.eve.marketwatch.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.model.dao.ItemWatch;
import com.eve.marketwatch.model.dao.ItemWatchRepository;
import com.eve.marketwatch.model.dao.Structure;
import com.eve.marketwatch.model.dao.StructureRepository;
import com.eve.marketwatch.service.SecurityService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class GetMyMarketsHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

    private static final Logger LOG = LogManager.getLogger(GetMyMarketsHandler.class);

    private final ItemWatchRepository itemWatchRepository;
    private final SecurityService securityService;
    private final StructureRepository structureRepository;

    public GetMyMarketsHandler() {
        securityService = new SecurityService();
        itemWatchRepository = ItemWatchRepository.getInstance();
        structureRepository = StructureRepository.getInstance();
    }

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

        final Set<Long> structureIds = itemWatchRepository.findByCharacterId(characterId).stream()
                .map(ItemWatch::getLocationId).collect(Collectors.toSet());
        final List<Structure> structures = structureRepository.findAll().stream()
                .filter(s -> structureIds.contains(s.getStructureId())).collect(Collectors.toList());

        return ApiGatewayResponse.builder()
                .setStatusCode(200)
                .setObjectBody(structures)
                .build();
    }
}
