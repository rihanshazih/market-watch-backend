package com.eve.marketwatch.api;

import com.eve.marketwatch.Constants;
import com.eve.marketwatch.model.dao.Structure;
import com.eve.marketwatch.model.dao.StructureRepository;
import com.eve.marketwatch.model.esi.SearchResponse;
import com.eve.marketwatch.model.esi.StationInfoResponse;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StationResolver implements Callable<List<String>> {

    private static final Logger LOG = LogManager.getLogger(StationResolver.class);

    private static final int MAX_THREADS = 50;
    // multiply with two so we have enough space to resolve stations and structures parallel
    private final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(MAX_THREADS);

    private final String term;
    private final List<Structure> allKnownStructures;
    private final StructureRepository structureRepository;
    private final javax.ws.rs.client.Client webClient;

    public StationResolver(String term, List<Structure> allKnownStructures, StructureRepository structureRepository, Client webClient) {
        this.term = term;
        this.allKnownStructures = allKnownStructures;
        this.structureRepository = structureRepository;
        this.webClient = webClient;
    }

    @Override
    public List<String> call() throws Exception {
        final Response searchResponse = webClient.target(Constants.ESI_BASE_URL)
                .path("/v2/search/")
                .queryParam("categories", "station")
                .queryParam("search", term)
                .queryParam("strict", false)
                .request()
                .get();

        final String json = searchResponse.readEntity(String.class);
        if (searchResponse.getStatus() != 200) {
            LOG.info(json);
            LOG.warn("Response from station search was " + searchResponse.getStatus());
            return Collections.emptyList();
        }

        final SearchResponse search = new GsonBuilder().create().fromJson(json, SearchResponse.class);
        if (search == null || search.getStation() == null || search.getStation().length == 0) {
            LOG.info("No station was found for term " + term);
            return Collections.emptyList();
        }

        final List<Long> allKnownStructureIds = allKnownStructures.stream().map(Structure::getStructureId).collect(Collectors.toList());

        final List<Future<Structure>> futures = Stream.of(search.getStation())
                // filter out known structures so we don't have to request them again
                .filter(id -> !allKnownStructureIds.contains(id))
                .limit(MAX_THREADS)
                .map(stationId -> {
                    final Callable<Structure> callable = new StationResolution(stationId);
                    return executor.submit(callable);
                }).collect(Collectors.toList());

        final List<Structure> resolvedStructures = collectStructures(futures);

        resolvedStructures.stream()
                .filter(s -> !allKnownStructureIds.contains(s.getStructureId()))
                .forEach(structureRepository::save);

        // re add the structures that we already knew
        final List<Structure> locallyResolvedStructures = Stream.of(search.getStation())
                // filter out known structures so we don't have to request them again
                .filter(allKnownStructureIds::contains)
                .map(id -> {
                    for (Structure structure : allKnownStructures) {
                        if (structure.getStructureId() == id) {
                            return structure;
                        }
                    }
                    return null;
                }).filter(Objects::nonNull)
                .collect(Collectors.toList());

        resolvedStructures.addAll(locallyResolvedStructures);

        return resolvedStructures.stream()
                .filter(Structure::isMarketService)
                .map(Structure::getStructureName).collect(Collectors.toList());
    }

    private List<Structure> collectStructures(List<Future<Structure>> futures) {
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
        return resolvedStructures;
    }

    private class StationResolution implements Callable<Structure> {

        private final long stationId;

        public StationResolution(long stationId) {
            this.stationId = stationId;
        }

        @Override
        public Structure call() throws Exception {
            final Response nameResponse = webClient.target(Constants.ESI_BASE_URL)
                    .path("/v2/universe/stations/" + stationId + "/")
                    .request()
                    .get();

            final String nameJson = nameResponse.readEntity(String.class);
            if (nameResponse.getStatus() == 200) {
                final StationInfoResponse stationInfo = new GsonBuilder().create().fromJson(nameJson, StationInfoResponse.class);
                final Structure structure = new Structure();
                structure.setStructureId(stationId);
                structure.setStructureName(stationInfo.getName());
                structure.setTypeId(stationInfo.getTypeId());
                if (stationInfo.getServices().contains("market")) {
                    structure.setMarketService(true);
                }
                structure.setNpcStation(true);
                return structure;
            } else {
                LOG.info(nameJson);
                LOG.warn(nameResponse.getStatus());
                return null;
            }
        }
    }
}
