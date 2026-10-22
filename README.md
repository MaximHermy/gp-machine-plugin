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


## In-game notifications (1.2.0)

Everything visual can be switched off in the plugin settings (wrench › GP MACHINE): coloured GE slots, the grind box, the
highlights, the arrow and the world map point. Two RuneLite notifications, also switchable:
- an offer that has waited 30 minutes beside the market (buy below what sellers get now, sell above what buyers pay);
- a buy limit that is free again for an item you bought.

## Grind Machine (1.2.0, steps in 1.3.0)

The **Grind** tab shows money making methods you can do right now, best gp per hour first. The plugin reads your skill
levels, combat level, quest points and finished quests once after login and every 15 minutes, and sends them to your
GP MACHINE account so the list is personal (dashboard › Grind Machine shows the same list with filters).

Pick a method and press **Start**:
- a checklist of what to bring, green or red against your inventory and equipment;
- an arrow and a world map marker to the place (when the wiki knows its coordinates);
- a counter: what the method yields and appears in your inventory counts as gained at GP MACHINE prices, what it uses
  and disappears counts as used.

**Steps (1.3.0)**: every method comes with steps from the server (bring, go there, do it, bank). A step with a place
puts the arrow and a gold tile on it and ticks off by itself when you arrive; a step that names an object (a bank
booth) outlines it in green; the rest you tick with Previous and Next. A small box in the game shows the method, the
step, the distance to the arrow and what you made. **Report** sends a problem with a method to the owner.
Filter chips: Skilling, Combat, Processing, Collecting, F2P.

A session also stops by itself when you log out or after 30 minutes without moving or looting. Each method shows what
you made per hour last time.

**Stop and save** sends the session (start, stop, gp, items gained and used) to your account. From 2 members, the
dashboard shows the real gp per hour next to the wiki figure. The method list comes from the OSRS Wiki money making
guides (CC BY-NC-SA), refreshed once a day on the server.

The panel needs your key (dashboard › Settings › Plugin). It only reads data from the GP MACHINE server and from your
own game client; it never does anything in the game.

## What it sends

Only when you switch on **Send GE offers** and paste your personal key:

- per offer: item id, buy or sell, price, quantity, quantity filled, gp spent, state (buying, bought, selling, sold, cancelled), GE slot and world;
- an anonymous account id (a hash, never your name);
- for Grind Machine (1.2.0): your skill levels, combat level, quest points and the names of finished quests, and when you
  press Stop: the session (method, start, stop, gp, items gained and used).

Nothing else is read or sent. The plugin never acts in the game.

## Setup

1. On the GP MACHINE dashboard, open **Plugin** and make a key.
2. In RuneLite, open the GP MACHINE settings, paste the key and switch on **Send GE offers**.
3. Your offers appear on the Plugin page of the dashboard.
