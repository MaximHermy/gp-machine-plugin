package com.gpmachine;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Sends the player's own Grand Exchange offers to the GP MACHINE dashboard.
 * Read only: it listens to offer updates and never acts in the game.
 */
@Slf4j
@PluginDescriptor(
	name = "GP MACHINE",
	description = "Sends your Grand Exchange offers to your GP MACHINE flipping dashboard",
	tags = {"grand exchange", "ge", "flipping", "trade"}
)
public class GpMachinePlugin extends Plugin
{
	static final String CONFIG_GROUP = "gpmachine";
	private static final long SEND_DELAY_MS = 2000;

	@Inject
	private Client client;

	@Inject
	private GpMachineConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	@Inject
	private ScheduledExecutorService executor;

	private final Map<Integer, Map<String, Object>> pending = new ConcurrentHashMap<>();
	private volatile String account;
	private volatile boolean sendScheduled;

	@Provides
	GpMachineConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GpMachineConfig.class);
	}

	@Override
	protected void shutDown()
	{
		pending.clear();
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		if (!config.enabled() || config.key().trim().isEmpty() || client.getAccountHash() == -1)
		{
			return;
		}
		GrandExchangeOffer offer = event.getOffer();
		GrandExchangeOfferState state = offer.getState();
		if (state == GrandExchangeOfferState.EMPTY)
		{
			// RuneLite also reports empty slots while logging in: ignore them, the slot keeps its offer id
			return;
		}
		int slot = event.getSlot();
		boolean buy = state == GrandExchangeOfferState.BUYING || state == GrandExchangeOfferState.BOUGHT
			|| state == GrandExchangeOfferState.CANCELLED_BUY;
		boolean complete = state != GrandExchangeOfferState.BUYING && state != GrandExchangeOfferState.SELLING;
		long now = System.currentTimeMillis();

		String key = "slot" + slot;
		OfferTracker.Result id = OfferTracker.identify(slot, offer.getItemId(), offer.getPrice(), offer.getTotalQuantity(),
			buy, offer.getQuantitySold(), complete, configManager.getRSProfileConfiguration(CONFIG_GROUP, key), now);
		configManager.setRSProfileConfiguration(CONFIG_GROUP, key, id.stored);

		account = anonymousId(client.getAccountHash());
		pending.put(slot, OfferSender.offer(id.offerId, slot, offer.getItemId(), buy, offer.getPrice(),
			offer.getTotalQuantity(), offer.getQuantitySold(), offer.getSpent(), state.name(), client.getWorld(),
			id.started, now));

		if (!sendScheduled)
		{
			// wait a moment, so the offers of one login or one tick go together in one message
			sendScheduled = true;
			executor.schedule(this::send, SEND_DELAY_MS, TimeUnit.MILLISECONDS);
		}
	}

	private void send()
	{
		sendScheduled = false;
		List<Map<String, Object>> offers = new ArrayList<>();
		for (Integer slot : new ArrayList<>(pending.keySet()))
		{
			Map<String, Object> o = pending.remove(slot);
			if (o != null)
			{
				offers.add(o);
			}
		}
		if (offers.isEmpty() || account == null)
		{
			return;
		}
		Request request;
		try
		{
			request = OfferSender.request(gson, config.serverUrl(), config.key(), account, offers);
		}
		catch (IllegalArgumentException e)
		{
			log.warn("GP MACHINE: invalid server address");
			return;
		}
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("GP MACHINE: sending failed, will retry with the next update", e);
				for (Map<String, Object> o : offers)
				{
					pending.putIfAbsent((Integer) o.get("slot"), o);
				}
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					if (!r.isSuccessful())
					{
						log.warn("GP MACHINE: server answered {}", r.code());
					}
				}
			}
		});
	}

	/**
	 * An anonymous id for the account: a hash of RuneLite's account hash. The player's name is never sent.
	 */
	static String anonymousId(long accountHash)
	{
		try
		{
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			byte[] digest = sha.digest(Long.toString(accountHash).getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (int i = 0; i < 8; i++)
			{
				hex.append(String.format("%02x", digest[i]));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			return Long.toHexString(accountHash);
		}
	}
}
