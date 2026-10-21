package com.gpmachine;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The GP MACHINE side panel: the best flips for your capital (FAST, SLOW, SOLID) and your day (profit, stock, open
 * offers, buy limits). Everything is computed by the GP MACHINE server; this panel only shows it.
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

	private final JToggleButton fastButton = toggle("FAST");
	private final JToggleButton slowButton = toggle("SLOW");
	private final JToggleButton solidButton = toggle("✅ SOLID");
	private final JToggleButton flipsTab = toggle("Flips");
	private final JToggleButton dayTab = toggle("Your day");
	private final JLabel status = new JLabel("Connecting…");
	private final JPanel list = new JPanel();
	private final Consumer<Map<String, Object>> onSelect;

	private Map<String, Object> data;
	private Map<String, Object> selected;

	GpMachinePanel(Consumer<Map<String, Object>> onSelect)
	{
		super(false);
		this.onSelect = onSelect;
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

		JPanel tabs = new JPanel(new GridLayout(1, 2, 4, 0));
		tabs.setBackground(ColorScheme.DARK_GRAY_COLOR);
		flipsTab.setSelected(true);
		flipsTab.addActionListener(e -> selectTab(true));
		dayTab.addActionListener(e -> selectTab(false));
		tabs.add(flipsTab);
		tabs.add(dayTab);
		top.add(tabs);
		top.add(Box.createVerticalStrut(6));

		JPanel filters = new JPanel(new GridLayout(1, 3, 4, 0));
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

	private void selectTab(boolean flips)
	{
		flipsTab.setSelected(flips);
		dayTab.setSelected(!flips);
		render();
	}

	void setStatus(String text, boolean error)
	{
		status.setText(text);
		status.setForeground(error ? RED : SOFT);
	}

	void update(Map<String, Object> packet)
	{
		data = packet;
		render();
	}

	Map<String, Object> getSelected()
	{
		return selected;
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

	private void render()
	{
		list.removeAll();
		if (data == null)
		{
			list.add(note("Waiting for the server…"));
		}
		else if (flipsTab.isSelected())
		{
			renderFlips();
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

	private JPanel flipCard(Map<String, Object> f, boolean fast, boolean solid)
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		boolean isSelected = selected != null && selected.get("item_id").equals(f.get("item_id"));
		card.setBackground(isSelected ? CARD_SELECTED : CARD);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(isSelected ? GOLD : (solid ? GREEN.darker() : CARD.brighter())),
			BorderFactory.createEmptyBorder(5, 7, 5, 7)));
		card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));

		JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		head.setOpaque(false);
		JLabel name = new JLabel(String.valueOf(f.get("name")));
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(Color.WHITE);
		head.add(name);
		head.add(tag(fast ? "FAST" : "SLOW", fast ? GREEN : ORANGE));
		if (solid)
		{
			head.add(tag("✅ SOLID", GREEN));
		}
		card.add(head);
		card.add(line("+" + N.format(toLong(f.get("per_item"))) + " gp per item", GOLD, true));
		card.add(line("Buy " + N.format(toLong(f.get("buy"))) + " · Sell " + N.format(toLong(f.get("sell"))), Color.WHITE, false));
		card.add(line("Up to " + N.format(toLong(f.get("qty"))) + " per 4h = +" + N.format(toLong(f.get("total"))) + " gp · "
			+ toLong(f.get("age_min")) + " min old", SOFT, false));
		card.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				selected = f;
				onSelect.accept(f);
				render();
			}
		});
		return card;
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
						+ (ok ? "" : N.format(toLong(g.get("market"))) + " gp");
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
