package autohost.handler;

import autohost.AutoHost;
import autohost.IRCBot;
import autohost.Lobby;
import autohost.irc.IRCClient;
import autohost.util.Beatmap;
import autohost.util.BrokenBeatmap;
import autohost.util.JSONUtils;
import autohost.util.LobbyChecker;
import autohost.util.RegexUtils;
import autohost.util.Slot;
import lt.ekgame.beatmap_analyzer.difficulty.Difficulty;
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
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.regex.Matcher;

import static autohost.util.MathUtils.round;

public class ChannelMessageHandler {
	private final IRCBot m_bot;
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
		Map<String, LobbyChecker> permanentlobbies = m_bot.getpermanentLobbies();
		if (lobbies.isEmpty() && permanentlobbies.isEmpty()) {
			return;
		}

		Lobby loadedLobby = null;
		if (lobbies.containsKey(channel)) {
			Lobby lobby = lobbies.get(channel);
			if (lobby.getChannel().equalsIgnoreCase(channel)) {
				// Is it an autohosted (by us) channel?
				loadedLobby = lobby;
			}
		}
		if (permanentlobbies.containsKey(channel)) {
			Lobby lobby = permanentlobbies.get(channel).lobby;
			if (lobby.getChannel().equalsIgnoreCase(channel)) {
				// Is it an autohosted (by us) channel?
				loadedLobby = lobby;
			}
		}

		if (loadedLobby != null) {
			if (sender.equalsIgnoreCase(m_client.getUser())) {
				return;
			}

			if (sender.equalsIgnoreCase("BanchoBot")) {
				handleBancho(loadedLobby, message);
			} else {
				handleCommands(loadedLobby, sender, message);
			}
		} else {
			System.out.println("Warning: Channel not loaded? C: " + channel);
		}
	}

	private void handleBancho(Lobby lobby, String message) {
		// Room name and ID, important (?)
		// Room name: test, History: https://osu.ppy.sh/mp/31026456
		// Room name: AutoHost 5-6* || !info || By HyPeX,
		// History: https://osu.ppy.sh/mp/32487590
		Matcher rNM = RegexUtils.matcher("Room name: (.+), History: https://osu.ppy.sh/mp/(.+)", message);

		if (rNM.matches()) {
			String name = rNM.group(1);
			System.out.println("New room name! " + name);
			lobby.setName(name);
			lobby.setMpID(Integer.valueOf(rNM.group(2)));
			return;
		}

		// Win condition... meh
		Matcher rTM = RegexUtils.matcher("Team Mode: (.+), Win condition: (.+)", message);
		if (rTM.matches()) {
			System.out.println("Team & Condition set");
			lobby.setTeamgamemode(rTM.group(1));
			lobby.setWinCondition(rTM.group(2));
			return;
		}

		// Beatmap change... i guess thats k?
		Matcher bM = RegexUtils.matcher("Beatmap: https://osu.ppy.sh/b/(\\d+) (.+)- (.+)", message);
		if (bM.matches()) {
			if (lobby.getCurrentBeatmap() != null) {
				lobby.getCurrentBeatmap().beatmap_id = Integer.valueOf(bM.group(1));
				lobby.setCurrentBeatmapAuthor(bM.group(2));
				lobby.setCurrentBeatmapName(bM.group(3));
			}
			return;
		}

		Matcher iMM = RegexUtils.matcher("Invalid map ID provided", message);
		if (iMM.matches()) {
			m_bot.nextbeatmap(lobby);
			return;
		}

		// Is this one even worth adding?
		Matcher pM = RegexUtils.matcher("Players: (\\d+)", message);
		if (pM.matches()) {
			if (lobby.getSlots().size() != Integer.valueOf(pM.group(1))) {

				m_client.sendMessage(lobby.getChannel(), "Warning: Player count mismatch! Did bot reconnect?");

			}
			return;
		}

		Matcher passmatch = RegexUtils.matcher("(.+) the match password", message);
		if (passmatch.matches()) {
			if (passmatch.group(1).equals("Changed")) {
				if (lobby.getPassword().equalsIgnoreCase("")) {
					m_client.sendMessage(lobby.getChannel(), "!mp password");
				}
			} else if (!lobby.getPassword().equalsIgnoreCase("")) {
				m_client.sendMessage(lobby.getChannel(), "!mp password");
			}
			return;
		}

		// Slot info on players... generally should be empty on start.. but
		// who knows.
		// Slot 1 Ready https://osu.ppy.sh/u/711080 HyPeX
		// Slot 1 Not Ready https://osu.ppy.sh/u/711080 HyPeX [Hidden]
		// Regex documentation:
		// Group 1: Slot number | 2: Whitespaces betwen slot and status | 3:
		// Ready/Status
		// 4: UserID | 5: User Name | 6: Entire [Mod] including brackets
		// 7: First '[' | 8: Mods alone 'Hidden,Hardrock' | 9: Second ']'
		Matcher sM = RegexUtils.matcher(
				"^Slot (\\d+)(\\s+){1,2}(.+) https://osu\\.ppy\\.sh/u/(\\d+) (.+?)(?=( *$| +(\\[)([^\\[\\]]+)?(\\])?)$)",
				message);
		if (sM.matches()) {
			int slotN = Integer.valueOf(sM.group(1));
			Matcher nameFix = RegexUtils.matcher(
					"(.+)?\\h*((\\[( |Host|\\/|NoFail|Easy|Relax|FlashLight|SpunOut|Hidden|HardRock|Relax2|,)*\\]))",
					sM.group(5).trim());
			String name;
			if (nameFix.matches()) {
				name = nameFix.group(1);
			} else {
				name = sM.group(5);
			}
			if (name == null) {
				return;
			}
			name = name.trim();
			if (lobby.getSlots().containsKey(slotN)) {
				Slot slotM = lobby.getSlots().get(slotN);
				slotM.status = sM.group(3);
				slotM.id = slotN;
				slotM.playerid = Integer.valueOf(sM.group(4));
				slotM.name = name;
				m_bot.m_writer.println("Slot movement: '" + name + "'");
				m_bot.m_writer.flush();
				lobby.getSlots().replace(slotN, slotM);
			} else {
				Slot slotM = new Slot();
				slotM.status = sM.group(3);
				slotM.id = slotN;
				slotM.playerid = Integer.valueOf(sM.group(4));
				slotM.name = name;
				m_bot.m_writer.println("Slot movement: '" + name + "'");
				m_bot.m_writer.flush();
				lobby.getSlots().put(slotN, slotM);
			}
			System.out.println("Slot movement: '" + name + "'");
			return;
		}

		// :BanchoBot!cho@ppy.sh PRIVMSG #mp_29691447 :HyPeX joined in slot
		// 1.
		Matcher joinMatch = RegexUtils.matcher("(.+) joined in slot (\\d+).", message);
		if (joinMatch.matches()) {
			String playerName = joinMatch.group(1);
			int jslot = Integer.valueOf(joinMatch.group(2));
			// int playerId = getId(playerName);
			int playerId = m_bot.getId(playerName);
			String status = "Not Ready";
			Slot newSlot = new Slot(jslot, playerName, playerId, status);
			m_bot.m_writer.println("New slot: '" + playerName + "'");
			m_bot.m_writer.flush();
			if (lobby.getSlots().containsKey(jslot)) {
				lobby.getSlots().replace(jslot, newSlot);
			} else {
				lobby.getSlots().put(jslot, newSlot);
			}
			lobby.getAfk().put(playerName, 0);

			int id = m_bot.getId(playerName);
			if (lobby.isOP(id)) {
				m_client.sendMessage(lobby.getChannel(), "Operator " + playerName + " has joined. Welcome!");
				m_client.sendMessage(lobby.getChannel(), "!mp addref " + playerName);
			}
			return;
		}

		Matcher moveMatcher = RegexUtils.matcher("(.+) moved to slot (\\d+)", message);
		if (moveMatcher.matches()) {
			int playerId = m_bot.getId(moveMatcher.group(1));
			Slot player = new Slot(Integer.valueOf(moveMatcher.group(2)), moveMatcher.group(1), playerId, "Not Ready");
			for (int i = 1; i < 17; i++) {
				if (lobby.getSlots().containsKey(i)) {
					if (lobby.getSlots().get(i).name.equalsIgnoreCase(moveMatcher.group(1))) {
						player = lobby.getSlots().get(i);
						lobby.getSlots().remove(i);
					}
				}
			}
			lobby.getSlots().put(Integer.valueOf(moveMatcher.group(2)), player);
			return;
		}

		// :BanchoBot!cho@ppy.sh PRIVMSG #mp_32757177 :TrackpadEasy left the
		// game.
		Matcher leftMatcher = RegexUtils.matcher("(.+) left the game.", message);
		if (leftMatcher.matches()) {
			for (int i = 1; i < 17; i++) {
				if (lobby.getSlots().containsKey(i)) {
					if (lobby.getSlots().get(i).name.equalsIgnoreCase(leftMatcher.group(1))) {
						lobby.getSlots().remove(i);
					}
				}
			}
			if (lobby.votestarted(leftMatcher.group(1))) {
				lobby.getVoteStart().remove(leftMatcher.group(1));
			}
			if (lobby.getSlots().size() == 0) {
				if (!lobby.getOPLobby()) {
					m_client.sendMessage(lobby.getChannel(), "!mp close");
					m_bot.removeLobby(lobby);
				}
			}
			checkLobbyReady(lobby);
			if (lobby.getRequests().containsKey(leftMatcher.group(1)))
				lobby.getRequests().remove(leftMatcher.group(1));
			return;
		}

		if (message.equalsIgnoreCase("All players are ready")) {
			m_client.sendMessage(lobby.getChannel(), "All players are ready! starting...");
			m_client.sendMessage(lobby.getChannel(), "!mp start 5");
			lobby.getTimer().stopTimer();
			return;
		}

		if (message.equalsIgnoreCase("The match has started!")) {
			lobby.getScores().clear();
			lobby.setPlaying(true);
			return;
		}

		if (message.equalsIgnoreCase("The match has finished!")) {
			for (Slot player : lobby.getSlots().values()) {
				if (!lobby.getScores().containsKey(player.name)) {
					m_bot.addAFK(lobby, player.name);
					m_bot.m_writer.println("AFK!: Player name: '" + player.name + "'");
					m_bot.m_writer.flush();
				}
			}
			m_bot.nextbeatmap(lobby);
			lobby.getTimer().continueTimer();
			return;
		}

		Matcher scoreMatcher = RegexUtils.matcher("(.+) finished playing \\(Score: (\\d+), (\\D+)\\).", message);
		if (scoreMatcher.matches()) {
			if (Integer.valueOf(scoreMatcher.group(2)) == 0) {
				m_bot.addAFK(lobby, scoreMatcher.group(1));
				m_bot.m_writer.println("AFK? Line: |" + message + "|");
				m_bot.m_writer.println("Player name: '" + scoreMatcher.group(1) + "'");
				m_bot.m_writer.flush();
			} else {
				m_bot.removeAFK(lobby, scoreMatcher.group(1));
				lobby.getScores().put(scoreMatcher.group(1), Integer.valueOf(scoreMatcher.group(2)));
			}
			return;
		}

		// Beatmap changed to: Rameses B - Neon Rainbow (ft. Anna Yvette)
		// [Easy] (https://osu.ppy.sh/b/961779)
		Matcher beatmapMatcher = RegexUtils.matcher("Changed beatmap to (https://osu.ppy.sh/b/(.+)) (.+) - (.+)",
				message);
		if (beatmapMatcher.matches()) {
			return;
		}

		Matcher modeMatcher = RegexUtils.matcher("Changed match mode to (.+)", message);
		if (modeMatcher.matches()) {
			return;
		}

		Matcher modsSelection = RegexUtils.matcher("Active mods: (.+)", message);
		if (modsSelection.matches()) {
			return;
		}

		Matcher ctdaborted = RegexUtils.matcher("Countdown aborted", message);
		if (ctdaborted.matches()) {
			return;
		}

		Matcher matchunlock = RegexUtils.matcher("Unlocked the match", message);
		if (matchunlock.matches()) {
			return;
		}

		Matcher matchlock = RegexUtils.matcher("Locked the match", message);
		if (matchlock.matches()) {
			return;
		}

		Matcher matchctd = RegexUtils.matcher("Match starts in (.+)", message);
		if (matchctd.matches()) {
			return;
		}

		Matcher modsawp = RegexUtils.matcher("Disabled all mods, enabled (.+)", message);
		if (modsawp.matches()) {
			return;
		}

		m_bot.m_writer.println("Regext error Line: |" + message + "|");
		m_bot.m_writer.flush();
	}

	private void checkLobbyReady(Lobby lobby) {
		if (lobby.getVoteStart().size() >= round(lobby.getSlots().size() * 0.75, 0)) {
			if (lobby.getTimer().added) {
				if (lobby.getTimer().askedAlready) {
					m_bot.start(lobby);
					return;
				}
				lobby.getTimer().starting = true;
			} else {
				m_bot.start(lobby);
			}
		}
	}

	private void handleCommands(Lobby lobby, String sender, String message) {
		message = message.trim().toLowerCase();
		// Player is playing, not AFK.
		m_bot.removeAFK(lobby, sender);

		if (!message.startsWith("!")) {
			return;
		}

		message = message.substring(1);
		String[] args = message.split(" ");
		switch (args[0]) {
			case "add":
				handleAdd(lobby, sender, message);
				break;
			case "adddt":
				handleAddDT(lobby, sender, message);
				break;
			case "ready":
			case "r":
				handleReady(lobby, sender);
				break;
			case "skip":
			case "s":
				handleSkip(lobby, sender);
				break;
			case "timerstatus":
				handleTimerStatus(lobby);
				break;
			case "keys":
				handleKeys(lobby, message);
				break;
			case "info":
				handleInfo(lobby);
				break;
			case "commands":
				handleCommands(lobby);
				break;
			case "playlist":
			case "queue":
				handlePlaylist(lobby);
				break;
			case "select":
				handleSelect(lobby, sender, message);
				break;
			case "maxdiff":
				handleMaxDifficulty(lobby, sender, message);
				break;
			case "freemods":
				handleFreeMods(lobby, sender);
				break;
			case "mode":
				handleMode(lobby, sender, message);
				break;
			case "graveyard":
				handleGraveyard(lobby, sender);
				break;
			case "prev":
			case "previous":
				handlePrevious(lobby, sender);
				break;
			case "lock":
				handleLock(lobby, sender);
				break;
			case "ver":
				handleVersion(lobby);
				break;
			case "wait":
				handleWait(lobby);
				break;
			case "lobby":
				handleLobby(lobby, sender, message);
				break;
			case "start":
				handleStart(lobby, sender);
				break;
			case "last":
			case "l":
				handleLast(lobby, sender);
				break;
			case "kick":
				handleKick(lobby, sender, message);
				break;
			case "addop":
				handleAddOP(lobby, sender, message);
				break;
			case "forceskip":
				handleForceSkip(lobby, sender);
				break;
			case "forcestart":
				handleForceStart(lobby, sender);
				break;
			case "password":
				handlePassword(lobby, sender, message);
				break;
			case "mindiff":
				handleMinDifficulty(lobby, sender, message);
				break;
			case "maxar":
				handleMaxAR(lobby, sender, message);
				break;
			case "maxyear":
				handleMaxYear(lobby, sender, message);
				break;
			case "minyear":
				handleMinYear(lobby, sender, message);
				break;
			case "limityear":
				handleLimitYear(lobby, sender);
				break;
			case "retry":
				handleRetryForNewMap(lobby, sender);
				break;
			case "minduration":
				handleMinDuration(lobby, sender, message);
				break;
			case "maxduration":
				handleMaxDuration(lobby, sender, message);
				break;
			case "hostme":
				handleHostMe(lobby, sender);
				break;
			case "clearhost":
				handleClearHost(lobby, sender);
				break;
			case "random":
				handleRandom(lobby, sender);
				break;
			case "rename":
				handleRename(lobby, sender, message);
				break;
			case "say":
				handleSay(lobby, sender, message);
				break;
			case "closeroom":
				handleCloseRoom(lobby, sender);
				break;
			case "searchsong":
				handleSearchSong(lobby, sender, message);
				break;
			default:
				// Unknown command.
		}
	}

	private void handleTimerStatus(Lobby lobby) {
		m_client.sendMessage(lobby.getChannel(), "Time passed since start: "
				+ (System.currentTimeMillis() - ((lobby.getTimer().startTime - lobby.getTimer().startAfter) - 200)));
		m_client.sendMessage(lobby.getChannel(),
				"Time left for start: " + (lobby.getTimer().startTime - System.currentTimeMillis()));
		m_client.sendMessage(lobby.getChannel(),
				"Lobby timer stopped status: " + lobby.getTimer().isRunning());
	}

	private void handleKeys(Lobby lobby, String message) {
		if (!lobby.getType().equals("3")) {
			m_client.sendMessage(lobby.getChannel(), "This only is available to mania lobbies.");
			return;
		}
		Matcher keys = RegexUtils.matcher("keys ?(\\d+)?", message);
		if (keys.matches()) {
			if (!(keys.group(1).equals(""))) {
				lobby.setKeys(Integer.valueOf(keys.group(1)));
				if (!lobby.getKeyLimit()) {
					m_client.sendMessage(lobby.getChannel(), "Enabled the key limiter");
					lobby.setKeyLimit(true);
				}
				m_client.sendMessage(lobby.getChannel(), "The new key mode is " + lobby.getKeys() + "K");
			} else {
				if (lobby.getKeyLimit()) {
					lobby.setKeyLimit(false);
					m_client.sendMessage(lobby.getChannel(), "The key limit was disabled");
				}
			}
		}
	}

	private void handleRetryForNewMap(Lobby lobby, String sender) {
		if (!lobby.getRetryForMap()) {
			m_client.sendMessage(sender, "The lobby didnt have an issue with the random? Not registered.");
			return;
		}
		m_bot.nextbeatmap(lobby);
		lobby.setRetryForMap(false);
	}

	private void handlePrevious(Lobby lobby, String sender) {
		if (lobby.getPreviousBeatmap() == null) {
			m_client.sendMessage(lobby.getChannel(),
					sender + " there is no beatmap in the last played. Did the lobby just get created?");

		} else {
			m_client.sendMessage(lobby.getChannel(),
					"The lastest beatmap played was [https://osu.ppy.sh/b/" + lobby.getPreviousBeatmap().beatmap_id + " "
							+ lobby.getPreviousBeatmap().artist + " - " + lobby.getPreviousBeatmap().title + "]");
		}
	}

	private void handleAdd(Lobby lobby, String sender, String message) {
		if (lobby.getLockAdding()) {
			m_client.sendMessage(lobby.getChannel(), sender + " sorry, beatmap requesting is currently disabled.");
			return;
		}
		if (m_bot.hasAlreadyRequested(lobby, sender)) {
			m_client.sendMessage(lobby.getChannel(), sender + " you have already requested a beatmap!");
			return;
		}
		int id = 0;
		Matcher mapR = RegexUtils.matcher("add (\\d+)", message);
		Matcher mapU = RegexUtils.matcher("add (.+)osu.ppy.sh/b/(\\d+)(.*)", message);
		Matcher mapUS = RegexUtils.matcher("add (.+)osu.ppy.sh/s/(\\d+)(.*)", message);
		if (mapR.matches()) {
			id = Integer.valueOf(mapR.group(1));
		} else if (mapU.matches()) {
			id = Integer.valueOf(mapU.group(2));
		} else if (mapUS.matches()) {
			m_client.sendMessage(lobby.getChannel(), sender
					+ " You introduced a beatmap set link, processing beatmaps... (for a direct difficulty add use the /b/ link)");
			int bid = Integer.valueOf(mapUS.group(2));
			m_bot.askForConfirmation(sender, bid, lobby);
			return;
		}
		if (id == 0) {
			m_client.sendMessage(lobby.getChannel(),
					sender + " Incorrect Arguments for !add. Please use the beatmap URL. !add [url]");
			return;
		}
		try {
			m_bot.getBeatmap(id, lobby, (JSONObject obj) -> {
				if (obj == null) {
					m_client.sendMessage(lobby.getChannel(), sender + ": Beatmap not found.");
					return;
				}

				String mode = JSONUtils.silentGetString(obj, "mode");
				if (!mode.equals(lobby.getType())) {
					m_client.sendMessage(lobby.getChannel(),
							sender + " That beatmap does not fit the lobby's current gamemode!");
					return;
				}

				Beatmap beatmap;
				try {
					beatmap = new Beatmap(obj);
				} catch (JSONException e) {
					e.printStackTrace();
					m_client.sendMessage(lobby.getChannel(), sender
							+ " there was an issue parsing the beatmap. Please try again later.");
					return;
				} catch (BrokenBeatmap e) {
					m_client.sendMessage(lobby.getChannel(), sender
							+ " this beatmap has invalid attributes. This may be due to being a broken beatmap. Avoiding.");
					return;
				}
				beatmap.RequestedBy = m_bot.getId(sender);
				if (lobby.getOnlyDifficulty()) { // Does the lobby have
					// locked difficulty limits?
					if (!(beatmap.difficulty >= lobby.getMinDifficulty() && beatmap.difficulty <= lobby.getMaxDifficulty())) { // Are
						// we
						// inside
						// the
						// criteria?
						// if
						// not,
						// return
						m_client.sendMessage(lobby.getChannel(),
								sender + " the difficulty of the song you requested does not match the lobby criteria. "
										+ "(Lobby m/M: " + lobby.getMinDifficulty() + "*/" + lobby.getMaxDifficulty() + "*),"
										+ " Song: " + beatmap.difficulty + "*");
						return;
					}
				}
				if (!lobby.getStatusTypes().get(beatmap.graveyard)) {
					m_client.sendMessage(lobby.getChannel(),
							sender + " that beatmap is not within ranking criteria for this lobby! (Ranked/loved/etc)");
					return;
				}

				if (lobby.getType().equals("3")) {
					if (lobby.getKeyLimit()) {
						if (beatmap.difficulty_cs != lobby.getKeys()) {
							m_client.sendMessage(lobby.getChannel(),
									sender + " that beatmap does not have the key count this lobby uses. Lobby: "
											+ lobby.getKeys() + "K");
							return;
						}
					}
				}
				if (lobby.getMaxAR() != 0) {
					if (beatmap.difficulty_ar > lobby.getMaxAR()) {

						m_client.sendMessage(lobby.getChannel(),
								sender + " That beatmap has a too high Approach Rate for this lobby! Max: "
										+ lobby.getMaxAR() + " beatmap AR: " + beatmap.difficulty_ar);
						return;
					}
				}

				if (lobby.getOnlyGenre()) {
					if (!beatmap.genre.equalsIgnoreCase(lobby.getGenre())) {
						m_client.sendMessage(lobby.getChannel(), sender + " This lobby is set to only play "
								+ lobby.getGenres()[Integer.valueOf(lobby.getGenre())] + " genre!");
						return;
					}
				}
				if(!checkLimitDate(lobby, beatmap, sender))
					return;

				if (beatmap.total_length >= lobby.getMaxLength()) {
					int minutes = lobby.getMaxLength() / 60;
					int seconds = lobby.getMaxLength() % 60;
					String str = String.format("%d:%02d", minutes, seconds);
					m_client.sendMessage(lobby.getChannel(), sender + " This beatmap too long! Max length is: " + str);
					return;
				}

				if (beatmap.total_length <= lobby.getMinLength()) {
					int minutes = lobby.getMinLength() / 60;
					int seconds = lobby.getMinLength() % 60;
					String str = String.format("%d:%02d", minutes, seconds);
					m_client.sendMessage(lobby.getChannel(), sender + " This beatmap too short! Min length is: " + str);
					return;
				}
				/*
				 * if (mapR.group(2) != null) { String modString = mapR.group(2); String[] mods
				 * = modString.split(" "); for (String arg : mods) { if
				 * (arg.equalsIgnoreCase("DT")) beatmap.DT = true; else if
				 * (arg.equalsIgnoreCase("NC")) beatmap.NC = true; else if
				 * (arg.equalsIgnoreCase("HT")) beatmap.HT = true; } }
				 */
				m_bot.addBeatmap(lobby, beatmap);

			});
		} catch (JSONException | URISyntaxException | IOException e) {
			e.printStackTrace();
		}
	}

	private boolean checkLimitDate(Lobby lobby, Beatmap beatmap, String sender) {
		if (lobby.getLimitDate()) {
			Matcher dateM = RegexUtils.matcher("(\\d+)\\-(\\d+)\\-(\\d+)(.+)", beatmap.date);
			if (dateM.matches()) {
				if (Integer.valueOf(dateM.group(1)) >= lobby.getMaxYear()
						|| Integer.valueOf(dateM.group(1)) <= lobby.getMinYear()) {
					m_client.sendMessage(lobby.getChannel(),
							sender + " This beatmap is too old or new for this beatmap! Range: " + lobby.getMinYear()
									+ "-" + lobby.getMaxYear());
					return false;
				}
			}
		}
		return true;
	}

	private void handleAddDT(Lobby lobby, String sender, String message) {
		if (lobby.getLockAdding()) {
			m_client.sendMessage(lobby.getChannel(), sender + " sorry, beatmap requesting is currently disabled.");
			return;
		}
		if (!lobby.isOP(m_bot.getId(sender))) {
			if (m_bot.hasAlreadyRequested(lobby, sender)) {
				m_client.sendMessage(lobby.getChannel(), sender + " you have already requested a beatmap!");
				return;
			}
		}
		Matcher mapU = RegexUtils.matcher("adddt (.+)osu.ppy.sh/b/(\\d+)(.*)", message);
		int id = 0;
		if (mapU.matches()) {
			id = Integer.valueOf(mapU.group(2));
		}
		if (id == 0) {
			m_client.sendMessage(lobby.getChannel(),
					sender + " Incorrect Arguments for !adddt. Please use the beatmap URL. !adddt [url]");
			return;
		}
		try {
			m_bot.getBeatmap(id, lobby, (obj) -> {
				if (obj == null) {
					m_client.sendMessage(lobby.getChannel(), sender + ": Beatmap not found.");
					return;
				}

				String mode = JSONUtils.silentGetString(obj, "mode");
				if (!mode.equals(lobby.getType())) {
					m_client.sendMessage(lobby.getChannel(),
							sender + " That beatmap does not fit the lobby's current gamemode!");
					return;
				}
				Beatmap beatmap = JSONUtils.silentGetBeatmap(obj);
				beatmap.RequestedBy = m_bot.getId(sender);
				beatmap.DT = true;
				try {
					RequestConfig defaultRequestConfig = RequestConfig.custom().setSocketTimeout(10000)
							.setConnectTimeout(10000).setConnectionRequestTimeout(10000).build();

					HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).build();
					URI uri = new URIBuilder().setScheme("http").setHost("osu.ppy.sh")
							.setPath("/osu/" + beatmap.beatmap_id).build();
					HttpGet request = new HttpGet(uri);
					HttpResponse response = httpClient.execute(request);
					InputStream content = response.getEntity().getContent();
					BeatmapParser parser = new BeatmapParser();
					lt.ekgame.beatmap_analyzer.beatmap.Beatmap cbp = parser.parse(content);

					Difficulty diff = cbp.getDifficulty(new Mods(Mod.DOUBLE_TIME));
					beatmap.difficulty = diff.getStars();
					beatmap.difficulty_ar = 4.66666 + 0.6666 * beatmap.difficulty_ar;
					beatmap.difficulty_od = cbp.getDifficultySettings().getOD();
					beatmap.difficulty_hp = cbp.getDifficultySettings().getHP();
					diff = null;
					cbp = null;
					parser = null;
				} catch (IOException | URISyntaxException | BeatmapException e) {
					e.printStackTrace();
					m_client.sendMessage(lobby.getChannel(), "Error Parsing beatmap. Please try again.");
				}

				if (lobby.getOnlyDifficulty()) { // Does the lobby have
					// locked difficulty limits?
					if (!((beatmap.difficulty >= lobby.getMinDifficulty()) && (beatmap.difficulty <= lobby.getMaxDifficulty()))) { // Are
						// we inside the criteria? if not, return
						m_client.sendMessage(lobby.getChannel(),
								sender + " the difficulty of the song you requested does not match the lobby criteria. "
										+ "(Lobby m/M: " + lobby.getMinDifficulty() + "*/" + lobby.getMaxDifficulty() + "*),"
										+ " Song: " + beatmap.difficulty + "*");
						return;
					}
				}
				if(!checkLimitDate(lobby, beatmap, sender))
					return;

				if ((beatmap.total_length / 1.5) >= lobby.getMaxLength()) {
					String length = "";
					int minutes = lobby.getMaxLength() / 60;
					int seconds = lobby.getMaxLength() - (minutes * 60);
					length = minutes + ":" + seconds;
					m_client.sendMessage(lobby.getChannel(), sender + " This beatmap too long! Max length is: " + length);
					return;
				}

				if (!lobby.getStatusTypes().get(beatmap.graveyard)) {
					m_client.sendMessage(lobby.getChannel(),
							sender + " That beatmap is not within ranking criteria for this lobby! (Ranked/loved/etc)");
					return;
				}

				if (lobby.getType().equals("3")) {
					if (lobby.getKeyLimit()) {
						if (beatmap.difficulty_cs != lobby.getKeys()) {
							m_client.sendMessage(lobby.getChannel(),
									sender + " that beatmap does not have the key count this lobby uses. Lobby: "
											+ lobby.getKeys() + "K");
							return;
						}
					}
				}

				if (lobby.getMaxAR() != 0) {
					if (beatmap.difficulty_ar > lobby.getMaxAR()) {

						m_client.sendMessage(lobby.getChannel(),
								sender + " That beatmap has a too high Approach Rate for this lobby! Max: "
										+ lobby.getMaxAR() + " beatmap AR: " + beatmap.difficulty_ar);
						return;
					}
				}

				if (lobby.getOnlyGenre()) {
					if (!beatmap.genre.equalsIgnoreCase(lobby.getGenre())) {
						m_client.sendMessage(lobby.getChannel(), sender + " This lobby is set to only play "
								+ lobby.getGenres()[Integer.valueOf(lobby.getGenre())] + " genre!");
						return;
					}
				}
				m_bot.addBeatmap(lobby, beatmap);
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void handleReady(Lobby lobby, String sender) {
		if (lobby.getPlaying()) {
			m_client.sendMessage(sender, "The lobby is currently playing, you cant vote for starting right now.");
			return;
		}
		if (lobby.getCurrentBeatmap() == null) {
			m_client.sendMessage(sender, "Please add a map before starting playing!");
			return;
		}
		if (lobby.votestarted(sender)) {
			m_client.sendMessage(sender, "You already voted for starting!");
		} else {
			lobby.getVoteStart().add(sender);
			m_client.sendMessage(lobby.getChannel(), sender + " voted for starting! (" + lobby.getVoteStart().size() + "/"
					+ (int) round(lobby.getSlots().size() * 0.75, 0) + ")");
			checkLobbyReady(lobby);
		}
	}

	private void handleSkip(Lobby lobby, String sender) {
		if (lobby.getPlaying()) {
			m_client.sendMessage(sender, "The lobby is currently playing, you cant vote for skipping right now.");
			return;
		}
		if (lobby.votedskip(sender)) {
			m_client.sendMessage(sender, "You already voted for skipping!");
		} else {
			lobby.getVoteskip().add(sender);
			m_client.sendMessage(lobby.getChannel(), sender + " voted for skipping! (" + lobby.getVoteskip().size() + "/"
					+ (int) round(lobby.getSlots().size() * 0.6, 0) + ")");
			if (lobby.getVoteskip().size() >= (int) round(lobby.getSlots().size() * 0.6, 0)) {
				m_client.sendMessage(lobby.getChannel(), "Map has been skipped by vote.");
				m_bot.nextbeatmap(lobby);
			}
		}
	}

	private void handleInfo(Lobby lobby) {
		m_client.sendMessage(lobby.getChannel(), "This is an in-development IRC version of autohost developed by HyPeX. "
				+ "Do !commands to know them ;) " + "[https://discord.gg/UDabf2y Discord] "
				+ "[https://www.reddit.com/r/osugame/comments/67u0k9/autohost_bot_is_finally_ready_for_public_usage/ Reddit Thread]");
	}

	private void handleCommands(Lobby lobby) {
		m_client.sendMessage(lobby.getChannel(),
				"C.List: !add [beatmap] " + "| !ready/!r " + "| !skip/!s " + "| !queue/!playlist " + "| !ver "
						+ "| !last " + "| !maxdiff " + "| !mindiff " + "| !graveyard " + "| !clearhost " + "| !hostme "
						+ "| !favorites/!fav");
	}

	private void handlePlaylist(Lobby lobby) {
		StringBuilder sb = new StringBuilder();
		sb.append("Queue: ").append(lobby.getBeatmapQueue().size());
		for (Beatmap bm : lobby.getBeatmapQueue()) {
			sb.append(" || ").append("[https://osu.ppy.sh/b/").append(bm.beatmap_id).append(" ").append(bm.artist)
					.append(" - ").append(bm.title).append("] [").append(round(bm.difficulty, 2)).append("*]");
		}
		m_client.sendMessage(lobby.getChannel(), sb.toString());
	}

	private void handleSelect(Lobby lobby, String sender, String message) {
		Matcher sm = RegexUtils.matcher("select (.+)", message);
		if (!sm.matches()) {
			m_client.sendMessage(lobby.getChannel(),
					"Incorrect usage, please do !select [number]. " + "Please consider using the number in []");
			return;
		}
		if (lobby.getRequests().containsKey(sender)) {
			int map = Integer.valueOf(sm.group(1));
			// TODO: Fix this ~~amazing~~ line.
			if (!(map > lobby.getRequests().get(sender).bids.size())) {
				m_bot.addBeatmap(lobby,
						lobby.getRequests().get(sender).beatmaps.get(lobby.getRequests().get(sender).bids.get(map)));
			} else {
				m_client.sendMessage(lobby.getChannel(),
						"The options only range from 0-" + (lobby.getRequests().get(sender).bids.size() - 1));
			}
			lobby.getRequests().remove(sender);
		} else {
			m_client.sendMessage(lobby.getChannel(), "You dont have any pending map requests.");
		}
	}

	private void handleMaxDifficulty(Lobby lobby, String sender, String message) {
		if (!lobby.isOP(m_bot.getId(sender)))
			return;

		Matcher diffM = RegexUtils.matcher("maxdiff (\\d+(?:\\.\\d+)?)", message);
		if (diffM.matches()) {
			double maxDiff = Double.valueOf(diffM.group(1));
			if (lobby.getMinDifficulty() != null && maxDiff <= lobby.getMinDifficulty()) {
				m_client.sendMessage(lobby.getChannel(),
						"Max diff must be greater than min diff, which is " + lobby.getMinDifficulty());
			} else {
				lobby.setMaxDifficulty(maxDiff);
 				m_client.sendMessage(lobby.getChannel(), "Max difficulty now is " + diffM.group(1));
			}
		}
	}

	private void handleFreeMods(Lobby lobby, String sender) {
		if (!lobby.isOP(m_bot.getId(sender)))
			return;
		m_client.sendMessage(lobby.getChannel(), "!mp mods Freemod");
	}

	private void handleMode(Lobby lobby, String sender, String message) {
		if (!lobby.isOP(m_bot.getId(sender)))
			return;

		Matcher modeMatch = RegexUtils.matcher("mode (.+)", message);
		if (modeMatch.matches()) {
			if (modeMatch.group(1).equalsIgnoreCase("mania")) {
				lobby.setType("3");
				m_client.sendMessage(lobby.getChannel(), "This lobby is now a mania lobby");
			} else if (modeMatch.group(1).equalsIgnoreCase("std") || modeMatch.group(1).equalsIgnoreCase("standard")) {
				lobby.setType("0");
				m_client.sendMessage(lobby.getChannel(), "This lobby is now a Standard lobby");
			} else if (modeMatch.group(1).equalsIgnoreCase("ctb")) {
				lobby.setType("2");
				m_client.sendMessage(lobby.getChannel(), "This lobby is now a Catch The Beat lobby");
			} else if (modeMatch.group(1).equalsIgnoreCase("taiko")) {
				lobby.setType("1");
				m_client.sendMessage(lobby.getChannel(), "This lobby is now a Taiko lobby");
			}
			if (!lobby.getType().equals("3") && lobby.getKeyLimit()) {
				lobby.setKeyLimit(false);
				m_client.sendMessage(lobby.getChannel(), "Since this is not a mania lobby anymore, disabling Key Limiter");
			}
		}
	}

	private void handleGraveyard(Lobby lobby, String sender) {
		if (!lobby.isOP(m_bot.getId(sender))) {
			m_client.sendMessage(lobby.getChannel(), sender + " You're not an Operator!");
			return;
		}

		if (lobby.getGraveyard()) {
			lobby.setGraveyard(false);
			lobby.setWIP(false);
			lobby.setPending(false);

			lobby.getStatusTypes().put(0, false);
			lobby.getStatusTypes().put(-1, false);
			lobby.getStatusTypes().put(-2, false);
			m_client.sendMessage(lobby.getChannel() , "Graveyard maps are now unallowed.");
		} else {
			lobby.setGraveyard(true);
			lobby.setWIP(true);
			lobby.setPending(true);
			lobby.getStatusTypes().put(0, true);
			lobby.getStatusTypes().put(-1, true);
			lobby.getStatusTypes().put(-2, true);
			m_client.sendMessage(lobby.getChannel(), "Graveyard maps are now allowed.");
		}
	}

	private void handleLock(Lobby lobby, String sender) {
		if (!lobby.isOP(m_bot.getId(sender))) {
			m_client.sendMessage(lobby.getChannel(), sender + ", you're not an Operator!");
			return;
		}

		if (lobby.getLockAdding()) {
			m_client.sendMessage(lobby.getChannel(), "Map requests are now enabled.");
			lobby.setLockAdding(false);
		} else {
			m_client.sendMessage(lobby.getChannel(), "Map requests are now disabled.");
			lobby.setLockAdding(true);
		}
	}

	private void handleVersion(Lobby lobby) {
		m_client.sendMessage(lobby.getChannel(), "Bot version is " + AutoHost.VERSION);
	}

	private void handleWait(Lobby lobby) {
		// TODO: This logic is slightly confusing, specifically the naming of
		// "extendTimer"
		m_client.sendMessage(lobby.getChannel(),
				lobby.getTimer().extendTimer() ? "Timer extended by 1 minute." : "Timer was already extended.");
	}

	private void handleLobby(Lobby lobby, String sender, String message) {
		if (!lobby.isOP(m_bot.getId(sender)))
			return;
		Matcher settingMatcher = RegexUtils.matcher("lobby (.+) (.*)", message);
		if (settingMatcher.matches()) {

		}
	}

	private void handleStart(Lobby lobby, String sender) {
		if (!lobby.isOP(m_bot.getId(sender)))
			return;
		m_bot.tryStart(lobby);
	}

	private void handleLast(Lobby lobby, String sender) {
		m_bot.getLastPlay(lobby, sender);
	}

	private void handleKick(Lobby lobby, String sender, String message) {
		if (!lobby.isOP(m_bot.getId(sender)))
			return;

		Matcher idmatch = RegexUtils.matcher("kick (\\d+)", message);
		Matcher namematch = RegexUtils.matcher("kick (.+)", message);
		if (idmatch.matches()) {
			m_client.sendMessage(lobby.getChannel(), "!mp kick #" + idmatch.group(1));
			return;
		} else if (namematch.matches()) {
			for (int i = 0; i < 16; i++) {
				Slot slot = lobby.getSlots().get(i);
				if (slot != null)
					if (slot.name.toLowerCase().contains(namematch.group(1).toLowerCase())) {
						m_client.sendMessage(lobby.getChannel(), "!mp kick #" + slot.playerid);
						return;
					}
			}
		}
		m_client.sendMessage(lobby.getChannel(), sender + " user not found.");
	}

	private void handleAddOP(Lobby lobby, String sender, String message) {
		if (!lobby.isOP(m_bot.getId(sender)))
			return;

		Matcher diffM = RegexUtils.matcher("addop (\\d+)", message);
		if (diffM.matches()) {
			lobby.getOPs().add(Integer.valueOf(diffM.group(1)));
		}
	}

	private void handleForceSkip(Lobby lobby, String sender) {
		if (!lobby.isOP(m_bot.getId(sender)))
			return;
		m_bot.nextbeatmap(lobby);
	}

	private void handleForceStart(Lobby lobby, String sender) {
		if (!lobby.isOP(m_bot.getId(sender)))
			return;
		m_client.sendMessage(lobby.getChannel(), "!mp start");
	}

	private void handlePassword(Lobby lobby, String sender, String message) {
		if (!lobby.isOP(m_bot.getId(sender)))
			return;

		Matcher pwmatch = RegexUtils.matcher("password (.+)?", message);
		if (pwmatch.matches() && pwmatch.groupCount() == 1) {
			if (pwmatch.group(1).equalsIgnoreCase("reset")) {
				lobby.setPassword("");
				m_client.sendMessage(lobby.getChannel(), "!mp password");
				m_client.sendMessage(lobby.getChannel(), "Password has been cleared!");
			} else {
				lobby.setPassword(pwmatch.group(1));
				m_client.sendMessage(lobby.getChannel(), "!mp password " + lobby.getPassword());
				m_client.sendMessage(lobby.getChannel(), "Password has been set!");
			}
		} else {
			if (lobby.getPassword() == null || lobby.getPassword().equals("")) {
				m_client.sendMessage(lobby.getChannel(), "This lobby has no password.");
			} else {
				m_client.sendMessage(lobby.getChannel(), "Current password is " + lobby.getPassword());
			}
		}
	}

	private void handleMinDifficulty(Lobby lobby, String sender, String message) {
		if (!lobby.isOP(m_bot.getId(sender)))
			return;

		Matcher diffM = RegexUtils.matcher("mindiff (\\d+(?:\\.\\d+)?)", message);
		if (diffM.matches()) {
			double minDiff = Double.valueOf(diffM.group(1));
			if (lobby.getMaxDifficulty() != null && minDiff >= lobby.getMaxDifficulty()) {
				m_client.sendMessage(lobby.getChannel(),
						"Min diff must be less than max diff, which is " + lobby.getMaxDifficulty());
			} else {
				lobby.setMinDifficulty(minDiff);
				m_client.sendMessage(lobby.getChannel(), "New minimum difficulty is " + diffM.group(1));
			}
		}
	}

	private void handleMaxAR(Lobby lobby, String sender, String message) {
		if (!lobby.isOP(m_bot.getId(sender)))
			return;

		Matcher diffM = RegexUtils.matcher("maxar (.+)", message);
		if (diffM.matches()) {
			lobby.setMaxAR(Double.valueOf(diffM.group(1)));
			if (lobby.getMaxAR() == 0.0)
				m_client.sendMessage(lobby.getChannel(), "Approach Rate limit was removed.");
			else
				m_client.sendMessage(lobby.getChannel(), "New maximum approach rate is " + diffM.group(1));
		} else {
			lobby.setMaxAR(0.0);
			m_client.sendMessage(lobby.getChannel(), "Approach Rate limit was removed.");
		}
	}

	private void handleMaxYear(Lobby lobby, String sender, String message) {
		if (!lobby.isOP(m_bot.getId(sender)))
			return;

		Matcher yrM = RegexUtils.matcher("maxyear (.+)", message);
		if (yrM.matches()) {
			if (Integer.valueOf(yrM.group(1)) < lobby.getMinYear()) {
				m_client.sendMessage(lobby.getChannel(),
						"Max year cant be smaller than min year. " + "Please lower that first ;)");
				return;
			}
			lobby.setMaxYear(Integer.valueOf(yrM.group(1)));
			m_client.sendMessage(lobby.getChannel(), "New newer year limit now is " + yrM.group(1));
			if (lobby.getLimitDate()) {

			} else {
				lobby.setLimitDate(true);
				m_client.sendMessage(lobby.getChannel(), "Year Limiter was enabled. For toggling it, do !limityear");
			}
		} else {
			lobby.setMaxYear(2200);
			m_client.sendMessage(lobby.getChannel(), "Newest year limit was removed.");
		}
	}

	private void handleMinYear(Lobby lobby, String sender, String message) {
		if (!lobby.isOP(m_bot.getId(sender)))
			return;

		Matcher yrM = RegexUtils.matcher("minyear (.+)", message);
		if (yrM.matches()) {
			if (Integer.valueOf(yrM.group(1)) > lobby.getMaxYear()) {
				m_client.sendMessage(lobby.getChannel(),
						"Min year cant be bigger than max year. " + "Please increase that first ;)");
				return;
			}
			lobby.setMinYear(Integer.valueOf(yrM.group(1)));
			m_client.sendMessage(lobby.getChannel(), "Oldest year limit now is " + yrM.group(1));
			if (lobby.getLimitDate()) {

			} else {
				lobby.setLimitDate(true);
				m_client.sendMessage(lobby.getChannel(), "Year Limiter was enabled. For toggling it, do !limityear");
			}
		} else {
			lobby.setMinYear(0);
			m_client.sendMessage(lobby.getChannel(), "Oldest year limit was removed");
		}
	}

	private void handleLimitYear(Lobby lobby, String sender) {
		if (!lobby.isOP(m_bot.getId(sender)))
			return;

		lobby.setLimitDate(!lobby.getLimitDate());
		m_client.sendMessage(lobby.getChannel(), "Toggled Date limiting. State: " + lobby.getLimitDate());
	}

	private void handleMinDuration(Lobby lobby, String sender, String message) {
		if (!lobby.isOP(m_bot.getId(sender)))
			return;

		Matcher yrM = RegexUtils.matcher("minduration (.+)", message);
		if (yrM.matches()) {
			if ((Integer.valueOf(yrM.group(1)) >= lobby.getMaxLength()) || (Integer.valueOf(yrM.group(1)) < 0)) {
				m_client.sendMessage(lobby.getChannel(), "Minimum duration cant be bigger or equal than maximum, or smaller than zero!");
				return;
			}
			lobby.setMinLength(Integer.valueOf(yrM.group(1)));
			int minutes = lobby.getMinLength() / 60;
			int seconds = lobby.getMinLength() % 60;
			String str = String.format("%d:%02d", minutes, seconds);
			m_client.sendMessage(lobby.getChannel(), "Minimum duration now is " + str);
		}
	}

	private void handleMaxDuration(Lobby lobby, String sender, String message) {
		if (!lobby.isOP(m_bot.getId(sender)))
			return;

		Matcher yrM = RegexUtils.matcher("maxduration (.+)", message);
		if (yrM.matches()) {
			if (Integer.valueOf(yrM.group(1)) <= lobby.getMinLength()) {
				m_client.sendMessage(lobby.getChannel(), "Maximum duration cant be smaller or equal than minimum!");
				return;
			}
			lobby.setMaxLength(Integer.valueOf(yrM.group(1)));
			int minutes = lobby.getMaxLength() / 60;
			int seconds = lobby.getMaxLength() % 60;
			String str = String.format("%d:%02d", minutes, seconds);
			m_client.sendMessage(lobby.getChannel(), "Maximum duration now is " + str);
		}
	}

	private void handleHostMe(Lobby lobby, String sender) {
		int id = m_bot.getId(sender);
		if (!lobby.isOP(id))
			return;
		m_client.sendMessage(lobby.getChannel(), "!mp host #" + id);
	}

	private void handleClearHost(Lobby lobby, String sender) {
		if (!lobby.isOP(m_bot.getId(sender)))
			return;
		m_client.sendMessage(lobby.getChannel(), "!mp clearhost");
	}

	private void handleRandom(Lobby lobby, String sender) {
		if (!lobby.isOP(m_bot.getId(sender)))
			return;
		lobby.setTrueRandom(!lobby.getTrueRandom());
		m_client.sendMessage(lobby.getChannel(), "Toggled Random Maps. State: " + lobby.getTrueRandom());
	}

	private void handleRename(Lobby lobby, String sender, String message) {
		if (!lobby.isOP(m_bot.getId(sender)))
			return;

		Matcher renameM = RegexUtils.matcher("rename (.+)", message);
		if (renameM.matches()) {
			lobby.setName(renameM.group(1));
		}
	}

	private void handleSay(Lobby lobby, String sender, String message) {
		if (!sender.equalsIgnoreCase("HyPeX")) {
			m_client.sendMessage(lobby.getChannel(), "I'm afraid " + sender + " I can't let you do that.");
			return;
		}
		Matcher sayM = RegexUtils.matcher("say (.+)", message);
		if (sayM.matches()) {
			m_client.sendMessage(lobby.getChannel(), sayM.group(1));
		} else {
			m_client.sendMessage(lobby.getChannel(),
					"Wrong command syntax. Really dude? " + "You made me... and you cant get a fucking command right");
		}
	}

	private void handleCloseRoom(Lobby lobby, String sender) {
		if (!lobby.isOP(m_bot.getId(sender))) {
			m_client.sendMessage(lobby.getChannel(), sender + " You're not an Operator!");
			return;
		}

		m_client.sendMessage(lobby.getChannel(), "!mp close");
		m_bot.removeLobby(lobby);
	}

	private void handleSearchSong(Lobby lobby, String sender, String message) {
		if (lobby.getLockAdding()) {
			m_client.sendMessage(lobby.getChannel(), sender + " sorry, beatmap requesting is currently disabled.");
			return;
		}
		if (!lobby.isOP(m_bot.getId(sender))) {
			if (m_bot.hasAlreadyRequested(lobby, sender)) {
				m_client.sendMessage(lobby.getChannel(), sender + " you have already requested a beatmap!");
				return;
			}
		}
		String mapname = null;
		String author = null;
		if (message.split(" ").length < 2) {
			m_client.sendMessage(lobby.getChannel(), sender + " Please include a name!");
			return;
		}
		Matcher mapsearchM = RegexUtils.matcher("searchsong ([^|]+)(\\ \\|\\ )?([^|]+)", message);
		if (mapsearchM.matches()) {
			mapname = mapsearchM.group(1);
			if (mapsearchM.groupCount() == 3 && mapsearchM.group(2) != null & mapsearchM.group(3) != null) {
				if (!mapsearchM.group(2).equalsIgnoreCase("") & !mapsearchM.group(3).equalsIgnoreCase("")) {
					author = mapsearchM.group(3);
				}
			}
			if (mapname == null) {
				m_client.sendMessage(lobby.getChannel(), sender
						+ " Incorrect Arguments for !searchsong. Please use the beatmap title. !searchsong [title]");
			} else {
				m_bot.searchBeatmap(mapname, lobby, sender, author);
			}
		}
	}
}
