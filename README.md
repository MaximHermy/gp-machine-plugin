# GP MACHINE

Sends your own Grand Exchange offers to your GP MACHINE flipping dashboard, so your flips are logged without any typing:
your stock shown like your bank, your profit, your open offers, a leaderboard, and private alerts when an offer sits
beside the market or a buy limit is free again.

**Start here: https://gp.217-160-143-251.sslip.io** (free to try; the Telegram bot @GPMachine_bot gives you your login).
GP MACHINE also posts live Grand Exchange flips (checked afterwards, hit rate public), CRASH/PUMP alerts and news-based
calls in a Telegram hub.

## Side panel (1.1.0)

Click the gold GP coin on the RuneLite side bar:

- **Flips**: the best Grand Exchange flips right now for your capital (set on the dashboard), with FAST / SLOW and the
  ✅ SOLID label (flips that worked out best so far). Buttons at the top filter them. Updated every minute.
- **Your day**: your profit today, your stock, your open offers (green = priced well, red = beside the market) and when
  your buy limits are free again.
- **Price hotkey** (default Alt+V): while the Grand Exchange asks you for a price, press the key and the GP MACHINE buy or
  sell price for that item is filled in. You still confirm the offer yourself. The plugin never buys or sells.

- **Favorites and item search**: type an item name at the top of the panel for its prices of now, the flip round,
  the members price and a ★ star. Starred items get alerts in Telegram and show under the ★ Favorites tab.
- **Coloured GE slots**: in the Grand Exchange window every slot with an offer gets a green edge (priced well) or a red
  edge (beside the market: a buy below what sellers get now, or a sell above what buyers pay now) with how long it has
  been waiting.

The panel needs your key (dashboard › Settings › Plugin). It only reads data from the GP MACHINE server.

## What it sends

Only when you switch on **Send GE offers** and paste your personal key:

- per offer: item id, buy or sell, price, quantity, quantity filled, gp spent, state (buying, bought, selling, sold, cancelled), GE slot and world;
- an anonymous account id (a hash, never your name).

Nothing else is read or sent. The plugin never acts in the game.

## Setup

1. On the GP MACHINE dashboard, open **Plugin** and make a key.
2. In RuneLite, open the GP MACHINE settings, paste the key and switch on **Send GE offers**.
3. Your offers appear on the Plugin page of the dashboard.
