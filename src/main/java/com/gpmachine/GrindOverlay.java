package com.gpmachine;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.function.Supplier;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * The small box in the game during a Grind Machine session: method, current step, what you made, gp per hour and the
 * distance to the arrow. Read only; the player can move it like every RuneLite overlay.
 */
class GrindOverlay extends OverlayPanel
{
	private static final Color GOLD = new Color(0xff, 0xcf, 0x3f);
	private static final Color GREEN = new Color(0x5e, 0xe0, 0x5e);
	private static final Color RED = new Color(0xff, 0x6a, 0x52);
	private static final NumberFormat N = NumberFormat.getIntegerInstance(Locale.UK);

	private final Supplier<GrindState> state;

	GrindOverlay(Supplier<GrindState> state)
	{
		this.state = state;
		setPosition(OverlayPosition.TOP_LEFT);
		setPriority(0.5f);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		GrindState s = state.get();
		if (s == null)
		{
			return null;
		}
		panelComponent.getChildren().clear();
		panelComponent.setPreferredSize(new Dimension(230, 0));
		panelComponent.getChildren().add(TitleComponent.builder().text("GP MACHINE · " + s.name()).color(GOLD).build());
		if (!s.steps.isEmpty())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Step " + (s.stepIndex + 1) + "/" + s.steps.size() + ": " + s.stepText()).build());
		}
		if (s.distance >= 0 && s.target != null)
		{
			panelComponent.getChildren().add(LineComponent.builder().left("Arrow").right(s.distance + " tiles").build());
		}
		long gp = s.gp();
		panelComponent.getChildren().add(LineComponent.builder()
			.left((gp >= 0 ? "+" : "") + N.format(gp) + " gp").leftColor(gp >= 0 ? GREEN : RED)
			.right(N.format(s.gpHour()) + " gp/h · " + (s.elapsedS() / 60) + " min").build());
		return super.render(g);
	}
}
