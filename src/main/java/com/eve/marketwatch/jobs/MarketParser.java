package com.eve.marketwatch.jobs;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.Constants;
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

    private final Map<Integer, Integer> systemToConstellationMappings = new HashMap<>();
    private final Map<Integer, Integer> constellationToRegionMappings = new HashMap<>();

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
        doParse();
        return ApiGatewayResponse.builder()
                .setStatusCode(200)
                .build();
    }

    void doParse() {
        final List<ItemSnapshot> itemSnapshots = itemSnapshotRepository.findAll();

        final List<ItemWatch> itemWatches = itemWatchRepository.findAll().stream()
                .filter(i -> !i.isDisabled()).collect(Collectors.toList());
        final Set<Long> locationIds = itemWatches.stream().map(ItemWatch::getLocationId).collect(Collectors.toSet());

        final List<Structure> structures = structureRepository.findAll().stream()
                .filter(market -> locationIds.contains(market.getStructureId()))
                .collect(Collectors.toList());

        for (final Structure structure : structures) {
            if (!structure.isNpcStation()) {
                processPlayerOwnedMarket(itemWatches, structure, itemSnapshots);
            }
        }

        final Map<Integer, List<Structure>> npcStationsGroupedByRegion = groupNpcStructures(structures);
        for (Map.Entry<Integer, List<Structure>> entry : npcStationsGroupedByRegion.entrySet()) {
            final Integer regionId = entry.getKey();
            try {
                final List<Structure> regionStructures = entry.getValue();
                processRegion(itemSnapshots, itemWatches, regionId, regionStructures);
            } catch (BadRequestException e) {
                LOG.error(e);
            }
        }

        LOG.info("Market parsing complete.");
    }

    private void processRegion(List<ItemSnapshot> itemSnapshots, List<ItemWatch> itemWatches, Integer regionId, List<Structure> regionStructures) throws BadRequestException {
        final Set<Integer> typeIds = itemWatches.stream()
                .filter(w -> regionStructures.stream().anyMatch(s -> s.getStructureId() == w.getLocationId()))
                .map(ItemWatch::getTypeId)
                .collect(Collectors.toSet());

        final List<MarketOrderResponse> regionMarketOrders = getRegionMarketOrders(regionId);
        final List<MarketOrderResponse> marketOrders = regionMarketOrders
                .stream()
                .filter(m -> typeIds.contains(m.getTypeId()))
                .collect(Collectors.toList());

        for (Structure structure : regionStructures) {
            final List<MarketOrderResponse> structureMarketOrders = marketOrders.stream()
                    .filter(o -> o.getLocationId() == structure.getStructureId())
                    .filter(o -> itemWatches.stream().anyMatch(w -> w.getLocationId() == o.getLocationId()))
                    .collect(Collectors.toList());

            final HashMap<Integer, Long> sellVolumes = computeVolumes(filterOrderType(structureMarketOrders, false));
            writeSnapshots(itemSnapshots, structure.getStructureId(), sellVolumes, false);

            final HashMap<Integer, Long> buyVolumes = computeVolumes(filterOrderType(structureMarketOrders, true));
            writeSnapshots(itemSnapshots, structure.getStructureId(), buyVolumes, true);
        }
    }

    private Map<Integer, List<Structure>> groupNpcStructures(List<Structure> structures) {
        final Map<Integer, List<Structure>> npcStationsGroupedByRegion = new HashMap<>();
        for (Structure structure : structures) {
            if (!structure.isNpcStation()) {
                continue;
            }
            try {
                final int regionId = getRegionId(structure);
                if (!npcStationsGroupedByRegion.containsKey(regionId)) {
                    npcStationsGroupedByRegion.put(regionId, new ArrayList<>());
                }
                final List<Structure> list = npcStationsGroupedByRegion.get(regionId);
                list.add(structure);
                npcStationsGroupedByRegion.put(regionId, list);
            } catch (BadRequestException e) {
                LOG.error(e);
            }
        }
        return npcStationsGroupedByRegion;
    }

    private void processPlayerOwnedMarket(final List<ItemWatch> itemWatches, final Structure structure, List<ItemSnapshot> itemSnapshots) {
        LOG.info("Parsing structure for locationId " + structure.getStructureId());

        final Set<Integer> typeIds = itemWatches.stream()
                .filter(w -> w.getLocationId() == structure.getStructureId())
                .map(ItemWatch::getTypeId).collect(Collectors.toSet());

        final Optional<Integer> accessCharacterId = findCharacterWithAccess(structure, itemWatches);
        if (accessCharacterId.isPresent()) {
            final int characterId = accessCharacterId.get();
            try {
                parsePlayerOwnedMarket(typeIds, structure, characterId, itemSnapshots);
                resetUserErrors(characterId);
            } catch (BadRequestException | UnknownUserException e) {

                if (e.getMessage().contains("invalid_token")) {
                    LOG.warn("Got an invalid_token for " + characterId);
                    updateUserErrors(characterId);
                }
                if (e.getMessage().contains("Market access denied")) {
                    disableWatchesForCharacter(itemWatches, structure, characterId);
                }

                LOG.warn("Failed to parse structure " + structure.getStructureId() + " with character "
                        + characterId + ": " + e.getMessage());
                // try again with next character
                processPlayerOwnedMarket(itemWatches, structure, itemSnapshots);
            }
        } else {
            LOG.warn("No character found for " + structure.getStructureId());
        }
    }

    private void disableWatchesForCharacter(List<ItemWatch> itemWatches, Structure structure, int characterId) {
        LOG.info("Disabling watches for character " + characterId + " and structure " + structure.getStructureId());
        itemWatches.stream()
                .filter(w -> w.getCharacterId() == characterId)
                .filter(w -> w.getLocationId() == structure.getStructureId())
                .peek(w -> w.setDisabled(true))
                .forEach(itemWatchRepository::save);
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
                LOG.info(user.getCharacterId() + " has been deleted due to too many client errors.");
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
        mail.setText("Your account at <a href=\"https://eve-market-watch.firebaseapp.com\">https://eve-market-watch.firebaseapp.com</a> has been deactivated due to an " +
                "invalid token. Please sign in to reactivate your account.");
        mailRepository.save(mail);
    }

    private void parsePlayerOwnedMarket(Set<Integer> typeIds, final Structure structure, final int characterId, List<ItemSnapshot> itemSnapshots) throws BadRequestException, UnknownUserException {
        final long locationId = structure.getStructureId();
        final List<MarketOrderResponse> marketOrders = getPlayerStructureMarketOrders(characterId, structure)
                .stream()
                .filter(m -> typeIds.contains(m.getTypeId()))
                .collect(Collectors.toList());

        final HashMap<Integer, Long> sellVolumes = computeVolumes(filterOrderType(marketOrders, false));
        writeSnapshots(itemSnapshots, locationId, sellVolumes, false);

        final HashMap<Integer, Long> buyVolumes = computeVolumes(filterOrderType(marketOrders, true));
        writeSnapshots(itemSnapshots, locationId, buyVolumes, true);
    }

    private List<MarketOrderResponse> filterOrderType(List<MarketOrderResponse> marketOrders, boolean isBuy) {
        return marketOrders.stream()
                .filter(marketOrderResponse -> isBuy == marketOrderResponse.isBuyOrder())
                .collect(Collectors.toList());
    }

    private void writeSnapshots(List<ItemSnapshot> existingSnapshots, long locationId, HashMap<Integer, Long> volumes, boolean isBuy) {
        for (final Map.Entry<Integer, Long> entry : volumes.entrySet()) {
            final int typeId = entry.getKey();
            final long amount = entry.getValue();
            final boolean alreadyExists = existingSnapshots.stream()
                    .filter(w -> w.isBuy() == isBuy)
                    .filter(w -> w.getAmount() == amount)
                    .anyMatch(w -> w.getTypeId() == typeId && w.getLocationId() == locationId);
            if (!alreadyExists) {
                final ItemSnapshot itemSnapshot = new ItemSnapshot();
                itemSnapshot.setId(typeId + "-" + locationId + "-" + (isBuy ? "buy" : "sell"));
                itemSnapshot.setTypeId(typeId);
                itemSnapshot.setAmount(amount);
                itemSnapshot.setLocationId(locationId);
                itemSnapshot.setBuy(isBuy);
                itemSnapshotRepository.save(itemSnapshot);
            }
        }
    }

    private HashMap<Integer, Long> computeVolumes(List<MarketOrderResponse> marketOrders) {
        final HashMap<Integer, Long> volumes = new HashMap<>();
        for (final MarketOrderResponse order : marketOrders) {
            volumes.computeIfPresent(order.getTypeId(), (integer, aLong) -> aLong + order.getVolumeRemain());
            volumes.putIfAbsent(order.getTypeId(), (long) order.getVolumeRemain());
        }
        return volumes;
    }

    private List<MarketOrderResponse> getRegionMarketOrders(final int regionId) throws BadRequestException {
        int page = 1;
        List<MarketOrderResponse> marketOrders = new ArrayList<>();
        List<MarketOrderResponse> chunk;
        do {
            LOG.info("Loading market orders for regionId/page: " + regionId + "/" + page);
            chunk = getRegionMarketOrders(regionId, page++);
            LOG.info("Collected page " + page + " with size " + chunk.size());
            marketOrders.addAll(chunk);
        } while (!chunk.isEmpty());
        return marketOrders;
    }

    private List<MarketOrderResponse> getRegionMarketOrders(int regionId, int page) throws BadRequestException {
        final Response response = webClient.target(Constants.ESI_BASE_URL)
                .path("/v1/markets/" + regionId + "/orders/")
                .queryParam("page", page)
                .request()
                .get();

        final String json = response.readEntity(String.class);
        if (response.getStatus() == 200) {
            return Arrays.asList(new GsonBuilder().create().fromJson(json, MarketOrderResponse[].class));
        } else {
            LOG.warn(json);
            throw new BadRequestException("Failed to retrieve market orders for region " + regionId);
        }
    }

    private List<MarketOrderResponse> getPlayerStructureMarketOrders(final int characterId, final Structure structure) throws BadRequestException, UnknownUserException {

        int page = 1;
        List<MarketOrderResponse> marketOrders = new ArrayList<>();
        final String accessToken = eveAuthService.getAccessToken(characterId);
        List<MarketOrderResponse> chunk;
        do {
            LOG.info("Loading market orders for structureId/page: " + structure.getStructureId() + "/" + page);
            chunk = getPlayerStructureMarketOrders(structure, accessToken, page++);
            LOG.info("Collected page " + page + " with size " + chunk.size());
            marketOrders.addAll(chunk);
        } while (!chunk.isEmpty());

        return marketOrders;
    }

    private List<MarketOrderResponse> getPlayerStructureMarketOrders(final Structure structure, final String accessToken, final int page) throws BadRequestException {
        final Response response = webClient.target(Constants.ESI_BASE_URL)
                .path("/v1/markets/structures/" + structure.getStructureId() + "/")
                .queryParam("page", page)
                .request()
                .header("Authorization", "Bearer " + accessToken)
                .get();

        final String json = response.readEntity(String.class);
        if (response.getStatus() == 200) {
            return Arrays.asList(new GsonBuilder().create().fromJson(json, MarketOrderResponse[].class));
        } else {
            LOG.warn(json);
            throw new BadRequestException("Failed to retrieve market orders for " + structure.getStructureId() + ": " + json);
        }
    }

    private int getRegionId(Structure station) throws BadRequestException {

        if (null != station.getRegionId()) {
            return station.getRegionId();
        }

        // todo: pull the region resolution into an upstream lambda. this code is not really market parsing but pre-work
        final StationInfoResponse stationInfo = getStationInfo(station.getStructureId());
        final int constellationId = getConstellationId(stationInfo.getSystemId());
        final int regionId = getRegionId(constellationId);

        station.setRegionId(regionId);
        structureRepository.save(station);

        return regionId;
    }

    private int getRegionId(int constellationId) throws BadRequestException {
        final int regionId;
        if (constellationToRegionMappings.containsKey(constellationId)) {
            regionId = constellationToRegionMappings.get(constellationId);
        } else {
            regionId = getConstellationInfo(constellationId).getRegionId();
            constellationToRegionMappings.put(constellationId, regionId);
        }
        return regionId;
    }

    private int getConstellationId(int systemId) throws BadRequestException {
        int constellationId;
        if (systemToConstellationMappings.containsKey(systemId)) {
            constellationId = systemToConstellationMappings.get(systemId);
        } else {
            constellationId = getSystemInfo(systemId).getConstellationId();
            systemToConstellationMappings.put(systemId, constellationId);
        }
        return constellationId;
    }

    private ConstellationInfoResponse getConstellationInfo(int constellationId) throws BadRequestException {
        final Response constellationResponse = webClient.target(Constants.ESI_BASE_URL)
                .path("/v1/universe/constellations/" + constellationId + "/")
                .request().get();
        if (constellationResponse.getStatus() != 200) {
            LOG.warn(constellationResponse.getStatus() + ": " + constellationResponse.readEntity(String.class));
            throw new BadRequestException("Failed to get constellation info for  " + constellationId);
        }
        return new GsonBuilder().create().fromJson(constellationResponse.readEntity(String.class), ConstellationInfoResponse.class);
    }

    private SystemInfoResponse getSystemInfo(int systemId) throws BadRequestException {
        final Response systemResponse = webClient.target(Constants.ESI_BASE_URL)
                .path("/v4/universe/systems/" + systemId + "/")
                .request().get();
        if (systemResponse.getStatus() != 200) {
            LOG.warn(systemResponse.getStatus() + ": " + systemResponse.readEntity(String.class));
            throw new BadRequestException("Failed to get system info for  " + systemId);
        }
        return new GsonBuilder().create().fromJson(systemResponse.readEntity(String.class), SystemInfoResponse.class);
    }

    private StationInfoResponse getStationInfo(long stationId) throws BadRequestException {
        final Response stationResponse = webClient.target(Constants.ESI_BASE_URL)
                .path("/v2/universe/stations/" + stationId + "/")
                .request().get();
        if (stationResponse.getStatus() != 200) {
            LOG.warn(stationResponse.getStatus() + ": " + stationResponse.readEntity(String.class));
            throw new BadRequestException("Failed to get station info for  " + stationId);
        }
        return new GsonBuilder().create().fromJson(stationResponse.readEntity(String.class), StationInfoResponse.class);
    }

    private Optional<Integer> findCharacterWithAccess(final Structure structure, final List<ItemWatch> itemWatches) {
        return itemWatches.stream()
                .filter(watch -> !watch.isDisabled())
                .filter(watch -> watch.getLocationId() == structure.getStructureId())
                .map(ItemWatch::getCharacterId)
                .filter(characterId -> userRepository.find(characterId).isPresent())
                .findAny();
    }
}
