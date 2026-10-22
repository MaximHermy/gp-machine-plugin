package com.gpmachine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

/**
 * The running Grind Machine session, shared between the plugin (which updates it on the client thread), the side
 * panel and the overlays (which only read it). Everything here is read from the game or from the GP MACHINE server;
 * the plugin never does anything in the game.
 */
class GrindState
{
	final Map<String, Object> method;
	final List<Map<String, Object>> steps;
	final long startMs;
	volatile int stepIndex;
	volatile long gained;
	volatile long used;
	volatile int distance = -1;
	volatile WorldPoint target;
	volatile WorldPoint lastPos;
	volatile long lastActivityMs;
	volatile List<Map<String, Object>> checklist = new ArrayList<>();

	GrindState(Map<String, Object> method, List<Map<String, Object>> steps, long startMs)
	{
		this.method = method;
		this.steps = steps;
		this.startMs = startMs;
		this.lastActivityMs = startMs;
	}

	String name()
	{
		return String.valueOf(method.get("name"));
	}

	Map<String, Object> step()
	{
		return steps.isEmpty() || stepIndex < 0 || stepIndex >= steps.size() ? null : steps.get(stepIndex);
	}

	String stepText()
	{
		Map<String, Object> s = step();
		return s == null ? "" : String.valueOf(s.get("text"));
	}

	@SuppressWarnings("unchecked")
	List<String> stepObjects()
	{
		Map<String, Object> s = step();
		List<String> uit = new ArrayList<>();
		if (s != null && s.get("objects") instanceof List)
		{
			for (Object o : (List<Object>) s.get("objects"))
			{
				uit.add(String.valueOf(o).toLowerCase());
			}
		}
		return uit;
	}

	long gp()
	{
		return gained - used;
	}

	long elapsedS()
	{
		return Math.max(1, (System.currentTimeMillis() - startMs) / 1000);
	}

	long gpHour()
	{
		return gp() * 3600 / elapsedS();
	}
}
