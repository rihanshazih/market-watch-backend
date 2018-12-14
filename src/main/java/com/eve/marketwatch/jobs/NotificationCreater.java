package com.eve.marketwatch.jobs;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.api.ApiGatewayResponse;
import com.eve.marketwatch.model.dao.ItemWatch;
import com.eve.marketwatch.model.dao.ItemWatchRepository;
import com.eve.marketwatch.model.dao.Mail;
import com.eve.marketwatch.model.dao.MailRepository;
import com.eve.marketwatch.model.dao.MailStatus;
import com.eve.marketwatch.model.dao.Structure;
import com.eve.marketwatch.model.dao.StructureRepository;
import com.eve.marketwatch.model.dao.User;
import com.eve.marketwatch.model.dao.UserRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class NotificationCreater implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private static final Logger LOG = LogManager.getLogger(NotificationCreater.class);

	private final ItemWatchRepository itemWatchRepository;
	private final StructureRepository structureRepository;
	private final MailRepository mailRepository;
	private final UserRepository userRepository;

	public NotificationCreater() {
		itemWatchRepository = ItemWatchRepository.getInstance();
		structureRepository = StructureRepository.getInstance();
		mailRepository = MailRepository.getInstance();
		userRepository = UserRepository.getInstance();
	}

	NotificationCreater(ItemWatchRepository itemWatchRepository, StructureRepository structureRepository, MailRepository mailRepository, UserRepository userRepository) {
		this.itemWatchRepository = itemWatchRepository;
		this.structureRepository = structureRepository;
		this.mailRepository = mailRepository;
		this.userRepository = userRepository;
	}

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
		doCreate();

		return ApiGatewayResponse.builder()
				.setStatusCode(200)
				.build();
	}

	void doCreate() {
		final Set<Integer> allCharacterIds = userRepository.findAll().stream()
				.map(User::getCharacterId).collect(Collectors.toSet());
		// todo: move filtering to DB
		final List<ItemWatch> itemWatches = itemWatchRepository.findAll().stream()
				.filter(w -> allCharacterIds.contains(w.getCharacterId()))
				.filter(w -> !w.isMailSent() && w.isTriggered())
				.collect(Collectors.toList());

		LOG.info("Found " + itemWatches.size() + " watches that should receive a mail.");

		final Set<Integer> characterIds = itemWatches.stream()
				.map(ItemWatch::getCharacterId)
				.collect(Collectors.toSet());

		LOG.info("Creating mail for " + characterIds.size() + " characters.");

		for (Integer characterId : characterIds) {
			process(characterId);
		}

		itemWatches.forEach(w -> {
					w.setMailSent(true);
					itemWatchRepository.save(w);
				});
	}

	private void process(final int characterId) {
		LOG.info("Creating mail for " + characterId);
		final String text = buildText(characterId);
		final Mail mail = createMail(characterId, text);
		mailRepository.save(mail);
	}

	private Mail createMail(final int characterId, final String text) {
		final Mail mail = new Mail();
		mail.setPriority(10);
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
		final List<Structure> structures = itemWatches.stream()
				.map(ItemWatch::getLocationId).distinct()
				.map(structureRepository::find)
				.map(Optional::get).collect(Collectors.toList());

		final StringBuilder builder = new StringBuilder();
		builder.append("Hi!\nThis is your overview of market notifications from https://eve-market-watch.firebaseapp.com\n\n");

		for (final Structure structure : structures) {
			// <url=showinfo:47515//1027847407700>GE-8JV - SOTA FACTORY</url>
			builder.append("<url=showinfo:").append(structure.getTypeId()).append("//")
					.append(structure.getStructureId()).append(">")
					.append(structure.getStructureName())
					.append("</url>")
					.append("\n\n");

			final List<ItemWatch> marketWatches = itemWatches.stream()
					.filter(w -> w.getLocationId() == structure.getStructureId()).collect(Collectors.toList());
			for (final ItemWatch watch : marketWatches) {
				// <url=showinfo:608>Atron</url>
				final boolean isLessThan = watch.getComparator() == null || watch.getComparator().equals("lt");
				builder.append("<url=showinfo:").append(watch.getTypeId()).append(">")
						.append(watch.getTypeName())
						.append("</url> is ")
						.append(isLessThan ? "below " : "above ")
						.append(watch.getThreshold()).append(" units.\n");
			}
			// todo: section styling
			builder.append("\n\n");
		}

		return builder.toString();
	}

}
