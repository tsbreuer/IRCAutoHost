package autohost.handler;

import autohost.IRCBot;
import autohost.Lobby;
import autohost.irc.IRCClient;
import autohost.util.Beatmap;
import autohost.util.JSONUtils;
import autohost.util.RegexUtils;
import autohost.util.Slot;
import lt.ekgame.beatmap_analyzer.parser.BeatmapException;
import lt.ekgame.beatmap_analyzer.parser.BeatmapParser;
import lt.ekgame.beatmap_analyzer.utils.Mod;
import lt.ekgame.beatmap_analyzer.utils.Mods;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static autohost.util.MathUtils.round;

/**
 * Created by knash on 2017-05-23.
 */
public class ChannelMessageHandler {
    private final IRCBot    m_bot;
    private final IRCClient m_client;

    public ChannelMessageHandler(IRCBot bot) {
        m_bot = bot;
        m_client = m_bot.getClient();
    }

    public void handle(String channel, String sender, String message) {
        if (sender.equalsIgnoreCase(m_client.getUser())) {
            return;
        }

        // Is this a multi lobby channel?
        if (!RegexUtils.matches("#mp_(\\d+)", channel)) {
            // If not a lobby channel, then why the fuck we care?
            return;
        }

        Map<String, Lobby> lobbies = m_bot.getLobbies();
        if (lobbies.isEmpty()) {
            return;
        }

        boolean channelLoaded = false;
        if (lobbies.containsKey(channel)) {
            Lobby lobby = lobbies.get(channel);
            if (lobby.channel.equalsIgnoreCase(channel)) {
                // Is it an autohosted (by us) channel?
                channelLoaded = true;
                parse(lobby, sender, message);
            }
        }

        if (!channelLoaded) {
            System.out.println("Warning: Channel not loaded? C: " + channel);
        }
    }

    private void parse(Lobby lobby, String sender, String message) {
        if (sender.equalsIgnoreCase(m_client.getUser())) {
            return;
        }

        if (sender.equalsIgnoreCase("BanchoBot")) {
            handleBancho(lobby, message);
        }
    }

    private void handleBancho(Lobby lobby, String message) {
        // Room name and ID, important (?)
        // Room name: test, History: https://osu.ppy.sh/mp/31026456
        // Room name: AutoHost 5-6* || !info || By HyPeX,
        //      History: https://osu.ppy.sh/mp/32487590
        Matcher rNM = RegexUtils.matcher(
                "Room name: (.+), History: https://osu.ppy.sh/mp/(.+)",
                message);

        if (rNM.matches()) {
            String name = rNM.group(1);
            System.out.println("New room name! " + name);
            lobby.name = name;
            lobby.mpID = Integer.valueOf(rNM.group(2));
        }

        // Win condition... meh
        Matcher rTM = RegexUtils.matcher(
                "Team Mode: (.+), Win condition: (.+)",
                message);
        if (rTM.matches()) {
            System.out.println("Team & Condition set");
            lobby.teamgamemode = rTM.group(1);
            lobby.winCondition = rTM.group(2);
        }

        // Beatmap change... i guess thats k?
        Pattern beatmap = Pattern.compile("Beatmap: https://osu.ppy.sh/b/(\\d+) (.+)- (.+)");
        Matcher bM = beatmap.matcher(message);
        if (bM.matches()) {
            lobby.currentBeatmap = Integer.valueOf(bM.group(1));
            lobby.currentBeatmapAuthor = bM.group(2);
            lobby.currentBeatmapName = bM.group(3);
        }

        // Is this one even worth adding?
        Pattern players = Pattern.compile("Players: (\\d+)");
        Matcher pM = players.matcher(message);
        if (pM.matches()) {
            if (lobby.slots.size() != Integer.valueOf(pM.group(1))) {
                // m_client.sendMessage(lobby.channel, "Warning: Player count
                // mismatch! Did bot reconnect?");
            }
        }

        Pattern password = Pattern.compile("(.+) the match password");
        Matcher passmatch = password.matcher(message);
        if (passmatch.matches()) {
            if (passmatch.group(1).equals("Enabled")) {
                if (lobby.Password.equalsIgnoreCase("")) {
                    m_client.sendMessage(lobby.channel, "!mp password");
                }
            } else {
                if (!lobby.Password.equalsIgnoreCase("")) {
                    m_client.sendMessage(lobby.channel, "!mp password");
                }
            }
        }

        // Slot info on players... generally should be empty on start.. but
        // who knows.
        // :Slot 1 Ready https://osu.ppy.sh/u/711080 HyPeX
        // :Slot 2 Not Ready https://osu.ppy.sh/u/6435456 Saerph
        // Slot 1 Not Ready https://osu.ppy.sh/u/711080 HyPeX [Hidden]
        // Slot 1 Not Ready https://osu.ppy.sh/u/711080 HyPeX [HardRock]
        // :Slot 1 Not Ready https://osu.ppy.sh/u/711080 HyPeX
        Pattern slot = Pattern.compile(
                "^Slot (\\d+)(\\s+){1,2}(.+) https://osu.ppy.sh/u/(\\d+) (.+)(\\s){11}(\\[)?([^\\[\\]]+)?(\\])?$|(Slot (\\d+)(\\s+){1,2}(.+) https://osu.ppy.sh/u/(\\d+) (.+))");
        Matcher sM = slot.matcher(message);
        if (sM.matches()) {
            if (sM.group(5) != null) {
                int slotN = Integer.valueOf(sM.group(1));
                if (lobby.slots.containsKey(slotN)) {
                    Slot slotM = lobby.slots.get(slotN);
                    slotM.status = sM.group(3);
                    slotM.id = slotN;
                    slotM.playerid = Integer.valueOf(sM.group(4));
                    slotM.name = sM.group(5);
                    lobby.slots.replace(slotN, slotM);
                } else {
                    Slot slotM = new Slot();
                    slotM.status = sM.group(3);
                    slotM.id = slotN;
                    slotM.playerid = Integer.valueOf(sM.group(4));
                    slotM.name = sM.group(5);
                    lobby.slots.put(slotN, slotM);
                }
                return;
            } else {
                int slotN = Integer.valueOf(sM.group(11));
                if (lobby.slots.containsKey(slotN)) {
                    Slot slotM = lobby.slots.get(slotN);
                    slotM.status = sM.group(13);
                    slotM.id = slotN;
                    slotM.playerid = Integer.valueOf(sM.group(14));
                    slotM.name = sM.group(15);
                    lobby.slots.replace(slotN, slotM);
                } else {
                    Slot slotM = new Slot();
                    slotM.status = sM.group(13);
                    slotM.id = slotN;
                    slotM.playerid = Integer.valueOf(sM.group(14));
                    slotM.name = sM.group(15);
                    lobby.slots.put(slotN, slotM);
                }
            }
        }


        Pattern join = Pattern.compile("(.+) joined in slot (\\d+).");
        // :BanchoBot!cho@ppy.sh PRIVMSG #mp_29691447 :HyPeX joined in slot
        // 1.
        Matcher joinMatch = join.matcher(message);
        if (joinMatch.matches()) {
            String playerName = joinMatch.group(1);
            int jslot = Integer.valueOf(joinMatch.group(2));
            // int playerId = getId(playerName);
            int playerId = 0;
            playerId = m_bot.getId(playerName);
            String status = "Not Ready";
            Slot newSlot = new Slot(jslot, playerName, playerId, status);
            if (lobby.slots.containsKey(jslot)) {
                lobby.slots.replace(jslot, newSlot);
            } else {
                lobby.slots.put(jslot, newSlot);
            }
            lobby.afk.put(playerName, 0);
            for (int ID : lobby.OPs) {
                if (ID == m_bot.getId(playerName)) {
                    m_client.sendMessage(lobby.channel, "Operator " + playerName + " has joined. Welcome!");
                    m_client.sendMessage(lobby.channel, "!mp addref #" + ID);
                }
            }
        }

        Pattern move = Pattern.compile("(.+) moved to slot (\\d+)");
        Matcher moveMatcher = move.matcher(message);
        if (moveMatcher.matches()) {
            int playerId = 0;
            playerId = m_bot.getId(moveMatcher.group(1));
            Slot player = new Slot(Integer.valueOf(moveMatcher.group(2)), moveMatcher.group(1), playerId,
                    "Not Ready");
            for (int i = 1; i < 17; i++) {
                if (lobby.slots.containsKey(i)) {
                    if (lobby.slots.get(i).name.equalsIgnoreCase(moveMatcher.group(1))) {
                        player = lobby.slots.get(i);
                        lobby.slots.remove(i);
                    }
                }
            }
            lobby.slots.put(Integer.valueOf(moveMatcher.group(2)), player);
        }

        // :BanchoBot!cho@ppy.sh PRIVMSG #mp_32757177 :TrackpadEasy left the
        // game.
        Pattern left = Pattern.compile("(.+) left the game.");
        Matcher leftMatcher = left.matcher(message);
        if (leftMatcher.matches()) {
            for (int i = 1; i < 17; i++) {
                if (lobby.slots.containsKey(i)) {
                    if (lobby.slots.get(i).name.equalsIgnoreCase(leftMatcher.group(1))) {
                        lobby.slots.remove(i);
                        m_bot.tryStart(lobby);
                    }
                }
            }
            if (lobby.slots.size() == 0) {
                if (!lobby.OPLobby) {
                    m_client.sendMessage(lobby.channel, "!mp close");
                    m_bot.removeLobby(lobby);
                }
            }
        }

        if (message.equalsIgnoreCase("All players are ready")) {
            m_client.sendMessage(lobby.channel, "All players are ready! starting...");
            m_client.sendMessage(lobby.channel, "!mp start 5");
            lobby.timer.stopTimer();
        }

        if (message.equalsIgnoreCase("The match has started!")) {
            lobby.scores.clear();
            lobby.Playing = true;
        }

        if (message.equalsIgnoreCase("The match has finished!")) {
            // Chech for player scores -- TODO
            // TODO: At the end of the match, the score isn't always reported immediately.
            //       This causes people to be kicked super often. Find a way to fix this.
            for (Slot player : lobby.slots.values()) {
                if (!lobby.scores.containsKey(player.name)) {
                    m_bot.addAFK(lobby, player.name);
                }
            }
            m_bot.nextbeatmap(lobby);
            lobby.timer.continueTimer();
				/*
				 * Integer orderedScores[] = new Integer[(lobby.LobbySize - 1)];
				 * orderedScores = orderScores(lobby); for (int i = 0; i < 3;
				 * i++) { String player = lobby.scores.get(orderedScores[i]);
				 * m_client.sendMessage(lobby.channel, player + " finished " + (i + 1) +
				 * "!"); }
				 */
        }

        Pattern score = Pattern.compile("(.+) has finished playing \\(Score: (.\\d), (.\\D)\\)");
        Matcher scoreMatcher = score.matcher(message);
        if (scoreMatcher.matches()) {
            if (Integer.valueOf(scoreMatcher.group(2)) == 0) {
                m_bot.addAFK(lobby, scoreMatcher.group(1));
            } else {
                m_bot.removeAFK(lobby, scoreMatcher.group(1));
                lobby.scores.put(scoreMatcher.group(1), Integer.valueOf(scoreMatcher.group(2)));
            }
        }

        // Beatmap changed to: Rameses B - Neon Rainbow (ft. Anna Yvette)
        // [Easy] (https://osu.ppy.sh/b/961779)
        Pattern beatmapPattern = Pattern.compile("Beatmap changed to: (.+) [(.+)] (https://osu.ppy.sh/b/(.+))");
        Matcher beatmapMatcher = beatmapPattern.matcher(message);
        if (beatmapMatcher.matches()) {

        }
        return;
    }

    private void handleOther(Lobby lobby, String sender, String message) {
        /*
         * if (message.toLowerCase().contains("hi")){ m_client.sendMessage(lobby.channel,
         * "Hi "+Sender+"!"); }
         */
        message = message.trim().toLowerCase();
        // --TODO
        // Player is playing, not AFK.
        m_bot.removeAFK(lobby, sender);

        if (message.startsWith("!")) {
            message = message.substring(1);
            String[] args = message.split(" ");
            if (args[0].equals("add")) {
                for (Beatmap beatmap : lobby.beatmapQueue) {
                    if (beatmap.RequestedBy == m_bot.getId(sender)) {
                        m_client.sendMessage(lobby.channel, sender + " you have already requested a beatmap!");
                        return;
                    }
                }
                int id = 0;
                Pattern maprequest = Pattern.compile("add (\\d+)");
                Matcher mapR = maprequest.matcher(message);
                Pattern mapURL = Pattern.compile("add (.+)osu.ppy.sh/b/(\\d+)(.*)");
                Matcher mapU = mapURL.matcher(message);
                Pattern mapURLS = Pattern.compile("add (.+)osu.ppy.sh/s/(\\d+)(.*)");
                Matcher mapUS = mapURLS.matcher(message);
                if (mapR.matches()) {
                    id = Integer.valueOf(mapR.group(1));
                } else if (mapU.matches()) {
                    id = Integer.valueOf(mapU.group(2));
                } else if (mapUS.matches()) {
                    m_client.sendMessage(lobby.channel, sender
                            + " You introduced a beatmap set link, processing beatmaps... (for a direct difficulty add use the /b/ link)");
                    int bid = Integer.valueOf(mapUS.group(2));
                    m_bot.askForConfirmation(sender, bid, lobby);
                    return;
                }
                if (id == 0) {
                    m_client.sendMessage(lobby.channel,
                            sender + " Incorrect Arguments for !add. Please use the beatmap URL. !add [url]");
                    return;
                }
                try {
                    m_bot.getBeatmap(id, lobby, (obj) -> {
                        if (obj == null) {
                            m_client.sendMessage(lobby.channel, sender + ": Beatmap not found.");
                            return;
                        }

                        String mode = JSONUtils.silentGetString(obj, "mode");
                        if (!mode.equals(lobby.type)) {
                            m_client.sendMessage(lobby.channel,
                                    sender + " That beatmap does not fit the lobby's current gamemode!");
                            return;
                        }
                        Beatmap beatmap = JSONUtils.silentGetBeatmap(obj);
                        beatmap.RequestedBy = m_bot.getId(sender);
                        if (lobby.onlyDifficulty) { // Does the lobby have
                            // locked difficulty limits?
                            if (!(beatmap.difficulty >= lobby.minDifficulty
                                    && beatmap.difficulty <= lobby.maxDifficulty)) { // Are
                                // we
                                // inside
                                // the
                                // criteria?
                                // if
                                // not,
                                // return
                                m_client.sendMessage(lobby.channel,
                                        sender + " the difficulty of the song you requested does not match the lobby criteria. "
                                                + "(Lobby m/M: " + lobby.minDifficulty + "*/" + lobby.maxDifficulty
                                                + "*)," + " Song: " + beatmap.difficulty + "*");
                                return;
                            }
                        }
                        if (!lobby.statusTypes.get(beatmap.graveyard)) {
                            m_client.sendMessage(lobby.channel, sender
                                    + " That beatmap is not within ranking criteria for this lobby! (Ranked/loved/etc)");
                            return;
                        }

                        if (lobby.maxAR != 0) {
                            if (beatmap.difficulty_ar > lobby.maxAR) {

                                m_client.sendMessage(lobby.channel,
                                        sender + " That beatmap has a too high Approach Rate for this lobby! Max: "
                                                + lobby.maxAR + " beatmap AR: " + beatmap.difficulty_ar);
                                return;
                            }
                        }

                        if (lobby.onlyGenre) {
                            if (!beatmap.genre.equalsIgnoreCase(lobby.genre)) {
                                m_client.sendMessage(lobby.channel, sender + " This lobby is set to only play "
                                        + lobby.genres[Integer.valueOf(lobby.genre)] + " genre!");
                                return;
                            }
                        }
                        if (lobby.limitDate) {
                            Pattern date = Pattern.compile("(\\d+)\\-(\\d+)\\-(\\d+)(.+)");
                            Matcher dateM = date.matcher(beatmap.date);
                            if (dateM.matches()) {
                                if (Integer.valueOf(dateM.group(1)) >= lobby.maxyear
                                        || Integer.valueOf(dateM.group(1)) <= lobby.minyear) {
                                    m_client.sendMessage(lobby.channel,
                                            sender + " This beatmap is too old or new for this beatmap! Range: "
                                                    + lobby.minyear + "-" + lobby.maxyear);
                                    return;
                                }
                            }
                        }
                        if (beatmap.total_length >= lobby.maxLength) {
                            String length = "";
                            int minutes = lobby.maxLength / 60;
                            int seconds = lobby.maxLength - (minutes * 60);
                            length = minutes + ":" + seconds;
                            m_client.sendMessage(lobby.channel, sender + " This beatmap too long! Max length is: " + length);
                            return;
                        }
						/*
						 * if (mapR.group(2) != null) { String modString =
						 * mapR.group(2); String[] mods = modString.split(" ");
						 * for (String arg : mods) { if
						 * (arg.equalsIgnoreCase("DT")) beatmap.DT = true; else
						 * if (arg.equalsIgnoreCase("NC")) beatmap.NC = true;
						 * else if (arg.equalsIgnoreCase("HT")) beatmap.HT =
						 * true; } }
						 */
                        m_bot.addBeatmap(lobby, beatmap);

                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }

            } else if (args[0].equalsIgnoreCase("adddt")) {
                for (Beatmap beatmap : lobby.beatmapQueue) {
                    if (beatmap.RequestedBy == m_bot.getId(sender)) {
                        m_client.sendMessage(lobby.channel, sender + " you have already requested a beatmap!");
                        return;
                    }
                }
                Pattern mapURL = Pattern.compile("adddt (.+)osu.ppy.sh/b/(\\d+)(.*)");
                Matcher mapU = mapURL.matcher(message);
                int id = 0;
                if (mapU.matches()) {
                    id = Integer.valueOf(mapU.group(2));
                }
                if (id == 0) {
                    m_client.sendMessage(lobby.channel,
                            sender + " Incorrect Arguments for !adddt. Please use the beatmap URL. !adddt [url]");
                    return;
                }
                try {
                    m_bot.getBeatmap(id, lobby, (obj) -> {
                        if (obj == null) {
                            m_client.sendMessage(lobby.channel, sender + ": Beatmap not found.");
                            return;
                        }

                        String mode = JSONUtils.silentGetString(obj, "mode");
                        if (!mode.equals(lobby.type)) {
                            m_client.sendMessage(lobby.channel,
                                    sender + " That beatmap does not fit the lobby's current gamemode!");
                            return;
                        }
                        Beatmap beatmap = JSONUtils.silentGetBeatmap(obj);
                        beatmap.RequestedBy = m_bot.getId(sender);
                        beatmap.DT = true;
                        try {
                            RequestConfig defaultRequestConfig = RequestConfig.custom().setSocketTimeout(10000)
                                    .setConnectTimeout(10000).setConnectionRequestTimeout(10000).build();

                            HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig)
                                    .build();
                            URI uri = new URIBuilder().setScheme("http").setHost("osu.ppy.sh")
                                    .setPath("/osu/" + beatmap.beatmap_id).build();
                            HttpGet request = new HttpGet(uri);
                            HttpResponse response = httpClient.execute(request);
                            InputStream content = response.getEntity().getContent();
                            BeatmapParser parser = new BeatmapParser();
                            lt.ekgame.beatmap_analyzer.Beatmap cbp = parser.parse(content);
                            cbp = cbp.applyMods(new Mods(Mod.DOUBLE_TIME));
                            beatmap.difficulty = cbp.getDifficulty().getStarDifficulty();
                            beatmap.difficulty_ar = 4.66666 + 0.6666 * beatmap.difficulty_ar;
                            beatmap.difficulty_od = cbp.getDifficultySettings().getOD();
                            beatmap.difficulty_hp = cbp.getDifficultySettings().getHP();

                        } catch (IOException | URISyntaxException | BeatmapException e) {
                            e.printStackTrace();
                            m_client.sendMessage(lobby.channel, "Error Parsing beatmap. Please try again.");
                        }

                        if (lobby.onlyDifficulty) { // Does the lobby have
                            // locked difficulty limits?
                            if (!(beatmap.difficulty >= lobby.minDifficulty
                                    && beatmap.difficulty <= lobby.maxDifficulty)) { // Are
                                // we
                                // inside
                                // the
                                // criteria?
                                // if
                                // not,
                                // return
                                m_client.sendMessage(lobby.channel,
                                        sender + " the difficulty of the song you requested does not match the lobby criteria. "
                                                + "(Lobby m/M: " + lobby.minDifficulty + "*/" + lobby.maxDifficulty
                                                + "*)," + " Song: " + beatmap.difficulty + "*");
                                return;
                            }
                        }
                        if (lobby.limitDate) {
                            Pattern date = Pattern.compile("(\\d+)\\-(\\d+)\\-(\\d+)(.+)");
                            Matcher dateM = date.matcher(beatmap.date);
                            if (dateM.matches()) {
                                if (Integer.valueOf(dateM.group(1)) >= lobby.maxyear
                                        || Integer.valueOf(dateM.group(1)) <= lobby.minyear) {
                                    m_client.sendMessage(lobby.channel,
                                            sender + " This beatmap is too old or new for this beatmap! Range: "
                                                    + lobby.minyear + "-" + lobby.maxyear);
                                    return;
                                }
                            }
                        }
                        if ((beatmap.total_length / 1.5) >= lobby.maxLength) {
                            String length = "";
                            int minutes = lobby.maxLength / 60;
                            int seconds = lobby.maxLength - (minutes * 60);
                            length = minutes + ":" + seconds;
                            m_client.sendMessage(lobby.channel, sender + " This beatmap too long! Max length is: " + length);
                            return;
                        }

                        if (!lobby.statusTypes.get(beatmap.graveyard)) {
                            m_client.sendMessage(lobby.channel, sender
                                    + " That beatmap is not within ranking criteria for this lobby! (Ranked/loved/etc)");
                            return;
                        }

                        if (lobby.maxAR != 0) {
                            if (beatmap.difficulty_ar > lobby.maxAR) {

                                m_client.sendMessage(lobby.channel,
                                        sender + " That beatmap has a too high Approach Rate for this lobby! Max: "
                                                + lobby.maxAR + " beatmap AR: " + beatmap.difficulty_ar);
                                return;
                            }
                        }

                        if (lobby.onlyGenre) {
                            if (!beatmap.genre.equalsIgnoreCase(lobby.genre)) {
                                m_client.sendMessage(lobby.channel, sender + " This lobby is set to only play "
                                        + lobby.genres[Integer.valueOf(lobby.genre)] + " genre!");
                                return;
                            }
                        }
                        m_bot.addBeatmap(lobby, beatmap);

                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }

            } else if (args[0].equalsIgnoreCase("r") || args[0].equalsIgnoreCase("ready")) {
                if (lobby.Playing) {
                    m_client.sendMessage(sender, "The lobby is currently playing, you cant vote for starting right now.");
                    return;
                }
                if (lobby.currentBeatmap == 0) {
                    m_client.sendMessage(sender, "Please add a map before starting playing!");
                    return;
                }
                if (lobby.votestarted(sender)) {
                    m_client.sendMessage(sender, "You already voted for starting!");
                } else {
                    lobby.voteStart.add(sender);
                    m_client.sendMessage(lobby.channel, sender + " voted for starting! (" + lobby.voteStart.size() + "/"
                            + (int) round(lobby.slots.size() * 0.75, 0) + ")");
                    if (lobby.voteStart.size() >= round(lobby.slots.size() * 0.75, 0)) {
                        m_bot.start(lobby);
                    }
                }
            } else if (args[0].equalsIgnoreCase("skip") || args[0].equalsIgnoreCase("s")) {
                if (lobby.Playing) {
                    m_client.sendMessage(sender, "The lobby is currently playing, you cant vote for skipping right now.");
                    return;
                }
                if (lobby.votedskip(sender)) {
                    m_client.sendMessage(sender, "You already voted for skipping!");
                } else {
                    lobby.voteskip.add(sender);
                    m_client.sendMessage(lobby.channel, sender + " voted for skipping! (" + lobby.voteskip.size() + "/"
                            + (int) round(lobby.slots.size() * 0.6, 0) + ")");
                    if (lobby.voteskip.size() >= (int) round(lobby.slots.size() * 0.6, 0)) {
                        m_client.sendMessage(lobby.channel, "Map has been skipped by vote.");
                        m_bot.nextbeatmap(lobby);
                    }
                }
            } else if (args[0].equalsIgnoreCase("info")) {
                m_client.sendMessage(lobby.channel,
                        "This is an in-development IRC version of autohost developed by HyPeX. Do !commands to know them ;) [https://discord.gg/UDabf2y Discord] [Reddit Thread](https://www.reddit.com/r/osugame/comments/67u0k9/autohost_bot_is_finally_ready_for_public_usage/)");

            } else if (args[0].equalsIgnoreCase("commands")) {
                m_client.sendMessage(lobby.channel,
                        "C.List: !add [beatmap] | !ready/!r | !skip/!s | !queue/!playlist | !ver | !last | !maxdiff | !mindiff | !graveyard | !clearhost | !hostme | !fav/!favorites");
            } else if (args[0].equalsIgnoreCase("playlist") || args[0].equalsIgnoreCase("queue")) {
                String playlist = "Queue: " + lobby.beatmapQueue.size() + " || ";
                for (Beatmap bm : lobby.beatmapQueue) {
                    playlist = playlist + "[https://osu.ppy.sh/b/" + bm.beatmap_id + " " + bm.artist + " - " + bm.title
                            + "] [" + round(bm.difficulty, 2) + "*] || ";
                }
                m_client.sendMessage(lobby.channel, playlist);
            } else if (args[0].equalsIgnoreCase("select")) {
                Pattern select = Pattern.compile("select (.+)");
                Matcher sm = select.matcher(message);
                if (!sm.matches()) {
                    m_client.sendMessage(lobby.channel,
                            "Incorrect usage, please do !select [number]. Please consider using the number in []");
                    return;
                }
                if (lobby.requests.containsKey(sender)) {
                    int map = Integer.valueOf(sm.group(1));
                    m_bot.addBeatmap(lobby,
                            lobby.requests.get(sender).beatmaps.get(lobby.requests.get(sender).bids.get(map)));
                    lobby.requests.remove(sender);
                } else {
                    m_client.sendMessage(lobby.channel, "You dont have any pending map requests.");
                }
            } else if (args[0].equalsIgnoreCase("maxdiff")) {
                for (int ID : lobby.OPs) {
                    if (ID == (m_bot.getId(sender))) {
                        Pattern maxdiff = Pattern.compile("maxdiff (\\d+(?:\\.\\d+)?)");
                        Matcher diffM = maxdiff.matcher(message);
                        if (diffM.matches()) {
                            lobby.maxDifficulty = Double.valueOf(diffM.group(1));
                            m_client.sendMessage(lobby.channel, "Max difficulty now is " + diffM.group(1));
                        }

                    }
                }
            } else if (args[0].equalsIgnoreCase("freemods")) {
                for (int ID : lobby.OPs) {
                    if (ID == (m_bot.getId(sender))) {
                        m_client.sendMessage(lobby.channel, "!mp mods Freemod");
                    }
                }
            } else if (args[0].equalsIgnoreCase("mode")) {
                for (int ID : lobby.OPs) {
                    if (ID == (m_bot.getId(sender))) {
                        Pattern mode = Pattern.compile("mode (.+)");
                        Matcher modeMatch = mode.matcher(message);
                        if (modeMatch.matches()) {
                            if (modeMatch.group(1).equalsIgnoreCase("mania")) {
                                lobby.type = "3";
                                m_client.sendMessage(lobby.channel, "This lobby is now a mania lobby");
                            } else if (modeMatch.group(1).equalsIgnoreCase("std")
                                    || modeMatch.group(1).equalsIgnoreCase("standard")) {
                                lobby.type = "0";
                                m_client.sendMessage(lobby.channel, "This lobby is now a Standard lobby");
                            } else if (modeMatch.group(1).equalsIgnoreCase("ctb")) {
                                lobby.type = "2";
                                m_client.sendMessage(lobby.channel, "This lobby is now a Catch The Beat lobby");
                            } else if (modeMatch.group(1).equalsIgnoreCase("taiko")) {
                                lobby.type = "1";
                                m_client.sendMessage(lobby.channel, "This lobby is now a Taiko lobby");
                            }
                        }
                    }
                }
            } else if (args[0].equalsIgnoreCase("graveyard")) {
                for (int ID : lobby.OPs) {
                    if (ID == (m_bot.getId(sender))) {
                        if (lobby.graveyard) {
                            lobby.graveyard = false;
                            lobby.WIP = false;
                            lobby.pending = false;
                            lobby.statusTypes.put(0, false);
                            lobby.statusTypes.put(-1, false);
                            lobby.statusTypes.put(-2, false);
                            m_client.sendMessage(lobby.channel, "Graveyard maps are now unallowed.");
                        } else {
                            lobby.graveyard = true;
                            lobby.WIP = true;
                            lobby.pending = true;
                            lobby.statusTypes.put(0, true);
                            lobby.statusTypes.put(-1, true);
                            lobby.statusTypes.put(-2, true);
                            m_client.sendMessage(lobby.channel, "Graveyard maps are now allowed.");
                        }
                        return;
                    }
                }
                m_client.sendMessage(lobby.channel, sender + " You're not an Operator!");

            } else if (args[0].equalsIgnoreCase("ver")) {
                m_client.sendMessage(lobby.channel, "Bot version is 2.8");

            } else if (args[0].equalsIgnoreCase("wait")) {
                Boolean extended = lobby.timer.extendTimer();
                if (extended)
                    m_client.sendMessage(lobby.channel, "Timer extended by 1 minute.");
                else
                    m_client.sendMessage(lobby.channel, "Timer was already extended.");
            } else if (args[0].equalsIgnoreCase("lobby")) {
                for (int ID : lobby.OPs) {
                    if (ID == (m_bot.getId(sender))) {
                        Pattern setting = Pattern.compile("lobby (.+) (.*)");
                        Matcher settingMatcher = setting.matcher(message);
                        if (settingMatcher.matches()) {

                        }
                    }
                }
            } else if (args[0].equalsIgnoreCase("start")) {
                for (int ID : lobby.OPs) {
                    if (ID == (m_bot.getId(sender))) {
                        m_bot.tryStart(lobby);
                    }
                }
            } else if (args[0].equalsIgnoreCase("last") || args[0].equalsIgnoreCase("l")) {
                m_bot.getLastPlay(lobby, sender);
            } else if (args[0].equalsIgnoreCase("kick")) {
                for (int ID : lobby.OPs) {
                    if (ID == (m_bot.getId(sender))) {
                        Pattern id = Pattern.compile("kick (\\d+)");
                        Pattern name = Pattern.compile("kick (.+)");
                        Matcher idmatch = id.matcher(message);
                        Matcher namematch = name.matcher(message);
                        if (idmatch.matches()) {
                            m_client.sendMessage(lobby.channel, "!mp kick #" + idmatch.group(1));
                            return;
                        } else if (namematch.matches()) {
                            for (int i = 0; i < 16; i++) {
                                Slot slot = lobby.slots.get(i);
                                if (slot != null)
                                    if (slot.name.toLowerCase().contains(namematch.group(1).toLowerCase())) {
                                        m_client.sendMessage(lobby.channel, "!mp kick #" + slot.id);
                                        return;
                                    }
                            }
                        }

                    }
                }
                m_client.sendMessage(lobby.channel, sender + " user not found.");
            } else if (args[0].equalsIgnoreCase("addop")) {
                for (int ID : lobby.OPs) {
                    if (ID == (m_bot.getId(sender))) {
                        Pattern maxdiff = Pattern.compile("addop (\\d+)");
                        Matcher diffM = maxdiff.matcher(message);
                        if (diffM.matches()) {
                            lobby.OPs.add(Integer.valueOf(diffM.group(1)));
                        }

                    }
                }
            } else if (args[0].equalsIgnoreCase("forceskip")) {
                for (int ID : lobby.OPs) {
                    if (ID == (m_bot.getId(sender))) {
                        m_bot.nextbeatmap(lobby);
                    }
                }
            } else if (args[0].equalsIgnoreCase("forcestart")) {
                for (int ID : lobby.OPs) {
                    if (ID == (m_bot.getId(sender))) {
                        m_client.sendMessage(lobby.channel, "!mp start");
                    }
                }
            } else if (args[0].equalsIgnoreCase("password")) {
                for (int ID : lobby.OPs) {
                    if (ID == (m_bot.getId(sender))) {
                        Pattern pw = Pattern.compile("password (.+)?");
                        Matcher pwmatch = pw.matcher(message);
                        if (pwmatch.matches()) {
                            if (pwmatch.groupCount() == 1) {
                                if (pwmatch.group(1).equalsIgnoreCase("reset")) {
                                    lobby.Password = "";
                                } else {
                                    lobby.Password = pwmatch.group(1);
                                }
                                m_client.sendMessage(lobby.channel, "!mp password");
                            } else {
                                m_client.sendMessage(lobby.channel, "Current password is " + lobby.Password);
                            }
                        }
                    }
                }
            } else if (args[0].equalsIgnoreCase("mindiff")) {
                for (int ID : lobby.OPs) {
                    if (ID == (m_bot.getId(sender))) {
                        Pattern maxdiff = Pattern.compile("mindiff (.+)");
                        Matcher diffM = maxdiff.matcher(message);
                        if (diffM.matches()) {
                            lobby.minDifficulty = Double.valueOf(diffM.group(1));
                            m_client.sendMessage(lobby.channel, "New minimum difficulty is " + diffM.group(1));
                        }

                    }
                }
            } else if (args[0].equalsIgnoreCase("maxar")) {
                for (int ID : lobby.OPs) {
                    if (ID == (m_bot.getId(sender))) {
                        Pattern maxdiff = Pattern.compile("maxar (.+)");
                        Matcher diffM = maxdiff.matcher(message);
                        if (diffM.matches()) {
                            lobby.maxAR = Double.valueOf(diffM.group(1));
                            if (lobby.maxAR == 0.0)
                                m_client.sendMessage(lobby.channel, "Approach Rate limit was removed.");
                            else
                                m_client.sendMessage(lobby.channel, "New maximum approach rate is " + diffM.group(1));
                        } else {
                            lobby.maxAR = 0.0;
                            m_client.sendMessage(lobby.channel, "Approach Rate limit was removed.");
                        }

                    }
                }
            } else if (args[0].equalsIgnoreCase("maxyear")) {
                for (int ID : lobby.OPs) {
                    if (ID == (m_bot.getId(sender))) {
                        Pattern maxyr = Pattern.compile("maxyear (.+)");
                        Matcher yrM = maxyr.matcher(message);
                        if (yrM.matches()) {
                            if (Integer.valueOf(yrM.group(1)) < lobby.minyear) {
                                m_client.sendMessage(lobby.channel,
                                        "Max year cant be smaller than min year. Please lower that first ;)");
                                return;
                            }
                            lobby.maxyear = Integer.valueOf(yrM.group(1));
                            m_client.sendMessage(lobby.channel, "New newer year limit now is " + yrM.group(1));
                        } else {
                            lobby.maxyear = 2200;
                            m_client.sendMessage(lobby.channel, "Newest year limit was removed.");
                        }

                    }
                }
            } else if (args[0].equalsIgnoreCase("minyear")) {
                for (int ID : lobby.OPs) {
                    if (ID == (m_bot.getId(sender))) {
                        Pattern minyr = Pattern.compile("minyear (.+)");
                        Matcher yrM = minyr.matcher(message);
                        if (yrM.matches()) {
                            if (Integer.valueOf(yrM.group(1)) > lobby.maxyear) {
                                m_client.sendMessage(lobby.channel,
                                        "Min year cant be bigger than max year. Please increase that first ;)");
                                return;
                            }
                            lobby.minyear = Integer.valueOf(yrM.group(1));
                            m_client.sendMessage(lobby.channel, "Oldest year limit now is " + yrM.group(1));
                        } else {
                            lobby.minyear = 0;
                            m_client.sendMessage(lobby.channel, "Oldest year limit was removed");
                        }

                    }
                }
            } else if (args[0].equalsIgnoreCase("limityear")) {
                for (int ID : lobby.OPs) {
                    if (ID == (m_bot.getId(sender))) {
                        lobby.limitDate = !lobby.limitDate;
                        m_client.sendMessage(lobby.channel, "Toggled Date limiting. State: " + lobby.limitDate);
                    }
                }
            } else if (args[0].equalsIgnoreCase("duration")) {
                for (int ID : lobby.OPs) {
                    if (ID == (m_bot.getId(sender))) {
                        Pattern minyr = Pattern.compile("duration (.+)");
                        Matcher yrM = minyr.matcher(message);
                        if (yrM.matches()) {
                            lobby.maxLength = Integer.valueOf(yrM.group(1));
                            String length = "";
                            int minutes = lobby.maxLength / 60;
                            int seconds = lobby.maxLength - (minutes * 60);
                            length = minutes + ":" + seconds;
                            m_client.sendMessage(lobby.channel, "Maximum duration now is " + length);
                        }
                    }
                }
            } else if (args[0].equalsIgnoreCase("hostme")) {
                for (int ID : lobby.OPs) {
                    if (ID == (m_bot.getId(sender))) {
                        m_client.sendMessage(lobby.channel, "!mp host #" + ID);
                    }
                }
            } else if (args[0].equalsIgnoreCase("clearhost")) {
                for (int ID : lobby.OPs) {
                    if (ID == (m_bot.getId(sender))) {
                        m_client.sendMessage(lobby.channel, "!mp clearhost");
                    }
                }
            } else if (args[0].equalsIgnoreCase("random")) {
                for (int ID : lobby.OPs) {
                    if (ID == (m_bot.getId(sender))) {
                        lobby.TrueRandom = !lobby.TrueRandom;
                        m_client.sendMessage(lobby.channel, "Toggled Random Maps. State: " + lobby.TrueRandom);
                    }
                }
            } else if (args[0].equalsIgnoreCase("rename")) {
                for (int ID : lobby.OPs) {
                    if (ID == (m_bot.getId(sender))) {
                        Pattern rename = Pattern.compile("rename (.+)");
                        Matcher renameM = rename.matcher(message);
                        if (renameM.matches()) {
                            lobby.name = renameM.group(1);
                        }

                    }
                }
            } else if (args[0].equalsIgnoreCase("say")) {
                if (!sender.equalsIgnoreCase("HyPeX")) {
                    m_client.sendMessage(lobby.channel, "I'm afraid " + sender + "i cant let you do that.");
                    return;
                }
                Pattern say = Pattern.compile("say (.+)");
                Matcher sayM = say.matcher(message);
                if (sayM.matches()) {
                    m_client.sendMessage(lobby.channel, sayM.group(1));
                } else {
                    m_client.sendMessage(lobby.channel,
                            "Wrong command syntax. Really dude? You made me... and you cant get a fucking command right");
                }

            } else if (args[0].equalsIgnoreCase("closeroom")) {
                for (int ID : lobby.OPs) {
                    if (ID == (m_bot.getId(sender))) {
                        m_client.sendMessage(lobby.channel, "!mp close");
                        m_bot.removeLobby(lobby);
                        return;
                    }
                }
                m_client.sendMessage(lobby.channel, sender + " You're not an Operator!");
            }
        }
    }
}
