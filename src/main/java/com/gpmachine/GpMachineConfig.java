package com.gpmachine;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

@ConfigGroup(GpMachinePlugin.CONFIG_GROUP)
public interface GpMachineConfig extends Config
{
	@ConfigItem(
		keyName = "enabled",
		name = "Send GE offers",
		description = "Send your Grand Exchange offers to your GP MACHINE dashboard",
		warning = "This sends your Grand Exchange offers (item, price, quantity, status) and an anonymous account id "
			+ "to the GP MACHINE server. Nothing else is sent. Continue?",
		position = 1
	)
	default boolean enabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "key",
		name = "Key",
		description = "Your personal key, made on the Plugin page of the GP MACHINE dashboard",
		secret = true,
		position = 2
	)
	default String key()
	{
		return "";
	}

	@ConfigItem(
		keyName = "serverUrl",
		name = "Server",
		description = "Address of the GP MACHINE server",
		position = 3
	)
	default String serverUrl()
	{
		return "https://gp.217-160-143-251.sslip.io/api/v1/ge";
	}

	@ConfigItem(
		keyName = "panel",
		name = "Side panel",
		description = "Show the GP MACHINE side panel: the best flips for your capital and your day (needs your key)",
		position = 4
	)
	default boolean panel()
	{
		return true;
	}

	@ConfigItem(
		keyName = "priceKey",
		name = "Price hotkey",
		description = "While the GE asks for a price: press this key and the GP MACHINE buy or sell price is filled in. You still confirm yourself.",
		position = 5
	)
	default Keybind priceKey()
	{
		return new Keybind(java.awt.event.KeyEvent.VK_V, java.awt.event.InputEvent.ALT_DOWN_MASK);
	}

	@ConfigItem(
		keyName = "notifyOffers",
		name = "Notify: offer beside the market",
		description = "A RuneLite notification when one of your GE offers has waited 30 minutes beside the market (buy below what sellers get, sell above what buyers pay)",
		position = 6
	)
	default boolean notifyOffers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notifyLimits",
		name = "Notify: buy limit free",
		description = "A RuneLite notification when the buy limit of an item you bought is free again",
		position = 7
	)
	default boolean notifyLimits()
	{
		return true;
	}

	@ConfigItem(keyName = "showGeSlots", name = "Colour GE slots", description = "Green edge = your offer is priced well, red edge = it sits beside the market", position = 8)
	default boolean showGeSlots()
	{
		return true;
	}

	@ConfigItem(keyName = "showGrindBox", name = "Grind: box in the game", description = "The small box with the method, the step, the distance and what you made during a Grind Machine session", position = 9)
	default boolean showGrindBox()
	{
		return true;
	}

	@ConfigItem(keyName = "showHighlights", name = "Grind: highlight tiles and objects", description = "The gold destination tile and the green outline around the objects of the current step", position = 10)
	default boolean showHighlights()
	{
		return true;
	}

	@ConfigItem(keyName = "showArrow", name = "Grind: arrow", description = "The hint arrow to the place of the current step", position = 11)
	default boolean showArrow()
	{
		return true;
	}

	@ConfigItem(keyName = "showMapPoint", name = "Grind: world map point", description = "A GP MACHINE marker on the world map at the place of the method", position = 12)
	default boolean showMapPoint()
	{
		return true;
	}
}
