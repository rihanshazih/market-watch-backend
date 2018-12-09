package com.eve.marketwatch.jobs;

import com.eve.marketwatch.model.dao.Structure;
import com.eve.marketwatch.model.dao.StructureRepository;
import com.eve.marketwatch.model.esi.SearchResponse;
import com.eve.marketwatch.model.esi.StructureInfoResponse;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

public class StructureResolver implements Callable<List<String>> {

    private static final Logger LOG = LogManager.getLogger(StructureResolver.class);

    private static final int MAX_THREADS = 50;
    // multiply with two so we have enough space to resolve stations and structures parallel
    private final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(MAX_THREADS);

    private static final List<Integer> IGNORED_STRUCTURE_TYPES = Arrays.asList(
            35825, // raitaru
            35835, // athanor
            35836, // tatara
            35841, // ansiblex jump gate
            35840, // pharolux cyno beacon
            37534, // cerebrex cyno jammer
            27674); // cynosural system jammer

    private final String term;
    private final List<Structure> allKnownStructures;
    private final StructureRepository structureRepository;
    private final javax.ws.rs.client.Client webClient;
    private final int characterId;
    private final String accessToken;

    public StructureResolver(String term, List<Structure> allKnownStructures, StructureRepository structureRepository, Client webClient, int characterId, String accessToken) {
        this.term = term;
        this.allKnownStructures = allKnownStructures;
        this.structureRepository = structureRepository;
        this.webClient = webClient;
        this.characterId = characterId;
        this.accessToken = accessToken;
    }

    @Override
    public List<String> call() throws Exception {
        final Response searchResponse = webClient.target("https://esi.evetech.net")
                .path("/v3/characters/" + characterId + "/search/")
                .queryParam("categories", "structure")
                .queryParam("search", term)
                .queryParam("strict", false)
                .request()
                .header("Authorization", "Bearer " + accessToken)
                .get();

        final String json = searchResponse.readEntity(String.class);
        LOG.info(json);
        if (searchResponse.getStatus() != 200) {
            LOG.warn("Response from structure search was " + searchResponse.getStatus());
            return Collections.emptyList();
        }

        final SearchResponse search = new GsonBuilder().create().fromJson(json, SearchResponse.class);
        if (search == null || search.getStructure() == null || search.getStructure().length == 0) {
            LOG.info("No structure was found for term " + term);
            return Collections.emptyList();
        }

        return getMarkets(accessToken, search, allKnownStructures).stream()
                .map(Structure::getStructureName).collect(Collectors.toList());
    }


    private List<Structure> getMarkets(String accessToken, SearchResponse search, List<Structure> allKnownStructures) {
        final List<Long> allKnownStructureIds = allKnownStructures.stream().map(Structure::getStructureId).collect(Collectors.toList());
        final List<Long> knownNonMarketStructureIds = allKnownStructures.stream()
                .filter(m -> IGNORED_STRUCTURE_TYPES.contains(m.getTypeId()))
                .map(Structure::getStructureId)
                .collect(Collectors.toList());

        final List<Long> searchedStructureIds = Arrays.stream(search.getStructure())
                .filter(id -> !knownNonMarketStructureIds.contains(id))
                .limit(MAX_THREADS)
                .collect(Collectors.toList());
        LOG.info("After filtering " + search.getStructure().length + " structures we now have " + searchedStructureIds.size());

        // don't resolve the structure ids from the database, as a user might not have access to those structures
        // the ACL is verified by the structure resolution call
        final List<Future<Structure>> futures = searchedStructureIds.stream().map(structureId -> {
            final Callable<Structure> callable = new StructureResolution(structureId, accessToken);
            return executor.submit(callable);
        }).collect(Collectors.toList());

        final List<Structure> resolvedStructures = collectStructures(futures);

        resolvedStructures.stream()
                .filter(s -> !allKnownStructureIds.contains(s.getStructureId()))
                .forEach(structureRepository::save);

        return resolvedStructures.stream()
                .filter(s -> !IGNORED_STRUCTURE_TYPES.contains(s.getTypeId()))
                .collect(Collectors.toList());
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

    private class StructureResolution implements Callable<Structure> {

        private final long structureId;
        private final String accessToken;

        private StructureResolution(long structureId, String accessToken) {
            this.structureId = structureId;
            this.accessToken = accessToken;
        }

        @Override
        public Structure call() throws Exception {
            LOG.info("Resolving name for structureId " + structureId);
            final Response nameResponse = webClient.target("https://esi.evetech.net")
                    .path("/v2/universe/structures/" + structureId + "/")
                    .request()
                    .header("Authorization", "Bearer " + accessToken)
                    .get();
            final String nameJson = nameResponse.readEntity(String.class);
            if (nameResponse.getStatus() == 200) {
                final StructureInfoResponse structureInfo = new GsonBuilder().create().fromJson(nameJson, StructureInfoResponse.class);
                final Structure structure = new Structure();
                structure.setStructureId(structureId);
                structure.setStructureName(structureInfo.getName());
                structure.setTypeId(structureInfo.getTypeId());
                return structure;
            } else {
                LOG.info(nameJson);
                LOG.warn(nameResponse.getStatus());
                return null;
            }
        }
    }
}
