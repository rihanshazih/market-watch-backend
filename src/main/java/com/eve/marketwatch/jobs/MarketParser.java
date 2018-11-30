package com.eve.marketwatch.jobs;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.api.ApiGatewayResponse;
import com.eve.marketwatch.service.EveAuthService;
import com.eve.marketwatch.exceptions.BadRequestException;
import com.eve.marketwatch.model.dao.ItemSnapshot;
import com.eve.marketwatch.model.dao.ItemSnapshotRepository;
import com.eve.marketwatch.model.dao.ItemWatch;
import com.eve.marketwatch.model.dao.ItemWatchRepository;
import com.eve.marketwatch.model.dao.Mail;
import com.eve.marketwatch.model.dao.MailRepository;
import com.eve.marketwatch.model.dao.MailStatus;
import com.eve.marketwatch.model.dao.Market;
import com.eve.marketwatch.model.esi.MarketOrderResponse;
import com.eve.marketwatch.model.dao.MarketRepository;
import com.eve.marketwatch.model.dao.User;
import com.eve.marketwatch.model.dao.UserRepository;
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
    private final MarketRepository marketRepository;
    private final UserRepository userRepository;
    private final ItemWatchRepository itemWatchRepository;
    private final ItemSnapshotRepository itemSnapshotRepository;
    private final EveAuthService eveAuthService;
    private final MailRepository mailRepository;

    public MarketParser() {
        marketRepository = MarketRepository.getInstance();
        userRepository = UserRepository.getInstance();
        itemWatchRepository = ItemWatchRepository.getInstance();
        itemSnapshotRepository = ItemSnapshotRepository.getInstance();
        eveAuthService = new EveAuthService();
        mailRepository = MailRepository.getInstance();
    }

    MarketParser(MarketRepository marketRepository, UserRepository userRepository, ItemWatchRepository itemWatchRepository, ItemSnapshotRepository itemSnapshotRepository, EveAuthService eveAuthService, MailRepository mailRepository) {
        this.marketRepository = marketRepository;
        this.userRepository = userRepository;
        this.itemWatchRepository = itemWatchRepository;
        this.itemSnapshotRepository = itemSnapshotRepository;
        this.eveAuthService = eveAuthService;
        this.mailRepository = mailRepository;
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
        final List<ItemWatch> itemWatches = itemWatchRepository.findAll();
        final Set<Long> locationIds = itemWatches.stream().map(ItemWatch::getLocationId).collect(Collectors.toSet());
        final Set<Integer> typeIds = itemWatches.stream().map(ItemWatch::getTypeId).collect(Collectors.toSet());

        final List<Market> markets = marketRepository.findAll().stream().filter(market -> locationIds.contains(market.getLocationId())).collect(Collectors.toList());
        for (final Market market : markets) {
            processMarket(itemWatches, typeIds, market);
        }
    }

    private void processMarket(List<ItemWatch> itemWatches, Set<Integer> typeIds, Market market) {
        LOG.info("Parsing market for locationId " + market.getLocationId());
        final Optional<Integer> accessCharacterId = findCharacterWithAccess(market, itemWatches);
        if (accessCharacterId.isPresent()) {
            final int characterId = accessCharacterId.get();
            try {
                parseMarket(typeIds, market, characterId);
                resetUserErrors(characterId);
            } catch (BadRequestException e) {

                if (e.getMessage().contains("invalid_token")) {
                    LOG.warn("Got an invalid_token for " + characterId);
                    updateUserErrors(characterId);
                }

                LOG.warn("Failed to parse market " + market.getLocationId() + " with character "
                        + characterId + ": " + e.getMessage());
                // try again with next character
                processMarket(itemWatches, typeIds, market);
            }
        } else {
            // if this point is reached, we likely have a bug
            throw new RuntimeException("No character has watches for " + market.getLocationId());
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
                "invalid token. Please sign in to reactive your account.");
        mailRepository.save(mail);
    }

    private void parseMarket(Set<Integer> typeIds, Market market, final int characterId) throws BadRequestException {
        final HashMap<Integer, Long> volumes = new HashMap<>();
        final long locationId = market.getLocationId();
        final List<MarketOrderResponse> marketOrders = getMarketOrders(characterId, locationId)
                .stream()
                .filter(m -> !m.isBuyOrder())
                .filter(m -> typeIds.contains(m.getTypeId()))
                .collect(Collectors.toList());
        for (final MarketOrderResponse order : marketOrders) {
            volumes.computeIfPresent(order.getTypeId(), (integer, aLong) -> aLong + order.getVolumeRemain());
            volumes.putIfAbsent(order.getTypeId(), (long) order.getVolumeRemain());
        }

        for (final Map.Entry<Integer, Long> entry : volumes.entrySet()) {
            final ItemSnapshot itemSnapshot = new ItemSnapshot();
            final int typeId = entry.getKey();
            itemSnapshot.setId(typeId + "-" + locationId);
            itemSnapshot.setTypeId(typeId);
            itemSnapshot.setAmount(entry.getValue());
            itemSnapshot.setLocationId(locationId);
            itemSnapshotRepository.save(itemSnapshot);
        }
    }

    private List<MarketOrderResponse> getMarketOrders(final int characterId, final long structureId) throws BadRequestException {
        int page = 1;
        final List<MarketOrderResponse> marketOrders = new ArrayList<>();
        final String accessToken = getAccessToken(characterId);
        List<MarketOrderResponse> chunk;
        do {
            chunk = getMarketOrders(structureId, accessToken, page++);
            LOG.info("Collected another chunk with size " + chunk.size());
            marketOrders.addAll(chunk);
        } while (!chunk.isEmpty());
        return marketOrders;
    }

    private String getAccessToken(final int characterId) throws BadRequestException {
        final Optional<User> user = userRepository.find(characterId);
        if (!user.isPresent()) {
            throw new BadRequestException("User " + characterId + "does not exist anymore.");
        }
        final String refreshToken = user.get().getRefreshToken();
        return eveAuthService.generateAccessToken(refreshToken).getAccessToken();
    }

    private List<MarketOrderResponse> getMarketOrders(final long structureId, final String accessToken, final int page) throws BadRequestException {
        LOG.info("Loading market orders for structureId/page: " + structureId + "/" + page);
        final Response response = webClient.target("https://esi.evetech.net")
                .path("/v1/markets/structures/" + structureId + "/")
                .queryParam("page", page)
                .request()
                .header("Authorization", "Bearer " + accessToken)
                .get();

        final String json = response.readEntity(String.class);
        LOG.info(json);
        if (response.getStatus() == 200) {
            return Arrays.asList(new GsonBuilder().create().fromJson(json, MarketOrderResponse[].class));
        } else {
            throw new BadRequestException("Failed to retrieve market orders for " + structureId);
        }
    }

    private Optional<Integer> findCharacterWithAccess(final Market market, final List<ItemWatch> itemWatches) {
        return itemWatches.stream()
                .filter(i -> i.getLocationId() == market.getLocationId())
                .map(ItemWatch::getCharacterId)
                .findAny();
    }
}
