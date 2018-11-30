package com.eve.marketwatch.jobs;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.ApiGatewayResponse;
import com.eve.marketwatch.EsiAuthUtil;
import com.eve.marketwatch.model.AccessTokenResponse;
import com.eve.marketwatch.model.ItemSnapshot;
import com.eve.marketwatch.model.ItemSnapshotRepository;
import com.eve.marketwatch.model.ItemWatch;
import com.eve.marketwatch.model.ItemWatchRepository;
import com.eve.marketwatch.model.Market;
import com.eve.marketwatch.model.MarketOrderResponse;
import com.eve.marketwatch.model.MarketRepository;
import com.eve.marketwatch.model.User;
import com.eve.marketwatch.model.UserRepository;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
	private final EsiAuthUtil esiAuthUtil;

	public MarketParser() {
		marketRepository = MarketRepository.getInstance();
		userRepository = UserRepository.getInstance();
		itemWatchRepository = ItemWatchRepository.getInstance();
		itemSnapshotRepository = ItemSnapshotRepository.getInstance();
		esiAuthUtil = new EsiAuthUtil();
	}

	MarketParser(MarketRepository marketRepository, UserRepository userRepository, ItemWatchRepository itemWatchRepository, ItemSnapshotRepository itemSnapshotRepository, EsiAuthUtil esiAuthUtil) {
		this.marketRepository = marketRepository;
		this.userRepository = userRepository;
		this.itemWatchRepository = itemWatchRepository;
		this.itemSnapshotRepository = itemSnapshotRepository;
		this.esiAuthUtil = esiAuthUtil;
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
			LOG.info("Parsing market for locationId " + market.getLocationId());
			final Optional<Integer> accessCharacterId = findCharacterWithAccess(market, itemWatches);
			if (accessCharacterId.isPresent()) {
				final HashMap<Integer, Long> volumes = new HashMap<>();
				final List<MarketOrderResponse> marketOrders = getMarketOrders(accessCharacterId.get(), market.getLocationId())
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
					itemSnapshot.setId(entry.getKey() + "-" + market.getLocationId());
					itemSnapshot.setTypeId(entry.getKey());
					itemSnapshot.setAmount(entry.getValue());
					itemSnapshot.setLocationId(market.getLocationId());
					itemSnapshotRepository.save(itemSnapshot);
				}
			} else {
				// todo: better handling
				LOG.warn("Failed to find accessCharacterId for locationId: " + market.getLocationId());
			}
		}
	}

	private List<MarketOrderResponse> getMarketOrders(final Integer accessCharacterId, final long structureId) {
		final Optional<User> userOpt = userRepository.find(accessCharacterId);
		int page = 1;
		final List<MarketOrderResponse> marketOrders = new ArrayList<>();
		if (userOpt.isPresent()) {
			final User user = userOpt.get();
			final AccessTokenResponse at = esiAuthUtil.generateAccessToken(user.getRefreshToken());
			final String accessToken = at.getAccessToken();
			// todo: migrate access token into dedicated method
			List<MarketOrderResponse> chunk;
			do {
				chunk = getMarketOrders(structureId, accessToken, page++);
				LOG.info("Collected another chunk with size " + chunk.size());
				marketOrders.addAll(chunk);
			} while (!chunk.isEmpty());
			return marketOrders;
		} else {
			// todo: better handling
			LOG.warn("Failed to get user entry for characterId " + accessCharacterId);
			return Collections.emptyList();
		}
	}

	private List<MarketOrderResponse> getMarketOrders(final long structureId, final String accessToken, final int page) {
		LOG.info("Loading market orders for structureId/page: " + structureId + "/" + page);
		final Response response = webClient.target("https://esi.evetech.net")
				.path("/v1/markets/structures/" + structureId + "/")
				.queryParam("page", page)
				.request()
				.header("Authorization", "Bearer " + accessToken)
				.get();

		final String json = response.readEntity(String.class);
		return Arrays.asList(new GsonBuilder().create().fromJson(json, MarketOrderResponse[].class));
	}

	private Optional<Integer> findCharacterWithAccess(final Market market, final List<ItemWatch> itemWatches) {
		return itemWatches.stream().filter(i -> i.getLocationId() == market.getLocationId())
				.map(ItemWatch::getCharacterId).findAny();
	}
}
