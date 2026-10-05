package com.gpmachine;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

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
}
