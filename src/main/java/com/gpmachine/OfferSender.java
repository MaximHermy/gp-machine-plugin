package com.gpmachine;

import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * Builds the message the plugin sends to the GP MACHINE server.
 */
final class OfferSender
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	private OfferSender()
	{
	}

	/**
	 * One offer as it is sent. Price and spent are in gp, times in milliseconds.
	 */
	static Map<String, Object> offer(String offerId, int slot, int itemId, boolean buy, long price, int total,
		int filled, long spent, String state, int world, long started, long updated)
	{
		Map<String, Object> o = new LinkedHashMap<>();
		o.put("offer_id", offerId);
		o.put("slot", slot);
		o.put("item_id", itemId);
		o.put("buy", buy);
		o.put("price", price);
		o.put("total", total);
		o.put("filled", filled);
		o.put("spent", spent);
		o.put("state", state);
		o.put("world", world);
		o.put("started", started);
		o.put("updated", updated);
		return o;
	}

	/**
	 * @throws IllegalArgumentException when the server address is not a valid http(s) address
	 */
	static Request request(Gson gson, String url, String key, String account, List<Map<String, Object>> offers)
	{
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("account", account);
		body.put("offers", offers);
		return new Request.Builder()
			.url(url.trim())
			.header("Authorization", "Bearer " + key.trim())
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build();
	}
}
