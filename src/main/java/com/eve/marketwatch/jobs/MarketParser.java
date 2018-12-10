package com.eve.marketwatch.jobs;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.api.ApiGatewayResponse;
import com.eve.marketwatch.exceptions.BadRequestException;
import com.eve.marketwatch.exceptions.UnknownUserException;
import com.eve.marketwatch.model.dao.ItemSnapshot;
import com.eve.marketwatch.model.dao.ItemSnapshotRepository;
import com.eve.marketwatch.model.dao.ItemWatch;
import com.eve.marketwatch.model.dao.ItemWatchRepository;
import com.eve.marketwatch.model.dao.Mail;
import com.eve.marketwatch.model.dao.MailRepository;
import com.eve.marketwatch.model.dao.MailStatus;
import com.eve.marketwatch.model.dao.Structure;
import com.eve.marketwatch.model.dao.StructureRepository;
import com.eve.marketwatch.model.dao.User;
import com.eve.marketwatch.model.dao.UserRepository;
import com.eve.marketwatch.model.esi.ConstellationInfoResponse;
import com.eve.marketwatch.model.esi.MarketOrderResponse;
import com.eve.marketwatch.model.esi.StationInfoResponse;
import com.eve.marketwatch.model.esi.SystemInfoResponse;
import com.eve.marketwatch.service.EveAuthService;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class MarketParser implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

    private static final Logger LOG = LogManager.getLogger(MarketParser.class);

    private final javax.ws.rs.client.Client webClient = ClientBuilder.newClient();
    private final StructureRepository structureRepository;
    private final UserRepository userRepository;
    private final ItemWatchRepository itemWatchRepository;
    private final ItemSnapshotRepository itemSnapshotRepository;
    private final EveAuthService eveAuthService;
    private final MailRepository mailRepository;

    public MarketParser() {
        structureRepository = StructureRepository.getInstance();
        userRepository = UserRepository.getInstance();
        itemWatchRepository = ItemWatchRepository.getInstance();
        itemSnapshotRepository = ItemSnapshotRepository.getInstance();
        eveAuthService = new EveAuthService();
        mailRepository = MailRepository.getInstance();
    }

    @Override
    public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
        LOG.info("received: {}", input);

        doParse();

        return ApiGatewayResponse.builder()
                .setStatusCode(200)
                .build();
    }

    void doParse() {
        final List<ItemSnapshot> itemSnapshots = itemSnapshotRepository.findAll();

        final List<ItemWatch> itemWatches = itemWatchRepository.findAll();
        final Set<Long> locationIds = itemWatches.stream().map(ItemWatch::getLocationId).collect(Collectors.toSet());
        final Set<Integer> typeIds = itemWatches.stream().map(ItemWatch::getTypeId).collect(Collectors.toSet());

        final List<Structure> structures = structureRepository.findAll().stream()
                .filter(market -> locationIds.contains(market.getStructureId()))
                .collect(Collectors.toList());
        for (final Structure structure : structures) {
            processMarket(itemWatches, typeIds, structure, itemSnapshots);
        }
    }

    private void processMarket(final List<ItemWatch> itemWatches, final Set<Integer> typeIds, final Structure structure, List<ItemSnapshot> itemSnapshots) {
        LOG.info("Parsing structure for locationId " + structure.getStructureId());
        final Optional<Integer> accessCharacterId = findCharacterWithAccess(structure, itemWatches);
        if (accessCharacterId.isPresent()) {
            final int characterId = accessCharacterId.get();
            try {
                parseMarket(typeIds, structure, characterId, itemSnapshots);
                resetUserErrors(characterId);
            } catch (BadRequestException | UnknownUserException e) {

                if (e.getMessage().contains("invalid_token")) {
                    LOG.warn("Got an invalid_token for " + characterId);
                    updateUserErrors(characterId);
                }

                LOG.warn("Failed to parse structure " + structure.getStructureId() + " with character "
                        + characterId + ": " + e.getMessage());
                // try again with next character
                processMarket(itemWatches, typeIds, structure, itemSnapshots);
            }
        } else {
            // if this point is reached, we likely have a bug
            throw new RuntimeException("No character has watches for " + structure.getStructureId());
        }
    }

    private void resetUserErrors(int characterId) {
        userRepository.find(characterId).ifPresent(user -> {
            if (user.getErrorCount() > 0) {
                LOG.info("Reset error count for " + characterId);
                user.resetErrorCount();
                userRepository.save(user);
            }
        });
    }

    private void updateUserErrors(int characterId) {
        userRepository.find(characterId).ifPresent(user -> {

            user.incrementErrorCount();

            if (user.getErrorCount() >= 5) {
                createDeactivationMail(user);
                userRepository.delete(user);
                LOG.warn(user.getCharacterId() + " has been deleted due to too many client errors.");
            } else {
                userRepository.save(user);
            }
        });
    }

    private void createDeactivationMail(User user) {
        final Mail mail = new Mail();
        mail.setCreated(new Date());
        mail.setMailStatus(MailStatus.NEW);
        mail.setRecipient(user.getCharacterId());
        mail.setSubject("Eve Market Watch - Deactivated");
        mail.setText("Your account at https://eve-market-watch.firebaseapp.com has been deactivated due to an " +
                "invalid token. Please sign in to reactivate your account.");
        mailRepository.save(mail);
    }

    private void parseMarket(final Set<Integer> typeIds, final Structure structure, final int characterId, List<ItemSnapshot> itemSnapshots) throws BadRequestException, UnknownUserException {
        final HashMap<Integer, Long> volumes = new HashMap<>();
        final long locationId = structure.getStructureId();
        final List<MarketOrderResponse> marketOrders = getMarketOrders(characterId, structure)
                .stream()
                .filter(m -> !m.isBuyOrder())
                .filter(m -> typeIds.contains(m.getTypeId()))
                .collect(Collectors.toList());
        for (final MarketOrderResponse order : marketOrders) {
            volumes.computeIfPresent(order.getTypeId(), (integer, aLong) -> aLong + order.getVolumeRemain());
            volumes.putIfAbsent(order.getTypeId(), (long) order.getVolumeRemain());
        }

        for (final Map.Entry<Integer, Long> entry : volumes.entrySet()) {
            final int typeId = entry.getKey();
            final long amount = entry.getValue();
            final boolean alreadyExists = itemSnapshots.stream()
                    .filter(w -> w.getAmount() == amount)
                    .anyMatch(w -> w.getTypeId() == typeId && w.getLocationId() == locationId);
            if (!alreadyExists) {
                final ItemSnapshot itemSnapshot = new ItemSnapshot();
                itemSnapshot.setId(typeId + "-" + locationId);
                itemSnapshot.setTypeId(typeId);
                itemSnapshot.setAmount(amount);
                itemSnapshot.setLocationId(locationId);
                itemSnapshotRepository.save(itemSnapshot);
            }
        }
    }

    private List<MarketOrderResponse> getMarketOrders(final int characterId, final Structure structure) throws BadRequestException, UnknownUserException {

        int page = 1;
        List<MarketOrderResponse> marketOrders = new ArrayList<>();
        final String accessToken = eveAuthService.getAccessToken(characterId);
        List<MarketOrderResponse> chunk;
        do {
            chunk = getMarketOrders(structure, accessToken, page++);
            LOG.info("Collected another chunk with size " + chunk.size());
            marketOrders.addAll(chunk);
        } while (!chunk.isEmpty());

        // filter out non-station orders that may have been retrieved from the region
        if (structure.isNpcStation()) {
            marketOrders = marketOrders.stream()
                    .filter(m -> m.getVolumeRemain() == structure.getStructureId())
                    .collect(Collectors.toList());
        }

        return marketOrders;
    }

    private List<MarketOrderResponse> getMarketOrders(final Structure structure, final String accessToken, final int page) throws BadRequestException {
        LOG.info("Loading market orders for structureId/page: " + structure.getStructureId() + "/" + page);
        final Response response;
        if (structure.isNpcStation()) {
            final int regionId = getRegionId(structure);
            response = webClient.target("https://esi.evetech.net")
                    .path("/v1/markets/" + regionId + "/orders/")
                    .queryParam("order_type", "sell")
                    .queryParam("page", page)
                    .request()
                    .get();
        } else {
            response = webClient.target("https://esi.evetech.net")
                    .path("/v1/markets/structures/" + structure.getStructureId() + "/")
                    .queryParam("page", page)
                    .request()
                    .header("Authorization", "Bearer " + accessToken)
                    .get();
        }

        final String json = response.readEntity(String.class);
        LOG.info(json);
        if (response.getStatus() == 200) {
            return Arrays.asList(new GsonBuilder().create().fromJson(json, MarketOrderResponse[].class));
        } else {
            throw new BadRequestException("Failed to retrieve market orders for " + structure.getStructureId());
        }
    }

    private int getRegionId(Structure structure) throws BadRequestException {

        if (null != structure.getRegionId()) {
            return structure.getRegionId();
        }

        final Response structureResponse = webClient.target("https://esi.evetech.net")
                .path("/v2/universe/stations/" + structure.getStructureId() + "/")
                .request().get();
        LOG.info("/v2/universe/stations/ response: " + structureResponse.getStatus());
        if (structureResponse.getStatus() == 200) {
            final StationInfoResponse stationInfo = new GsonBuilder().create().fromJson(structureResponse.readEntity(String.class), StationInfoResponse.class);
            final Response systemResponse = webClient.target("https://esi.evetech.net")
                    .path("/v4/universe/systems/" + stationInfo.getSystemId() + "/")
                    .request().get();
            LOG.info("/v4/universe/systems/ response: " + systemResponse.getStatus());
            if (systemResponse.getStatus() == 200) {
                final SystemInfoResponse systemInfo = new GsonBuilder().create().fromJson(structureResponse.readEntity(String.class), SystemInfoResponse.class);
                final Response constellationResponse = webClient.target("https://esi.evetech.net")
                        .path("/v1/universe/constellations/" + systemInfo.getConstellationId() + "/")
                        .request().get();
                LOG.info("/v1/universe/constellations/ response: " + systemResponse.getStatus());
                if (constellationResponse.getStatus() == 200) {
                    final ConstellationInfoResponse constellationInfo = new GsonBuilder().create().fromJson(structureResponse.readEntity(String.class), ConstellationInfoResponse.class);

                    final int regionId = constellationInfo.getRegionId();
                    structure.setRegionId(regionId);
                    structureRepository.save(structure);

                    return regionId;
                }
            }
        }
        throw new BadRequestException("Failed to resolve station>system>constellation>region " + structure);
    }

    private Optional<Integer> findCharacterWithAccess(final Structure structure, final List<ItemWatch> itemWatches) {
        return itemWatches.stream()
                .filter(i -> i.getLocationId() == structure.getStructureId())
                .map(ItemWatch::getCharacterId)
                .findAny();
    }
}
