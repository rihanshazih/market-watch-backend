package com.eve.marketwatch.jobs;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.api.ApiGatewayResponse;
import com.eve.marketwatch.model.dao.ItemSnapshot;
import com.eve.marketwatch.model.dao.ItemSnapshotRepository;
import com.eve.marketwatch.model.dao.ItemWatch;
import com.eve.marketwatch.model.dao.ItemWatchRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

public class WatchChecker implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private static final Logger LOG = LogManager.getLogger(WatchChecker.class);
	private static final int MISSING_DELAY = 10;

	private final ItemWatchRepository itemWatchRepository;
	private final ItemSnapshotRepository itemSnapshotRepository;

	public WatchChecker() {
		itemWatchRepository = ItemWatchRepository.getInstance();
		itemSnapshotRepository = ItemSnapshotRepository.getInstance();
	}

	WatchChecker(ItemWatchRepository itemWatchRepository, ItemSnapshotRepository itemSnapshotRepository) {
		this.itemWatchRepository = itemWatchRepository;
		this.itemSnapshotRepository = itemSnapshotRepository;
	}

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
		doCheck();

		return ApiGatewayResponse.builder()
				.setStatusCode(200)
				.build();
	}

	void doCheck() {
		final List<ItemWatch> itemWatches = itemWatchRepository.findAll();
		final List<ItemSnapshot> itemSnapshots = itemSnapshotRepository.findAll();

		LOG.info("Checking " + itemWatches.size() + " watches against " + itemSnapshots.size() + " snapshots.");
		for (final ItemWatch watch : itemWatches) {
			checkWatch(itemSnapshots, watch);
		}

	}

	private void checkWatch(List<ItemSnapshot> itemSnapshots, ItemWatch watch) {
		for (ItemSnapshot snapshot : itemSnapshots) {
			if (isSameLocationAndType(watch, snapshot)) {
				final boolean isLessThan = watch.getComparator() == null || watch.getComparator().equals("lt");
				if (isLessThan && snapshot.getAmount() < watch.getThreshold()
					|| !isLessThan && snapshot.getAmount() > watch.getThreshold()) {
					if (!watch.isTriggered()) {
						LOG.info("Triggered watch: " + watch);
						watch.setTriggered(true);
						itemWatchRepository.save(watch);
					}
				} else if (watch.isTriggered() || watch.isMailSent()) {
					LOG.info("Reset watch: " + watch);
					watch.reset();
					itemWatchRepository.save(watch);
				}
				return;
			}
		}
		if (isOlderThanMinimumDelay(watch) && !watch.isTriggered()) {
			handleMissingSnapshot(watch);
		}
	}

	private boolean isOlderThanMinimumDelay(final ItemWatch watch) {
        if (null == watch.getCreated()) {
            // todo: remove once all entries are migrated (= have a created date)
            watch.setCreated(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)));
            itemWatchRepository.save(watch);
        }
		return watch.getCreated().before(Date.from(Instant.now().minus(MISSING_DELAY, ChronoUnit.MINUTES)));
	}

	private void handleMissingSnapshot(ItemWatch watch) {
		LOG.info("Snapshot is missing and therefore triggered: " + watch);
		watch.setTriggered(true);
		itemWatchRepository.save(watch);
	}

	private boolean isSameLocationAndType(ItemWatch watch, ItemSnapshot snapshot) {
		return watch.getTypeId() == snapshot.getTypeId() && watch.getLocationId() == snapshot.getLocationId();
	}

}
