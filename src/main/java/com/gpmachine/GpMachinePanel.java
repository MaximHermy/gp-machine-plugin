package com.gpmachine;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/**
 * The GP MACHINE side panel: the best flips for your capital (FAST, SLOW, SOLID), your day (profit, stock, open offers,
 * buy limits), your favorites, item search, and Grind Machine (money making methods you can do with your levels, with
 * steps, a checklist, an arrow to the place and a counter). Everything is computed by the GP MACHINE server; this
 * panel only shows it.
 */
class GpMachinePanel extends PluginPanel
{
	private static final Color GOLD = new Color(0xff, 0xcf, 0x3f);
	private static final Color ORANGE = new Color(0xff, 0x98, 0x1f);
	private static final Color GREEN = new Color(0x5e, 0xe0, 0x5e);
	private static final Color RED = new Color(0xff, 0x6a, 0x52);
	private static final Color SOFT = new Color(0xbc, 0xab, 0x88);
	private static final Color CARD = new Color(0x3a, 0x31, 0x25);
	private static final Color CARD_SELECTED = new Color(0x4a, 0x3d, 0x2b);
	private static final NumberFormat N = NumberFormat.getIntegerInstance(Locale.UK);
	private static final String[][] KINDS = {{"all", "All"}, {"Skilling", "Skilling"}, {"Combat", "Combat"},
		{"Processing", "Processing"}, {"Collecting", "Collecting"}, {"f2p", "F2P"}};

	/**
	 * What the panel asks the plugin to do. The plugin only reads from the game and talks to the GP MACHINE server.
	 */
	interface Actions
	{
		void select(Map<String, Object> flip);

		void search(String query);

		void favorite(long itemId);

		void grindStart(Map<String, Object> method);

		void grindStop();

		void grindStep(int delta);

		void report(String method, String text);
	}

	private final JToggleButton fastButton = toggle("FAST");
	private final JToggleButton slowButton = toggle("SLOW");
	private final JToggleButton solidButton = toggle("✅ SOLID");
	private final JToggleButton flipsTab = toggle("Flips");
	private final JToggleButton dayTab = toggle("Day");
	private final JToggleButton favTab = toggle("★");
	private final JToggleButton grindTab = toggle("Grind");
	private final JPanel filters = new JPanel(new GridLayout(1, 3, 4, 0));
	private final JTextField search = new JTextField();
	private final JLabel status = new JLabel("Connecting…");
	private final JPanel list = new JPanel();
	private final Actions actions;

	private Map<String, Object> data;
	private Map<String, Object> selected;
	private Map<String, Object> selectedGrind;
	private GrindState grindSession;
	private String grindKind = "all";
	private List<Map<String, Object>> searchResults;
	private String searchText = "";

	GpMachinePanel(Actions actions)
	{
		super(false);
		this.actions = actions;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("GP MACHINE");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(GOLD);
		top.add(title);
		status.setFont(FontManager.getRunescapeSmallFont());
		status.setForeground(SOFT);
		top.add(status);
		top.add(Box.createVerticalStrut(6));

		search.setFont(FontManager.getRunescapeSmallFont());
		search.setToolTipText("Type an item name and press Enter");
		search.addActionListener(e -> doSearch());
		JPanel searchRow = new JPanel(new BorderLayout(4, 0));
		searchRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		searchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		searchRow.add(search, BorderLayout.CENTER);
		JButton go = new JButton("Search");
		go.setFont(FontManager.getRunescapeSmallFont());
		go.setFocusPainted(false);
		go.setMargin(new Insets(0, 6, 0, 6));
		go.addActionListener(e -> doSearch());
		searchRow.add(go, BorderLayout.EAST);
		top.add(searchRow);
		top.add(Box.createVerticalStrut(6));

		JPanel tabs = new JPanel(new GridLayout(1, 4, 4, 0));
		tabs.setBackground(ColorScheme.DARK_GRAY_COLOR);
		flipsTab.setSelected(true);
		flipsTab.addActionListener(e -> selectTab(flipsTab));
		dayTab.addActionListener(e -> selectTab(dayTab));
		favTab.addActionListener(e -> selectTab(favTab));
		grindTab.addActionListener(e -> selectTab(grindTab));
		dayTab.setToolTipText("Your day: profit, stock, open offers, buy limits");
		favTab.setToolTipText("Your favorites");
		grindTab.setToolTipText("Grind Machine: money making methods for your levels");
		tabs.add(flipsTab);
		tabs.add(dayTab);
		tabs.add(favTab);
		tabs.add(grindTab);
		top.add(tabs);
		top.add(Box.createVerticalStrut(6));

		filters.setBackground(ColorScheme.DARK_GRAY_COLOR);
		fastButton.setSelected(true);
		slowButton.setSelected(true);
		solidButton.setSelected(true);
		fastButton.addActionListener(e -> render());
		slowButton.addActionListener(e -> render());
		solidButton.addActionListener(e -> render());
		filters.add(fastButton);
		filters.add(slowButton);
		filters.add(solidButton);
		top.add(filters);
		top.add(Box.createVerticalStrut(8));
		add(top, BorderLayout.NORTH);

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(list, BorderLayout.CENTER);
	}

	private static JToggleButton toggle(String text)
	{
		JToggleButton b = new JToggleButton(text);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setFocusPainted(false);
		return b;
	}

	private static JButton button(String text, Color color, String tip)
	{
		JButton b = new JButton(text);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setForeground(color);
		b.setFocusPainted(false);
		b.setMargin(new Insets(1, 5, 1, 5));
		if (tip != null)
		{
			b.setToolTipText(tip);
		}
		return b;
	}

	private void selectTab(JToggleButton tab)
	{
		flipsTab.setSelected(tab == flipsTab);
		dayTab.setSelected(tab == dayTab);
		favTab.setSelected(tab == favTab);
		grindTab.setSelected(tab == grindTab);
		filters.setVisible(tab == flipsTab);
		searchResults = null;
		render();
	}

	private void doSearch()
	{
		String q = search.getText().trim();
		if (q.length() < 2)
		{
			return;
		}
		searchText = q;
		setStatus("Searching “" + q + "”…", false);
		actions.search(q);
	}

	void setStatus(String text, boolean error)
	{
		status.setText(text);
		status.setForeground(error ? RED : SOFT);
	}

	void update(Map<String, Object> packet)
	{
		data = packet;
		if (searchResults != null)
		{
			// keep the search on screen, but take the fresh stars from the packet
			List<Map<String, Object>> favs = favorites();
			for (Map<String, Object> it : searchResults)
			{
				boolean fav = false;
				for (Map<String, Object> f : favs)
				{
					if (toLong(f.get("item_id")) == toLong(it.get("item_id")))
					{
						fav = true;
					}
				}
				it.put("favorite", fav);
			}
		}
		render();
	}

	void showSearch(List<Map<String, Object>> items)
	{
		searchResults = items;
		render();
	}

	/**
	 * The running grind session from the plugin (null = none).
	 */
	void setGrindSession(GrindState session)
	{
		grindSession = session;
		if (grindTab.isSelected())
		{
			render();
		}
	}

	Map<String, Object> getSelected()
	{
		return selected;
	}

	Map<String, Object> getData()
	{
		return data;
	}

	@SuppressWarnings("unchecked")
	List<Map<String, Object>> flips()
	{
		if (data == null || !(data.get("flips") instanceof List))
		{
			return new ArrayList<>();
		}
		return (List<Map<String, Object>>) data.get("flips");
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> favorites()
	{
		if (data == null || !(data.get("favorites") instanceof List))
		{
			return new ArrayList<>();
		}
		return (List<Map<String, Object>>) data.get("favorites");
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> grind()
	{
		if (data == null || !(data.get("grind") instanceof Map))
		{
			return null;
		}
		return (Map<String, Object>) data.get("grind");
	}

	private void render()
	{
		list.removeAll();
		if (searchResults != null)
		{
			renderItems(searchResults, "Results for “" + searchText + "”", "No item found. Try a shorter name.");
		}
		else if (data == null)
		{
			list.add(note("Waiting for the server…"));
		}
		else if (flipsTab.isSelected())
		{
			renderFlips();
		}
		else if (favTab.isSelected())
		{
			renderItems(favorites(), favorites().size() + " favorites", "No favorites yet. Search an item and press ☆.");
		}
		else if (grindTab.isSelected())
		{
			renderGrind();
		}
		else
		{
			renderDay();
		}
		list.add(Box.createVerticalGlue());
		list.revalidate();
		list.repaint();
	}

	private void renderFlips()
	{
		boolean onlySolid = solidButton.isSelected();
		int shown = 0;
		for (Map<String, Object> f : flips())
		{
			boolean fast = Boolean.TRUE.equals(f.get("fast"));
			boolean solid = Boolean.TRUE.equals(f.get("solid"));
			if ((fast && !fastButton.isSelected()) || (!fast && !slowButton.isSelected()) || (onlySolid && !solid))
			{
				continue;
			}
			list.add(flipCard(f, fast, solid));
			list.add(Box.createVerticalStrut(4));
			shown++;
		}
		Object capital = data.get("capital");
		list.add(note(shown + " flips for " + N.format(toLong(capital)) + " gp" + (onlySolid ? " · SOLID only" : "")), 0);
		if (shown == 0)
		{
			list.add(note("Nothing good enough right now. Try SOLID off, or raise your capital on the dashboard."));
		}
	}

	private void renderItems(List<Map<String, Object>> items, String head, String empty)
	{
		list.add(note(head));
		if (items.isEmpty())
		{
			list.add(note(empty));
			return;
		}
		for (Map<String, Object> it : items)
		{
			list.add(itemCard(it));
			list.add(Box.createVerticalStrut(4));
		}
	}

	private JPanel card(boolean solid, boolean isSelected, Runnable onClick)
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(isSelected ? CARD_SELECTED : CARD);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(isSelected ? GOLD : (solid ? GREEN.darker() : CARD.brighter())),
			BorderFactory.createEmptyBorder(5, 7, 5, 7)));
		card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
		card.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				onClick.run();
				render();
			}
		});
		return card;
	}

	private JPanel itemHead(Map<String, Object> f, Boolean fast, boolean solid, boolean withStar)
	{
		JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		head.setOpaque(false);
		JLabel name = new JLabel(String.valueOf(f.get("name")));
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(Color.WHITE);
		head.add(name);
		if (fast != null)
		{
			head.add(tag(fast ? "FAST" : "SLOW", fast ? GREEN : ORANGE));
		}
		if (solid)
		{
			head.add(tag("✅ SOLID", GREEN));
		}
		if (withStar)
		{
			boolean fav = Boolean.TRUE.equals(f.get("favorite"));
			JButton star = button(fav ? "★" : "☆", GOLD, fav ? "Remove from favorites" : "Add to favorites (you get alerts about it)");
			star.setFont(FontManager.getRunescapeBoldFont());
			star.setMargin(new Insets(0, 4, 0, 4));
			star.addActionListener(e -> actions.favorite(toLong(f.get("item_id"))));
			head.add(star);
		}
		return head;
	}

	private static String membersPaid(Object mp)
	{
		if (!(mp instanceof Map))
		{
			return null;
		}
		Map<?, ?> m = (Map<?, ?>) mp;
		return "Members paid: " + (m.get("low") == null ? "" : "buy " + N.format(toLong(m.get("low"))))
			+ (m.get("low") != null && m.get("high") != null ? " · " : "")
			+ (m.get("high") == null ? "" : "sell " + N.format(toLong(m.get("high"))))
			+ " (" + toLong(m.get("members")) + " members, " + toLong(m.get("minutes")) + " min ago)";
	}

	private JPanel flipCard(Map<String, Object> f, boolean fast, boolean solid)
	{
		boolean isSelected = selected != null && toLong(selected.get("item_id")) == toLong(f.get("item_id"));
		JPanel card = card(solid, isSelected, () ->
		{
			selected = f;
			actions.select(f);
		});
		card.add(itemHead(f, fast, solid, true));
		card.add(line("+" + N.format(toLong(f.get("per_item"))) + " gp per item", GOLD, true));
		card.add(line("Buy " + N.format(toLong(f.get("buy"))) + " · Sell " + N.format(toLong(f.get("sell"))), Color.WHITE, false));
		card.add(line("Up to " + N.format(toLong(f.get("qty"))) + " per 4h = +" + N.format(toLong(f.get("total"))) + " gp · "
			+ toLong(f.get("age_min")) + " min old", SOFT, false));
		String mp = membersPaid(f.get("members_price"));
		if (mp != null)
		{
			card.add(line(mp, GREEN, false));
		}
		return card;
	}

	/**
	 * An item from search or favorites: the prices of now, the flip round if there is one, what members paid, and a star.
	 */
	private JPanel itemCard(Map<String, Object> it)
	{
		boolean solid = Boolean.TRUE.equals(it.get("solid"));
		Object fastObj = it.get("fast");
		boolean isSelected = selected != null && toLong(selected.get("item_id")) == toLong(it.get("item_id"));
		JPanel card = card(solid, isSelected, () ->
		{
			selected = it;
			actions.select(it);
		});
		card.add(itemHead(it, fastObj instanceof Boolean ? (Boolean) fastObj : null, solid, true));
		if (it.get("high") != null && it.get("low") != null)
		{
			card.add(line("Now: high " + N.format(toLong(it.get("high"))) + " · low " + N.format(toLong(it.get("low")))
				+ (it.get("age_min") == null ? "" : " · " + toLong(it.get("age_min")) + " min old"), Color.WHITE, false));
		}
		else
		{
			card.add(line("No price known right now.", SOFT, false));
		}
		if (it.get("per_item") != null)
		{
			long per = toLong(it.get("per_item"));
			card.add(line((per >= 0 ? "+" : "") + N.format(per) + " gp per item · buy " + N.format(toLong(it.get("buy")))
				+ " · sell " + N.format(toLong(it.get("sell"))), per > 0 ? GOLD : SOFT, per > 0));
		}
		else if (Boolean.TRUE.equals(it.get("members_item")))
		{
			card.add(line("Members item: the flip round is for members.", SOFT, false));
		}
		String mp = membersPaid(it.get("members_price"));
		if (mp != null)
		{
			card.add(line(mp, GREEN, false));
		}
		return card;
	}

	/**
	 * Grind Machine: the methods you can do with your levels (from the server), filter chips, Start on the chosen one,
	 * and the running session (steps, checklist, arrow, counter) from the plugin.
	 */
	@SuppressWarnings("unchecked")
	private void renderGrind()
	{
		if (grindSession != null)
		{
			renderSession();
			return;
		}
		Map<String, Object> g = grind();
		if (g == null)
		{
			list.add(note("Grind Machine is not on this server yet."));
			return;
		}
		boolean known = Boolean.TRUE.equals(g.get("levels_known"));
		boolean canStart = !Boolean.FALSE.equals(g.get("can_start"));
		List<Map<String, Object>> all = g.get("methods") instanceof List ? (List<Map<String, Object>>) g.get("methods") : new ArrayList<>();
		JPanel chips = new JPanel(new GridLayout(2, 3, 3, 3));
		chips.setBackground(ColorScheme.DARK_GRAY_COLOR);
		chips.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
		for (String[] k : KINDS)
		{
			JToggleButton b = toggle(k[1]);
			b.setSelected(k[0].equals(grindKind));
			b.addActionListener(e ->
			{
				grindKind = k[0];
				render();
			});
			chips.add(b);
		}
		list.add(chips);
		list.add(Box.createVerticalStrut(6));
		List<Map<String, Object>> methods = new ArrayList<>();
		for (Map<String, Object> m : all)
		{
			if ("all".equals(grindKind) || ("f2p".equals(grindKind) ? !Boolean.TRUE.equals(m.get("members")) : grindKind.equals(m.get("category"))))
			{
				methods.add(m);
			}
		}
		list.add(note(known ? methods.size() + " methods for your levels, best first"
			: "Your levels are not known yet (log in once, they are read then). Best methods overall:"));
		if (!canStart)
		{
			list.add(note("Members only: Start with the arrow, checklist, steps and counter."));
		}
		if (methods.isEmpty())
		{
			list.add(note("Nothing here. Try another filter; the list comes from the wiki once a day."));
		}
		for (Map<String, Object> m : methods)
		{
			boolean isSelected = selectedGrind != null && toLong(selectedGrind.get("id")) == toLong(m.get("id"));
			JPanel card = card(false, isSelected, () -> selectedGrind = m);
			JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
			head.setOpaque(false);
			JLabel name = new JLabel(String.valueOf(m.get("name")));
			name.setFont(FontManager.getRunescapeBoldFont());
			name.setForeground(Color.WHITE);
			head.add(name);
			if (Boolean.TRUE.equals(m.get("ok")))
			{
				head.add(tag("✔", GREEN));
			}
			card.add(head);
			card.add(line(N.format(toLong(m.get("gp_hour"))) + " gp/h"
				+ (m.get("members_gp_hour") == null ? "" : " · members made " + N.format(toLong(m.get("members_gp_hour")))), GOLD, true));
			String where = m.get("location") == null ? "" : String.valueOf(m.get("location"));
			Object intensity = m.get("intensity");
			String intens = intensity == null ? "" : (toLong(intensity) == 1 ? "relaxed" : toLong(intensity) == 2 ? "moderate" : "intense");
			card.add(line((where.isEmpty() ? "" : where + " · ") + intens + (Boolean.TRUE.equals(m.get("members")) ? "" : " · F2P"), SOFT, false));
			String req = m.get("requirements") == null ? "" : String.valueOf(m.get("requirements"));
			if (!req.isEmpty())
			{
				card.add(line(req, SOFT, false));
			}
			if (m.get("missing") instanceof List && !((List<?>) m.get("missing")).isEmpty())
			{
				card.add(line("Missing: " + String.join(", ", toStrings((List<?>) m.get("missing"))), RED, false));
			}
			if (isSelected)
			{
				JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
				row.setOpaque(false);
				if (canStart)
				{
					JButton start = button("▶ Start", GREEN, "Steps, a checklist of what to bring, an arrow to the place and a counter of what you make");
					start.setFont(FontManager.getRunescapeBoldFont());
					start.addActionListener(e -> actions.grindStart(m));
					row.add(start);
				}
				JButton wiki = button("Wiki", SOFT, "The steps on the OSRS Wiki, in your browser");
				wiki.addActionListener(e -> LinkBrowser.browse(String.valueOf(m.get("url"))));
				row.add(wiki);
				row.add(reportButton(String.valueOf(m.get("name"))));
				card.add(row);
			}
			list.add(card);
			list.add(Box.createVerticalStrut(4));
		}
	}

	private JButton reportButton(String method)
	{
		JButton report = button("Report", ORANGE, "Something wrong with this method? Tell the owner.");
		report.addActionListener(e ->
		{
			String text = JOptionPane.showInputDialog(this, "What is wrong with “" + method + "”?", "Report a problem", JOptionPane.PLAIN_MESSAGE);
			if (text != null && !text.trim().isEmpty())
			{
				actions.report(method, text.trim());
			}
		});
		return report;
	}

	private void renderSession()
	{
		GrindState s = grindSession;
		list.add(line("▶ " + s.name(), GOLD, true));
		long gp = s.gp();
		list.add(line("Session: " + (s.elapsedS() / 60) + " min · " + (gp >= 0 ? "+" : "") + N.format(gp) + " gp · "
			+ N.format(s.gpHour()) + " gp/h", gp >= 0 ? GREEN : RED, true));
		if (!s.steps.isEmpty())
		{
			list.add(Box.createVerticalStrut(6));
			list.add(line("Step " + (s.stepIndex + 1) + " of " + s.steps.size(), GOLD, true));
			list.add(line(s.stepText(), Color.WHITE, false));
			if (s.target != null && s.distance >= 0)
			{
				list.add(line("Arrow on the map → " + s.distance + " tiles away", SOFT, false));
			}
			JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
			row.setOpaque(false);
			JButton prev = button("◀ Previous", SOFT, null);
			prev.setEnabled(s.stepIndex > 0);
			prev.addActionListener(e -> actions.grindStep(-1));
			JButton next = button("Next ▶", GOLD, "Steps with a place or an item tick off by themselves; the rest you tick here");
			next.setEnabled(s.stepIndex < s.steps.size() - 1);
			next.addActionListener(e -> actions.grindStep(1));
			row.add(prev);
			row.add(next);
			list.add(row);
		}
		list.add(Box.createVerticalStrut(6));
		list.add(line("Bring", GOLD, true));
		List<Map<String, Object>> check = s.checklist;
		if (check != null && !check.isEmpty())
		{
			for (Map<String, Object> c : check)
			{
				boolean have = Boolean.TRUE.equals(c.get("have"));
				list.add(line((have ? "✔ " : "✘ ") + c.get("name") + (toLong(c.get("count")) > 0 ? " ×" + N.format(toLong(c.get("count"))) : ""),
					have ? GREEN : RED, false));
			}
		}
		else
		{
			list.add(line("The wiki lists nothing to bring.", SOFT, false));
		}
		list.add(Box.createVerticalStrut(8));
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		row.setOpaque(false);
		JButton stop = button("■ Stop and save", RED, "The session goes to your account; from 2 members the dashboard shows the real gp per hour");
		stop.setFont(FontManager.getRunescapeBoldFont());
		stop.addActionListener(e -> actions.grindStop());
		row.add(stop);
		row.add(reportButton(s.name()));
		list.add(row);
		list.add(line("The counter adds what appears in your inventory (at GP MACHINE prices) and subtracts what you use up.", SOFT, false));
	}

	private static List<String> toStrings(List<?> l)
	{
		List<String> uit = new ArrayList<>();
		for (Object o : l)
		{
			uit.add(String.valueOf(o));
		}
		return uit;
	}

	private void renderDay()
	{
		Object dayObj = data.get("day");
		if (!(dayObj instanceof Map))
		{
			list.add(note("No data yet."));
			return;
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> day = (Map<String, Object>) dayObj;
		long profit = toLong(day.get("profit_today"));
		list.add(line("Profit today: " + (profit >= 0 ? "+" : "") + N.format(profit) + " gp · "
			+ toLong(day.get("sales_today")) + " sales", profit >= 0 ? GREEN : RED, true));
		long value = toLong(day.get("stock_value"));
		long paid = toLong(day.get("stock_paid"));
		list.add(line("Stock: " + toLong(day.get("stock_items")) + " items, worth " + N.format(value) + " gp ("
			+ (value - paid >= 0 ? "+" : "") + N.format(value - paid) + ")", SOFT, false));
		list.add(Box.createVerticalStrut(8));
		list.add(line("Open offers", GOLD, true));
		Object offers = day.get("offers");
		if (offers instanceof List && !((List<?>) offers).isEmpty())
		{
			for (Object o : (List<?>) offers)
			{
				if (o instanceof Map)
				{
					@SuppressWarnings("unchecked")
					Map<String, Object> g = (Map<String, Object>) o;
					boolean ok = Boolean.TRUE.equals(g.get("ok"));
					boolean buy = Boolean.TRUE.equals(g.get("buy"));
					String text = (buy ? "Buy " : "Sell ") + g.get("name") + ": " + N.format(toLong(g.get("filled"))) + " / "
						+ N.format(toLong(g.get("total"))) + " at " + N.format(toLong(g.get("price"))) + " gp · "
						+ toLong(g.get("minutes")) + " min";
					String why = ok ? "priced well" : (buy ? "too low: others pay " : "too high: others get ")
						+ N.format(toLong(g.get("market"))) + " gp";
					list.add(line(text, ok ? GREEN : RED, false));
					list.add(line("   " + why, SOFT, false));
				}
			}
		}
		else
		{
			list.add(line("None open.", SOFT, false));
		}
		list.add(Box.createVerticalStrut(8));
		list.add(line("Buy limits", GOLD, true));
		Object limits = day.get("limits");
		if (limits instanceof List && !((List<?>) limits).isEmpty())
		{
			for (Object o : (List<?>) limits)
			{
				if (o instanceof Map)
				{
					@SuppressWarnings("unchecked")
					Map<String, Object> a = (Map<String, Object>) o;
					long left = toLong(a.get("left_min"));
					list.add(line(a.get("name") + ": bought " + N.format(toLong(a.get("bought")))
						+ (a.get("limit") == null ? "" : " / " + N.format(toLong(a.get("limit"))))
						+ " · again in " + (left / 60) + "h " + String.format("%02d", left % 60) + "m", SOFT, false));
				}
			}
		}
		else
		{
			list.add(line("Nothing bought in the last 4 hours.", SOFT, false));
		}
	}

	private static JLabel tag(String text, Color color)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(color);
		return l;
	}

	private static JLabel line(String text, Color color, boolean bold)
	{
		JLabel l = new JLabel("<html><body style='width:170px'>" + escape(text) + "</body></html>");
		l.setFont(bold ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
		l.setForeground(color);
		l.setHorizontalAlignment(SwingConstants.LEFT);
		return l;
	}

	private static JLabel note(String text)
	{
		JLabel l = line(text, SOFT, false);
		l.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
		return l;
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	static long toLong(Object o)
	{
		if (o instanceof Number)
		{
			return ((Number) o).longValue();
		}
		try
		{
			return o == null ? 0 : Long.parseLong(String.valueOf(o));
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}
}
