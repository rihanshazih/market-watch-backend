package com.eve.marketwatch.jobs;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.Constants;
import com.eve.marketwatch.api.ApiGatewayResponse;
import com.eve.marketwatch.exceptions.BadRequestException;
import com.eve.marketwatch.exceptions.UnknownUserException;
import com.eve.marketwatch.model.dao.ItemWatch;
import com.eve.marketwatch.model.dao.ItemWatchRepository;
import com.eve.marketwatch.model.dao.Structure;
import com.eve.marketwatch.model.dao.StructureRepository;
import com.eve.marketwatch.service.EveAuthService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class UpdateDisabledWatches implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

    private static final Logger LOG = LogManager.getLogger(UpdateDisabledWatches.class);

    private final javax.ws.rs.client.Client webClient = ClientBuilder.newClient();
    private final StructureRepository structureRepository;
    private final ItemWatchRepository itemWatchRepository;
    private final EveAuthService eveAuthService;


    public UpdateDisabledWatches() {
        structureRepository = StructureRepository.getInstance();
        itemWatchRepository = ItemWatchRepository.getInstance();
        eveAuthService = new EveAuthService();
    }

    @Override
    public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
        doUpdate();
        return ApiGatewayResponse.builder()
                .setStatusCode(200)
                .build();
    }

    void doUpdate() {
        final List<ItemWatch> itemWatches = itemWatchRepository.findAll().stream()
                .filter(ItemWatch::isDisabled)
                .collect(Collectors.toList());

        final Set<Aggregate> aggregates = itemWatches.stream()
                .map(w -> new Aggregate(w.getCharacterId(), w.getLocationId()))
                .collect(Collectors.toSet());

        for (final Aggregate aggregate : aggregates) {
            final Optional<Structure> structure = structureRepository.find(aggregate.getStructureId());
            if (structure.isPresent() && !structure.get().isNpcStation()) {
                try {
                    final String accessToken = eveAuthService.getAccessToken(aggregate.getCharacterId());
                    aggregate.setEnable(hasMarketAccess(structure.get(), accessToken));
                } catch (BadRequestException | UnknownUserException e) {
                    LOG.warn("Failed to retrieve access token for character " + aggregate.getCharacterId(), e);
                }
            }
        }

        itemWatches.stream()
                .filter(itemWatch -> aggregates.stream()
                        .filter(a -> a.getCharacterId() == itemWatch.getCharacterId())
                        .filter(a -> a.getStructureId() == itemWatch.getLocationId())
                        .anyMatch(Aggregate::isEnable)
                )
                .peek(w -> w.setDisabled(false))
                .forEach(itemWatchRepository::save);

        LOG.info("Updating disabled watches complete.");
    }

    private boolean hasMarketAccess(final Structure structure, final String accessToken) {
        final Response response = webClient.target(Constants.ESI_BASE_URL)
                .path("/v1/markets/structures/" + structure.getStructureId() + "/")
                .request()
                .header("Authorization", "Bearer " + accessToken)
                .get();

        return response.getStatus() == 200;
    }

    private class Aggregate {
        int characterId;
        long structureId;
        boolean enable;

        public Aggregate(int characterId, long structureId) {
            this.characterId = characterId;
            this.structureId = structureId;
        }

        public boolean isEnable() {
            return enable;
        }

        public void setEnable(boolean enable) {
            this.enable = enable;
        }

        public int getCharacterId() {
            return characterId;
        }

        public long getStructureId() {
            return structureId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Aggregate aggregate = (Aggregate) o;
            return characterId == aggregate.characterId &&
                    structureId == aggregate.structureId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(characterId, structureId);
        }
    }
}
