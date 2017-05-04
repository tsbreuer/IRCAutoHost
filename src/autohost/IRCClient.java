package autohost;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import autohost.utils.Beatmap;
import autohost.utils.InputDumper;
import autohost.utils.RateLimiterThread;
import autohost.utils.Request;
import autohost.utils.Slot;
import autohost.utils.TimerThread;
import autohost.utils.beatmapFile;
import lt.ekgame.beatmap_analyzer.calculator.Difficulty;
import lt.ekgame.beatmap_analyzer.calculator.Performance;
import lt.ekgame.beatmap_analyzer.calculator.PerformanceCalculator;
import lt.ekgame.beatmap_analyzer.parser.BeatmapException;
import lt.ekgame.beatmap_analyzer.parser.BeatmapParser;
import lt.ekgame.beatmap_analyzer.utils.Mod;
import lt.ekgame.beatmap_analyzer.utils.Mods;
import lt.ekgame.beatmap_analyzer.utils.ScoreVersion;
//import lt.ekgame.beatmap_analyzer.Beatmap;

public class IRCClient {

	// Every single IRC client i tried fails, so i decided to make my own with
	// blackjack & hookers.
	// Blackjack
	// Hookers
	private static String server;
	private static int port = 6667;
	private static String user;
	private static String password;
	public Socket connectSocket;
	private PrintStream out;
	Map<String, Lobby> Lobbies = new HashMap<>();
	public Queue<Lobby> LobbyCreation = new LinkedList<>();
	public Queue<Lobby> DeadLobbies = new LinkedList<>();
	public List<RateLimiter> limiters = new ArrayList<>();
	// private RateLimiterThread rate;

	public Autohost autohost;
	public InputDumper inputThread; // for debugging
	private RateLimiterThread rate;
	private int RateLimit;
	Config configuration;
	public Map<Integer, String> usernames = new HashMap<>();

	/// This is the reconnection data, just info i store for checking wether bancho went RIP
	public Boolean isReconnecting = false;
	public long LastConnection = System.currentTimeMillis();
	public long LastRequested = System.currentTimeMillis();
	public String LastMessagePING = "";
	
	// Main code
	
	@SuppressWarnings("static-access")
	public IRCClient(Autohost autohost, Config config) throws UnknownHostException, IOException {
		// Define all settings. Meh.
		this.autohost = autohost;
		this.configuration = config;
		this.server = config.server;
		this.port = 6667;
		this.user = config.user;
		this.password = config.password;
		this.RateLimit = config.rate;
		// Mods definition, ignore
		// Connect
		AttemptConnection();
	}

	public IRCClient(Autohost autohost, Config config, Map<String, Lobby> Lobbies, Queue<Lobby> LobbyCreation, Queue<Lobby> DeadLobbies, Map<Integer, String> usernames) throws UnknownHostException, IOException {
		// Define all settings. Meh.
		this.autohost = autohost;
		this.configuration = config;
		this.server = config.server;
		this.port = 6667;
		this.user = config.user;
		this.password = config.password;
		this.RateLimit = config.rate;
		this.isReconnecting = true;
		System.out.println("Reconnect lobbies: "+Lobbies.size());
		this.Lobbies = Lobbies;
		this.DeadLobbies = DeadLobbies;
		this.usernames = usernames;
		this.LobbyCreation = LobbyCreation;
		// Mods definition, ignore
		// Connect
		AttemptConnection();
	}

	public void AttemptConnection() throws UnknownHostException, IOException {
		connect();
		try {
			register();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	public void connect() throws UnknownHostException, IOException {
		// Connect to the server
		connectSocket = new Socket(server, port);
		out = new PrintStream(connectSocket.getOutputStream());

		// for debugging
		inputThread = new InputDumper(connectSocket.getInputStream(), this, this.autohost);

		inputThread.start();
		rate = new RateLimiterThread(this, RateLimit);
		rate.start();
	}

	public void stopIRC(){
		try {
			this.inputThread.stopReading();
			this.connectSocket.close();
			this.out.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public void log(String line) {
		if (line.contains("cho@ppy.sh QUIT :") || (line.contains("PING cho.ppy.sh"))
				|| (line.contains("PONG cho.ppy.sh"))) {
			return;
		}
		
		Pattern endOfMotd = Pattern.compile(":cho.ppy.sh 376 (.+)");
		Matcher endofmotdmatch = endOfMotd.matcher(line);
		if (endofmotdmatch.matches()) {
			System.out.println("End of motd, we're connected.");
			if (this.isReconnecting){
				System.out.println("Lobby is from reconnection.");
				this.isReconnecting = false;
				for (Lobby lobby : Lobbies.values()){
					Lobbies.remove(lobby.channel);
					reconnectLobby(lobby);
				}
			}
		}
		System.out.println(line);
		// :cho.ppy.sh 401 AutoHost #mp_32349656 :No such nick
		Pattern ChannelNo = Pattern.compile(":cho.ppy.sh 401 (.+) #mp_(.+) :No such nick");
		Matcher channelded = ChannelNo.matcher(line);
		if (channelded.matches()) {
			if (Lobbies.containsKey("#mp_" + channelded.group(2))) {
				Lobby lobby = Lobbies.get("#mp_" + channelded.group(2));
				if (lobby.channel.equalsIgnoreCase("#mp_" + channelded.group(2))) {
					lobby.timer.stopTimer();
					removeLobby(lobby);
				}
			}
		}
		Pattern channel = Pattern.compile(":(.+)!cho@ppy.sh PRIVMSG (.+) :(.+)");
		Matcher channelmatch = channel.matcher(line);
		if (channelmatch.find()) {
			// :AutoHost!cho@ppy.sh PRIVMSG #lobby :asd
			String user = channelmatch.group(1);
			String target = channelmatch.group(2);
			String message = channelmatch.group(3);
			if (target.startsWith("#"))
				ChannelMessage(target, user, message);
			else
				PrivateMessage(target, user, message);
		}

		// :HyPeX!cho@ppy.sh JOIN :#mp_29904363
		Pattern pattern = Pattern.compile(":(.+)!cho@ppy.sh JOIN :(.+)");
		Matcher matcher = pattern.matcher(line);
		if (matcher.matches()) {
			if (matcher.group(1).equalsIgnoreCase(user)) {
				String lobbyChannel = matcher.group(2);
				newLobby(lobbyChannel);
			}
		}
	}

	public void createNewLobby(String name, double mindiff, double maxdiff, String creator, Boolean isOP) {
		Lobby lobby = new Lobby();
		lobby.slots.clear();
		lobby.LobbySize = 16;
		lobby.type = "0";
		lobby.status = 1;
		lobby.name = name;
		lobby.maxDifficulty = maxdiff;
		lobby.minDifficulty = mindiff;
		lobby.OPLobby = isOP;
		lobby.creatorName = creator;
		LobbyCreation.add(lobby);
		for (int op : configuration.ops) {
			if (op != getId(creator))
			lobby.OPs.add(op);
		}
		lobby.OPs.add(getId(creator));
		SendMessage("BanchoBot", "!mp make " + name);
	}

	public void reconnectLobby(Lobby lobby) {
		lobby.slots.clear();
		lobby.rejoined = true;
		LobbyCreation.add(lobby);
		Write("JOIN " + lobby.channel);
		SendMessage("" + lobby.channel, "Bot reconnected to this lobby after connection lost");
	}
	
	public void reconnectLobby(String creator, String channel, Boolean isOP) {
		Lobby lobby = new Lobby();
		lobby.slots.clear();
		lobby.LobbySize = 16;
		lobby.type = "0";
		lobby.status = 1;
		lobby.maxDifficulty = (double) 5;
		lobby.minDifficulty = (double) 4;
		lobby.OPLobby = isOP;
		lobby.creatorName = creator;
		lobby.rejoined = true;
		LobbyCreation.add(lobby);
		for (int op : configuration.ops) {
			if (op != getId(creator))
			lobby.OPs.add(op);
		}
		lobby.OPs.add(getId(creator));
		Write("JOIN #mp_" + channel);
		SendMessage("#mp_" + channel, "Bot reconnect requested to this lobby by " + creator);
		SendMessage("#mp_" + channel, creator + " All settings will be set to default, so please re-set them.");
	}

	public void newLobby(String lobbyChannel) {
		Lobby lobby = LobbyCreation.poll();
		if (lobby != null) {
			lobby.channel = lobbyChannel;
			Lobbies.put(lobbyChannel, lobby);
			SendMessage(lobbyChannel, "!mp settings");
			SendMessage(lobbyChannel, "!mp unlock");
			SendMessage(lobbyChannel, "!mp password");
			SendMessage(lobbyChannel, "!mp mods Freemod");
			Boolean inside = false;
			if (lobby.rejoined) {
				for (Slot slot : lobby.slots.values()) {
					if (slot.name.equalsIgnoreCase(lobby.creatorName)) {
						inside = true;
					}
				}
			}
			if (!inside)
				SendMessage(lobbyChannel, "!mp move " + lobby.creatorName);
			lobby.timer = new TimerThread(this, lobby);
			lobby.timer.start();

		} else {
			lobby = new Lobby(lobbyChannel);
			lobby.channel = lobbyChannel;
			Lobbies.put(lobbyChannel, lobby);
			lobby.slots.clear();
			SendMessage(lobbyChannel, "!mp settings");
			SendMessage(lobbyChannel, "!mp unlock");
			SendMessage(lobbyChannel, "!mp password");
			SendMessage(lobbyChannel, "!mp mods Freemod");
			lobby.LobbySize = 16;
			lobby.type = "0";
			lobby.status = 1;
			lobby.maxDifficulty = (double) 5;
			lobby.minDifficulty = (double) 4;
			lobby.timer = new TimerThread(this, lobby);
			lobby.timer.start();
		}
		if (!LobbyCreation.isEmpty()) {
			String name = LobbyCreation.peek().name;
			SendMessage("BanchoBot", "!mp make " + name);
		}
	}

	public void ChannelMessage(String channel, String Sender, String message) {
		if (Sender.equalsIgnoreCase(user)) {
			return;
		}

		Pattern pattern = Pattern.compile("#mp_(\\d+)"); // Is this a multi
															// lobby channel?
		Matcher matcher = pattern.matcher(channel);
		if (matcher.matches()) {
			try {
				if (Lobbies.size() > 0) {
					Boolean channelLoaded = false;
					if (Lobbies.containsKey(channel)) {
						Lobby lobby = Lobbies.get(channel);
						if (lobby.channel.equalsIgnoreCase(channel)) { // Is it
																		// an
																		// autohosted
																		// (by
																		// us)
																		// channel?
							channelLoaded = true;
							try {
								ParseChannelMessage(lobby, Sender, message);
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}
					if (!channelLoaded) {
						System.out.println("Warning: Channel not loaded? C: " + channel);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		// If not a lobby channel, then why the fuck we care?
	}

	public void ParseChannelMessage(Lobby lobby, String Sender, String message)
			throws ClientProtocolException, URISyntaxException, IOException {
		if (Sender.equalsIgnoreCase(user)) {
			return;
		}
		if (Sender.equalsIgnoreCase("BanchoBot")) {

			// Room name and ID, important (?)
			// Room name: test, History: https://osu.ppy.sh/mp/31026456
			// Room name: AutoHost 5-6* || !info || By HyPeX, History:
			// https://osu.ppy.sh/mp/32487590
			Pattern roomName = Pattern.compile("Room name: (.+), History: https://osu.ppy.sh/mp/(.+)");
			Matcher rNM = roomName.matcher(message);

			if (rNM.matches()) {
				String name = rNM.group(1);
				System.out.println("New room name! " + name);
				lobby.name = name;
				lobby.mpID = Integer.valueOf(rNM.group(2));
			}

			// Win condition... meh
			Pattern teamMode = Pattern.compile("Team Mode: (.+), Win condition: (.+)");
			Matcher rTM = teamMode.matcher(message);
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
					// SendMessage(lobby.channel, "Warning: Player count
					// mismatch! Did bot reconnect?");
				}
			}

			Pattern password = Pattern.compile("(.+) the match password");
			Matcher passmatch = password.matcher(message);
			if (passmatch.matches()) {
				if (passmatch.group(1).equals("Enabled")) {
					if (lobby.Password.equalsIgnoreCase("")) {
						SendMessage(lobby.channel, "!mp password");
					}
				} else {
					if (!lobby.Password.equalsIgnoreCase("")) {
						SendMessage(lobby.channel, "!mp password");
					}
				}
			}

			// Slot info on players... generally should be empty on start.. but
			// who knows.
			// :Slot 1 Ready https://osu.ppy.sh/u/711080 HyPeX
			// :Slot 2 Not Ready https://osu.ppy.sh/u/6435456 Saerph
			// :Slot 1 Not Ready https://osu.ppy.sh/u/711080 HyPeX
			Pattern slot = Pattern.compile("Slot (\\d+)(\\s+){1,2}(.+) https://osu.ppy.sh/u/(\\d+) (.+)");
			Matcher sM = slot.matcher(message);
			if (sM.matches()) {
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
				playerId = getId(playerName);
				String status = "Not Ready";
				Slot newSlot = new Slot(jslot, playerName, playerId, status);
				if (lobby.slots.containsKey(jslot)) {
					lobby.slots.replace(jslot, newSlot);
				} else {
					lobby.slots.put(jslot, newSlot);
				}
				lobby.afk.put(playerName, 0);
				for (int ID : lobby.OPs) {
					if (ID == getId(playerName)) {
						SendMessage(lobby.channel, "Operator " + playerName + " has joined. Welcome!");
						SendMessage(lobby.channel, "!mp addref #" + ID);
					}
				}
			}

			Pattern move = Pattern.compile("(.+) moved to slot (\\d+)");
			Matcher moveMatcher = move.matcher(message);
			if (moveMatcher.matches()) {
				int playerId = 0;
				playerId = getId(moveMatcher.group(1));
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
							tryStart(lobby);
						}
					}
				}
				if (lobby.slots.size() == 0) {
					if (!lobby.OPLobby) {
						SendMessage(lobby.channel, "!mp close");
						removeLobby(lobby);
					}
				}
			}

			if (message.equalsIgnoreCase("All players are ready")) {
				SendMessage(lobby.channel, "All players are ready! starting...");
				SendMessage(lobby.channel, "!mp start 5");
				lobby.timer.stopTimer();
			}

			if (message.equalsIgnoreCase("The match has started!")) {
				lobby.scores.clear();
				lobby.Playing = true;
			}

			if (message.equalsIgnoreCase("The match has finished!")) {
				// Chech for player scores -- TODO
				for (Slot player : lobby.slots.values()) {
					if (!lobby.scores.containsKey(player.name)) {
						addAFK(lobby, player.name);
					}
				}
				nextbeatmap(lobby);
				lobby.timer.continueTimer();
				/*
				 * Integer orderedScores[] = new Integer[(lobby.LobbySize - 1)];
				 * orderedScores = orderScores(lobby); for (int i = 0; i < 3;
				 * i++) { String player = lobby.scores.get(orderedScores[i]);
				 * SendMessage(lobby.channel, player + " finished " + (i + 1) +
				 * "!"); }
				 */
			}

			Pattern score = Pattern.compile("(.+) has finished playing \\(Score: (.\\d), (.\\D)\\)");
			Matcher scoreMatcher = score.matcher(message);
			if (scoreMatcher.matches()) {
				if (Integer.valueOf(scoreMatcher.group(2)) == 0) {
					addAFK(lobby, scoreMatcher.group(1));
				} else {
					removeAFK(lobby, scoreMatcher.group(1));
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
		} // End of BanchoBot message filtering

		/*
		 * if (message.toLowerCase().contains("hi")){ SendMessage(lobby.channel,
		 * "Hi "+Sender+"!"); }
		 */
		message = message.trim().toLowerCase();
		// --TODO
		// Player is playing, not AFK.
		removeAFK(lobby, Sender);

		if (message.startsWith("!")) {
			message = message.substring(1);
			String[] args = message.split(" ");
			if (args[0].equals("add")) {
				for (Beatmap beatmap : lobby.beatmapQueue) {
					if (beatmap.RequestedBy == getId(Sender)) {
						SendMessage(lobby.channel, Sender + " you have already requested a beatmap!");
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
					SendMessage(lobby.channel, Sender
							+ " You introduced a beatmap set link, processing beatmaps... (for a direct difficulty add use the /b/ link)");
					int bid = Integer.valueOf(mapUS.group(2));
					askForConfirmation(Sender, bid, lobby);
					return;
				}
				if (id == 0) {
					SendMessage(lobby.channel,
							Sender + " Incorrect Arguments for !add. Please use the beatmap URL. !add [url]");
					return;
				}
				try {
					getBeatmap(id, lobby, (obj) -> {
						if (obj == null) {
							SendMessage(lobby.channel, Sender + ": Beatmap not found.");
							return;
						}

						String mode = obj.getString("mode");
						if (!mode.equals(lobby.type)) {
							SendMessage(lobby.channel,
									Sender + " That beatmap does not fit the lobby's current gamemode!");
							return;
						}
						Beatmap beatmap = new Beatmap(obj);
						beatmap.RequestedBy = getId(Sender);
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
								SendMessage(lobby.channel,
										Sender + " the difficulty of the song you requested does not match the lobby criteria. "
												+ "(Lobby m/M: " + lobby.minDifficulty + "*/" + lobby.maxDifficulty
												+ "*)," + " Song: " + beatmap.difficulty + "*");
								return;
							}
						}
						if (!lobby.statusTypes.get(beatmap.graveyard)) {
							SendMessage(lobby.channel, Sender
									+ " That beatmap is not within ranking criteria for this lobby! (Ranked/loved/etc)");
							return;
						}

						if (lobby.maxAR != 0) {
							if (beatmap.difficulty_ar > lobby.maxAR) {

								SendMessage(lobby.channel,
										Sender + " That beatmap has a too high Approach Rate for this lobby! Max: "
												+ lobby.maxAR + " beatmap AR: " + beatmap.difficulty_ar);
								return;
							}
						}

						if (lobby.onlyGenre) {
							if (!beatmap.genre.equalsIgnoreCase(lobby.genre)) {
								SendMessage(lobby.channel, Sender + " This lobby is set to only play "
										+ lobby.genres[Integer.valueOf(lobby.genre)] + " genre!");
								return;
							}
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
						addBeatmap(lobby, beatmap);

					});
				} catch (Exception e) {
					e.printStackTrace();
				}

			} else if (args[0].equalsIgnoreCase("adddt")) {
				for (Beatmap beatmap : lobby.beatmapQueue) {
					if (beatmap.RequestedBy == getId(Sender)) {
						SendMessage(lobby.channel, Sender + " you have already requested a beatmap!");
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
					SendMessage(lobby.channel,
							Sender + " Incorrect Arguments for !adddt. Please use the beatmap URL. !adddt [url]");
					return;
				}
				try {
					getBeatmap(id, lobby, (obj) -> {
						if (obj == null) {
							SendMessage(lobby.channel, Sender + ": Beatmap not found.");
							return;
						}

						String mode = obj.getString("mode");
						if (!mode.equals(lobby.type)) {
							SendMessage(lobby.channel,
									Sender + " That beatmap does not fit the lobby's current gamemode!");
							return;
						}
						Beatmap beatmap = new Beatmap(obj);
						beatmap.RequestedBy = getId(Sender);
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

						} catch (JSONException | IOException | URISyntaxException | BeatmapException e) {
							e.printStackTrace();
							SendMessage(lobby.channel, "Error Parsing beatmap");
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
								SendMessage(lobby.channel,
										Sender + " the difficulty of the song you requested does not match the lobby criteria. "
												+ "(Lobby m/M: " + lobby.minDifficulty + "*/" + lobby.maxDifficulty
												+ "*)," + " Song: " + beatmap.difficulty + "*");
								return;
							}
						}
						if (!lobby.statusTypes.get(beatmap.graveyard)) {
							SendMessage(lobby.channel, Sender
									+ " That beatmap is not within ranking criteria for this lobby! (Ranked/loved/etc)");
							return;
						}

						if (lobby.maxAR != 0) {
							if (beatmap.difficulty_ar > lobby.maxAR) {

								SendMessage(lobby.channel,
										Sender + " That beatmap has a too high Approach Rate for this lobby! Max: "
												+ lobby.maxAR + " beatmap AR: " + beatmap.difficulty_ar);
								return;
							}
						}

						if (lobby.onlyGenre) {
							if (!beatmap.genre.equalsIgnoreCase(lobby.genre)) {
								SendMessage(lobby.channel, Sender + " This lobby is set to only play "
										+ lobby.genres[Integer.valueOf(lobby.genre)] + " genre!");
								return;
							}
						}
						addBeatmap(lobby, beatmap);

					});
				} catch (Exception e) {
					e.printStackTrace();
				}

			} else if (args[0].equalsIgnoreCase("r") || args[0].equalsIgnoreCase("ready")) {
				if (lobby.Playing) {
					SendMessage(Sender, "The lobby is currently playing, you cant vote for starting right now.");
					return;
				}
				if (lobby.currentBeatmap == 0) {
					SendMessage(Sender, "Please add a map before starting playing!");
					return;
				}
				if (lobby.votestarted(Sender)) {
					SendMessage(Sender, "You already voted for starting!");
				} else {
					lobby.voteStart.add(Sender);
					SendMessage(lobby.channel, Sender + " voted for starting! (" + lobby.voteStart.size() + "/"
							+ (int) round(lobby.slots.size() * 0.75, 0) + ")");
					if (lobby.voteStart.size() >= round(lobby.slots.size() * 0.75, 0)) {
						start(lobby);
					}
				}
			} else if (args[0].equalsIgnoreCase("skip") || args[0].equalsIgnoreCase("s")) {
				if (lobby.Playing) {
					SendMessage(Sender, "The lobby is currently playing, you cant vote for skipping right now.");
					return;
				}
				if (lobby.votedskip(Sender)) {
					SendMessage(Sender, "You already voted for skipping!");
				} else {
					lobby.voteskip.add(Sender);
					SendMessage(lobby.channel, Sender + " voted for skipping! (" + lobby.voteskip.size() + "/"
							+ (int) round(lobby.slots.size() * 0.6, 0) + ")");
					if (lobby.voteskip.size() >= (int) round(lobby.slots.size() * 0.6, 0)) {
						SendMessage(lobby.channel, "Map has been skipped by vote.");
						nextbeatmap(lobby);
					}
				}
			} else if (args[0].equalsIgnoreCase("info")) {
				SendMessage(lobby.channel,
						"This is an in-development IRC version of autohost developed by HyPeX. Do !commands to know them ;) [https://discord.gg/UDabf2y Discord] [Reddit Thread](https://www.reddit.com/r/osugame/comments/67u0k9/autohost_bot_is_finally_ready_for_public_usage/)");

			} else if (args[0].equalsIgnoreCase("commands")) {
				SendMessage(lobby.channel,
						"C.List: !add [beatmap] | !ready/!r | !skip/!s | !queue/!playlist | !ver | !last | !maxdiff | !mindiff | !graveyard | !clearhost | !hostme | !fav/!favorites");
			} else if (args[0].equalsIgnoreCase("playlist") || args[0].equalsIgnoreCase("queue")) {
				String playlist = "Queue: " + lobby.beatmapQueue.size() + " || ";
				for (Beatmap bm : lobby.beatmapQueue) {
					playlist = playlist + "[https://osu.ppy.sh/b/" + bm.beatmap_id + " " + bm.artist + " - " + bm.title
							+ "] [" + round(bm.difficulty, 2) + "*] || ";
				}
				SendMessage(lobby.channel, playlist);
			} else if (args[0].equalsIgnoreCase("select")) {
				Pattern select = Pattern.compile("select (.+)");
				Matcher sm = select.matcher(message);
				if (!sm.matches()) {
					SendMessage(lobby.channel,
							"Incorrect usage, please do !select [number]. Please consider using the number in []");
					return;
				}
				if (lobby.requests.containsKey(Sender)) {
					int map = Integer.valueOf(sm.group(1));
					addBeatmap(lobby,
							lobby.requests.get(Sender).beatmaps.get(lobby.requests.get(Sender).bids.get(map)));
					lobby.requests.remove(Sender);
				} else {
					SendMessage(lobby.channel, "You dont have any pending map requests.");
				}
			} else if (args[0].equalsIgnoreCase("maxdiff")) {
				for (int ID : lobby.OPs) {
					if (ID == (getId(Sender))) {
						Pattern maxdiff = Pattern.compile("maxdiff (\\d+(?:\\.\\d+)?)");
						Matcher diffM = maxdiff.matcher(message);
						if (diffM.matches()) {
							lobby.maxDifficulty = Double.valueOf(diffM.group(1));
							SendMessage(lobby.channel, "Max difficulty now is " + diffM.group(1));
						}

					}
				}
			} else if (args[0].equalsIgnoreCase("freemods")) {
				for (int ID : lobby.OPs) {
					if (ID == (getId(Sender))) {
						SendMessage(lobby.channel, "!mp mods Freemod");
					}
				}
			} else if (args[0].equalsIgnoreCase("mode")) {
				for (int ID : lobby.OPs) {
					if (ID == (getId(Sender))) {
						Pattern mode = Pattern.compile("mode (.+)");
						Matcher modeMatch = mode.matcher(message);
						if (modeMatch.matches()) {
							if (modeMatch.group(1).equalsIgnoreCase("mania")) {
								lobby.type = "3";
								SendMessage(lobby.channel, "This lobby is now a mania lobby");
							} else if (modeMatch.group(1).equalsIgnoreCase("std")
									|| modeMatch.group(1).equalsIgnoreCase("standard")) {
								lobby.type = "0";
								SendMessage(lobby.channel, "This lobby is now a Standard lobby");
							} else if (modeMatch.group(1).equalsIgnoreCase("ctb")) {
								lobby.type = "2";
								SendMessage(lobby.channel, "This lobby is now a Catch The Beat lobby");
							} else if (modeMatch.group(1).equalsIgnoreCase("taiko")) {
								lobby.type = "1";
								SendMessage(lobby.channel, "This lobby is now a Taiko lobby");
							}
						}
					}
				}
			} else if (args[0].equalsIgnoreCase("graveyard")) {
				for (int ID : lobby.OPs) {
					if (ID == (getId(Sender))) {
						if (lobby.graveyard) {
							lobby.graveyard = false;
							lobby.WIP = false;
							lobby.pending = false;
							lobby.statusTypes.put(0, false);
							lobby.statusTypes.put(-1, false);
							lobby.statusTypes.put(-2, false);
							SendMessage(lobby.channel, "Graveyard maps are now unallowed.");
						} else {
							lobby.graveyard = true;
							lobby.WIP = true;
							lobby.pending = true;
							lobby.statusTypes.put(0, true);
							lobby.statusTypes.put(-1, true);
							lobby.statusTypes.put(-2, true);
							SendMessage(lobby.channel, "Graveyard maps are now allowed.");
						}
						return;
					}
				}
				SendMessage(lobby.channel, Sender + " You're not an Operator!");

			} else if (args[0].equalsIgnoreCase("ver")) {
				SendMessage(lobby.channel, "Bot version is 2.8");

			} else if (args[0].equalsIgnoreCase("wait")) {
				Boolean extended = lobby.timer.extendTimer();
				if (extended)
					SendMessage(lobby.channel, "Timer extended by 1 minute.");
				else
					SendMessage(lobby.channel, "Timer was already extended.");
			} else if (args[0].equalsIgnoreCase("lobby")) {
				for (int ID : lobby.OPs) {
					if (ID == (getId(Sender))) {
						Pattern setting = Pattern.compile("lobby (.+) (.*)");
						Matcher settingMatcher = setting.matcher(message);
						if (settingMatcher.matches()) {

						}
					}
				}
			} else if (args[0].equalsIgnoreCase("start")) {
				for (int ID : lobby.OPs) {
					if (ID == (getId(Sender))) {
						tryStart(lobby);
					}
				}
			} else if (args[0].equalsIgnoreCase("last") || args[0].equalsIgnoreCase("l")) {
				getLastPlay(lobby, Sender);
			} else if (args[0].equalsIgnoreCase("kick")) {
				for (int ID : lobby.OPs) {
					if (ID == (getId(Sender))) {
						Pattern id = Pattern.compile("kick (\\d+)");
						Pattern name = Pattern.compile("kick (.+)");
						Matcher idmatch = id.matcher(message);
						Matcher namematch = name.matcher(message);
						if (idmatch.matches()) {
							SendMessage(lobby.channel, "!mp kick #" + idmatch.group(1));
							return;
						} else if (namematch.matches()) {
							for (int i = 0; i < 16; i++) {
								Slot slot = lobby.slots.get(i);
								if (slot != null)
									if (slot.name.toLowerCase().contains(namematch.group(1).toLowerCase())) {
										SendMessage(lobby.channel, "!mp kick #" + slot.id);
										return;
									}
							}
						}

					}
				}
				SendMessage(lobby.channel, Sender + " user not found.");
			} else if (args[0].equalsIgnoreCase("addop")) {
				for (int ID : lobby.OPs) {
					if (ID == (getId(Sender))) {
						Pattern maxdiff = Pattern.compile("addop (\\d+)");
						Matcher diffM = maxdiff.matcher(message);
						if (diffM.matches()) {
							lobby.OPs.add(Integer.valueOf(diffM.group(1)));
						}

					}
				}
			} else if (args[0].equalsIgnoreCase("forceskip")) {
				for (int ID : lobby.OPs) {
					if (ID == (getId(Sender))) {
						nextbeatmap(lobby);
					}
				}
			} else if (args[0].equalsIgnoreCase("forcestart")) {
				for (int ID : lobby.OPs) {
					if (ID == (getId(Sender))) {
						SendMessage(lobby.channel, "!mp start");
					}
				}
			} else if (args[0].equalsIgnoreCase("password")) {
				for (int ID : lobby.OPs) {
					if (ID == (getId(Sender))) {
						Pattern pw = Pattern.compile("password (.+)?");
						Matcher pwmatch = pw.matcher(message);
						if (pwmatch.matches()) {
							if (pwmatch.groupCount() == 1) {
								if (pwmatch.group(1).equalsIgnoreCase("reset")) {
									lobby.Password = "";
								} else {
									lobby.Password = pwmatch.group(1);
								}
								SendMessage(lobby.channel, "!mp password");
							} else {
								SendMessage(lobby.channel, "Current password is " + lobby.Password);
							}
						}
					}
				}
			} else if (args[0].equalsIgnoreCase("mindiff")) {
				for (int ID : lobby.OPs) {
					if (ID == (getId(Sender))) {
						Pattern maxdiff = Pattern.compile("mindiff (.+)");
						Matcher diffM = maxdiff.matcher(message);
						if (diffM.matches()) {
							lobby.minDifficulty = Double.valueOf(diffM.group(1));
							SendMessage(lobby.channel, "New minimum difficulty is " + diffM.group(1));
						}

					}
				}
			} else if (args[0].equalsIgnoreCase("maxar")) {
				for (int ID : lobby.OPs) {
					if (ID == (getId(Sender))) {
						Pattern maxdiff = Pattern.compile("maxar (.+)");
						Matcher diffM = maxdiff.matcher(message);
						if (diffM.matches()) {
							lobby.maxAR = Double.valueOf(diffM.group(1));
							if (lobby.maxAR == 0.0)
								SendMessage(lobby.channel, "Approach Rate limit was removed.");
							else
								SendMessage(lobby.channel, "New maximum approach rate is " + diffM.group(1));
						} else {
							lobby.maxAR = 0.0;
							SendMessage(lobby.channel, "Approach Rate limit was removed.");
						}

					}
				}
			} else if (args[0].equalsIgnoreCase("hostme")) {
				for (int ID : lobby.OPs) {
					if (ID == (getId(Sender))) {
						SendMessage(lobby.channel, "!mp host #" + ID);
					}
				}
			} else if (args[0].equalsIgnoreCase("clearhost")) {
				for (int ID : lobby.OPs) {
					if (ID == (getId(Sender))) {
						SendMessage(lobby.channel, "!mp clearhost");
					}
				}
			} else if (args[0].equalsIgnoreCase("rename")) {
				for (int ID : lobby.OPs) {
					if (ID == (getId(Sender))) {
						Pattern rename = Pattern.compile("rename (.+)");
						Matcher renameM = rename.matcher(message);
						if (renameM.matches()) {
							lobby.name = renameM.group(1);
						}

					}
				}
			} else if (args[0].equalsIgnoreCase("say")) {
				if (!Sender.equalsIgnoreCase("HyPeX")) {
					SendMessage(lobby.channel, "I'm afraid " + Sender + "i cant let you do that.");
					return;
				}
				Pattern say = Pattern.compile("say (.+)");
				Matcher sayM = say.matcher(message);
				if (sayM.matches()) {
					SendMessage(lobby.channel, sayM.group(1));
				} else {
					SendMessage(lobby.channel,
							"Wrong command syntax. Really dude? You made me... and you cant get a fucking command right");
				}

			} else if (args[0].equalsIgnoreCase("closeroom")) {
				for (int ID : lobby.OPs) {
					if (ID == (getId(Sender))) {
						SendMessage(lobby.channel, "!mp close");
						removeLobby(lobby);
						return;
					}
				}
				SendMessage(lobby.channel, Sender + " You're not an Operator!");
			}
		}

	}

	public void addAFK(Lobby lobby, String player) {
		if (lobby.afk.containsKey(player)) {
			lobby.afk.put(player, lobby.afk.get(player) + 1);
			if (lobby.afk.get(player) >= 3) {
				SendMessage(lobby.channel, "!mp kick " + player);
				SendMessage(lobby.channel, player + " was kicked for being AFK for 5 rounds.");
				SendMessage(player, "You were kicked from the lobby for being AFK.");
			}
		} else {
			lobby.afk.put(player, 1);
		}
	}

	public void removeAFK(Lobby lobby, String player) {
		lobby.afk.put(player, 0);
	}

	void removeLobby(Lobby lobby) {
		synchronized (Lobbies) {
			DeadLobbies.add(Lobbies.get(lobby.channel));
			Lobbies.remove(lobby.channel);
			lobby.timer.stopTimer();
		}
	}

	private void addBeatmap(Lobby lobby, Beatmap beatmap) {
		lobby.beatmapQueue.add(beatmap);
		SendMessage(lobby.channel, beatmap.artist + " - " + beatmap.title + "(" + beatmap.difficulty_name + ")" + " ["
				+ round(beatmap.difficulty, 2) + "*] was added to the queue! Pos: " + lobby.beatmapQueue.size());
		if (lobby.currentBeatmap == null || (lobby.currentBeatmap == 0)) {
			nextbeatmap(lobby);
		}
	}

	private void askForConfirmation(String Sender, int beatmapnumber, Lobby lobby) {
		try {
			getBeatmapDiff(beatmapnumber, lobby, (array) -> {
				if (array == null) {
					SendMessage(lobby.channel, Sender + ": Beatmap not found.");
					return;
				}
				Request request = new Request();
				// lobby.requests
				System.out.println("Array has #objects: " + array.length());
				for (int i = 0; i < array.length(); i++) {
					JSONObject obj = array.getJSONObject(i);
					Boolean block = false;
					String mode = obj.getString("mode");
					if (!mode.equals(lobby.type)) {
						SendMessage(lobby.channel, Sender + " That beatmap does not fit the lobby's current gamemode!");
						return;
					}
					Beatmap beatmap = new Beatmap(obj);
					beatmap.RequestedBy = getId(Sender);
					if (lobby.onlyDifficulty) { // Does the lobby have
												// locked difficulty limits?
						if (!(beatmap.difficulty >= lobby.minDifficulty && beatmap.difficulty <= lobby.maxDifficulty)) {
							// SendMessage(lobby.channel, Sender+ " Difficulty
							// [https://osu.ppy.sh/b/"+beatmap.beatmap_id+"
							// "+beatmap.difficulty_name+"] doesnt match the
							// lobby difficulty.");
							block = true;
						}
					}

					if (!lobby.statusTypes.get(beatmap.graveyard)) {
						if (!block) {
							// SendMessage(lobby.channel, Sender+ "That beatmap
							// is not within ranking criteria for this lobby!
							// (Ranked/loved/etc)");
							block = true;
						}
					}

					if (lobby.maxAR != 0 && beatmap.difficulty_ar < lobby.maxAR) {
						SendMessage(lobby.channel,
								Sender + " That beatmap has a too high Approach Rate for this lobby!");
						return;
					}

					if (lobby.onlyGenre) {
						if (!beatmap.genre.equalsIgnoreCase(lobby.genre)) {
							if (!block) {
								// SendMessage(lobby.channel, Sender + "This
								// lobby is set to only play "
								// + lobby.genres[Integer.valueOf(lobby.genre)]
								// + " genre!");
								block = true;
							}
						}
					}
					if (!block) {
						request.beatmaps.put(beatmap.beatmap_id, beatmap);
						request.bids.add(obj.getInt("beatmap_id"));
					}
				}
				if (request.bids.size() == 0) {
					SendMessage(lobby.channel,
							Sender + " This beatmap set doesnt have any difficulty matching the lobby's range!");
				} else if (request.bids.size() == 1) {
					SendMessage(lobby.channel, Sender + " Selecting the only matching difficulty from the linked set");
					addBeatmap(lobby, request.beatmaps.get(request.bids.iterator().next()));
				} else {
					lobby.requests.put(Sender, request);
					SendMessage(lobby.channel,
							Sender + " Please pick one of the following difficulties using !select [number]");
					for (int i = 0; i < request.bids.size(); i++) {

						SendMessage(lobby.channel,
								"[" + i + "] " + "[https://osu.ppy.sh/b/"
										+ request.beatmaps.get(request.bids.get(i)).beatmap_id + " "
										+ request.beatmaps.get(request.bids.get(i)).difficulty_name + "] - "
										+ round(request.beatmaps.get(request.bids.get(i)).difficulty, 2) + "*");
					}
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void getBeatmap(int beatmapId, Lobby lobby, Consumer<JSONObject> callback)
			throws URISyntaxException, ClientProtocolException, IOException {
		RequestConfig defaultRequestConfig = RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(10000)
				.setConnectionRequestTimeout(10000).build();

		HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).build();
		URI uri = new URIBuilder().setScheme("http").setHost("osu.ppy.sh").setPath("/api/get_beatmaps")
				.setParameter("k", configuration.apikey).setParameter("b", "" + beatmapId).setParameter("m", lobby.type)
				.build();
		HttpGet request = new HttpGet(uri);
		HttpResponse response = httpClient.execute(request);
		InputStream content = response.getEntity().getContent();
		String stringContent = IOUtils.toString(content, "UTF-8");
		JSONArray array = new JSONArray(stringContent);
		callback.accept(array.length() > 0 ? (JSONObject) array.get(0) : null);
	}

	public void getBeatmapDiff(int beatmapId, Lobby lobby, Consumer<JSONArray> callback)
			throws URISyntaxException, ClientProtocolException, IOException {
		RequestConfig defaultRequestConfig = RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(10000)
				.setConnectionRequestTimeout(10000).build();

		HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).build();
		URI uri = new URIBuilder().setScheme("http").setHost("osu.ppy.sh").setPath("/api/get_beatmaps")
				.setParameter("k", configuration.apikey).setParameter("s", "" + beatmapId).setParameter("m", lobby.type)
				.build();
		HttpGet request = new HttpGet(uri);
		HttpResponse response = httpClient.execute(request);
		InputStream content = response.getEntity().getContent();
		String stringContent = IOUtils.toString(content, "UTF-8");
		JSONArray array = new JSONArray(stringContent);
		callback.accept(array.length() > 0 ? array : null);
	}

	public Integer[] orderScores(Lobby lobby) {
		Integer score[] = new Integer[(lobby.LobbySize - 1)];
		int i = 0;
		for (int ss : lobby.scores.values()) {
			score[i] = ss;
			i++;
		}

		Comparator<Integer> comp = new Comparator<Integer>() {
			@Override
			public int compare(Integer x, Integer y) {
				return y - x;
			}
		};
		Arrays.sort(score, comp);

		return score;
	}

	public void playerLeft(Lobby lobby) {
		int ready = 0;
		int players = 0;
		for (int i = 0; i < 16; i++) {
			if (lobby.slots.get(i) != null) {
				if (lobby.slots.get(i).playerid != 0) {
					Boolean voted = false;
					for (String string : lobby.voteStart) {
						if (string.equalsIgnoreCase(lobby.slots.get(i).name)) {
							ready++;
							voted = true;
						}
					}
					if (!voted) {
						if (lobby.slots.get(i).status.equalsIgnoreCase("Ready")) {
							ready++;
						}
					}
					players++;
				}
			}
		}
		if (players == 0) {
			lobby.timer.resetTimer();
			return;
		}

		if (ready >= round(players * 0.6, 0)) {
			SendMessage(lobby.channel, ready + "/" + players + " have voted to start the game, starting.");
			start(lobby);
		}
		if (ready < round(players * 0.6, 0)) {
			SendMessage(lobby.channel, ready + "/" + (int) (round(players * 0.75, 0))
					+ " votes to start the game. Please do !ready (or !r) if you're ready.");
		}
		if (players == 0) {
			nextbeatmap(lobby);
		}
	}

	public void tryStart(Lobby lobby) {
		int ready = 0;
		int players = 0;
		for (int i = 0; i < 16; i++) {
			if (lobby.slots.get(i) != null) {
				if (lobby.slots.get(i).playerid != 0) {
					Boolean voted = false;
					for (String string : lobby.voteStart) {
						if (string.equalsIgnoreCase(lobby.slots.get(i).name)) {
							ready++;
							voted = true;
						}
					}
					if (!voted) {
						if (lobby.slots.get(i).status.equalsIgnoreCase("Ready")) {
							ready++;
						}
					}
					players++;
				}
			}
		}
		if (players == 0) {
			lobby.timer.resetTimer();
			return;
		}

		if (ready >= round(players * 0.6, 0)) {
			SendMessage(lobby.channel, ready + "/" + players + " have voted to start the game, starting.");
			start(lobby);
		}
		if (ready < round(players * 0.6, 0)) {
			SendMessage(lobby.channel, ready + "/" + (int) (round(players * 0.75, 0))
					+ " votes to start the game. Please do !ready (or !r) if you're ready.");
		}
		if (players == 0) {
			nextbeatmap(lobby);
		}
		lobby.timer.resetTimer();
	}

	public void start(Lobby lobby) {
		SendMessage(lobby.channel, "!mp start 5");
		lobby.timer.stopTimer();
		lobby.Playing = true;
	}

	public void getLastPlay(Lobby lobby, String user) {
		try {
			RequestConfig defaultRequestConfig = RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(10000)
					.setConnectionRequestTimeout(10000).build();
			HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).build();
			URI uri = new URIBuilder().setScheme("http").setHost("osu.ppy.sh").setPath("/api/get_user_recent")
					.setParameter("k", configuration.apikey).setParameter("u", "" + user).setParameter("type", "string")
					.setParameter("limit", "1").build();
			HttpGet request = new HttpGet(uri);
			HttpResponse response = httpClient.execute(request);
			InputStream content = response.getEntity().getContent();
			String stringContent = IOUtils.toString(content, "UTF-8");
			JSONArray array = new JSONArray(stringContent);
			int totalMaps = lobby.beatmapPlayed.size();
			int lastBeatmap = 0;
			if (lobby.previousBeatmap == null) {
				SendMessage(lobby.channel, user + " No beatmap was played yet!");
				return;
			}
			lastBeatmap = lobby.previousBeatmap;
			Boolean foundMap = false;
			for (int i = 0; i < array.length(); i++) {
				String str = "" + array.get(i);
				JSONObject beatmap = new JSONObject(str);
				int id = beatmap.getInt("beatmap_id");
				if (id == lastBeatmap) {
					int score = beatmap.getInt("score");
					int maxcombo = beatmap.getInt("maxcombo");
					int c50s = beatmap.getInt("count50");
					int c100s = beatmap.getInt("count100");
					int c300s = beatmap.getInt("count300");
					int miss = beatmap.getInt("countmiss");
					int mods = beatmap.getInt("enabled_mods");
					int totalhits = c300s + c100s + c50s + miss;
					double acc = ((c300s * 6 + c100s * 2 + c50s) / ((double) totalhits * 6));
					String rank = beatmap.getString("rank");
					Mods modsFlag = Mods.parse(mods);
					String modsString = modsFlag.toString();
					foundMap = true;
					lt.ekgame.beatmap_analyzer.Beatmap ppcalc = null;
					ppcalc = lobby.beatmaps.get(lastBeatmap).applyMods(modsFlag);
					Performance perf = ppcalc.getPerformance(ppcalc.getMaxCombo(), c100s, c50s, miss);
					double pp = perf.getPerformance();
					if (modsString.equalsIgnoreCase(""))
						modsString = "NOMOD";
					SendMessage(lobby.channel,
							user + " || Rank: " + rank + " || Mods: " + modsString + " || Hits: " + c300s + "/" + c100s
									+ "/" + c50s + "/" + miss + " || Combo: (" + maxcombo + "/" + ppcalc.getMaxCombo()
									+ ") || " + String.format("%.02f", +acc * 100) + "% || PP: "
									+ String.format("%.02f", pp) + " ");

				}
			}

			if (!foundMap) {
				SendMessage(lobby.channel, user + " You didnt play (or pass) last beatmap!");
			}
		} catch (URISyntaxException | IOException e) {
			e.printStackTrace();
		}
	}

	public beatmapFile getPeppyPoints(int beatmapid, Lobby lobby) {
		double[] str = new double[4];
		beatmapFile bm = new beatmapFile(beatmapid);
		try {
			double ssNOMOD = 0;
			double ssHIDDEN = 0;
			double ssHR = 0;
			double ssHDHR = 0;
			RequestConfig defaultRequestConfig = RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(10000)
					.setConnectionRequestTimeout(10000).build();

			HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).build();
			URI uri = new URIBuilder().setScheme("http").setHost("osu.ppy.sh").setPath("/osu/" + beatmapid).build();
			HttpGet request = new HttpGet(uri);
			HttpResponse response = httpClient.execute(request);
			InputStream content = response.getEntity().getContent();
			// String stringContent = IOUtils.toString(content, "UTF-8");
			BeatmapParser parser = new BeatmapParser();
			lt.ekgame.beatmap_analyzer.Beatmap cbp = parser.parse(content);
			lobby.beatmaps.put(beatmapid, cbp);
			lt.ekgame.beatmap_analyzer.Beatmap cbp1 = null;
			lt.ekgame.beatmap_analyzer.Beatmap cbp2 = null;
			lt.ekgame.beatmap_analyzer.Beatmap cbp3 = null;
			lt.ekgame.beatmap_analyzer.Beatmap cbp4 = null;
			if (lobby.DoubleTime || lobby.NightCore) {
				// Arrays.fill(currentBeatmap, cbp);
				cbp1 = cbp.applyMods(new Mods(Mod.DOUBLE_TIME));
				cbp2 = cbp.applyMods(new Mods(Mod.HIDDEN, Mod.DOUBLE_TIME));
				cbp3 = cbp.applyMods(new Mods(Mod.HARDROCK, Mod.DOUBLE_TIME));
				cbp4 = cbp.applyMods(new Mods(Mod.HIDDEN, Mod.HARDROCK, Mod.DOUBLE_TIME));
			}

			if (lobby.HalfTime) {
				// Arrays.fill(currentBeatmap, cbp);
				cbp1 = cbp.applyMods(new Mods(Mod.HALF_TIME));
				cbp2 = cbp.applyMods(new Mods(Mod.HIDDEN, Mod.HALF_TIME));
				cbp3 = cbp.applyMods(new Mods(Mod.HARDROCK, Mod.HALF_TIME));
				cbp4 = cbp.applyMods(new Mods(Mod.HIDDEN, Mod.HARDROCK, Mod.HALF_TIME));
			}

			if (!lobby.HalfTime && !(lobby.DoubleTime || lobby.NightCore)) {
				// Arrays.fill(currentBeatmap, cbp);
				cbp1 = cbp.applyMods(new Mods());
				cbp2 = cbp.applyMods(new Mods(Mod.HIDDEN));
				cbp3 = cbp.applyMods(new Mods(Mod.HARDROCK));
				cbp4 = cbp.applyMods(new Mods(Mod.HIDDEN, Mod.HARDROCK));
			}
			Performance perf = cbp1.getPerformance(cbp.getMaxCombo(), 0, 0, 0);
			ssNOMOD = perf.getPerformance();

			Performance perf2 = cbp2.getPerformance(cbp2.getMaxCombo(), 0, 0, 0);
			ssHIDDEN = perf2.getPerformance();
			Performance perf3 = cbp3.getPerformance(cbp3.getMaxCombo(), 0, 0, 0);
			ssHR = perf3.getPerformance();
			Performance perf4 = cbp4.getPerformance(cbp4.getMaxCombo(), 0, 0, 0);
			ssHDHR = perf4.getPerformance();
			str[0] = ssNOMOD;
			str[1] = ssHIDDEN;
			str[2] = ssHR;
			str[3] = ssHDHR;
		} catch (JSONException | IOException | URISyntaxException | BeatmapException e) {
			e.printStackTrace();
			SendMessage(lobby.channel, "Error Parsing beatmap");
		}
		bm.setpptab(str);
		return bm;
	}

	public void nextbeatmap(Lobby lobby) {
		lobby.voteskip.clear();
		lobby.voteStart.clear();
		lobby.Playing = false;
		Beatmap next = lobby.beatmapQueue.poll();
		if (next == null) {
			next = lobby.beatmapPlayed.poll();
			if (next == null) {
				lobby.currentBeatmap = null;
				SendMessage(lobby.channel, "Played Queue is Empty. Please add some maps ;(");
				return;
			}
		}

		SendMessage(lobby.channel, "!mp map " + next.beatmap_id + " " + lobby.type);
		lobby.previousBeatmap = lobby.currentBeatmap;
		lobby.currentBeatmap = next.beatmap_id;
		lobby.currentBeatmapAuthor = next.artist;
		lobby.currentBeatmapName = next.title;
		lobby.timer.continueTimer();
		if (next.DT) {
			if (!lobby.DoubleTime) {
				SendMessage(lobby.channel, "!mp mods DT Freemod");
				lobby.DoubleTime = true;
			}
		} else if (next.HT) {
			if (!lobby.HalfTime) {
				lobby.HalfTime = true;
				SendMessage(lobby.channel, "!mp mods HT Freemod");
			}
		} else if (next.NC) {
			if (!lobby.NightCore) {
				lobby.NightCore = true;
				SendMessage(lobby.channel, "!mp mods NC Freemod");
			}
		} else {
			if (lobby.DoubleTime || lobby.HalfTime || lobby.NightCore) {
				{
					SendMessage(lobby.channel, "!mp mods Freemod");
					lobby.DoubleTime = false;
					lobby.HalfTime = false;
					lobby.NightCore = false;
				}
			}
		}
		SendMessage(lobby.channel, "Up next: [https://osu.ppy.sh/b/" + next.beatmap_id + " " + next.artist + " - "
				+ next.title + "] [" + round(next.difficulty, 2) + "*]");

		String md = "";

		beatmapFile pplife = getPeppyPoints(next.beatmap_id, lobby);
		if (lobby.DoubleTime)
			md = md + "DT";
		if (lobby.NightCore)
			md = md + "NC";
		if (lobby.HalfTime)
			md = md + "HT";
		if (pplife.ppvalues[0] != 0) {
			SendMessage(lobby.channel,
					md + "SS: " + String.format("%.02f", pplife.ppvalues[0]) + "pp || " + md + "HD: "
							+ String.format("%.02f", pplife.ppvalues[1]) + "pp || " + md + "HR: "
							+ String.format("%.02f", pplife.ppvalues[2]) + "pp || " + md + "HDHR: "
							+ String.format("%.02f", pplife.ppvalues[3]) + "pp");
		}
		lobby.beatmapPlayed.add(next);
	}

	public Boolean isOP(String user) {
		for (int ID : configuration.ops) {
			if (ID == (getId(user))) {
				return true;
			}
		}
		return false;
	}

	public void PrivateMessage(String target, String sender, String message) {
		System.out.println(sender + ": " + message);
		if (sender.equalsIgnoreCase(user))
			return;
		message = message.trim();
		if (message.startsWith("!")) {
			message = message.substring(1);
			String[] args = message.split(" ");
			if (args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("info")) {
				SendMessage(sender, configuration.pmhelp);
				int i = 0;
				for (Lobby lobby : Lobbies.values()) {
					i++;
					String password = "";
					if (lobby.Password.equalsIgnoreCase(""))
						password = "Password: Disabled";
					else
						password = "Password: Enabled";

					SendMessage(sender,
							"Lobby [" + i + "] || Name: " + lobby.name + " || Stars: " + lobby.minDifficulty + "* - "
									+ lobby.maxDifficulty + "* || Slots: [" + lobby.slots.size() + "/16] || "
									+ password);
				}

				return;
			} else if (args[0].equalsIgnoreCase("reloadRooms")) {
				if (!isOP(sender))
					return;
				for (Lobby lobby : Lobbies.values()) {
					lobby.slots.clear();
					SendMessage(lobby.channel, "!mp settings");
					System.out.println("Reloading " + lobby.channel);
				}
			} else if (args[0].equalsIgnoreCase("commands")) {
				SendMessage(sender, "Commands: !createroom [name] | !droplobby | !recreate | !moveme [id/pasword]");

			} else if (args[0].equalsIgnoreCase("globalsay")) {
				for (int ID : configuration.ops) {
					if (ID == (getId(sender))) {
						Pattern globalmsg = Pattern.compile("globalsay (.+)");
						Matcher globalmatch = globalmsg.matcher(message);
						if (globalmatch.matches()) {
							SendMessage(sender, "Message sent");
							for (Lobby lobby : Lobbies.values()) {
								SendMessage(lobby.channel, "GlobalMessage: " + globalmatch.group(1));
							}
						} else {
							SendMessage(sender, "Syntax error. Please use !globalsay [message]");
						}
					}
				}
			} else if (args[0].equalsIgnoreCase("recreate")) {
				for (Lobby lobby : DeadLobbies) {
					if (lobby.creatorName.equalsIgnoreCase(sender)) {
						DeadLobbies.remove(lobby);
						createNewLobby(lobby.name, lobby.minDifficulty, lobby.maxDifficulty, lobby.creatorName,
								lobby.OPLobby);
						SendMessage(sender, "Lobby is being created. Please wait...");
						return;
					}
				}
			} else if (args[0].equalsIgnoreCase("droplobby")) {
				for (Lobby lobby : DeadLobbies) {
					if (lobby.creatorName.equalsIgnoreCase(sender)) {
						DeadLobbies.remove(lobby);
						SendMessage(sender, "Lobby dropped. You're now able to create a new one!");
						return;
					}
				}
			} else if (args[0].equalsIgnoreCase("reconnection")) {
				for (int ID : configuration.ops) {
					if (ID == (getId(sender))) {
						try {
							AttemptConnection();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			} else if (args[0].equalsIgnoreCase("moveme")) {
				if (args.length >= 2) {
					Pattern moveme = Pattern.compile("moveme (\\d+)");
					Matcher matchMove = moveme.matcher(message);
					if (!matchMove.matches()) {
						Pattern movemePass = Pattern.compile("moveme (.+)");
						Matcher matchPW = movemePass.matcher(message);
						if (!matchPW.matches()) {
							SendMessage(sender, "Wrong format, please use !moveme [lobby number provided by help]");
							return;
						} else {
							for (Lobby lobby : Lobbies.values()) {
								if (lobby.Password.equals(matchPW.group(1))) {
									if (lobby.slots.size() < lobby.LobbySize) {
										SendMessage(lobby.channel, "!mp move " + sender);
									} else
										SendMessage(sender, "Lobby is full, try again later ;)");

									return;
								}

							}
							SendMessage(sender, "No lobby matched your password.");
						}
					}
					if (matchMove.matches()) {
						int moveMe = Integer.valueOf(matchMove.group(1));
						int i = 0;
						for (Lobby lobby : Lobbies.values()) {
							i++;
							if (i == moveMe) {
								if (lobby.slots.size() < 16) {
									if (lobby.Password.equals("")) {
										SendMessage(lobby.channel, "!mp move " + sender);
									} else {
										if (matchMove.groupCount() < 2) {
											SendMessage(sender,
													"The lobby you selected has a password. Please use !moveme [lobby] [pw]");
										} else {
											if (matchMove.group(2).equals(lobby.Password)) {
												SendMessage(lobby.channel, "!mp move " + sender);
											}
										}
									}
								} else {
									SendMessage(sender, "Lobby is full, sorry");
								}
							}
						}
						return;
					}
				}
			} else if (args[0].equalsIgnoreCase("createroom")) {
				Boolean isOP = false;
				if (args.length <= 1) {
					SendMessage(sender, "Please include all arguments. Usage: !createroom <name>");
					return;
				}
				for (int ID : configuration.ops) {
					if (ID == (getId(sender))) {
						isOP = true;
						break;
					}
				}
				if (!isOP) {
					for (Lobby lobby : Lobbies.values()) {
						if (lobby.creatorName.equalsIgnoreCase(sender)) {
							SendMessage(sender, "You already have a live lobby!");
							return;
						}
					}
					for (Lobby lobby : DeadLobbies) {
						if (lobby.creatorName.equalsIgnoreCase(sender)) {
							SendMessage(sender,
									"You already have an older lobby, please do !recreate to revive it, or !droplobby to remove it from the list.");
							return;
						}
					}
				}
				Pattern roomNamePattern = Pattern.compile("createroom (.+)");
				Matcher roomNameMatcher = roomNamePattern.matcher(message);
				if (roomNameMatcher.matches()) {
					// --TODO
					String roomName = roomNameMatcher.group(1);
					double mindiff = 4;
					double maxdiff = 5;
					createNewLobby(roomName, mindiff, maxdiff, sender, isOP);
					SendMessage(sender, "Creating room, please wait 1 second and pm me !help to ask for a move");
				} else {
					SendMessage(sender, "Incorrect Syntax. Please use !createroom <name>");
				}
			} else if (args[0].equalsIgnoreCase("reconnect")) {
				if (args.length <= 1) {
					SendMessage(sender, "Please include a lobby id. Usage: !reconnect <mp id>");
					return;
				}
				Boolean isOP = isOP(sender);

				Pattern roomIDPattern = Pattern.compile("reconnect (.+)");
				Matcher roomIDMatcher = roomIDPattern.matcher(message);
				if (roomIDMatcher.matches()) {
					reconnectLobby(sender, roomIDMatcher.group(1), isOP);
				} else {
					SendMessage(sender, "Incorrect Syntax. Please use !createroom <mindiff> <maxdiff>");
				}
			} else
				SendMessage(sender, "Unrecognized Command. Please check !help, or !commands");

		} else {
			if (!sender.equalsIgnoreCase("BanchoBot"))
				SendMessage(sender, "This account is a bot. Command prefix is !. Send me !help for more info.");

		}
	}

	public void register() throws InterruptedException {
		Write("PASS" + " " + password);
		Write("NICK" + " " + user);
		Write("USER" + " " + user + " HyPeX irc.ppy.sh : Osu! Autohost Bot");
	}

	public void SendMessage(String target, String message) {
		Boolean exists = false;
		for (RateLimiter limiter : this.limiters) {
			if (limiter.target.equals(target)) {
				limiter.addMessage(message);
				exists = true;
			}
		}
		if (!exists) {
			// System.out.println("New target. Add.");
			RateLimiter rlimiter = new RateLimiter(target, RateLimit);
			rlimiter.addMessage(message);
			limiters.add(rlimiter);
		}
	}

	public void Write(String message) {
		if (!message.contains("PASS")) {
			System.out.println(message);
		}
		out.println(message);
	}

	public String searchBeatmap(String name, Lobby lobby, String sender) {
		try {
			RequestConfig defaultRequestConfig = RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(10000)
					.setConnectionRequestTimeout(10000).build();
			HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).build();
			String ranked = "Ranked";
			String modes = lobby.type;
			if (lobby.status == 1) {

			}

			URI uri = new URIBuilder().setScheme("http").setHost("osusearch.com").setPath("/query/")
					.setParameter("title", name).setParameter("statuses", "Ranked").setParameter("modes", modes)
					.setParameter("order", "play_count")
					.setParameter("star", "( " + lobby.minDifficulty + "," + lobby.maxDifficulty + ")").build();
			HttpGet request = new HttpGet(uri);
			HttpResponse response = httpClient.execute(request);
			InputStream content = response.getEntity().getContent();
			String stringContent = IOUtils.toString(content, "UTF-8");
			JSONObject obj = new JSONObject(stringContent);
			JSONArray Info = obj.getJSONArray("beatmaps");
			int size = 0;
			for (int i = 0; i < Info.length(); i++) {
				size = size + 1;
			}
			;
			if (size > 1) {
				if (size > 3) {
					SendMessage(lobby.channel, sender + ": " + "Found " + size + " maps, please be more precise!");
				} else if (size < 4) {
					SendMessage(lobby.channel, sender + ": "
							+ "Please retry being more specific from the one of the following maps and use !add:");
					String returnMaps = "";
					for (int i = 0; i < Info.length(); i++) {
						String str = "" + Info.get(i);
						JSONObject beatmap = new JSONObject(str);
						int id = beatmap.getInt("beatmap_id");
						String artist = beatmap.getString("artist");
						String title = beatmap.getString("title");
						String difficulty = beatmap.getString("difficulty_name");
						String result = artist + " - " + title + " (" + difficulty + ")";
						String urllink = "http://osu.ppy.sh/b/" + id;
						returnMaps = returnMaps + " || [" + urllink + " " + result + "]";
					}
					;
					SendMessage(lobby.channel, sender + ": " + returnMaps);
				}
			} else if (size == 0) {
				SendMessage(lobby.channel, sender + ": 0 beatmaps found in current difficulty range!");
			} else if (size == 1) {
				// bot.bancho.sendMessage(sender, "Correct!");
				// int result = Info.getInt(1);
				String str = "" + Info.get(0);
				JSONObject beatmap = new JSONObject(str);
				String artist = beatmap.getString("artist");
				String title = beatmap.getString("title");
				String difficulty = beatmap.getString("difficulty_name");
				String rating = BigDecimal.valueOf(Math.round((beatmap.getDouble("difficulty") * 100d)) / 100d)
						.toPlainString();
				int bID = beatmap.getInt("beatmap_id");
				String result = artist + " - " + title + " [ " + difficulty + " ] - [ " + rating + "* ]";
				String result2 = "[http://osu.ppy.sh/b/" + bID + " Link]";
			}
		} catch (JSONException | URISyntaxException | IOException e) {
			e.printStackTrace();
			SendMessage(sender, sender + ": Error");
		}
		return "";
	}

	public int getId(String name) {
		int id = 0;
		for (Map.Entry<Integer, String> entry : usernames.entrySet()) {
			if (entry.getValue().equals(name)) {
				id = entry.getKey();
			}
		}
		if (id == 0) {
			try {
				RequestConfig defaultRequestConfig = RequestConfig.custom().setSocketTimeout(10000)
						.setConnectTimeout(10000).setConnectionRequestTimeout(10000).build();
				HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).build();
				URI uri = new URIBuilder().setScheme("http").setHost("osu.ppy.sh").setPath("/api/get_user")
						.setParameter("k", configuration.apikey).setParameter("u", "" + name)
						.setParameter("type", "string").build();
				HttpGet request = new HttpGet(uri);
				HttpResponse response = httpClient.execute(request);
				InputStream content = response.getEntity().getContent();
				String stringContent = IOUtils.toString(content, "UTF-8");
				JSONArray array = new JSONArray(stringContent);
				id = array.getJSONObject(0).getInt("user_id");
			} catch (URISyntaxException | IOException e) {
				e.printStackTrace();
			}
		}

		return id;
	}

	public static double round(double value, int places) {
		if (places < 0)
			throw new IllegalArgumentException();

		BigDecimal bd = new BigDecimal(value);
		bd = bd.setScale(places, RoundingMode.HALF_UP);
		return bd.doubleValue();
	}

	public String getUsername(int userId) {
		if (usernames.containsKey(userId) && (!usernames.get(userId).equals("")))
			return usernames.get(userId);

		String username = ""; // get username with api
		try {
			RequestConfig defaultRequestConfig = RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(10000)
					.setConnectionRequestTimeout(10000).build();
			HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).build();
			URI uri = new URIBuilder().setScheme("http").setHost("osu.ppy.sh").setPath("/api/get_user")
					.setParameter("k", configuration.apikey).setParameter("u", "" + userId).setParameter("type", "id")
					.build();
			HttpGet request = new HttpGet(uri);
			HttpResponse response = httpClient.execute(request);
			InputStream content = response.getEntity().getContent();
			String stringContent = IOUtils.toString(content, "UTF-8");
			JSONArray array = new JSONArray(stringContent);
			if (array.length() > 0)
				username = array.getJSONObject(0).getString("username");
		} catch (URISyntaxException | JSONException | IOException e) {
			e.printStackTrace();
		}
		usernames.put(userId, username);
		return username;
	}
}
