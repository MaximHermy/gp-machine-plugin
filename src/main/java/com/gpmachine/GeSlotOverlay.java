package com.gpmachine;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Colours the 8 offer slots in the Grand Exchange window: green = your offer is priced well, red = it sits beside the
 * market (buy offer below what sellers get now, or sell offer above what buyers pay now), with how long it has been
 * waiting. The judgement comes from the GP MACHINE server (the same as "Your day" in the side panel).
 */
class GeSlotOverlay extends Overlay
{
	private static final int FIRST_SLOT_CHILD = 7; // the 8 offer slots are children 7 to 14 of the Grand Exchange interface
	private static final Color GOOD = new Color(0x5e, 0xe0, 0x5e);
	private static final Color BAD = new Color(0xff, 0x6a, 0x52);
	private static final Color SHADOW = new Color(0, 0, 0, 170);

	private final Client client;
	private final Supplier<Map<String, Object>> packet;

	GeSlotOverlay(Client client, Supplier<Map<String, Object>> packet)
	{
		this.client = client;
		this.packet = packet;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Map<String, Object> data = packet.get();
		if (data == null || !(data.get("day") instanceof Map))
		{
			return null;
		}
		Object offers = ((Map<?, ?>) data.get("day")).get("offers");
		if (!(offers instanceof List))
		{
			return null;
		}
		g.setFont(FontManager.getRunescapeSmallFont());
		for (Object o : (List<?>) offers)
		{
			if (!(o instanceof Map))
			{
				continue;
			}
			Map<?, ?> offer = (Map<?, ?>) o;
			int slot = (int) GpMachinePanel.toLong(offer.get("slot"));
			if (slot < 0 || slot > 7)
			{
				continue;
			}
			Widget w = client.getWidget(InterfaceID.GRAND_EXCHANGE, FIRST_SLOT_CHILD + slot);
			if (w == null || w.isHidden())
			{
				continue;
			}
			Rectangle r = w.getBounds();
			if (r == null || r.width < 20)
			{
				continue;
			}
			boolean ok = Boolean.TRUE.equals(offer.get("ok"));
			long minutes = GpMachinePanel.toLong(offer.get("minutes"));
			g.setColor(ok ? GOOD : BAD);
			g.setStroke(new BasicStroke(2));
			g.drawRect(r.x + 1, r.y + 1, r.width - 3, r.height - 3);
			String text = ok ? "priced well" : (Boolean.TRUE.equals(offer.get("buy")) ? "too low" : "too high") + " · " + minutes + " min";
			int tx = r.x + 4;
			int ty = r.y + r.height - 4;
			g.setColor(SHADOW);
			g.drawString(text, tx + 1, ty + 1);
			g.setColor(ok ? GOOD : BAD);
			g.drawString(text, tx, ty);
		}
		return null;
	}
}
