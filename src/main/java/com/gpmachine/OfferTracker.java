package com.gpmachine;

/**
 * Gives every Grand Exchange offer a stable id, also across logins.
 * RuneLite reports offers per slot; an offer is identified by its slot and the moment it was first seen.
 * The state per slot is kept as a small string, so it can be stored in the RuneLite profile config.
 */
final class OfferTracker
{
	private OfferTracker()
	{
	}

	static final class Result
	{
		final String offerId;
		final long started;
		final String stored;

		Result(String offerId, long started, String stored)
		{
			this.offerId = offerId;
			this.started = started;
			this.stored = stored;
		}
	}

	/**
	 * @param stored  what was stored for this slot before ("item:price:total:buy:started:filled:complete"), or null
	 * @param now     current time in milliseconds
	 * @return the id of this offer and the new value to store for the slot
	 */
	static Result identify(int slot, int itemId, long price, int total, boolean buy, int filled, boolean complete,
		String stored, long now)
	{
		long started = now;
		if (stored != null)
		{
			String[] p = stored.split(":");
			if (p.length == 7)
			{
				try
				{
					boolean same = Integer.parseInt(p[0]) == itemId
						&& Long.parseLong(p[1]) == price
						&& Integer.parseInt(p[2]) == total
						&& Boolean.parseBoolean(p[3]) == buy;
					int filledBefore = Integer.parseInt(p[5]);
					boolean completeBefore = Boolean.parseBoolean(p[6]);
					// a new offer with the same item, price and quantity: the old one was finished, or less is filled now
					boolean newOffer = !same || (completeBefore && !complete) || filled < filledBefore;
					if (!newOffer)
					{
						started = Long.parseLong(p[4]);
					}
				}
				catch (NumberFormatException e)
				{
					// unreadable: treat as a new offer
				}
			}
		}
		String value = itemId + ":" + price + ":" + total + ":" + buy + ":" + started + ":" + filled + ":" + complete;
		return new Result(slot + "-" + started, started, value);
	}
}
