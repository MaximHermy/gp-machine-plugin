package com.gpmachine;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.ScriptID;
import net.runelite.api.VarClientStr;
import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.HotkeyListener;
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
	description = "Live GE flips, your stock and profit in a side panel, and your offers sent to your GP MACHINE dashboard",
	tags = {"grand exchange", "ge", "flipping", "trade"}
)
public class GpMachinePlugin extends Plugin
{
	static final String CONFIG_GROUP = "gpmachine";
	private static final long SEND_DELAY_MS = 2000;
	private static final long PANEL_REFRESH_S = 60;

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

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClientThread clientThread;

	@Inject
	private KeyManager keyManager;

	private final Map<Integer, Map<String, Object>> pending = new ConcurrentHashMap<>();
	private volatile String account;
	private volatile boolean sendScheduled;

	private GpMachinePanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> refresher;
	private volatile Map<String, Object> selectedFlip;

	private final HotkeyListener priceHotkey = new HotkeyListener(() -> config.priceKey())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invoke(GpMachinePlugin.this::fillPrice);
		}
	};

	@Provides
	GpMachineConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GpMachineConfig.class);
	}

	@Override
	protected void startUp()
	{
		keyManager.registerKeyListener(priceHotkey);
		if (config.panel())
		{
			showPanel();
		}
	}

	@Override
	protected void shutDown()
	{
		pending.clear();
		keyManager.unregisterKeyListener(priceHotkey);
		hidePanel();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CONFIG_GROUP.equals(event.getGroup()))
		{
			return;
		}
		if ("panel".equals(event.getKey()))
		{
			if (config.panel())
			{
				showPanel();
			}
			else
			{
				hidePanel();
			}
		}
		else if ("key".equals(event.getKey()) || "serverUrl".equals(event.getKey()))
		{
			refreshPanel();
		}
	}

	private void showPanel()
	{
		if (panel != null)
		{
			return;
		}
		panel = new GpMachinePanel(flip -> selectedFlip = flip);
		navButton = NavigationButton.builder()
			.tooltip("GP MACHINE")
			.icon(icon())
			.priority(5)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		refresher = executor.scheduleAtFixedRate(this::refreshPanel, 1, PANEL_REFRESH_S, TimeUnit.SECONDS);
	}

	private void hidePanel()
	{
		if (refresher != null)
		{
			refresher.cancel(false);
			refresher = null;
		}
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		panel = null;
	}

	/**
	 * Fetches the packet for the side panel (flips for your capital and your day) from the GP MACHINE server.
	 */
	private void refreshPanel()
	{
		GpMachinePanel p = panel;
		if (p == null)
		{
			return;
		}
		String key = config.key().trim();
		if (key.isEmpty())
		{
			SwingUtilities.invokeLater(() -> p.setStatus("Paste your key in the plugin settings (dashboard > Settings > Plugin).", true));
			return;
		}
		String url = panelUrl(config.serverUrl());
		Request request;
		try
		{
			request = new Request.Builder().url(url).header("Authorization", "Bearer " + key).get().build();
		}
		catch (IllegalArgumentException e)
		{
			SwingUtilities.invokeLater(() -> p.setStatus("Invalid server address.", true));
			return;
		}
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				SwingUtilities.invokeLater(() -> p.setStatus("No connection to the server. Retrying every minute.", true));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					if (!r.isSuccessful() || r.body() == null)
					{
						int code = r.code();
						SwingUtilities.invokeLater(() -> p.setStatus(code == 401 ? "Key not accepted. Make a new one on the dashboard."
							: "Server answered " + code + ".", true));
						return;
					}
					Map<String, Object> packet = gson.fromJson(r.body().string(), new TypeToken<Map<String, Object>>()
					{
					}.getType());
					SwingUtilities.invokeLater(() ->
					{
						p.setStatus("Updated " + java.time.LocalTime.now().withNano(0) + " \u00b7 every minute", false);
						p.update(packet);
					});
				}
				catch (Exception e)
				{
					SwingUtilities.invokeLater(() -> p.setStatus("Could not read the server's answer.", true));
				}
			}
		});
	}

	/**
	 * The panel address from the offers address: .../api/v1/ge becomes .../api/v1/panel.
	 */
	static String panelUrl(String serverUrl)
	{
		String u = serverUrl.trim();
		if (u.endsWith("/ge"))
		{
			return u.substring(0, u.length() - 3) + "/panel";
		}
		return u.endsWith("/") ? u + "api/v1/panel" : u + "/api/v1/panel";
	}

	/**
	 * The price hotkey: while the Grand Exchange asks for a price, fill in the GP MACHINE price for the item of the
	 * offer (buy price for a buy offer, sell price for a sell offer). The player still confirms the offer. Nothing is
	 * bought or sold by the plugin.
	 */
	private void fillPrice()
	{
		GpMachinePanel p = panel;
		Widget input = client.getWidget(ComponentID.CHATBOX_FULL_INPUT);
		if (p == null || input == null || input.isHidden())
		{
			return;
		}
		int item = client.getVarpValue(VarPlayer.CURRENT_GE_ITEM);
		boolean sell = client.getVarbitValue(Varbits.GE_OFFER_CREATION_TYPE) == 1;
		Map<String, Object> flip = null;
		for (Map<String, Object> f : p.flips())
		{
			if (GpMachinePanel.toLong(f.get("item_id")) == item)
			{
				flip = f;
				break;
			}
		}
		if (flip == null)
		{
			flip = selectedFlip;
		}
		if (flip == null || (item > 0 && GpMachinePanel.toLong(flip.get("item_id")) != item))
		{
			return;
		}
		long price = GpMachinePanel.toLong(flip.get(sell ? "sell" : "buy"));
		if (price <= 0)
		{
			return;
		}
		client.setVarcStrValue(VarClientStr.INPUT_TEXT, String.valueOf(price));
		client.runScript(ScriptID.CHAT_PROMPT_INIT);
	}

	/**
	 * The icon on the RuneLite side bar: a gold coin with GP, drawn here so the plugin needs no image file.
	 */
	private static BufferedImage icon()
	{
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(0xb8, 0x86, 0x0b));
		g.fillOval(0, 0, 16, 16);
		g.setColor(new Color(0xff, 0xcf, 0x3f));
		g.fillOval(2, 2, 12, 12);
		g.setColor(new Color(0x4a, 0x35, 0x05));
		g.setFont(g.getFont().deriveFont(java.awt.Font.BOLD, 8f));
		g.drawString("GP", 2, 11);
		g.dispose();
		return img;
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
