package com.gpmachine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import org.junit.Test;

public class OfferTrackerTest
{
	@Test
	public void sameOfferKeepsItsIdAcrossUpdatesAndLogins()
	{
		OfferTracker.Result first = OfferTracker.identify(3, 2, 191, 1000, true, 0, false, null, 1000L);
		OfferTracker.Result later = OfferTracker.identify(3, 2, 191, 1000, true, 400, false, first.stored, 5000L);
		OfferTracker.Result done = OfferTracker.identify(3, 2, 191, 1000, true, 1000, true, later.stored, 9000L);
		assertEquals("3-1000", first.offerId);
		assertEquals(first.offerId, later.offerId);
		assertEquals(first.offerId, done.offerId);
		// after a new login RuneLite reports the same finished offer again: still the same id
		assertEquals(first.offerId, OfferTracker.identify(3, 2, 191, 1000, true, 1000, true, done.stored, 20000L).offerId);
	}

	@Test
	public void identicalNewOfferAfterTheOldOneFinishedGetsANewId()
	{
		OfferTracker.Result done = OfferTracker.identify(0, 2, 191, 1000, true, 1000, true, null, 1000L);
		OfferTracker.Result again = OfferTracker.identify(0, 2, 191, 1000, true, 0, false, done.stored, 50000L);
		assertNotEquals(done.offerId, again.offerId);
		assertEquals("0-50000", again.offerId);
	}

	@Test
	public void otherItemOrUnreadableStateIsANewOffer()
	{
		OfferTracker.Result a = OfferTracker.identify(1, 2, 191, 1000, true, 10, false, null, 1000L);
		assertNotEquals(a.offerId, OfferTracker.identify(1, 4151, 191, 1000, true, 0, false, a.stored, 2000L).offerId);
		assertEquals("1-3000", OfferTracker.identify(1, 2, 191, 1000, true, 0, false, "rubbish", 3000L).offerId);
	}

	@Test
	public void anonymousIdHidesTheAccountHash()
	{
		String id = GpMachinePlugin.anonymousId(123456789L);
		assertEquals(16, id.length());
		assertEquals(id, GpMachinePlugin.anonymousId(123456789L));
		assertNotEquals(id, GpMachinePlugin.anonymousId(987654321L));
	}
}
