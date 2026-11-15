"""The hand-written parts of docs/index.html: the guide and one short example per SDK method.

tools/gen_docs.py reads the method list from the Java sources and the routes from spec/openapi.json; this file only holds words and
examples a person has to write. Every public SDK method needs an entry in EXAMPLES (gen_docs.py --check fails otherwise).

Examples are Java snippets that assume `hub` is a connected RuneClanClient. Never put a real key, id or secret here: this repository is
public. Use variable names (guildId, alias, ...) instead of made-up ids.
"""

# Groups, in the order the page shows them: (accessor on the client, class, one line). "hub" = methods on RuneClanClient itself.
GROUPS = [
    ("hub", "RuneClanClient", "The client itself: the handshake, scopes, reading for one server, and any route."),
    ("players()", "Players", "Players who granted your app access, their RuneSync events, XP, logs and stats."),
    ("guilds()", "Guilds", "Your bot's Discord servers and which granted players are in them."),
    ("feed()", "Feed", "Everything that changed for your app, oldest first."),
    ("webhooks()", "Webhooks", "Get the feed pushed to you, and check the deliveries are real."),
    ("clans()", "Clans", "Registered clans: search, events, site, leagues, polls, signups, features."),
    ("communities()", "Communities", "Communities (groups with a RuneClans website that aren't a clan)."),
    ("sites()", "Sites", "Published clan and community websites by their address."),
    ("events()", "Events", "Competitions and bingos clans share publicly: standings and boards."),
    ("directory()", "Directory", "The public clan directory."),
    ("gimGroups()", "GimGroups", "RS3 Group Ironman groups from the game's group leaderboard."),
]

# Methods of RuneClanClient the page leaves out (they are covered elsewhere or are plumbing).
HIDDEN = {"RuneClanClient.create", "RuneClanClient.toString", "RuneClanClient.close"}

EXAMPLES = {
    # --- the client ------------------------------------------------------------------------------------------------------------
    "RuneClanClient.guildId": """String server = hub.forGuild(guildId).guildId();   // the server this view reads for""",
    "RuneClanClient.forGuild": """// A player may share their data with your app for some servers only. Read "as" one server:
RuneClanClient here = hub.forGuild(event.getGuild().getId());
Optional<Player> p = here.players().get(alias).complete();""",
    "RuneClanClient.handshake": """Me me = hub.handshake().orElseThrow();
System.out.println(me.appName() + " " + me.tier() + " " + me.scopes());
System.out.println(me.rateLimit().perMinute() + " calls a minute");""",
    "RuneClanClient.refreshHandshake": """// After the hub gave your key a new scope:
Me me = hub.refreshHandshake();""",
    "RuneClanClient.hasScope": """if (hub.hasScope("links:read")) {
    hub.players().byDiscordId(userId).queue(p -> ...);
}""",
    "RuneClanClient.me": """Me me = hub.me().complete();   // asks the hub again (handshake() answers the saved copy)""",
    "RuneClanClient.route": """EventBoard board = hub.route(Routes.GET_EVENTS_EVENT_ID_BOARD)
        .path("eventId", eventId)
        .as(EventBoard.class)
        .complete();""",
    "RuneClanClient.grantUrl": """// Send a player here so they can let your app see their data:
String link = RuneClanClient.grantUrl("https://runeclans.com", hub.handshake().orElseThrow().appId());
event.reply("Let the bot see your RuneScape data: " + link).queue();""",
    # --- players ---------------------------------------------------------------------------------------------------------------
    "Players.list": """PlayerPage page = hub.players().list(null, 50).complete();      // first page
PlayerPage next = hub.players().list(page.nextCursor(), 50).complete();""",
    "Players.all": """for (Player p : hub.players().all()) {        // every page, one after another
    System.out.println(p.alias() + " " + p.characters());
}""",
    "Players.get": """hub.players().get(alias).complete()
        .ifPresent(p -> System.out.println(p.characters().get(0).rsn()));""",
    "Players.byDiscordId": """hub.players().byDiscordId(event.getUser().getId()).queue(player -> {
    if (player.isEmpty()) event.reply("Link your RuneScape account first.").queue();
    else event.reply("You are " + player.get().characters().get(0).rsn()).queue();
});""",
    "Players.byRsn": """List<Player> players = hub.players().byRsn("Zezima").complete();   // empty when nobody who granted you has it""",
    "Players.events": """EventPage drops = hub.players().events(alias, "DROPS", null, null, 20).complete();
System.out.println(drops.data().size() + " drops; more: " + (drops.nextCursor() != null));""",
    "Players.xp": """XpAnswer xp = hub.players().xp(alias, Map.of("from", "2026-11-01", "to", "2026-11-08")).complete();""",
    "Players.logs": """PlayerLogs logs = hub.players().logs(alias).complete();   // drop log, clue log, pets, kill counts""",
    "Players.stats": """PlayerStatsBatch stats = hub.players().stats(List.of("Zezima", "Some Name")).complete();
// names you may not see are listed under stats.missing()""",
    # --- guilds ----------------------------------------------------------------------------------------------------------------
    "Guilds.register": """// On start and on every GuildJoinEvent (safe to repeat):
hub.guilds().register(guild.getId(), guild.getName()).queue();""",
    "Guilds.remove": """// On GuildLeaveEvent:
hub.guilds().remove(guild.getId()).queue();""",
    "Guilds.addMember": """hub.guilds().addMember(guildId, alias).queue();   // safe to repeat""",
    "Guilds.removeMember": """hub.guilds().removeMember(guildId, alias).queue();""",
    "Guilds.players": """PlayerPage page = hub.guilds().players(guildId, null, 100).complete();""",
    "Guilds.allPlayers": """for (Player p : hub.guilds().allPlayers(guildId)) System.out.println(p.alias());""",
    "Guilds.events": """EventPage recent = hub.guilds().events(guildId, "2026-11-01T00:00:00Z", null, 50).complete();""",
    # --- feed ------------------------------------------------------------------------------------------------------------------
    "Feed.after": """FeedPage page = hub.feed().after(savedCursor, 100).complete();
for (FeedEntry e : page.data()) handle(e);
savedCursor = page.nextCursor();                  // store it; page.more() = call again now""",
    # --- webhooks --------------------------------------------------------------------------------------------------------------
    "Webhooks.create": """Webhook hook = hub.webhooks().create("https://my-bot.example.org/runeclan", List.of("plugin.event")).complete();
saveSecretSomewhereSafe(hook.secret());   // shown only this once""",
    "Webhooks.list": """JsonNode hooks = hub.webhooks().list().complete();""",
    "Webhooks.delete": """hub.webhooks().delete(webhookId).complete();""",
    "Webhooks.ping": """hub.webhooks().ping(webhookId).complete();   // the hub sends a test delivery""",
    "Webhooks.resume": """hub.webhooks().resume(webhookId).complete();   // after the hub paused it for failing""",
    "Webhooks.verify": """// In your HTTP handler, with the raw body exactly as it arrived:
if (!Webhooks.verify(secret, request.header("X-Hub-Signature"), rawBody)) {
    return 401;
}""",
    # --- clans -----------------------------------------------------------------------------------------------------------------
    "Clans.search": """List<Clan> found = hub.clans().search("you").complete();   // names starting with "you\"""",
    "Clans.get": """Optional<Clan> clan = hub.clans().get(clanId).complete();""",
    "Clans.byName": """Clan clan = hub.clans().byName("Younglings").complete().orElseThrow();""",
    "Clans.events": """EventList open = hub.clans().events(clan.clanId(), "open").complete();""",
    "Clans.site": """hub.clans().site(clanId).complete().ifPresent(site -> System.out.println("https://" + site.slug() + ".runeclans.com"));""",
    "Clans.leagues": """LeagueList leagues = hub.clans().leagues(clanId).complete();""",
    "Clans.polls": """PollList polls = hub.clans().polls(clanId, "open").complete();   // needs polls:read""",
    "Clans.signups": """SignupList signups = hub.clans().signups(clanId, "active").complete();   // needs signups:read""",
    "Clans.features": """SiteFeatures features = hub.clans().features(clanId).complete();""",
    # --- communities -----------------------------------------------------------------------------------------------------------
    "Communities.list": """for (CommunityListing c : hub.communities().list().complete()) System.out.println(c.name());""",
    "Communities.bySlug": """Optional<CommunityListing> c = hub.communities().bySlug("pvm-hub").complete();""",
    "Communities.get": """hub.communities().get(communityId).complete().ifPresent(c -> System.out.println(c.joinMode()));""",
    # --- sites -----------------------------------------------------------------------------------------------------------------
    "Sites.get": """hub.sites().get("younglings").complete().ifPresent(node -> {
    if (node.has("movedTo")) System.out.println("Moved to " + node.get("movedTo").asText());
    else System.out.println(node.path("name").asText());
});""",
    "Sites.features": """SiteFeatures features = hub.sites().features(siteId).complete();""",
    "Sites.polls": """JsonNode polls = hub.sites().polls("younglings").complete();""",
    "Sites.signups": """JsonNode signups = hub.sites().signups("younglings").complete();""",
    "Sites.groups": """JsonNode groups = hub.sites().groups("younglings").complete();""",
    "Sites.forms": """JsonNode forms = hub.sites().forms("younglings").complete();""",
    # --- events ----------------------------------------------------------------------------------------------------------------
    "Events.shared": """EventList shared = hub.events().shared(null, null, null).complete();   // public events, defaults""",
    "Events.get": """Optional<Event> e = hub.events().get(eventId).complete();""",
    "Events.participants": """EventParticipants who = hub.events().participants(eventId).complete();""",
    "Events.standings": """for (var row : hub.events().standings(eventId).complete().standings()) {
    System.out.println(row.place() + ". " + row.name() + " " + row.points());
}""",
    "Events.board": """EventBoard board = hub.events().board(eventId).complete();""",
    "Events.boardPng": """byte[] png = hub.events().boardPng(eventId).complete();
channel.sendFiles(FileUpload.fromData(png, "board.png")).queue();""",
    # --- directory -------------------------------------------------------------------------------------------------------------
    "Directory.clans": """Directory.ClanPage page = hub.directory().clans(null, "OPEN", null, 50).complete();   // recruiting clans""",
    "Directory.allClans": """for (DirectoryClan c : hub.directory().allClans("pvm", null)) System.out.println(c.name());""",
    "Directory.clan": """Optional<DirectoryClan> c = hub.directory().clan("younglings").complete();""",
    # --- gim groups ------------------------------------------------------------------------------------------------------------
    "GimGroups.list": """GimGroupList top = hub.gimGroups().list().complete();                                  // best first by XP
GimGroupList fives = hub.gimGroups().list(null, 5, "competitive", "level", 20, 0).complete();""",
    "GimGroups.get": """hub.gimGroups().get(groupId, 90).complete()
        .ifPresent(g -> System.out.println(g.group().name() + ": " + g.history().size() + " days"));""",
}

# Scope words for the Scopes table. Routes per scope come from the spec.
SCOPES = {
    "players:read": "List and read the players who granted your app.",
    "events:read": "Their RuneSync events and XP, in the categories they share with you.",
    "links:read": "Look a player up by Discord id, only if they granted you their identity.",
    "guilds:write": "Register your Discord servers and which granted players are in them.",
    "feed:read": "Read your app's change feed.",
    "webhooks:manage": "Add, list, test and remove webhooks (at most 5).",
    "polls:read": "A clan's polls and results (anonymous polls: counts only).",
    "signups:read": "A clan's signups and group-finder posts.",
    "forms:read": "A clan's web forms.",
    "economy:read": "A clan's points, coffer, Citadel wheel and giveaways.",
    "features:report": "Tell the hub what your bot offers, per server.",
}

# Rate limit tiers outside apps can have (requests a minute, burst). Your key's own numbers come from the handshake.
TIERS = [("standard", 60, 120, "Every new key."), ("partner", 600, 1200, "Bigger bots, by agreement.")]

# The SDK's own error codes (nothing, or no clear answer, came back from the hub).
SDK_ERRORS = [
    ("missing_scope", "Your key lacks the scope the call needs. With local() true the SDK caught it before sending anything."),
    ("unreachable", "No connection to the hub (after the SDK's own retries for safe calls)."),
    ("timeout", "Sent, but no answer in time: it may have happened. Writes are not sent again on their own."),
    ("interrupted", "Your thread was interrupted while waiting."),
    ("bad_answer", "The hub answered something the SDK couldn't read."),
    ("sdk_too_old", "426: the hub no longer serves this SDK version. Update the SDK."),
    ("rate_limited", "429 that lasted longer than maxRetryWait (default 60 s). retryAfter() says how long."),
]
