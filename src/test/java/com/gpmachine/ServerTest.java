package com.gpmachine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Map;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.junit.Test;

/**
 * Sends a test offer to a real GP MACHINE server, exactly as the plugin does.
 * Only runs when GPM_TEST_URL and GPM_TEST_KEY are set (a test key, not a player's key).
 */
public class ServerTest
{
	@Test
	public void serverAcceptsOffersFromThePlugin() throws Exception
	{
		String url = System.getenv("GPM_TEST_URL");
		String key = System.getenv("GPM_TEST_KEY");
		assumeTrue(url != null && key != null);
		Gson gson = new Gson();
		OkHttpClient http = new OkHttpClient();
		long now = System.currentTimeMillis();

		OfferTracker.Result id = OfferTracker.identify(2, 2, 191L, 1000, true, 1000, true, null, now);
		Map<String, Object> bought = OfferSender.offer(id.offerId, 2, 2, true, 191L, 1000, 1000, 191_000L, "BOUGHT", 302,
			id.started, now);
		Map<String, Object> selling = OfferSender.offer("5-" + now, 5, 2, false, 199L, 1000, 250, 49_750L, "SELLING", 302,
			now, now);

		try (Response r = http.newCall(OfferSender.request(gson, url, key, GpMachinePlugin.anonymousId(42L),
			Arrays.asList(bought, selling))).execute())
		{
			assertEquals(200, r.code());
			String body = r.body().string();
			assertTrue(body, body.contains("\"saved\": 2"));
		}
		try (Response r = http.newCall(OfferSender.request(gson, url, "gpm_wrong", "x", Arrays.asList(bought))).execute())
		{
			assertEquals(401, r.code());
		}
	}
}
