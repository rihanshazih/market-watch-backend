package com.eve.marketwatch.jobs;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.api.ApiGatewayResponse;
import com.eve.marketwatch.model.dao.ItemWatch;
import com.eve.marketwatch.model.dao.ItemWatchRepository;
import com.eve.marketwatch.model.dao.Mail;
import com.eve.marketwatch.model.dao.MailRepository;
import com.eve.marketwatch.model.dao.MailStatus;
import com.eve.marketwatch.model.dao.Market;
import com.eve.marketwatch.model.dao.MarketRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class NotificationCreater implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private static final Logger LOG = LogManager.getLogger(NotificationCreater.class);

	private final ItemWatchRepository itemWatchRepository;
	private final MarketRepository marketRepository;
	private final MailRepository mailRepository;

	public NotificationCreater() {
		itemWatchRepository = ItemWatchRepository.getInstance();
		marketRepository = MarketRepository.getInstance();
		mailRepository = MailRepository.getInstance();
	}

	NotificationCreater(ItemWatchRepository itemWatchRepository, MarketRepository marketRepository, MailRepository mailRepository) {
		this.itemWatchRepository = itemWatchRepository;
		this.marketRepository = marketRepository;
		this.mailRepository = mailRepository;
	}

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
		LOG.info("received: {}", input);

		doCreate();

		return ApiGatewayResponse.builder()
				.setStatusCode(200)
				.build();
	}

	void doCreate() {
		// todo: move filtering to DB
		final List<ItemWatch> itemWatches = itemWatchRepository.findAll().stream()
				.filter(w -> !w.isMailSent() && w.isTriggered()).collect(Collectors.toList());

		LOG.info("Found " + itemWatches.size() + " watches that should receive a mail.");

		if (!itemWatches.isEmpty()) {
			LOG.info("Creating mail for " + itemWatches.size() + " item watches.");
			for (ItemWatch itemWatch : itemWatches) {
				process(itemWatch.getCharacterId());
				itemWatch.setMailSent(true);
				itemWatchRepository.save(itemWatch);
			}
		}
	}

	private void process(final int characterId) {
		LOG.info("Processing mail for " + characterId);
		final String text = buildText(characterId);
		final Mail mail = createMail(characterId, text);
		mailRepository.save(mail);
	}

	private Mail createMail(final int characterId, final String text) {
		final Mail mail = new Mail();
		mail.setRecipient(characterId);
		mail.setSubject("Market watch notification");
		mail.setText(text);
		mail.setMailStatus(MailStatus.NEW);
		return mail;
	}

	private String buildText(final int characterId) {
		// todo: move filtering to DB
		final List<ItemWatch> itemWatches = itemWatchRepository.findByCharacterId(characterId).stream()
				.filter(w -> !w.isMailSent() && w.isTriggered())
				.sorted((o1, o2) -> o1.getTypeName().compareToIgnoreCase(o2.getTypeName()))
				.collect(Collectors.toList());
		final List<Market> markets = itemWatches.stream()
				.map(ItemWatch::getLocationId).distinct()
				.map(marketRepository::find)
				.map(Optional::get).collect(Collectors.toList());

		final StringBuilder builder = new StringBuilder();

		for (final Market market : markets) {
			// <url=showinfo:47515//1027847407700>GE-8JV - SOTA FACTORY</url>
			builder.append("<url=showinfo:").append(market.getTypeId()).append("//")
					.append(market.getLocationId()).append(">")
					.append(market.getLocationName())
					.append("</url>")
					.append("\n\n");

			final List<ItemWatch> marketWatches = itemWatches.stream()
					.filter(w -> w.getLocationId() == market.getLocationId()).collect(Collectors.toList());
			for (final ItemWatch watch : marketWatches) {
				// <url=showinfo:608>Atron</url>
				builder.append("<url=showinfo:").append(watch.getTypeId()).append(">")
						.append(watch.getTypeName())
						.append("</url>")
						.append(" is below ").append(watch.getThreshold()).append(" units.\n");
			}
			// todo: section styling
			builder.append("\n\n");
		}

		builder.append("This mail was sent to you from https://eve-market-watch.firebaseapp.com");

		return builder.toString();
	}

}
