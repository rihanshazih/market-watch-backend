package com.eve.marketwatch.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.model.dao.ItemWatch;
import com.eve.marketwatch.model.dao.ItemWatchRepository;
import com.eve.marketwatch.model.dao.Structure;
import com.eve.marketwatch.model.dao.StructureRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GetMyMarketsHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

    private static final Logger LOG = LogManager.getLogger(GetMyMarketsHandler.class);

    private final ItemWatchRepository itemWatchRepository;
    private final StructureRepository structureRepository;

    public GetMyMarketsHandler() {
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

        final int characterId = InputExtractor.getCharacterId(input);
        final Set<Long> structureIds = itemWatchRepository.findByCharacterId(characterId).stream()
                .map(ItemWatch::getLocationId).collect(Collectors.toSet());
        final List<Structure> structures = structureRepository.findAll().stream()
                .filter(s -> structureIds.contains(s.getStructureId()))
                .sorted((o1, o2) -> o1.getStructureName().compareToIgnoreCase(o2.getStructureName()))
                .collect(Collectors.toList());

        return ApiGatewayResponse.builder()
                .setStatusCode(200)
                .setObjectBody(structures)
                .build();
    }
}
