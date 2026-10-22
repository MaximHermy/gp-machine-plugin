package com.gpmachine;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import java.util.Set;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * In the game world during a session: the destination tile of the current step (gold) and the objects the step talks
 * about, such as a bank booth (green outline). Like Quest Helper does for quests. Nothing is clicked for the player.
 */
class GrindSceneOverlay extends Overlay
{
	private static final Color GOLD = new Color(0xff, 0xcf, 0x3f);
	private static final Color GOLD_FILL = new Color(0xff, 0xcf, 0x3f, 50);
	private static final Color GREEN = new Color(0x5e, 0xe0, 0x5e);
	private static final Color GREEN_FILL = new Color(0x5e, 0xe0, 0x5e, 40);

	private final Client client;
	private final Supplier<GrindState> state;
	private final Supplier<Set<TileObject>> objects;
	private final java.util.function.BooleanSupplier enabled;

	GrindSceneOverlay(Client client, Supplier<GrindState> state, Supplier<Set<TileObject>> objects, java.util.function.BooleanSupplier enabled)
	{
		this.client = client;
		this.state = state;
		this.objects = objects;
		this.enabled = enabled;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		GrindState s = state.get();
		if (s == null || !enabled.getAsBoolean())
		{
			return null;
		}
		WorldPoint target = s.target;
		if (target != null && target.getPlane() == client.getPlane())
		{
			LocalPoint lp = LocalPoint.fromWorld(client, target);
			if (lp != null)
			{
				Polygon poly = net.runelite.api.Perspective.getCanvasTilePoly(client, lp);
				if (poly != null)
				{
					g.setColor(GOLD_FILL);
					g.fill(poly);
					g.setColor(GOLD);
					g.setStroke(new BasicStroke(2));
					g.draw(poly);
				}
			}
		}
		for (TileObject o : objects.get())
		{
			Shape hull = o.getClickbox();
			if (hull == null)
			{
				continue;
			}
			g.setColor(GREEN_FILL);
			g.fill(hull);
			g.setColor(GREEN);
			g.setStroke(new BasicStroke(2));
			g.draw(hull);
		}
		return null;
	}
}
