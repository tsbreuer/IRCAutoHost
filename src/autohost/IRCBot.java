package autohost;

import autohost.handler.ChannelMessageHandler;
import autohost.handler.PrivateMessageHandler;
import autohost.irc.IRCClient;
import autohost.util.*;
import autohost.util.TimerThread;
import lt.ekgame.beatmap_analyzer.difficulty.Difficulty;
import lt.ekgame.beatmap_analyzer.parser.BeatmapException;
import lt.ekgame.beatmap_analyzer.parser.BeatmapParser;
import lt.ekgame.beatmap_analyzer.performance.Performance;
import lt.ekgame.beatmap_analyzer.performance.scores.Score;
import lt.ekgame.beatmap_analyzer.utils.Mod;
import lt.ekgame.beatmap_analyzer.utils.Mods;
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

import com.google.common.collect.HashBiMap;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static autohost.util.MathUtils.round;
import static autohost.util.TimeUtils.SECOND;

public class IRCBot {
	public PrintWriter m_writer;
	private final IRCClient m_client;
	private Config m_config;
	private boolean m_shouldStop;

	private Map<String, Lobby> m_lobbies = new HashMap<>();
	private Queue<Lobby> m_deadLobbies = new LinkedList<>();
	private Map<String, LobbyChecker> m_permanentLobbies = new HashMap<>();
	// Every single IRC client i tried fails, so i decided to make my own with
	// blackjack & hookers.
	// Blackjack
	// Hookers
	public Queue<Lobby> LobbyCreation = new LinkedList<>();

	public AutoHost autohost;
	public HashBiMap<Integer, String> usernames = HashBiMap.create();

	// This is the reconnection data, just info i store for checking if Bancho
	// went RIP
	private ReconnectTimer m_reconnectTimer;

	// Main code

	@SuppressWarnings("static-access")
	public IRCBot(AutoHost autohost, Config config) throws IOException {
		// Define all settings. Meh.
		this.autohost = autohost;
		try {
			m_writer = new PrintWriter("afklog.txt", "UTF-8");
			m_writer.write("Bot started. This is a error log file");
			m_writer.flush();
		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		m_config = config;
		m_client = new autohost.irc.IRCClient(config.server, 6667, config.user, config.password);
		m_client.setDelay(config.rate);
		// Mods definition, ignore
		// Connect
		connect();
	}

	public IRCBot(AutoHost autohost, Config config, Map<String, Lobby> Lobbies,
			Map<String, LobbyChecker> permanentLobbies, Queue<Lobby> LobbyCreation, Queue<Lobby> deadLobbies,
			HashBiMap<Integer, String> usernames) throws IOException {
		// Define all settings. Meh.
		this.autohost = autohost;
		m_config = config;
		m_client = new autohost.irc.IRCClient(config.server, 6667, config.user, config.password);
		m_client.setDelay(config.rate);
		System.out.println("Reconnect lobbies: " + Lobbies.size());
		this.m_lobbies = Lobbies;
		m_deadLobbies = deadLobbies;
		m_permanentLobbies = permanentLobbies;
		this.usernames = usernames;
		this.LobbyCreation = LobbyCreation;
		// Mods definition, ignore
		// Connect
		connect();
	}

	public IRCClient getClient() {
		return m_client;
	}

	public Config getConfig() {
		return m_config;
	}

	public Map<String, Lobby> getLobbies() {
		return m_lobbies;
	}

	public Map<String, LobbyChecker> getpermanentLobbies() {
		return m_permanentLobbies;
	}

	public Queue<Lobby> getDeadLobbies() {
		return m_deadLobbies;
	}

	public void connect() {
		if (m_reconnectTimer == null) {
			m_reconnectTimer = new ReconnectTimer(this);
		}
		while (true) {
			m_reconnectTimer.messageReceived();
			try {
				System.out.println("Attempting to connect.");
				if (!m_client.isDisconnected()) {
					System.out.println("Disconnecting first though.");
					m_client.disconnect();
				}
				m_client.connect();
				System.out.println("Listening...");
				listen();
				System.out.println("Done listening...");
			} catch (IOException e) {
				e.printStackTrace();
			}
			synchronized (m_client) {
				m_shouldStop = false;
			}
			ThreadUtils.sleepQuietly(SECOND);
		}
	}

	private void listen() throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(m_client.getInputStream()));

		String msg = "";
		boolean safeShouldStop;
		synchronized (m_client) {
			safeShouldStop = m_shouldStop;
		}
		while ((msg = reader.readLine()) != null && !safeShouldStop) {
			long now = System.currentTimeMillis();
			m_reconnectTimer.messageReceived();

			if (!msg.contains("cho@ppy.sh QUIT")) {
				if (msg.contains("001")) {
					System.out.println("Logged in");
					System.out.println("Line: " + msg);
				} else if (msg.startsWith("PING")) {
					String pingResponse = msg.replace("PING", "PONG");
					m_client.write(pingResponse);
				} else if (msg.startsWith("PONG")) {
					System.out.println("Got pong at " + now + ": " + msg);
				} else {
					System.out.println("RECV(" + new Date(now) + "): " + msg);
					log(msg);
					m_writer.println(msg);
					m_writer.flush();
				}
			}
		}
		reader.close();
	}

	public void reconnect() {
		try {
			if (!m_client.isDisconnected()) {
				m_client.disconnect();
			}
		} catch (IOException e) {
			// Do nothing.
		}
		synchronized (m_client) {
			m_shouldStop = true;
		}
	}

	public void log(String line) {
		Pattern endOfMotd = Pattern.compile(":cho.ppy.sh 376 (.+)");
		Matcher endofmotdmatch = endOfMotd.matcher(line);
		try {
			if (endofmotdmatch.matches()) {
				System.out.println("End of motd, we're connected.");
				for (Lobby lobby : m_lobbies.values()) {
					m_lobbies.remove(lobby.channel);
					reconnectLobby(lobby);
				}
			}
		} catch (ConcurrentModificationException e) {
			e.printStackTrace();
		}
		// :cho.ppy.sh 401 AutoHost #mp_32349656 :No such nick
		// :cho.ppy.sh 401 AutoHost #mp_35465451 :No such nick
		Pattern ChannelNo = Pattern.compile(":cho.ppy.sh 401 (.+) #mp_(.+) :No such nick");
		Matcher channelded = ChannelNo.matcher(line);
		if (channelded.matches()) {
			if (m_lobbies.containsKey("#mp_" + channelded.group(2))) {
				Lobby lobby = m_lobbies.get("#mp_" + channelded.group(2));
				if (lobby.channel.equalsIgnoreCase("#mp_" + channelded.group(2))) {
					lobby.timer.stopTimer();
					removeLobby(lobby);
				}
			}
			if (m_permanentLobbies.containsKey("#mp_" + channelded.group(2))) {
				Lobby lobby = m_permanentLobbies.get("#mp_" + channelded.group(2)).lobby;
				m_permanentLobbies.get("#mp_" + channelded.group(2)).stopped = true;
				m_permanentLobbies.get("#mp_" + channelded.group(2)).lobby.timer.stopTimer();
				m_permanentLobbies.remove("#mp_" + channelded.group(2));
				createNewLobby(lobby.name, lobby.minDifficulty, lobby.maxDifficulty, lobby.creatorName, lobby.OPLobby,
						true);
			}
		}
		Pattern channel = Pattern.compile(":(.+)!cho@ppy.sh PRIVMSG (.+) :(.+)");
		Matcher channelmatch = channel.matcher(line);
		if (channelmatch.find()) {
			// :AutoHost!cho@ppy.sh PRIVMSG #lobby :asd
			String user = channelmatch.group(1);
			String target = channelmatch.group(2);
			String message = channelmatch.group(3);
			if (target.startsWith("#")) {
				new ChannelMessageHandler(this).handle(target, user, message);
			} else {
				// if (!user.equalsIgnoreCase("BanchoBot")) {
				// m_client.sendMessage(user, "hi hi o/ " +
				// "|| I'm currently testing AutoHost, I can't see your
				// messages. " +
				// "Send me a message on discord if you want to chat:
				// kieve#7362");
				// }
				new PrivateMessageHandler(this).handle(user, message);
			}
		}

		// :HyPeX!cho@ppy.sh JOIN :#mp_29904363
		Pattern pattern = Pattern.compile(":(.+)!cho@ppy.sh JOIN :(.+)");
		Matcher matcher = pattern.matcher(line);
		if (matcher.matches()) {
			if (matcher.group(1).equalsIgnoreCase(m_client.getUser())) {
				String lobbyChannel = matcher.group(2);
				newLobby(lobbyChannel);
				System.out.println("New lobby: " + lobbyChannel);
			}
		}

		// :AutoHost!cho@ppy.sh PART :#mp_35457515
		Pattern part = Pattern.compile(":(.+)!cho@ppy.sh PART :(.+)");
		Matcher partM = part.matcher(line);
		if (partM.matches()) {
			if (partM.group(1).equalsIgnoreCase(m_client.getUser())) {
				if (m_lobbies.containsKey("#mp_" + partM.group(2))) {
					Lobby lobby = m_lobbies.get("#mp_" + partM.group(2));
					if (lobby.channel.equalsIgnoreCase("#mp_" + partM.group(2))) {
						lobby.timer.stopTimer();
						removeLobby(lobby);
					}
				}
				if (m_permanentLobbies.containsKey("#mp_" + partM.group(2))) {
					Lobby lobby = m_permanentLobbies.get("#mp_" + partM.group(2)).lobby;
					m_permanentLobbies.get("#mp_" + partM.group(2)).stopped = true;
					m_permanentLobbies.remove("#mp_" + partM.group(2));
					createNewLobby(lobby.name, lobby.minDifficulty, lobby.maxDifficulty, lobby.creatorName,
							lobby.OPLobby, true);
				}
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
		lobby.permanent = false;
		lobby.creatorName = creator;
		int creatorID = getId(creator);
		LobbyCreation.add(lobby);
		for (int op : m_config.ops) {
			if (op != creatorID)
				lobby.OPs.add(op);
		}
		lobby.OPs.add(creatorID);
		m_client.sendMessage("BanchoBot", "!mp make " + name);
	}

	public void createNewLobby(String name, double mindiff, double maxdiff, String creator, Boolean isOP,
			Boolean Permanent) {
		Lobby lobby = new Lobby();
		lobby.slots.clear();
		lobby.LobbySize = 16;
		lobby.type = "0";
		lobby.status = 1;
		lobby.name = name;
		lobby.maxDifficulty = maxdiff;
		lobby.minDifficulty = mindiff;
		lobby.OPLobby = isOP;
		lobby.permanent = true;
		lobby.creatorName = creator;
		LobbyCreation.add(lobby);
		int creatorID = getId(creator);
		for (int op : m_config.ops) {
			if (op != creatorID)
				lobby.OPs.add(op);
		}
		lobby.OPs.add(creatorID);
		m_client.sendMessage("BanchoBot", "!mp make " + name);
	}

	public void reconnectLobby(Lobby lobby) {
		lobby.slots.clear();
		lobby.rejoined = true;
		LobbyCreation.add(lobby);
		m_client.write("JOIN " + lobby.channel);
		m_client.sendMessage("" + lobby.channel, "Bot reconnected to this lobby after connection lost");
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
		int creatorID = getId(creator);
		for (int op : m_config.ops) {
			if (op != creatorID)
				lobby.OPs.add(op);
		}
		lobby.OPs.add(creatorID);
		m_client.write("JOIN #mp_" + channel);
		m_client.sendMessage("#mp_" + channel, "Bot reconnect requested to this lobby by " + creator);
		m_client.sendMessage("#mp_" + channel,
				creator + " All settings will be set to default, so please re-set them.");
	}

	public void newLobby(String lobbyChannel) {
		Lobby lobby = LobbyCreation.poll();
		if (lobby != null) {
			System.out.println("LobbyCreationPoll good " + lobbyChannel);
			lobby.channel = lobbyChannel;
			if (lobby.permanent) {
				LobbyChecker checker = new LobbyChecker(this, lobby);
				checker.startTime = System.currentTimeMillis();
				checker.start();
				m_permanentLobbies.put(lobbyChannel, checker);
				System.out.println("Permanent lobby");
			} else {
				m_lobbies.put(lobbyChannel, lobby);
				System.out.println("Common lobby");
			}

			m_client.sendMessage(lobbyChannel, "!mp settings");
			m_client.sendMessage(lobbyChannel, "!mp unlock");
			m_client.sendMessage(lobbyChannel, "!mp password");
			m_client.sendMessage(lobbyChannel, "!mp mods Freemod");
			Boolean inside = false;
			if (lobby.rejoined) {
				for (Slot slot : lobby.slots.values()) {
					if (slot.name.equalsIgnoreCase(lobby.creatorName)) {
						inside = true;
					}
				}
			}
			if (!inside)
				m_client.sendMessage(lobbyChannel, "!mp invite " + lobby.creatorName);
			lobby.timer = new TimerThread(this, lobby);
			lobby.timer.start();
			if (lobby.permanent) {
				nextbeatmap(lobby);
			}

		} else {
			System.out.println("LobbyCreationPoll null");
			lobby = new Lobby(lobbyChannel);
			lobby.channel = lobbyChannel;
			m_lobbies.put(lobbyChannel, lobby);
			lobby.slots.clear();
			m_client.sendMessage(lobbyChannel, "!mp settings");
			m_client.sendMessage(lobbyChannel, "!mp unlock");
			m_client.sendMessage(lobbyChannel, "!mp password");
			m_client.sendMessage(lobbyChannel, "!mp mods Freemod");
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
			m_client.sendMessage("BanchoBot", "!mp make " + name);
		}
	}

	public void addAFK(Lobby lobby, String player) {
		if (lobby.afk.containsKey(player)) {
			lobby.afk.put(player, lobby.afk.get(player) + 1);
			if (lobby.afk.get(player) >= 3) {
				m_client.sendMessage(lobby.channel, "!mp kick " + player);
				m_client.sendMessage(lobby.channel, player + " was kicked for being AFK for 5 rounds.");
				m_client.sendMessage(player, "You were kicked from the lobby for being AFK.");
			}
		} else {
			lobby.afk.put(player, 1);
		}

	}

	public void removeAFK(Lobby lobby, String player) {
		lobby.afk.put(player, 0);
	}

	public void removeLobby(Lobby lobby) {
		synchronized (m_lobbies) {
			if (m_lobbies.containsKey(lobby.channel)) {
				m_deadLobbies.add(m_lobbies.get(lobby.channel));
				m_lobbies.remove(lobby.channel);
				lobby.timer.stopTimer();
			}
		}
		synchronized (m_permanentLobbies) {
			if (m_permanentLobbies.containsKey(lobby.channel)) {
				m_permanentLobbies.get(lobby.channel).stopped = true;
				m_permanentLobbies.remove(lobby.channel);
			}
		}
	}

	public void addBeatmap(Lobby lobby, Beatmap beatmap) {
		lobby.beatmapQueue.add(beatmap);
		m_client.sendMessage(lobby.channel, beatmap.artist + " - " + beatmap.title + "(" + beatmap.difficulty_name + ")"
				+ " [" + round(beatmap.difficulty, 2) + "*] was added to the queue! Pos: " + lobby.beatmapQueue.size());
		if (lobby.currentBeatmap == null) {
			nextbeatmap(lobby);
		}
	}

	public void askForConfirmation(String Sender, int beatmapnumber, Lobby lobby) {
		try {
			getBeatmapDiff(beatmapnumber, lobby, (array) -> {
				if (array == null) {
					m_client.sendMessage(lobby.channel, Sender + ": Beatmap not found.");
					return;
				}
				int senderID = getId(Sender);
				Request request = new Request();
				// lobby.requests
				System.out.println("Array has #objects: " + array.length());
				for (int i = 0; i < array.length(); i++) {
					JSONObject obj = JSONUtils.silentGetArray(array, i);
					Boolean block = false;
					String mode = JSONUtils.silentGetString(obj, "mode");
					if (!mode.equals(lobby.type)) {
						m_client.sendMessage(lobby.channel,
								Sender + " That beatmap does not fit the lobby's current gamemode!");
						return;
					}
					Beatmap beatmap = JSONUtils.silentGetBeatmap(obj);
					beatmap.RequestedBy = senderID;
					if (lobby.onlyDifficulty) { // Does the lobby have
												// locked difficulty limits?
						if (!(beatmap.difficulty >= lobby.minDifficulty && beatmap.difficulty <= lobby.maxDifficulty)) {
							// m_client.sendMessage(lobby.channel, Sender+ "
							// Difficulty
							// [https://osu.ppy.sh/b/"+beatmap.beatmap_id+"
							// "+beatmap.difficulty_name+"] doesnt match the
							// lobby difficulty.");
							block = true;
						}
					}

					if (!lobby.statusTypes.get(beatmap.graveyard)) {
						if (!block) {
							// m_client.sendMessage(lobby.channel, Sender+ "That
							// beatmap
							// is not within ranking criteria for this lobby!
							// (Ranked/loved/etc)");
							block = true;
						}
					}

					if (lobby.maxAR != 0 && beatmap.difficulty_ar < lobby.maxAR) {
						m_client.sendMessage(lobby.channel,
								Sender + " That beatmap has a too high Approach Rate for this lobby!");
						return;
					}

					if ((beatmap.total_length) >= lobby.maxLength) {
						String length = "";
						int minutes = lobby.maxLength / 60;
						int seconds = lobby.maxLength - (minutes * 60);
						length = minutes + ":" + seconds;
						m_client.sendMessage(lobby.channel,
								Sender + " This beatmap too long! Max length is: " + length);
						return;
					}

					if (!lobby.statusTypes.get(beatmap.graveyard)) {
						m_client.sendMessage(lobby.channel, Sender
								+ " That beatmap is not within ranking criteria for this lobby! (Ranked/loved/etc)");
						return;
					}

					if (lobby.onlyGenre) {
						if (!beatmap.genre.equalsIgnoreCase(lobby.genre)) {
							if (!block) {
								// m_client.sendMessage(lobby.channel, Sender +
								// "This
								// lobby is set to only play "
								// + lobby.genres[Integer.valueOf(lobby.genre)]
								// + " genre!");
								block = true;
							}
						}
					}
					if (!block) {
						request.beatmaps.put(beatmap.beatmap_id, beatmap);
						request.bids.add(JSONUtils.silentGetInt(obj, "beatmap_id"));
					}
				}
				if (request.bids.size() == 0) {
					m_client.sendMessage(lobby.channel,
							Sender + " This beatmap set doesnt have any difficulty matching the lobby's range!");
				} else if (request.bids.size() == 1) {
					m_client.sendMessage(lobby.channel,
							Sender + " Selecting the only matching difficulty from the linked set");
					addBeatmap(lobby, request.beatmaps.get(request.bids.iterator().next()));
				} else {
					lobby.requests.put(Sender, request);
					m_client.sendMessage(lobby.channel,
							Sender + " Please pick one of the following difficulties using !select [number]");
					for (int i = 0; i < request.bids.size(); i++) {

						m_client.sendMessage(lobby.channel,
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
			throws URISyntaxException, ClientProtocolException, IOException, JSONException {
		RequestConfig defaultRequestConfig = RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(10000)
				.setConnectionRequestTimeout(10000).build();

		HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).build();
		URI uri = new URIBuilder().setScheme("http").setHost("osu.ppy.sh").setPath("/api/get_beatmaps")
				.setParameter("k", m_config.apikey).setParameter("b", "" + beatmapId).setParameter("m", lobby.type)
				.build();
		HttpGet request = new HttpGet(uri);
		HttpResponse response = httpClient.execute(request);
		InputStream content = response.getEntity().getContent();
		String stringContent = IOUtils.toString(content, "UTF-8");
		JSONArray array = new JSONArray(stringContent);
		callback.accept(array.length() > 0 ? (JSONObject) array.get(0) : null);
	}

	public void getBeatmapDiff(int beatmapId, Lobby lobby, Consumer<JSONArray> callback)
			throws URISyntaxException, ClientProtocolException, IOException, JSONException {
		RequestConfig defaultRequestConfig = RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(10000)
				.setConnectionRequestTimeout(10000).build();

		HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).build();
		URI uri = new URIBuilder().setScheme("http").setHost("osu.ppy.sh").setPath("/api/get_beatmaps")
				.setParameter("k", m_config.apikey).setParameter("s", "" + beatmapId).setParameter("m", lobby.type)
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

		Comparator<Integer> comp = (x, y) -> y - x;
		Arrays.sort(score, comp);

		return score;
	}

	/*
	 * public void playerLeft(Lobby lobby) { int ready = 0; int players = 0; for
	 * (int i = 0; i < 16; i++) { if (lobby.slots.get(i) != null) { if
	 * (lobby.slots.get(i).playerid != 0) { Boolean voted = false; for (String
	 * string : lobby.voteStart) { if
	 * (string.equalsIgnoreCase(lobby.slots.get(i).name)) { ready++; voted =
	 * true; } } if (!voted) { if
	 * (lobby.slots.get(i).status.equalsIgnoreCase("Ready")) { ready++; } }
	 * players++; } } } if (players == 0) { lobby.timer.resetTimer(); return; }
	 *
	 * if (ready >= round(players * 0.6, 0)) {
	 * m_client.sendMessage(lobby.channel, ready + "/" + players +
	 * " have voted to start the game, starting."); start(lobby); } if (ready <
	 * round(players * 0.6, 0)) { m_client.sendMessage(lobby.channel, ready +
	 * "/" + (int) (round(players * 0.75, 0)) +
	 * " votes to start the game. Please do !ready (or !r) if you're ready."); }
	 * if (players == 0) { nextbeatmap(lobby); } }
	 */

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
			nextbeatmap(lobby);
			return;
		}

		if (ready >= round(players * 0.6, 0)) {
			m_client.sendMessage(lobby.channel, ready + "/" + players + " have voted to start the game, starting.");
			start(lobby);
		}
		if (ready < round(players * 0.6, 0)) {
			m_client.sendMessage(lobby.channel, ready + "/" + (int) (round(players * 0.75, 0))
					+ " votes to start the game. Please do !ready (or !r) if you're ready.");
		}
		if (players == 0) {
			nextbeatmap(lobby);
		}
		lobby.timer.resetTimer();
	}

	public void start(Lobby lobby) {
		m_client.sendMessage(lobby.channel, "!mp start 5");
		lobby.timer.stopTimer();
		lobby.Playing = true;
	}

	public void getLastPlay(Lobby lobby, String user) {
		try {
			RequestConfig defaultRequestConfig = RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(10000)
					.setConnectionRequestTimeout(10000).build();
			HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).build();
			URI uri = new URIBuilder().setScheme("http").setHost("osu.ppy.sh").setPath("/api/get_user_recent")
					.setParameter("k", m_config.apikey).setParameter("u", "" + user).setParameter("type", "string")
					.setParameter("limit", "1").build();
			HttpGet request = new HttpGet(uri);
			HttpResponse response = httpClient.execute(request);
			InputStream content = response.getEntity().getContent();
			String stringContent = IOUtils.toString(content, "UTF-8");
			JSONArray array = new JSONArray(stringContent);
			int totalMaps = lobby.beatmapPlayed.size();
			int lastBeatmap = 0;
			if (lobby.previousBeatmap == null) {
				m_client.sendMessage(lobby.channel, user + " No beatmap was played yet!");
				return;
			}
			lastBeatmap = lobby.previousBeatmap.beatmap_id;
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
					lt.ekgame.beatmap_analyzer.beatmap.Beatmap ppcalc = null;
					try {
						RequestConfig Requestconfig = RequestConfig.custom().setSocketTimeout(10 * (int) SECOND)
								.setConnectTimeout(10 * (int) SECOND).setConnectionRequestTimeout(10 * (int) SECOND)
								.build();
						HttpClient httpC = HttpClients.custom().setDefaultRequestConfig(Requestconfig).build();
						URI uriB = new URIBuilder().setScheme("http").setHost("osu.ppy.sh").setPath("/osu/" + id)
								.build();
						HttpGet requestGet = new HttpGet(uriB);
						HttpResponse resp = httpC.execute(requestGet);
						InputStream input = resp.getEntity().getContent();
						BeatmapParser parser = new BeatmapParser();
						ppcalc = parser.parse(input);
						if (ppcalc == null) {
							m_client.sendMessage(lobby.channel, "Beatmap " + id + " is no longer available.");
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
					Difficulty diff = ppcalc.getDifficulty(modsFlag);
					Score ScoreP = Score.of(ppcalc).combo(maxcombo).accuracy(acc, miss).build();
					Performance perf = diff.getPerformance(ScoreP);
					double pp = perf.getPerformance();
					if (modsString.equalsIgnoreCase(""))
						modsString = "NOMOD";
					m_client.sendMessage(lobby.channel,
							user + " || Rank: " + rank + " || Mods: " + modsString + " || Hits: " + c300s + "/" + c100s
									+ "/" + c50s + "/" + miss + " || Combo: (" + maxcombo + "/" + ppcalc.getMaxCombo()
									+ ") || " + String.format("%.02f", +acc * 100) + "% || PP: "
									+ String.format("%.02f", pp) + " ");
				}
			}

			if (!foundMap) {
				m_client.sendMessage(lobby.channel, user + " You didnt play (or pass) last beatmap!");
			}
		} catch (URISyntaxException | IOException | JSONException e) {
			e.printStackTrace();
		}
	}

	public beatmapFile getPeppyPoints(int beatmapid, Lobby lobby) {
		if (lobby.type.equals("2"))
			return null;

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
			lt.ekgame.beatmap_analyzer.beatmap.Beatmap cbp = parser.parse(content);
			if (cbp == null) {
				m_client.sendMessage(lobby.channel, "Beatmap " + beatmapid + " is no longer available.");
			}
			Score ss = Score.of(cbp).combo(cbp.getMaxCombo()).build();
			lobby.beatmaps.put(beatmapid, cbp);
			Difficulty cbp1 = null;
			Difficulty cbp2 = null;
			Difficulty cbp3 = null;
			Difficulty cbp4 = null;
			if (lobby.DoubleTime || lobby.NightCore) {
				// Arrays.fill(currentBeatmap, cbp);
				cbp1 = cbp.getDifficulty(new Mods(Mod.DOUBLE_TIME));
				cbp2 = cbp.getDifficulty(new Mods(Mod.HIDDEN, Mod.DOUBLE_TIME));
				cbp3 = cbp.getDifficulty(new Mods(Mod.HARDROCK, Mod.DOUBLE_TIME));
				cbp4 = cbp.getDifficulty(new Mods(Mod.HIDDEN, Mod.HARDROCK, Mod.DOUBLE_TIME));
			}

			if (lobby.HalfTime) {
				// Arrays.fill(currentBeatmap, cbp);
				cbp1 = cbp.getDifficulty(new Mods(Mod.HALF_TIME));
				cbp2 = cbp.getDifficulty(new Mods(Mod.HIDDEN, Mod.HALF_TIME));
				cbp3 = cbp.getDifficulty(new Mods(Mod.HARDROCK, Mod.HALF_TIME));
				cbp4 = cbp.getDifficulty(new Mods(Mod.HIDDEN, Mod.HARDROCK, Mod.HALF_TIME));
			}

			if (!lobby.HalfTime && !(lobby.DoubleTime || lobby.NightCore)) {
				// Arrays.fill(currentBeatmap, cbp);
				cbp1 = cbp.getDifficulty(new Mods());
				cbp2 = cbp.getDifficulty(new Mods(Mod.HIDDEN));
				cbp3 = cbp.getDifficulty(new Mods(Mod.HARDROCK));
				cbp4 = cbp.getDifficulty(new Mods(Mod.HIDDEN, Mod.HARDROCK));
			}
			Performance perf = cbp1.getPerformance(ss);
			ssNOMOD = perf.getPerformance();

			Performance perf2 = cbp2.getPerformance(ss);
			ssHIDDEN = perf2.getPerformance();
			Performance perf3 = cbp3.getPerformance(ss);
			ssHR = perf3.getPerformance();
			Performance perf4 = cbp4.getPerformance(ss);
			ssHDHR = perf4.getPerformance();
			str[0] = ssNOMOD;
			str[1] = ssHIDDEN;
			str[2] = ssHR;
			str[3] = ssHDHR;
		} catch (IOException | URISyntaxException | BeatmapException e) {
			e.printStackTrace();
			m_client.sendMessage(lobby.channel, "Error Parsing beatmap");
			return null;
		}
		bm.setpptab(str);
		return bm;
	}

	public void getRandomBeatmap(Lobby lobby) {
		Beatmap returnBeatmap = new Beatmap();
		try {
			getRandomWithinSettings(lobby, (obj) -> {
				if (obj == null) {
					m_client.sendMessage(lobby.channel, "An error ocurred while searching for a random beatmap.");
					m_client.sendMessage(lobby.channel, "Maybe no matches for current lobby settings? Retrying");
					nextbeatmap(lobby);
					return;
				}

				String mode = "" + JSONUtils.silentGetInt(obj, "gamemode");
				if (!mode.equals(lobby.type)) {
					m_client.sendMessage(lobby.channel, "ERORR: The random beatmap did not fit this lobby's gamemode!");
					return;
				}
				Beatmap beatmap = JSONUtils.silentGetBeatmap(obj, true);
				beatmapFile bm = getPeppyPoints(beatmap.beatmap_id, lobby);
				if (bm == null) {
					if (!lobby.type.equals("2")) {
						m_client.sendMessage(lobby.channel, "An error ocurred while loading the random beatmap.");
						m_client.sendMessage(lobby.channel, "Maybe it doesnt exist anymore? Retrying");
						nextbeatmap(lobby);
						return;
					}
				}
				if (lobby.onlyDifficulty) { // Does the lobby have
											// locked difficulty limits?
					if (!(beatmap.difficulty >= lobby.minDifficulty && beatmap.difficulty <= lobby.maxDifficulty)) { // Are
																														// we
																														// inside
																														// the
																														// criteria?
																														// if
																														// not,
																														// return
						m_client.sendMessage(lobby.channel,
								"ERROR: The difficulty of the random beatmap found does not match the lobby criteria."
										+ "(Lobby m/M: " + lobby.minDifficulty + "*/" + lobby.maxDifficulty + "*),"
										+ " Song: " + beatmap.difficulty + "*");
						return;
					}
				}
				if (!lobby.statusTypes.get(beatmap.graveyard)) {
					m_client.sendMessage(lobby.channel,
							"ERROR: The random beatmap is not within ranking criteria for this lobby! (Ranked/loved/etc)");
					return;
				}

				if (lobby.maxAR != 0) {
					if (beatmap.difficulty_ar > lobby.maxAR) {

						m_client.sendMessage(lobby.channel,
								"ERROR: The random beatmap has a too high Approach Rate for this lobby! Max: "
										+ lobby.maxAR + " beatmap AR: " + beatmap.difficulty_ar);
						return;
					}
				}

				if (lobby.onlyGenre) {
					if (!beatmap.genre.equalsIgnoreCase(lobby.genre)) {
						m_client.sendMessage(lobby.channel,
								"ERROR: Beatmap genre is incorrect. This lobby is set to only play "
										+ lobby.genres[Integer.valueOf(lobby.genre)] + " genre!");
						return;
					}
				}

				changeBeatmap(lobby, beatmap);
			});
		} catch (Exception e) {
			e.printStackTrace();
			nextbeatmap(lobby);
		}
	}

	public void getRandomWithinSettings(Lobby lobby, Consumer<JSONObject> callback)
			throws URISyntaxException, ClientProtocolException, IOException, JSONException {
		RequestConfig defaultRequestConfig = RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(10000)
				.setConnectionRequestTimeout(10000).build();

		HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).build();
		// http://osusearch.com/search/?genres=Anime
		// &languages=Japanese&statuses=Ranked
		// &modes=Standard&star=(3.60,6.40)&min_length=30
		// &max_length=300&query_order=play_count

		// http://osusearch.com/query/?genres=Anime&languages=Japanese&statuses=Ranked&modes=Standard&star=(4.1,4.3)&min_length=30&max_length=300&query_order=play_count
		String status = "Ranked";
		if (!lobby.statusTypes.get(-2)) {
			status = "Ranked";
		} else {
			status = "Ranked,Qualified,Unranked";
		}
		String mode = "Standard";
		String maxAR = "12";
		// 0 = osu!, 1 = Taiko, 2 = CtB, 3 = osu!mania
		if (lobby.type.equals("1")) {
			mode = "Taiko";
		} else if (lobby.type.equals("0")) {
			mode = "Standard";
		} else if (lobby.type.equals("2")) {
			mode = "CtB";
		} else if (lobby.type.equals("3")) {
			mode = "Mania";
		}
		if (lobby.maxAR > 0.0) {
			maxAR = "" + lobby.maxAR;
		}
		String date_start = "2000-1-1";
		String date_end = "2020-1-1";
		if (lobby.limitDate) {
			date_start = lobby.minyear + "-1-1";
			date_end = lobby.maxyear + "-1-1";
		}
		String maxcs = "11";
		String mincs = "0";
		if (lobby.type.equals("3")) {
			if (lobby.keyLimit) {
				maxcs = "" + lobby.keys;
				mincs = "" + lobby.keys;
			}
		}
		URI uri = new URIBuilder().setScheme("http").setHost("osusearch.com").setPath("/random/")
				.setParameter("statuses", status).setParameter("modes", mode).setParameter("order", "-difficulty")
				.setParameter("max_length", "300")
				.setParameter("star", "( " + lobby.minDifficulty + "," + lobby.maxDifficulty + ")")
				.setParameter("date_start", date_start).setParameter("date_end", date_end).setParameter("ammount", "5")
				.setParameter("ar", "( 0," + maxAR + ")").setParameter("cs", "(" + mincs + "," + maxcs + ")").build();
		HttpGet request = new HttpGet(uri);
		request.setHeader("Accept", "json");
		System.out.println(uri.toString());
		HttpResponse response = httpClient.execute(request);
		InputStream content = response.getEntity().getContent();
		String stringContent = IOUtils.toString(content, "UTF-8");
		JSONObject obj = new JSONObject(stringContent);
		JSONArray array = obj.getJSONArray("beatmaps");
		Random randomNumber = new Random();
		int pick;
		if (array.length() > 1) {
			pick = randomNumber.nextInt(array.length());
		} else if (array.length() == 1) {
			pick = 1;
		} else {
			m_client.sendMessage(lobby.channel, "Random returned 0 results. Fucked up?");
			pick = 1;
		}
		callback.accept(array.length() > 0 ? (JSONObject) array.get(pick) : null);
	}

	public String searchBeatmap(String name, Lobby lobby, String sender, String author) {
		try {
			RequestConfig defaultRequestConfig = RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(10000)
					.setConnectionRequestTimeout(10000).build();
			HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).build();
			String status = "Ranked";
			if (!lobby.statusTypes.get(-2)) {
				status = "Ranked";
			} else {
				status = "Ranked,Qualified,Unranked";
			}
			String mode = "Standard";
			String maxAR = "12";
			// 0 = osu!, 1 = Taiko, 2 = CtB, 3 = osu!mania
			if (lobby.type.equals("1")) {
				mode = "Taiko";
			} else if (lobby.type.equals("0")) {
				mode = "Standard";
			} else if (lobby.type.equals("2")) {
				mode = "CtB";
			} else if (lobby.type.equals("3")) {
				mode = "Mania";
			}
			if (lobby.maxAR > 0.0) {
				maxAR = "" + lobby.maxAR;
			}
			String date_start = "2000-1-1";
			String date_end = "2020-1-1";
			URIBuilder uriBuilder = new URIBuilder().setScheme("http").setHost("osusearch.com").setPath("/query/")
					.setParameter("statuses", status).setParameter("modes", mode).setParameter("order", "-difficulty")
					.setParameter("max_length", "" + lobby.maxLength).setParameter("title", name)
					.setParameter("star", "( " + lobby.minDifficulty + "," + lobby.maxDifficulty + ")")
					.setParameter("date_start", date_start).setParameter("date_end", date_end)
					.setParameter("ar", "( 0," + maxAR + ")");
			if (author != null) {
				uriBuilder.setParameter("artist", author);
			}
			URI uri = uriBuilder.build();
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
				if (size > 5) {
					m_client.sendMessage(lobby.channel,
							sender + ": " + "Found " + size + " maps, please be more precise!");
				} else if (size < 6) {
					String returnMaps = "";
					Request askForWhich = new Request();
					for (int i = 0; i < Info.length(); i++) {
						String str = "" + Info.get(i);
						JSONObject beatmap = new JSONObject(str);
						Beatmap beatmapObj = new Beatmap(beatmap, true);
						int id = beatmap.getInt("beatmap_id");
						String artist = beatmap.getString("artist");
						String title = beatmap.getString("title");
						String difficulty = beatmap.getString("difficulty_name");
						String result = artist + " - " + title + " (" + difficulty + ")";
						String urllink = "http://osu.ppy.sh/b/" + id;
						returnMaps = returnMaps + " || [" + urllink + " " + result + "]"; // returnmaps
																							// is
																							// dead
																							// old
																							// code
						askForWhich.beatmaps.put(beatmapObj.beatmap_id, beatmapObj);
						askForWhich.bids.add(beatmapObj.beatmap_id);
					}
					if (askForWhich.bids.size() == 0) {
						m_client.sendMessage(lobby.channel,
								sender + " This beatmap set doesnt have any difficulty matching the lobby's range!");
					} else if (askForWhich.bids.size() == 1) {
						m_client.sendMessage(lobby.channel,
								sender + " Selecting the only matching difficulty from the linked set");
						addBeatmap(lobby, askForWhich.beatmaps.get(askForWhich.bids.iterator().next()));
					} else {
						lobby.requests.put(sender, askForWhich);
						m_client.sendMessage(lobby.channel,
								sender + " Please pick one of the following difficulties using !select [number]");
						for (int a = 0; a < askForWhich.bids.size(); a++) {
							m_client.sendMessage(lobby.channel, "[" + a + "] || "
									+ askForWhich.beatmaps.get(askForWhich.bids.get(a)).artist + " - "
									+ askForWhich.beatmaps.get(askForWhich.bids.get(a)).title + " "
									+ "[[https://osu.ppy.sh/b/"
									+ askForWhich.beatmaps.get(askForWhich.bids.get(a)).beatmap_id + ""
									+ askForWhich.beatmaps.get(askForWhich.bids.get(a)).difficulty_name + "]] - "
									+ round(askForWhich.beatmaps.get(askForWhich.bids.get(a)).difficulty, 2) + "*");
						}
					}
				}
			} else if (size == 0) {
				m_client.sendMessage(lobby.channel, sender + ": 0 beatmaps found in current difficulty range!");
			} else if (size == 1) {
				// bot.bancho.sendMessage(sender, "Correct!");
				// int result = Info.getInt(1);
				String str = "" + Info.get(0);
				JSONObject beatmap = new JSONObject(str);
				System.out.println(str);
				Beatmap beatmapObj = new Beatmap(beatmap, true);
				addBeatmap(lobby, beatmapObj);
			}
		} catch (JSONException | URISyntaxException | IOException e) {
			e.printStackTrace();
			m_client.sendMessage(sender, sender + ": Error");
		}
		return "";
	}

	public void changeBeatmap(Lobby lobby, Beatmap next) {
		lobby.voteskip.clear();
		lobby.voteStart.clear();
		lobby.Playing = false;
		m_client.sendMessage(lobby.channel, "!mp map " + next.beatmap_id + " " + lobby.type);
		lobby.previousBeatmap = lobby.currentBeatmap;
		lobby.currentBeatmap = next;
		lobby.currentBeatmapAuthor = next.artist;
		lobby.currentBeatmapName = next.title;
		lobby.timer.continueTimer();
		if (next.DT) {
			if (!lobby.DoubleTime) {
				m_client.sendMessage(lobby.channel, "!mp moFds DT Freemod");
				lobby.DoubleTime = true;
			}
		} else if (next.HT) {
			if (!lobby.HalfTime) {
				lobby.HalfTime = true;
				m_client.sendMessage(lobby.channel, "!mp mods HT Freemod");
			}
		} else if (next.NC) {
			if (!lobby.NightCore) {
				lobby.NightCore = true;
				m_client.sendMessage(lobby.channel, "!mp mods NC Freemod");
			}
		} else {
			if (lobby.DoubleTime || lobby.HalfTime || lobby.NightCore) {
				{
					m_client.sendMessage(lobby.channel, "!mp mods Freemod");
					lobby.DoubleTime = false;
					lobby.HalfTime = false;
					lobby.NightCore = false;
				}
			}
		}
		if (lobby.type.equals("3")) {
			m_client.sendMessage(lobby.channel,
					"Up next: [https://osu.ppy.sh/b/" + next.beatmap_id + " " + next.artist + " - " + next.title + "] ["
							+ round(next.difficulty, 2) + "*] || Keys: " + next.difficulty_cs + "K");

		} else {
			m_client.sendMessage(lobby.channel, "Up next: [https://osu.ppy.sh/b/" + next.beatmap_id + " " + next.artist
					+ " - " + next.title + "] [" + round(next.difficulty, 2) + "*]");

		}

		String md = "";

		beatmapFile pplife = getPeppyPoints(next.beatmap_id, lobby);
		if (pplife == null) {
			if (!lobby.type.equals("2")) {
				m_client.sendMessage(lobby.channel, "Beatmap was unable to be analyzed. Does it exist? Skipping");
				nextbeatmap(lobby);
				return;
			} else {
				m_client.sendMessage(lobby.channel, "CTB analyzer currently doesn't work. Sorry about that.");
			}
		}
		if (lobby.DoubleTime)
			md = md + "DT";
		if (lobby.NightCore)
			md = md + "NC";
		if (lobby.HalfTime)
			md = md + "HT";
		if (pplife != null)
			if (pplife.ppvalues[0] != 0) {
				m_client.sendMessage(lobby.channel,
						md + "SS: " + String.format("%.02f", pplife.ppvalues[0]) + "pp || " + md + "HD: "
								+ String.format("%.02f", pplife.ppvalues[1]) + "pp || " + md + "HR: "
								+ String.format("%.02f", pplife.ppvalues[2]) + "pp || " + md + "HDHR: "
								+ String.format("%.02f", pplife.ppvalues[3]) + "pp");
			}
		lobby.beatmapPlayed.add(next);
	}

	public void nextbeatmap(Lobby lobby) {
		lobby.voteskip.clear();
		lobby.voteStart.clear();
		lobby.Playing = false;
		lobby.beatmapPlayed.add(lobby.currentBeatmap);
		Beatmap next = lobby.beatmapQueue.poll();
		if (next == null) {
			if (lobby.TrueRandom) {
				m_client.sendMessage(lobby.channel,
						"Queue is empty. Selecting a random beatmap matching this lobby...");
				getRandomBeatmap(lobby);
				return;
			} else {
				next = lobby.beatmapPlayed.poll();
				if (next == null) {
					lobby.currentBeatmap = null;
					m_client.sendMessage(lobby.channel, "Played Queue is Empty. Please add some maps ;(");
					return;
				}
				m_client.sendMessage(lobby.channel, "Queue is empty. Selecting the oldest map played.");
			}
		}

		m_client.sendMessage(lobby.channel, "!mp map " + next.beatmap_id + " " + lobby.type);
		lobby.previousBeatmap = lobby.currentBeatmap;
		lobby.currentBeatmap = next;
		lobby.currentBeatmapAuthor = next.artist;
		lobby.currentBeatmapName = next.title;
		lobby.timer.continueTimer();
		if (next.DT) {
			if (!lobby.DoubleTime) {
				m_client.sendMessage(lobby.channel, "!mp mods DT Freemod");
				lobby.DoubleTime = true;
			}
		} else if (next.HT) {
			if (!lobby.HalfTime) {
				lobby.HalfTime = true;
				m_client.sendMessage(lobby.channel, "!mp mods HT Freemod");
			}
		} else if (next.NC) {
			if (!lobby.NightCore) {
				lobby.NightCore = true;
				m_client.sendMessage(lobby.channel, "!mp mods NC Freemod");
			}
		} else {
			if (lobby.DoubleTime || lobby.HalfTime || lobby.NightCore) {
				{
					m_client.sendMessage(lobby.channel, "!mp mods Freemod");
					lobby.DoubleTime = false;
					lobby.HalfTime = false;
					lobby.NightCore = false;
				}
			}
		}
		m_client.sendMessage(lobby.channel, "Up next: [https://osu.ppy.sh/b/" + next.beatmap_id + " " + next.artist
				+ " - " + next.title + "] [" + round(next.difficulty, 2) + "*]");

		String md = "";

		beatmapFile pplife = getPeppyPoints(next.beatmap_id, lobby);
		if (lobby.DoubleTime)
			md = md + "DT";
		if (lobby.NightCore)
			md = md + "NC";
		if (lobby.HalfTime)
			md = md + "HT";
		if (pplife != null)
			if (pplife.ppvalues[0] != 0) {
				m_client.sendMessage(lobby.channel,
						md + "SS: " + String.format("%.02f", pplife.ppvalues[0]) + "pp || " + md + "HD: "
								+ String.format("%.02f", pplife.ppvalues[1]) + "pp || " + md + "HR: "
								+ String.format("%.02f", pplife.ppvalues[2]) + "pp || " + md + "HDHR: "
								+ String.format("%.02f", pplife.ppvalues[3]) + "pp");
			}
	}

	public Boolean isOP(String user) {
		int userID = getId(user);
		for (int ID : m_config.ops) {
			if (ID == userID) {
				return true;
			}
		}
		return false;
	}

	public int getId(String name) {
		if (name.equalsIgnoreCase("BanchoBot"))
			return 3;
		int id = 0;
		if (usernames.containsValue(name)) {
			id = usernames.inverse().get(name);
		}
		if (id == 0) {
			try {
				RequestConfig defaultRequestConfig = RequestConfig.custom().setSocketTimeout(10000)
						.setConnectTimeout(10000).setConnectionRequestTimeout(10000).build();
				HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).build();
				URIBuilder uriBuilder = new URIBuilder().setScheme("http").setHost("osu.ppy.sh")
						.setPath("/api/get_user").setParameter("k", m_config.apikey).setParameter("u", "" + name)
						.setParameter("type", "string");
				URI uri = uriBuilder.build();
				HttpGet request = new HttpGet(uri);
				HttpResponse response = httpClient.execute(request);
				InputStream content = response.getEntity().getContent();
				String stringContent = IOUtils.toString(content, "UTF-8");
				JSONArray array = new JSONArray(stringContent);
				id = array.getJSONObject(0).getInt("user_id");
				/*
				 * RequestConfig defaultRequestConfig =
				 * RequestConfig.custom().setSocketTimeout(10000)
				 * .setConnectTimeout(10000).setConnectionRequestTimeout(10000).
				 * build(); HttpClient httpClient =
				 * HttpClients.custom().setDefaultRequestConfig(
				 * defaultRequestConfig).build(); URI uri = new URIBuilder()
				 * .setScheme("http") .setHost("osu.ppy.sh")
				 * .setPath("/api/get_user") .setParameter("k", m_config.apikey)
				 * .setParameter("u", "" + name) .setParameter("type", "string")
				 * .build(); HttpGet request = new HttpGet(uri); HttpResponse
				 * response = httpClient.execute(request); InputStream content =
				 * response.getEntity().getContent(); String stringContent =
				 * IOUtils.toString(content, "UTF-8"); JSONArray array = new
				 * JSONArray(stringContent); id =
				 * array.getJSONObject(0).getInt("user_id");
				 */
			} catch (JSONException | URISyntaxException | IOException e) {
				e.printStackTrace();
				e.printStackTrace(m_writer);
				m_writer.flush();
			}
		}

		if (id != 0) {
			usernames.put(id, name);
			System.out.println("New user: |" + name + "|ID: |" + id + "|");
		}
		return id;
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
					.setParameter("k", m_config.apikey).setParameter("u", "" + userId).setParameter("type", "id")
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

	public boolean hasAlreadyRequested(Lobby lobby, String sender) {
		int senderID = getId(sender);
		for (Beatmap beatmap : lobby.beatmapQueue) {
			if (beatmap.RequestedBy == senderID) {
				return true;
			}
		}
		return false;
	}
}
