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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.ScriptID;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.VarClientStr;
import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.HotkeyListener;
import okhttp3.MediaType;
import okhttp3.RequestBody;
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

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private WorldMapPointManager worldMapPointManager;

	@Inject
	private Notifier notifier;

	private static final long OFFER_NOTIFY_MIN = 30;
	private final Set<String> notified = new HashSet<>();

	private static final long LEVELS_EVERY_S = 15 * 60;
	private static final int COINS = 995;

	// Grind Machine: the running session (null = none). Only read from the game, never anything done in it.
	private volatile GrindState grind;
	private GrindOverlay grindOverlay;
	private GrindSceneOverlay grindSceneOverlay;
	private final Set<TileObject> stepObjects = new HashSet<>();
	private final Map<Integer, Integer> grindGainedItems = new HashMap<>();
	private final Map<Integer, Integer> grindUsedItems = new HashMap<>();
	private final Map<Integer, Long> outputPrices = new HashMap<>();
	private final Map<Integer, Long> inputPrices = new HashMap<>();
	private Map<Integer, Integer> lastInventory;
	private WorldMapPoint mapPoint;
	private ScheduledFuture<?> grindTicker;
	private ScheduledFuture<?> levelsTimer;

	private final Map<Integer, Map<String, Object>> pending = new ConcurrentHashMap<>();
	private volatile String account;
	private volatile boolean sendScheduled;

	private GpMachinePanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> refresher;
	private GeSlotOverlay slotOverlay;
	private volatile Map<String, Object> selectedFlip;
	private volatile Map<String, Object> lastPacket;

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
		slotOverlay = new GeSlotOverlay(client, () -> lastPacket, config::showGeSlots);
		overlayManager.add(slotOverlay);
		grindOverlay = new GrindOverlay(() -> grind, config::showGrindBox);
		grindSceneOverlay = new GrindSceneOverlay(client, () -> grind, () -> stepObjects, config::showHighlights);
		overlayManager.add(grindOverlay);
		overlayManager.add(grindSceneOverlay);
		if (config.panel())
		{
			showPanel();
		}
	}

	@Override
	protected void shutDown()
	{
		pending.clear();
		if (grind != null)
		{
			stopGrind();
		}
		if (grindOverlay != null)
		{
			overlayManager.remove(grindOverlay);
			overlayManager.remove(grindSceneOverlay);
			grindOverlay = null;
			grindSceneOverlay = null;
		}
		if (levelsTimer != null)
		{
			levelsTimer.cancel(false);
			levelsTimer = null;
		}
		keyManager.unregisterKeyListener(priceHotkey);
		if (slotOverlay != null)
		{
			overlayManager.remove(slotOverlay);
			slotOverlay = null;
		}
		lastPacket = null;
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
		else if ("showArrow".equals(event.getKey()) || "showMapPoint".equals(event.getKey()))
		{
			GrindState s = grind;
			if (s != null)
			{
				clientThread.invoke(() -> applyStep(s));
			}
		}
	}

	private void showPanel()
	{
		if (panel != null)
		{
			return;
		}
		panel = new GpMachinePanel(new GpMachinePanel.Actions()
		{
			@Override
			public void select(Map<String, Object> flip)
			{
				selectedFlip = flip;
			}

			@Override
			public void search(String query)
			{
				searchItem(query);
			}

			@Override
			public void favorite(long itemId)
			{
				toggleFavorite(itemId);
			}

			@Override
			public void grindStart(Map<String, Object> method)
			{
				startGrind(method);
			}

			@Override
			public void grindStop()
			{
				stopGrind();
			}

			@Override
			public void grindStep(int delta)
			{
				clientThread.invoke(() -> moveStep(delta));
			}

			@Override
			public void report(String method, String text)
			{
				reportProblem(method, text);
			}
		});
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
					lastPacket = packet;
					notifyFromPacket(packet);
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
		return apiUrl(serverUrl, "panel");
	}

	static String apiUrl(String serverUrl, String naam)
	{
		String u = serverUrl.trim();
		if (u.endsWith("/ge"))
		{
			return u.substring(0, u.length() - 3) + "/" + naam;
		}
		return (u.endsWith("/") ? u : u + "/") + "api/v1/" + naam;
	}

	/**
	 * Item search from the panel: GET .../api/v1/item?q=name (the server answers with up to 8 items).
	 */
	private void searchItem(String q)
	{
		GpMachinePanel p = panel;
		if (p == null || config.key().trim().isEmpty())
		{
			return;
		}
		String url;
		try
		{
			url = apiUrl(config.serverUrl(), "item") + "?q=" + java.net.URLEncoder.encode(q, "UTF-8");
		}
		catch (java.io.UnsupportedEncodingException e)
		{
			return;
		}
		Request request = new Request.Builder().url(url).header("Authorization", "Bearer " + config.key().trim()).get().build();
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				SwingUtilities.invokeLater(() -> p.setStatus("No connection to the server.", true));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					if (!r.isSuccessful() || r.body() == null)
					{
						return;
					}
					Map<String, Object> uit = gson.fromJson(r.body().string(), new TypeToken<Map<String, Object>>()
					{
					}.getType());
					Object items = uit.get("items");
					@SuppressWarnings("unchecked")
					List<Map<String, Object>> lijst = items instanceof List ? (List<Map<String, Object>>) items : new ArrayList<>();
					SwingUtilities.invokeLater(() ->
					{
						p.setStatus(lijst.size() + " found", false);
						p.showSearch(lijst);
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
	 * The star in the panel: POST .../api/v1/favorite {"item_id": N}. Afterwards the panel is refreshed.
	 */
	private void toggleFavorite(long itemId)
	{
		if (config.key().trim().isEmpty())
		{
			return;
		}
		Request request = new Request.Builder()
			.url(apiUrl(config.serverUrl(), "favorite"))
			.header("Authorization", "Bearer " + config.key().trim())
			.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "{\"item_id\": " + itemId + "}"))
			.build();
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("GP MACHINE: favorite failed", e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				response.close();
				GpMachinePanel p = panel;
				if (p != null)
				{
					SwingUtilities.invokeLater(() -> p.setStatus("Favorite saved \u00b7 refreshing\u2026", false));
				}
				refreshPanel();
			}
		});
	}

	/**
	 * In-game notifications (choice of the owner: only these two): an offer that has waited 30 minutes beside the
	 * market, and a buy limit that is free again. Each one once; the set is cleared when it grows large.
	 */
	@SuppressWarnings("unchecked")
	private void notifyFromPacket(Map<String, Object> packet)
	{
		if (packet == null || !(packet.get("day") instanceof Map))
		{
			return;
		}
		Map<String, Object> day = (Map<String, Object>) packet.get("day");
		if (notified.size() > 500)
		{
			notified.clear();
		}
		if (config.notifyOffers() && day.get("offers") instanceof List)
		{
			for (Object o : (List<?>) day.get("offers"))
			{
				if (!(o instanceof Map))
				{
					continue;
				}
				Map<String, Object> g = (Map<String, Object>) o;
				boolean buy = Boolean.TRUE.equals(g.get("buy"));
				if (Boolean.TRUE.equals(g.get("ok")) || GpMachinePanel.toLong(g.get("minutes")) < OFFER_NOTIFY_MIN)
				{
					continue;
				}
				String key = "offer|" + g.get("slot") + "|" + g.get("name") + "|" + g.get("price") + "|" + buy;
				if (notified.add(key))
				{
					notifier.notify("GP MACHINE: your " + (buy ? "buy" : "sell") + " offer for " + g.get("name") + " is "
						+ (buy ? "below" : "above") + " the market (" + GpMachinePanel.toLong(g.get("market")) + " gp now).");
				}
			}
		}
		if (config.notifyLimits() && day.get("limits") instanceof List)
		{
			for (Object o : (List<?>) day.get("limits"))
			{
				if (!(o instanceof Map))
				{
					continue;
				}
				Map<String, Object> a = (Map<String, Object>) o;
				if (GpMachinePanel.toLong(a.get("left_min")) > 1)
				{
					continue;
				}
				String key = "limit|" + a.get("name") + "|" + a.get("free_at");
				if (notified.add(key))
				{
					notifier.notify("GP MACHINE: the buy limit for " + a.get("name") + " is free again.");
				}
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		// Grind Machine: your levels and finished quests, read once after login and then every 15 minutes
		if (levelsTimer != null)
		{
			levelsTimer.cancel(false);
		}
		levelsTimer = executor.scheduleAtFixedRate(() -> clientThread.invoke(this::sendLevels), 8, LEVELS_EVERY_S, TimeUnit.SECONDS);
	}

	/**
	 * Grind Machine: sends your skill levels, combat level, quest points and finished quests to the server, so the list
	 * shows what you can do right now. Read only; runs on the client thread.
	 */
	private void sendLevels()
	{
		if (config.key().trim().isEmpty() || client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
		{
			return;
		}
		Map<String, Object> skills = new HashMap<>();
		for (Skill s : Skill.values())
		{
			int level = client.getRealSkillLevel(s);
			if (level > 0)
			{
				skills.put(s.getName(), level);
			}
		}
		skills.put("Combat", client.getLocalPlayer().getCombatLevel());
		skills.put("Quest points", client.getVarpValue(VarPlayer.QUEST_POINTS));
		List<String> quests = new ArrayList<>();
		for (Quest q : Quest.values())
		{
			try
			{
				if (q.getState(client) == QuestState.FINISHED)
				{
					quests.add(q.getName());
				}
			}
			catch (RuntimeException e)
			{
				// a quest the client cannot read right now: skip it
			}
		}
		Map<String, Object> body = new HashMap<>();
		body.put("skills", skills);
		body.put("quests", quests);
		postJson("levels", body, null);
	}

	/**
	 * POST JSON to .../api/v1/<name> with the key; the callback gets the parsed answer (or null on failure).
	 */
	private void postJson(String name, Map<String, Object> body, java.util.function.Consumer<Map<String, Object>> then)
	{
		Request request;
		try
		{
			request = new Request.Builder()
				.url(apiUrl(config.serverUrl(), name))
				.header("Authorization", "Bearer " + config.key().trim())
				.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), gson.toJson(body)))
				.build();
		}
		catch (IllegalArgumentException e)
		{
			return;
		}
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("GP MACHINE: {} failed", name, e);
				if (then != null)
				{
					then.accept(null);
				}
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				Map<String, Object> uit = null;
				try (Response r = response)
				{
					if (r.isSuccessful() && r.body() != null)
					{
						uit = gson.fromJson(r.body().string(), new TypeToken<Map<String, Object>>()
						{
						}.getType());
					}
				}
				catch (Exception e)
				{
					log.debug("GP MACHINE: {} unreadable answer", name, e);
				}
				if (then != null)
				{
					then.accept(uit);
				}
			}
		});
	}

	/**
	 * Grind Machine, Start: the server gives the items to bring and what the method yields (with prices), and the place.
	 * The plugin then shows a checklist, puts an arrow and a map marker on the place, and counts what appears in your
	 * inventory. Nothing is done in the game.
	 */
	@SuppressWarnings("unchecked")
	private void startGrind(Map<String, Object> method)
	{
		if (method == null || grind != null)
		{
			return;
		}
		Map<String, Object> body = new HashMap<>();
		body.put("method_id", GpMachinePanel.toLong(method.get("id")));
		GpMachinePanel p = panel;
		if (p != null)
		{
			p.setStatus("Starting…", false);
		}
		postJson("grind/start", body, answer ->
		{
			if (answer == null || !Boolean.TRUE.equals(answer.get("ok")))
			{
				if (p != null)
				{
					SwingUtilities.invokeLater(() -> p.setStatus("Could not start: no answer from the server.", true));
				}
				return;
			}
			synchronized (grindGainedItems)
			{
				grindGainedItems.clear();
				grindUsedItems.clear();
				outputPrices.clear();
				inputPrices.clear();
				fillPrices(answer.get("outputs"), outputPrices);
				fillPrices(answer.get("inputs"), inputPrices);
			}
			List<Map<String, Object>> steps = answer.get("steps") instanceof List ? (List<Map<String, Object>>) answer.get("steps") : new ArrayList<>();
			GrindState s = new GrindState(answer, steps, System.currentTimeMillis());
			grind = s;
			clientThread.invoke(() ->
			{
				lastInventory = inventoryCounts();
				applyStep(s);
			});
			if (grindTicker != null)
			{
				grindTicker.cancel(false);
			}
			grindTicker = executor.scheduleAtFixedRate(() -> clientThread.invoke(this::tickGrind), 1, 5, TimeUnit.SECONDS);
		});
	}

	@SuppressWarnings("unchecked")
	private static void fillPrices(Object list, Map<Integer, Long> into)
	{
		if (!(list instanceof List))
		{
			return;
		}
		for (Object o : (List<?>) list)
		{
			if (o instanceof Map)
			{
				Map<String, Object> it = (Map<String, Object>) o;
				long id = GpMachinePanel.toLong(it.get("item_id"));
				if (id > 0)
				{
					into.put((int) id, it.get("price") == null ? (id == COINS ? 1L : 0L) : GpMachinePanel.toLong(it.get("price")));
				}
			}
		}
	}

	/**
	 * Grind Machine, Stop: the session (start, stop, gp, what was gained and used) goes to the server; from 2 members
	 * the dashboard shows the real gp per hour next to the wiki figure. Arrow and map marker are removed.
	 */
	private void stopGrind()
	{
		GrindState s = grind;
		if (s == null)
		{
			return;
		}
		Map<String, Object> m = s.method;
		grind = null;
		synchronized (stepObjects)
		{
			stepObjects.clear();
		}
		if (grindTicker != null)
		{
			grindTicker.cancel(false);
			grindTicker = null;
		}
		clientThread.invoke(() ->
		{
			client.clearHintArrow();
			if (mapPoint != null)
			{
				worldMapPointManager.remove(mapPoint);
				mapPoint = null;
			}
		});
		Map<String, Object> body = new HashMap<>();
		body.put("method_id", GpMachinePanel.toLong(m.get("id")));
		body.put("start", s.startMs / 1000);
		body.put("stop", System.currentTimeMillis() / 1000);
		synchronized (grindGainedItems)
		{
			body.put("gp", s.gp());
			body.put("gained", new HashMap<>(grindGainedItems));
			body.put("used", new HashMap<>(grindUsedItems));
		}
		if (account != null)
		{
			body.put("account", account);
		}
		GpMachinePanel p = panel;
		if (p != null)
		{
			SwingUtilities.invokeLater(() ->
			{
				p.setGrindSession(null);
				p.setStatus("Session saved · refreshing…", false);
			});
		}
		postJson("grind/session", body, answer -> refreshPanel());
	}

	/**
	 * Every 5 seconds while a session runs: the checklist (what you carry or wear) and the counter for the panel.
	 */
	@SuppressWarnings("unchecked")
	private void tickGrind()
	{
		GrindState s = grind;
		GpMachinePanel p = panel;
		if (s == null || p == null)
		{
			return;
		}
		Map<String, Object> m = s.method;
		Map<Integer, Integer> have = inventoryCounts();
		ItemContainer eq = client.getItemContainer(InventoryID.EQUIPMENT);
		if (eq != null)
		{
			for (Item it : eq.getItems())
			{
				if (it.getId() > 0)
				{
					have.merge(it.getId(), it.getQuantity(), Integer::sum);
				}
			}
		}
		List<Map<String, Object>> checklist = new ArrayList<>();
		if (m.get("inputs") instanceof List)
		{
			for (Object o : (List<?>) m.get("inputs"))
			{
				if (!(o instanceof Map))
				{
					continue;
				}
				Map<String, Object> it = (Map<String, Object>) o;
				int id = (int) GpMachinePanel.toLong(it.get("item_id"));
				Map<String, Object> c = new HashMap<>();
				c.put("name", it.get("name"));
				int count = id > 0 ? have.getOrDefault(id, 0) : 0;
				c.put("count", count);
				c.put("have", id > 0 ? count > 0 : Boolean.TRUE); // an item we cannot identify is not marked missing
				checklist.add(c);
			}
		}
		s.checklist = checklist;
		// the arrow: distance to the target of the current step, and steps with a place tick off by themselves
		Player me = client.getLocalPlayer();
		WorldPoint target = s.target;
		if (me != null && target != null)
		{
			WorldPoint here = me.getWorldLocation();
			s.distance = here.getPlane() == target.getPlane() ? here.distanceTo(target) : -1;
			Map<String, Object> step = s.step();
			if (step != null && "walk".equals(step.get("kind")) && Boolean.TRUE.equals(step.get("auto"))
				&& s.distance >= 0 && s.distance <= GpMachinePanel.toLong(step.get("radius")) && s.stepIndex < s.steps.size() - 1)
			{
				moveStep(1);
			}
		}
		else
		{
			s.distance = -1;
		}
		SwingUtilities.invokeLater(() -> p.setGrindSession(s));
	}

	/**
	 * Previous or next step (the buttons in the panel), or the next step when the plugin ticks one off itself.
	 * Runs on the client thread.
	 */
	private void moveStep(int delta)
	{
		GrindState s = grind;
		if (s == null || s.steps.isEmpty())
		{
			return;
		}
		int i = Math.max(0, Math.min(s.steps.size() - 1, s.stepIndex + delta));
		if (i == s.stepIndex)
		{
			return;
		}
		s.stepIndex = i;
		applyStep(s);
		GpMachinePanel p = panel;
		if (p != null)
		{
			SwingUtilities.invokeLater(() -> p.setGrindSession(s));
		}
	}

	/**
	 * The current step in the game: the arrow and the gold tile on the step's place (or the method's place), and the
	 * objects of the step (a bank booth, for example) outlined in green. Runs on the client thread.
	 */
	private void applyStep(GrindState s)
	{
		Map<String, Object> step = s.step();
		long x = step != null && step.get("x") != null ? GpMachinePanel.toLong(step.get("x")) : GpMachinePanel.toLong(s.method.get("x"));
		long y = step != null && step.get("y") != null ? GpMachinePanel.toLong(step.get("y")) : GpMachinePanel.toLong(s.method.get("y"));
		int plane = step != null && step.get("plane") != null ? (int) GpMachinePanel.toLong(step.get("plane")) : 0;
		if (x > 0 && y > 0)
		{
			s.target = new WorldPoint((int) x, (int) y, plane);
			if (config.showArrow())
			{
				client.setHintArrow(s.target);
			}
			else
			{
				client.clearHintArrow();
			}
		}
		else
		{
			s.target = null;
			client.clearHintArrow();
		}
		syncMapPoint(s);
		synchronized (stepObjects)
		{
			stepObjects.clear();
			List<String> names = s.stepObjects();
			if (!names.isEmpty())
			{
				Scene scene = client.getScene();
				Tile[][][] tiles = scene == null ? null : scene.getTiles();
				if (tiles != null)
				{
					for (Tile[] row : tiles[client.getPlane()])
					{
						for (Tile tile : row)
						{
							if (tile == null)
							{
								continue;
							}
							for (GameObject o : tile.getGameObjects())
							{
								if (o != null && matchesStep(o, names))
								{
									stepObjects.add(o);
								}
							}
						}
					}
				}
			}
		}
	}

	/**
	 * The world map point at the place of the method, following the switch in the settings. Client thread.
	 */
	private void syncMapPoint(GrindState s)
	{
		long x = GpMachinePanel.toLong(s.method.get("x"));
		long y = GpMachinePanel.toLong(s.method.get("y"));
		boolean want = config.showMapPoint() && x > 0 && y > 0;
		if (want && mapPoint == null)
		{
			mapPoint = WorldMapPoint.builder().worldPoint(new WorldPoint((int) x, (int) y, 0)).image(icon())
				.tooltip("GP MACHINE: " + s.name()).build();
			worldMapPointManager.add(mapPoint);
		}
		else if (!want && mapPoint != null)
		{
			worldMapPointManager.remove(mapPoint);
			mapPoint = null;
		}
	}

	private boolean matchesStep(GameObject o, List<String> names)
	{
		ObjectComposition def = client.getObjectDefinition(o.getId());
		if (def == null)
		{
			return false;
		}
		if (def.getImpostorIds() != null)
		{
			try
			{
				def = def.getImpostor();
			}
			catch (RuntimeException e)
			{
				return false;
			}
		}
		String name = def == null || def.getName() == null ? "" : def.getName().toLowerCase();
		return !name.isEmpty() && !"null".equals(name) && names.contains(name);
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		GrindState s = grind;
		if (s == null)
		{
			return;
		}
		List<String> names = s.stepObjects();
		if (!names.isEmpty() && matchesStep(event.getGameObject(), names))
		{
			synchronized (stepObjects)
			{
				stepObjects.add(event.getGameObject());
			}
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		synchronized (stepObjects)
		{
			stepObjects.remove(event.getGameObject());
		}
	}

	/**
	 * The Report button in the Grind tab: the text goes to the owner's Support page, with the method's name.
	 */
	private void reportProblem(String method, String text)
	{
		Map<String, Object> body = new HashMap<>();
		body.put("method", method);
		body.put("text", text);
		GpMachinePanel p = panel;
		postJson("grind/report", body, answer ->
		{
			if (p != null)
			{
				boolean ok = answer != null && Boolean.TRUE.equals(answer.get("ok"));
				SwingUtilities.invokeLater(() -> p.setStatus(ok ? "Thanks, the owner got your report." : "Report failed, try the flag on the dashboard.", !ok));
			}
		});
	}

	private Map<Integer, Integer> inventoryCounts()
	{
		Map<Integer, Integer> counts = new HashMap<>();
		ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
		if (inv != null)
		{
			for (Item it : inv.getItems())
			{
				if (it.getId() > 0)
				{
					counts.merge(unnoted(it.getId()), it.getQuantity(), Integer::sum);
				}
			}
		}
		return counts;
	}

	/**
	 * A noted item (a bank note of a drop) counts as the item itself, so the counter and the checklist see it.
	 */
	private int unnoted(int id)
	{
		net.runelite.api.ItemComposition def = client.getItemDefinition(id);
		return def != null && def.getNote() != -1 && def.getLinkedNoteId() > 0 ? def.getLinkedNoteId() : id;
	}

	/**
	 * The counter: what the method yields and appears in your inventory counts as gained (at GP MACHINE prices), what
	 * the method uses and disappears counts as used. Banking a drop or withdrawing supplies is ignored by looking only
	 * at increases of outputs and decreases of inputs.
	 */
	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		GrindState s = grind;
		if (s == null || event.getContainerId() != InventoryID.INVENTORY.getId())
		{
			return;
		}
		Map<Integer, Integer> now = inventoryCounts();
		Map<Integer, Integer> before = lastInventory == null ? new HashMap<>() : lastInventory;
		synchronized (grindGainedItems)
		{
			for (Map.Entry<Integer, Long> e : outputPrices.entrySet())
			{
				int delta = now.getOrDefault(e.getKey(), 0) - before.getOrDefault(e.getKey(), 0);
				if (delta > 0)
				{
					s.gained += delta * e.getValue();
					grindGainedItems.merge(e.getKey(), delta, Integer::sum);
				}
			}
			for (Map.Entry<Integer, Long> e : inputPrices.entrySet())
			{
				int delta = before.getOrDefault(e.getKey(), 0) - now.getOrDefault(e.getKey(), 0);
				if (delta > 0)
				{
					s.used += delta * e.getValue();
					grindUsedItems.merge(e.getKey(), delta, Integer::sum);
				}
			}
		}
		lastInventory = now;
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
