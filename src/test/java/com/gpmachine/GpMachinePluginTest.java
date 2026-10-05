package com.gpmachine;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Starts RuneLite with this plugin loaded, for testing on your own computer.
 */
public class GpMachinePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GpMachinePlugin.class);
		RuneLite.main(args);
	}
}
