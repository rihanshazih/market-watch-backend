package com.eve.marketwatch.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.Constants;
import com.eve.marketwatch.model.dao.ItemWatch;
import com.eve.marketwatch.model.dao.ItemWatchRepository;
import com.eve.marketwatch.model.esi.SearchEntry;
import com.eve.marketwatch.model.esi.UniverseIdsResponse;
import com.eve.marketwatch.model.evepraisal.EvepraisalItem;
import com.eve.marketwatch.model.evepraisal.EvepraisalResponse;
import com.eve.marketwatch.service.SecurityService;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.eve.marketwatch.api.Util.parseBody;
import static java.util.Collections.singletonList;

public class AddItemWatchHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

    private static final Logger LOG = LogManager.getLogger(AddItemWatchHandler.class);

    private final ItemWatchRepository itemWatchRepository;
    private final javax.ws.rs.client.Client webClient;
    private final SecurityService securityService;

    public AddItemWatchHandler() {
        securityService = new SecurityService();
        webClient = ClientBuilder.newClient();
        itemWatchRepository = ItemWatchRepository.getInstance();
    }

    public AddItemWatchHandler(ItemWatchRepository itemWatchRepository, Client webClient, SecurityService securityService) {
        this.itemWatchRepository = itemWatchRepository;
        this.webClient = webClient;
        this.securityService = securityService;
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

        final ItemWatch watch = parseBody(ItemWatch.class, input);

        if (null == watch || null == watch.getTypeName() || watch.getTypeName().isEmpty()) {
            LOG.info("Bad ItemWatch: " + watch);
            return ApiGatewayResponse.builder()
                    .setStatusCode(400)
                    .build();
        }

        final int characterId = InputExtractor.getCharacterId(input);
        watch.setCharacterId(characterId);
        watch.setCreated(new Date());

        try {
            if (watch.getTypeName().contains("evepraisal")) {
                handleBulk(watch, characterId);
            } else {
                handleSingleItem(watch);
            }
        } catch (NotFoundException e) {
            return ApiGatewayResponse.builder()
                    .setStatusCode(404)
                    .build();
        }

        return ApiGatewayResponse.builder()
                .setStatusCode(201)
                .setObjectBody(watch)
                .build();
    }

    private void handleBulk(ItemWatch watchRequest, int characterId) throws NotFoundException {
        // split by /a/ to prevent rogue urls from being used
        String evepraisalPath = watchRequest.getTypeName().split("/a/")[1];
        if (!evepraisalPath.endsWith("json")) {
            evepraisalPath += ".json";
        }

        final Response appraisalResponse = webClient.target("https://evepraisal.com")
                .path("/a/" + evepraisalPath)
                .request()
                .get();

        final String json = appraisalResponse.readEntity(String.class);
        if (appraisalResponse.getStatus() != 200) {
            LOG.info(json);
            LOG.info("Evepraisal response code was " + appraisalResponse.getStatus());
            throw new NotFoundException();
        }

        final EvepraisalResponse evepraisal = new GsonBuilder().create().fromJson(json, EvepraisalResponse.class);
        final Set<Integer> typeIds = Stream.of(evepraisal.getItems()).map(EvepraisalItem::getTypeID).collect(Collectors.toSet());

        // delete old entries that we will override
        itemWatchRepository.findByCharacterId(characterId).stream()
                .filter(w -> w.getLocationId() == watchRequest.getLocationId())
                .filter(w -> typeIds.contains(w.getTypeId()))
                .forEach(itemWatchRepository::delete);

        for (EvepraisalItem item : evepraisal.getItems()) {
            final ItemWatch itemWatch = new ItemWatch();
            itemWatch.setCharacterId(watchRequest.getCharacterId());
            itemWatch.setLocationId(watchRequest.getLocationId());
            itemWatch.setTypeId(item.getTypeID());
            itemWatch.setTypeName(item.getTypeName());
            itemWatch.setThreshold(item.getQuantity());
            itemWatch.setCreated(watchRequest.getCreated());
            itemWatch.setBuy(watchRequest.isBuy());
            itemWatchRepository.save(itemWatch);
        }
    }

    private void handleSingleItem(ItemWatch watch) throws NotFoundException {
        final SearchEntry itemInfo = getTypeId(watch.getTypeName());
        watch.setTypeName(itemInfo.getName());
        watch.setTypeId(itemInfo.getId());
        watch.reset();
        itemWatchRepository.save(watch);
    }

    SearchEntry getTypeId(final String typeName) throws NotFoundException {
        final Response idResponse = webClient.target(Constants.ESI_BASE_URL)
                .path("/v1/universe/ids/")
                .request()
                .post(Entity.entity(singletonList(typeName), "application/json"));
        final String json = idResponse.readEntity(String.class);
        if (idResponse.getStatus() != 200) {
            LOG.info(json);
            LOG.error("Failed to resolve typeId for " + typeName + ": " + idResponse.getStatus());
            throw new NotFoundException();
        }

        final UniverseIdsResponse universeIdsResponse = new GsonBuilder().create().fromJson(json, UniverseIdsResponse.class);
        final SearchEntry[] inventoryTypes = universeIdsResponse.getInventoryTypes();
        if (inventoryTypes == null || inventoryTypes.length == 0) {
            LOG.error("Response from /universe/ids did not contain any inventoryTypes for " + typeName);
            throw new NotFoundException();
        }
        return inventoryTypes[0];
    }

    class NotFoundException extends Throwable {
    }
}
