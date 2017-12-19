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
import moe.autohost.shared.Global;
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

import java.io.BufferedInputStream;
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
	// This is to have AutoHost ignore commands from other people while testing.
	// Set this to your name to have AutoHost only accept your commands.
	private static final String LOCK_NAME = null;

	public static int lobbyCount = 0;
	public PrintWriter m_writer;

	private final IRCClient m_client;
	private Config m_config;
	private boolean m_shouldStop;

	private static Map<String, Lobby> m_lobbies = new HashMap<>();
	private static Map<String, LobbyChecker> m_permanentLobbies = new HashMap<>();
	// Every single IRC client i tried fails, so i decided to make my own with
	// blackjack & hookers.
	// Blackjack
	// Hookers
	public static Queue<Lobby> LobbyCreation = new LinkedList<>();

	public AutoHost autohost;
	public static HashBiMap<Integer, String> usernames = HashBiMap.create();
	public static HashBiMap<Integer, User> userDB = HashBiMap.create();

	// This is the reconnection data, just info i store for checking if Bancho
	// went RIP
	private ReconnectTimer m_reconnectTimer;

	// Main code

	public IRCBot(AutoHost autohost, Config config) throws IOException {
		// Define all settings. Meh.
		this.autohost = autohost;
		try {
			m_writer = new PrintWriter(Global.WORKING_DIRECTORY + "afklog.txt", "UTF-8");
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
		IRCBot.m_lobbies = Lobbies;
		m_permanentLobbies = permanentLobbies;
		IRCBot.usernames = usernames;
		IRCBot.LobbyCreation = LobbyCreation;
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

	public User getUser(int id) {
		if (!userDB.containsKey(id)) {
			userDB.put(id, new User(getUsername(id), id));
		}
		return userDB.get(id);
	}

	public User getUser(String name) {
		int id = getId(name);
		if (!userDB.containsKey(id)) {
			userDB.put(id, new User(name, id));
		}
		return userDB.get(id);
	}

	public Map<String, Lobby> getLobbies() {
		return m_lobbies;
	}

	public Map<String, LobbyChecker> getpermanentLobbies() {
		return m_permanentLobbies;
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
				if (msg.contains("001 AutoHost")) {
					System.out.println("Logged in");
					System.out.println("Line: " + msg);
				} else if (msg.startsWith("PING")) {
					String pingResponse = msg.replace("PING", "PONG");
					m_client.write(pingResponse);
				} else if (msg.startsWith("PONG")) {
					System.out.println("Got pong at " + now + ": " + msg);
				} else {
					System.out.println("RECV(" + new Date(now) + "): " + msg);
					try {
						log(msg);
					} catch (Exception e) {
						System.err.println("Unhandled exception thrown!");
						e.printStackTrace();
					}
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
					m_lobbies.remove(lobby.getChannel());
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
				if (lobby.getChannel().equalsIgnoreCase("#mp_" + channelded.group(2))) {
					lobby.getTimer().stopTimer();
					removeLobby(lobby);
					lobby = null;
				}
			}
			if (m_permanentLobbies.containsKey("#mp_" + channelded.group(2))) {
				Lobby lobby = m_permanentLobbies.get("#mp_" + channelded.group(2)).lobby;
				m_permanentLobbies.get("#mp_" + channelded.group(2)).stopped = true;
				m_permanentLobbies.get("#mp_" + channelded.group(2)).lobby.getTimer().stopTimer();
				m_permanentLobbies.remove("#mp_" + channelded.group(2));
				createNewLobby(lobby.getName(), lobby.getMinDifficulty(), lobby.getMaxDifficulty(), lobby.getCreatorName(), lobby.getOPLobby(),
						true);
				lobby = null;
			}
		}

		// RECV(Mon Oct 23 16:14:35 ART 2017): :BanchoBot!cho@ppy.sh PRIVMSG AutoHost :
		// You cannot create any more tournament matches. Please close any previous
		// tournament matches you have open.
		Pattern staph = Pattern.compile(
				":BanchoBot!cho@ppy.sh PRIVMSG AutoHost :You cannot create any more tournament matches. Please close any previous tournament matches you have open.");
		Matcher staphM = staph.matcher(line);
		if (staphM.matches()) {
			if (staphM.group(1).equalsIgnoreCase(m_client.getUser())) {
				String lobbyChannel = staphM.group(2);
				noMore(lobbyChannel);
				System.out.println("Lobby cancelled due to limit: " + lobbyChannel);
				return;
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
				if (LOCK_NAME != null && !user.equalsIgnoreCase(LOCK_NAME) && !user.equalsIgnoreCase("BanchoBot")) {
					m_client.sendMessage(user, "hypex is currently testing / fixing AutoHost. "
							+ "He'll announce in the [https://discord.gg/UDabf2y AutoHost Discord] when he's done");
				} else {
					new PrivateMessageHandler(this).handle(user, message);
				}
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
					if (lobby.getChannel().equalsIgnoreCase("#mp_" + partM.group(2))) {
						lobby.getTimer().stopTimer();
						removeLobby(lobby);
					}
				}
				if (m_permanentLobbies.containsKey("#mp_" + partM.group(2))) {
					Lobby lobby = m_permanentLobbies.get("#mp_" + partM.group(2)).lobby;
					m_permanentLobbies.get("#mp_" + partM.group(2)).stopped = true;
					m_permanentLobbies.remove("#mp_" + partM.group(2));
					createNewLobby(lobby.getName(), lobby.getMinDifficulty(), lobby.getMaxDifficulty(), lobby.getCreatorName(),
							lobby.getOPLobby(), true);
				}
			}
		}
	}

	public void createNewLobby(String name, double mindiff, double maxdiff, String creator, Boolean isOP) {
		Lobby lobby = new Lobby();
		lobby.getSlots().clear();
		lobby.setLobbySize(16);
		lobby.setType("0");
		lobby.setStatus(1);
		lobby.setName(name);
		lobby.setMaxDifficulty(maxdiff);
		lobby.setMinDifficulty(mindiff);
		lobby.setOPLobby(isOP);
		lobby.setPermanent(false);
		lobby.setCreatorName(creator);
		int creatorID = getId(creator);
		LobbyCreation.add(lobby);
		for (int op : m_config.ops) {
			if (op != creatorID)
				lobby.getOPs().add(op);
		}
		lobby.getOPs().add(creatorID);
		m_client.sendMessage("BanchoBot", "!mp make " + name);
	}

	public void createNewLobby(String name, double mindiff, double maxdiff, String creator, Boolean isOP,
			Boolean Permanent) {
		Lobby lobby = new Lobby();
		lobby.getSlots().clear();
		lobby.setLobbySize(16);
		lobby.setType("0");
		lobby.setStatus(1);
		lobby.setName(name);
		lobby.setMaxDifficulty(maxdiff);
		lobby.setMinDifficulty(mindiff);
		lobby.setOPLobby(isOP);
		lobby.setPermanent(false);
		lobby.setCreatorName(creator);
		int creatorID = getId(creator);
		LobbyCreation.add(lobby);
		for (int op : m_config.ops) {
			if (op != creatorID)
				lobby.getOPs().add(op);
		}
		IRCBot.lobbyCount++;
		lobby.setLobbyNumber(IRCBot.lobbyCount);
		lobby.getOPs().add(creatorID);
		m_client.sendMessage("BanchoBot", "!mp make " + name);
	}

	public void reconnectLobby(Lobby lobby) {
		lobby.getSlots().clear();
		lobby.setRejoined(true);
		LobbyCreation.add(lobby);
		m_client.write("JOIN " + lobby.getChannel());
		m_client.sendMessage("" + lobby.getChannel(), "Bot reconnected to this lobby after connection lost");
	}

	public void reconnectLobby(String creator, String channel, Boolean isOP) {
		Lobby lobby = new Lobby();
		lobby.getSlots().clear();
		lobby.setLobbySize(16);
		lobby.setType("0");
		lobby.setStatus(1);
		lobby.setMaxDifficulty(5.0);
		lobby.setMinDifficulty(4.0);
		lobby.setOPLobby(isOP);
		lobby.setCreatorName(creator);
		lobby.setRejoined(true);
		LobbyCreation.add(lobby);
		int creatorID = getId(creator);
		for (int op : m_config.ops) {
			if (op != creatorID)
				lobby.getOPs().add(op);
		}
		IRCBot.lobbyCount++;
		lobby.setLobbyNumber(IRCBot.lobbyCount);
		lobby.getOPs().add(creatorID);
		m_client.write("JOIN #mp_" + channel);
		m_client.sendMessage("#mp_" + channel, "Bot reconnect requested to this lobby by " + creator);
		m_client.sendMessage("#mp_" + channel,
				creator + " All settings will be set to default, so please re-set them.");
	}

	public void noMore(String lobbyChannel) {
		Lobby lobby = LobbyCreation.poll();
		if (lobby != null) {
			m_client.sendMessage(lobby.getCreatorName(),
					"Sorry, autohost is currently at the maximum ammount of concurrent lobbies possible. Your lobby creation was cancelled.");
		}
		if (!LobbyCreation.isEmpty()) {
			String name = LobbyCreation.peek().getName();
			m_client.sendMessage("BanchoBot", "!mp make " + name);
		}
	}

	public void newLobby(String lobbyChannel) {
		Lobby lobby = LobbyCreation.poll();
		if (lobby != null) {
			System.out.println("LobbyCreationPoll good " + lobbyChannel);
			lobby.setChannel(lobbyChannel);
			if (lobby.isPermanent()) {
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
			if (lobby.getRejoined()) {
				for (Slot slot : lobby.getSlots().values()) {
					if (slot.name.equalsIgnoreCase(lobby.getCreatorName())) {
						inside = true;
					}
				}
			}
			if (!inside)
				m_client.sendMessage(lobbyChannel, "!mp invite " + lobby.getCreatorName());
			lobby.setTimer(new TimerThread(this, lobby));
			lobby.getTimer().start();
			if (lobby.isPermanent()) {
				nextbeatmap(lobby);
			}

		} else {
			System.out.println("LobbyCreationPoll null");
			lobby = new Lobby(lobbyChannel);
			lobby.setChannel(lobbyChannel);
			m_lobbies.put(lobbyChannel, lobby);
			lobby.getSlots().clear();
			m_client.sendMessage(lobbyChannel, "!mp settings");
			m_client.sendMessage(lobbyChannel, "!mp unlock");
			m_client.sendMessage(lobbyChannel, "!mp password");
			m_client.sendMessage(lobbyChannel, "!mp mods Freemod");
			lobby.setLobbySize(16);
			lobby.setType("0");
			lobby.setStatus(1);
			lobby.setMaxDifficulty(5.0);
			lobby.setMinDifficulty(4.0);
			lobby.setTimer(new TimerThread(this, lobby));
			lobby.getTimer().start();
		}
		if (!LobbyCreation.isEmpty()) {
			String name = LobbyCreation.peek().getName();
			m_client.sendMessage("BanchoBot", "!mp make " + name);
		}
	}

	public void addAFK(Lobby lobby, String player) {
		if (lobby.getAfk().containsKey(player)) {
			lobby.getAfk().put(player, lobby.getAfk().get(player) + 1);
			if (lobby.getAfk().get(player) >= 3) {
				m_client.sendMessage(lobby.getChannel(), "!mp kick " + player);
				m_client.sendMessage(lobby.getChannel(), player + " was kicked for being AFK for 5 rounds.");
				m_client.sendMessage(player, "You were kicked from the lobby for being AFK.");
			}
		} else {
			lobby.getAfk().put(player, 1);
		}

	}

	public void removeAFK(Lobby lobby, String player) {
		lobby.getAfk().put(player, 0);
	}

	public void removeLobby(Lobby lobby) {
		synchronized (m_lobbies) {
			if (m_lobbies.containsKey(lobby.getChannel())) {
				m_lobbies.remove(lobby.getChannel());
				lobby.getTimer().stopTimer();
			}
		}
		synchronized (m_permanentLobbies) {
			if (m_permanentLobbies.containsKey(lobby.getChannel())) {
				m_permanentLobbies.get(lobby.getChannel()).stopped = true;
				m_permanentLobbies.get(lobby.getChannel()).lobby.getTimer().stopTimer();
				m_permanentLobbies.remove(lobby.getChannel());
			}
		}
	}

	public void addBeatmap(Lobby lobby, Beatmap beatmap) {
		lobby.getBeatmapQueue().add(beatmap);
		m_client.sendMessage(lobby.getChannel(), beatmap.artist + " - " + beatmap.title + "(" + beatmap.difficulty_name + ")"
				+ " [" + round(beatmap.difficulty, 2) + "*] was added to the queue! Pos: " + lobby.getBeatmapQueue().size());
		if (lobby.getCurrentBeatmap() == null) {
			nextbeatmap(lobby);
		}
	}

	public void askForConfirmation(String sender, int beatmapnumber, Lobby lobby) {
		try {
			getBeatmapDiff(beatmapnumber, lobby, (array) -> {
				if (array == null) {
					m_client.sendMessage(lobby.getChannel(), sender + ": Beatmap not found.");
					return;
				}
				int senderID = getId(sender);
				Request request = new Request();
				// lobby.requests
				System.out.println("Array has #objects: " + array.length());
				for (int i = 0; i < array.length(); i++) {
					JSONObject obj = JSONUtils.silentGetArray(array, i);
					Boolean block = false;
					String mode = JSONUtils.silentGetString(obj, "mode");
					if (!mode.equals(lobby.getType())) {
						m_client.sendMessage(lobby.getChannel(),
								sender + " That beatmap does not fit the lobby's current gamemode!");
						return;
					}
					Beatmap beatmap = JSONUtils.silentGetBeatmap(obj);
					beatmap.RequestedBy = senderID;
					if (lobby.getOnlyDifficulty()) { // Does the lobby have
												// locked difficulty limits?
						if (!(beatmap.difficulty >= lobby.getMinDifficulty() && beatmap.difficulty <= lobby.getMaxDifficulty())) {
							// m_client.sendMessage(lobby.getChannel(), Sender+ "
							// Difficulty
							// [https://osu.ppy.sh/b/"+beatmap.beatmap_id+"
							// "+beatmap.difficulty_name+"] doesnt match the
							// lobby difficulty.");
							block = true;
						}
					}

					if (!lobby.getStatusTypes().get(beatmap.graveyard)) {
						if (!block) {
							// m_client.sendMessage(lobby.getChannel(), Sender+ "That
							// beatmap
							// is not within ranking criteria for this lobby!
							// (Ranked/loved/etc)");
							block = true;
						}
					}

					if (lobby.getMaxAR() != 0 && beatmap.difficulty_ar < lobby.getMaxAR()) {
						m_client.sendMessage(lobby.getChannel(),
								sender + " That beatmap has a too high Approach Rate for this lobby!");
						return;
					}

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

					if (!lobby.getStatusTypes().get(beatmap.graveyard)) {
						m_client.sendMessage(lobby.getChannel(), sender
								+ " That beatmap is not within ranking criteria for this lobby! (Ranked/loved/etc)");
						return;
					}

					if (lobby.getOnlyGenre()) {
						if (!beatmap.genre.equalsIgnoreCase(lobby.getGenre())) {
							if (!block) {
								// m_client.sendMessage(lobby.getChannel(), Sender +
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
					m_client.sendMessage(lobby.getChannel(),
							sender + " This beatmap set doesnt have any difficulty matching the lobby's range!");
				} else if (request.bids.size() == 1) {
					m_client.sendMessage(lobby.getChannel(),
							sender + " Selecting the only matching difficulty from the linked set");
					addBeatmap(lobby, request.beatmaps.get(request.bids.iterator().next()));
				} else {
					lobby.getRequests().put(sender, request);
					m_client.sendMessage(lobby.getChannel(),
							sender + " Please pick one of the following difficulties using !select [number]");
					for (int i = 0; i < request.bids.size(); i++) {

						m_client.sendMessage(lobby.getChannel(),
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
				.setParameter("k", m_config.apikey).setParameter("b", "" + beatmapId).setParameter("m", lobby.getType())
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
				.setParameter("k", m_config.apikey).setParameter("s", "" + beatmapId).setParameter("m", lobby.getType())
				.build();
		HttpGet request = new HttpGet(uri);
		HttpResponse response = httpClient.execute(request);
		InputStream content = response.getEntity().getContent();
		String stringContent = IOUtils.toString(content, "UTF-8");
		JSONArray array = new JSONArray(stringContent);
		callback.accept(array.length() > 0 ? array : null);
	}

	public Integer[] orderScores(Lobby lobby) {
		Integer score[] = new Integer[(lobby.getLobbySize() - 1)];
		int i = 0;
		for (int ss : lobby.getScores().values()) {
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
	 * (string.equalsIgnoreCase(lobby.slots.get(i).name)) { ready++; voted = true; }
	 * } if (!voted) { if (lobby.slots.get(i).status.equalsIgnoreCase("Ready")) {
	 * ready++; } } players++; } } } if (players == 0) { lobby.timer.resetTimer();
	 * return; }
	 *
	 * if (ready >= round(players * 0.6, 0)) { m_client.sendMessage(lobby.getChannel(),
	 * ready + "/" + players + " have voted to start the game, starting.");
	 * start(lobby); } if (ready < round(players * 0.6, 0)) {
	 * m_client.sendMessage(lobby.getChannel(), ready + "/" + (int) (round(players *
	 * 0.75, 0)) +
	 * " votes to start the game. Please do !ready (or !r) if you're ready."); } if
	 * (players == 0) { nextbeatmap(lobby); } }
	 */

	public void tryStart(Lobby lobby) {
		int ready = 0;
		int players = 0;
		for (int i = 0; i < 16; i++) {
			if (lobby.getSlots().get(i) != null) {
				if (lobby.getSlots().get(i).playerid != 0) {
					if (lobby.getSlots().get(i).status.equalsIgnoreCase("Ready")) {
						if (!lobby.getVoteStart().contains(lobby.getSlots().get(i).name))
						ready++;
					}
					players++;
				}
			}
		}
		ready = ready + lobby.getVoteStart().size();
		if (players == 0) {
			lobby.getTimer().resetTimer();
			nextbeatmap(lobby);
			return;
		}

		if (ready >= round(players * 0.6, 0)) {
			m_client.sendMessage(lobby.getChannel(), ready + "/" + players + " have voted to start the game, starting.");
			start(lobby);
		}
		if (ready < round(players * 0.6, 0)) {
			m_client.sendMessage(lobby.getChannel(), ready + "/" + (int) (round(players * 0.6, 0))
					+ " votes to start the game. Please do !ready (or !r) if you're ready.");
		}
		if (players == 0) {
			nextbeatmap(lobby);
		}
		lobby.getTimer().resetTimer();
	}

	public void start(Lobby lobby) {
		m_client.sendMessage(lobby.getChannel(), "!mp start 5");
		lobby.getTimer().stopTimer();
		lobby.setPlaying(true);
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
			int lastBeatmap = 0;
			if (lobby.getPreviousBeatmap() == null) {
				m_client.sendMessage(lobby.getChannel(), user + " No beatmap was played yet!");
				return;
			}
			lastBeatmap = lobby.getPreviousBeatmap().beatmap_id;
			Boolean foundMap = false;
			for (int i = 0; i < array.length(); i++) {
				String str = "" + array.get(i);
				JSONObject beatmap = new JSONObject(str);
				int id = beatmap.getInt("beatmap_id");
				if (id == lastBeatmap) {
					int maxcombo = beatmap.getInt("maxcombo");
					int c50s = beatmap.getInt("count50");
					int c100s = beatmap.getInt("count100");
					int c300s = beatmap.getInt("count300");
					int miss = beatmap.getInt("countmiss");
					int mods = beatmap.getInt("enabled_mods");
					int totalhits;
					totalhits = c300s + c100s + c50s + miss;
					double acc = ((c300s * 6 + c100s * 2 + c50s) / ((double) totalhits * 6));
					String rank = beatmap.getString("rank");
					Mods modsFlag = Mods.parse(mods);
					String modsString = modsFlag.toString();
					foundMap = true;
					lt.ekgame.beatmap_analyzer.beatmap.Beatmap ppcalc = null;
					BeatmapParser parser = new BeatmapParser();

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
						ppcalc = parser.parse(input);
						if (ppcalc == null) {
							m_client.sendMessage(lobby.getChannel(), "Beatmap " + id + " is no longer available.");
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
					m_client.sendMessage(lobby.getChannel(),
							user + " || Rank: " + rank + " || Mods: " + modsString + " || Hits: " + c300s + "/" + c100s
									+ "/" + c50s + "/" + miss + " || Combo: (" + maxcombo + "/" + ppcalc.getMaxCombo()
									+ ") || " + String.format("%.02f", +acc * 100) + "% || PP: "
									+ String.format("%.02f", pp) + " ");
					ppcalc = null;
					parser = null;
					beatmap = null;
					System.gc();
				}
			}
			if (!foundMap) {
				m_client.sendMessage(lobby.getChannel(), user + " You didnt play (or pass) last beatmap!");
			}
		} catch (URISyntaxException | IOException | JSONException e) {
			e.printStackTrace();
		}
	}

	public beatmapFile getPeppyPoints(int beatmapid, Lobby lobby) throws BrokenBeatmap {
		if (lobby.getType().equals("2"))
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
			InputStream bufferedIn = new BufferedInputStream(response.getEntity().getContent());
			bufferedIn.mark(1);
			bufferedIn.reset();
			if (bufferedIn.read() < 0) {
				throw (new BrokenBeatmap("doesnt-exist"));
			}
			// String stringContent = IOUtils.toString(content, "UTF-8");
			BeatmapParser parser = new BeatmapParser();
			lt.ekgame.beatmap_analyzer.beatmap.Beatmap cbp = parser.parse(bufferedIn);
			if (cbp == null) {
				m_client.sendMessage(lobby.getChannel(), "Beatmap " + beatmapid + " is no longer available.");
			}
			Score ss = Score.of(cbp).combo(cbp.getMaxCombo()).build();
			// lobby.beatmaps.put(beatmapid, cbp);
			Difficulty cbp1 = null;
			Difficulty cbp2 = null;
			Difficulty cbp3 = null;
			Difficulty cbp4 = null;
			if (lobby.getDoubleTime() || lobby.getNightCore()) {
				// Arrays.fill(currentBeatmap, cbp);
				cbp1 = cbp.getDifficulty(new Mods(Mod.DOUBLE_TIME));
				cbp2 = cbp.getDifficulty(new Mods(Mod.HIDDEN, Mod.DOUBLE_TIME));
				cbp3 = cbp.getDifficulty(new Mods(Mod.HARDROCK, Mod.DOUBLE_TIME));
				cbp4 = cbp.getDifficulty(new Mods(Mod.HIDDEN, Mod.HARDROCK, Mod.DOUBLE_TIME));
			}

			if (lobby.getHalfTime()) {
				// Arrays.fill(currentBeatmap, cbp);
				cbp1 = cbp.getDifficulty(new Mods(Mod.HALF_TIME));
				cbp2 = cbp.getDifficulty(new Mods(Mod.HIDDEN, Mod.HALF_TIME));
				cbp3 = cbp.getDifficulty(new Mods(Mod.HARDROCK, Mod.HALF_TIME));
				cbp4 = cbp.getDifficulty(new Mods(Mod.HIDDEN, Mod.HARDROCK, Mod.HALF_TIME));
			}

			if (!lobby.getHalfTime() && !(lobby.getDoubleTime() || lobby.getNightCore())) {
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
			parser = null;
			cbp = null;
			cbp1 = cbp2 = cbp3 = cbp4 = null;
			perf = perf2 = perf3 = perf4 = null;
		} catch (IOException | URISyntaxException | BeatmapException | BrokenBeatmap e) {
			System.out.println(bm.id);
			bm = null;
			System.gc();
			if (e.getClass().equals(BrokenBeatmap.class)) {
				throw (new BrokenBeatmap("doesnt-exist"));
			}
			Matcher error = RegexUtils.matcher("Couldn't find required \"General\" tag found", e.getMessage());
			m_client.sendMessage(lobby.getChannel(), "Error Parsing beatmap");
			if (error.matches()) {
				throw new BrokenBeatmap("broken-tag");
			} else
				e.printStackTrace();
			return null;
		}

		bm.setpptab(str);
		return bm;
	}

	public void getRandomBeatmap(Lobby lobby) throws BrokenBeatmap {
		try {
			try {
				getRandomWithinSettings(lobby, (obj) -> {
					if (obj == null) {
						m_client.sendMessage(lobby.getChannel(),
								"Maybe no matches for current lobby settings? Anyone do '!retry' to try again.");
						lobby.setRetryForMap(true);
						return;
					}

					String mode = "" + JSONUtils.silentGetInt(obj, "gamemode");
					if (!mode.equals(lobby.getType())) {
						m_client.sendMessage(lobby.getChannel(),
								"ERORR: The random beatmap did not fit this lobby's gamemode!");
						return;
					}
					Beatmap beatmap = JSONUtils.silentGetBeatmap(obj, true);
					beatmapFile bm = null;
					try {
						bm = getPeppyPoints(beatmap.beatmap_id, lobby);
					} catch (BrokenBeatmap e) {
						if (e.getMessage().equals("broken-tag")) {
							m_client.sendMessage(lobby.getChannel(), "Beatmap has no 'general' tag. Is it broken?");
						} else if (e.getMessage().equals("doesnt-exist")) {
							m_client.sendMessage(lobby.getChannel(),
									"Beatmap no longer exists. Anyone send !retry for trying a new one");
							lobby.setRetryForMap(true);
						}
						bm = null;
						return;
					}
					System.gc();

					if (bm == null) {
						if (!lobby.getType().equals("2")) {
							m_client.sendMessage(lobby.getChannel(),
									"An error ocurred while loading the random beatmap. Anyone send !retry to attempt again");
							lobby.setRetryForMap(true);
							return;
						}
					}
					if (lobby.getOnlyDifficulty()) { // Does the lobby have
												// locked difficulty limits?
						if (!(beatmap.difficulty >= lobby.getMinDifficulty() && beatmap.difficulty <= lobby.getMaxDifficulty())) {
							// Are we inside the criteria? if not, return
							m_client.sendMessage(lobby.getChannel(),
									"ERROR: The difficulty of the random beatmap found does not match the lobby criteria."
											+ "(Lobby m/M: " + lobby.getMinDifficulty() + "*/" + lobby.getMaxDifficulty() + "*),"
											+ " Song: " + beatmap.difficulty + "*");
							return;
						}
					}
					if (!lobby.getStatusTypes().get(beatmap.graveyard)) {
						m_client.sendMessage(lobby.getChannel(),
								"ERROR: The random beatmap is not within ranking criteria for this lobby! (Ranked/loved/etc)");
						return;
					}

					if (lobby.getMaxAR() != 0) {
						if (beatmap.difficulty_ar > lobby.getMaxAR()) {

							m_client.sendMessage(lobby.getChannel(),
									"ERROR: The random beatmap has a too high Approach Rate for this lobby! Max: "
											+ lobby.getMaxAR() + " beatmap AR: " + beatmap.difficulty_ar);
							return;
						}
					}

					if (lobby.getOnlyGenre()) {
						if (!beatmap.genre.equalsIgnoreCase(lobby.getGenre())) {
							m_client.sendMessage(lobby.getChannel(),
									"ERROR: Beatmap genre is incorrect. This lobby is set to only play "
											+ lobby.getGenres()[Integer.valueOf(lobby.getGenre())] + " genre!");
							return;
						}
					}

					changeBeatmap(lobby, beatmap);
					bm = null;
				});
			} catch (SocketTimeoutException | JSONException e) {
				if (e.getClass().equals(SocketTimeoutException.class)) {
					m_client.sendMessage(lobby.getChannel(),
							"We're getting timed out. Is [http://osusearch.com osusearch] down? If so, use !add command.");
					throw new BrokenBeatmap("timed-out");
				} else if (e.getClass().equals(JSONException.class)) {
					m_client.sendMessage(lobby.getChannel(),
							"There was an error parsing the JSON from [http://osusearch.com osusearch]. Please do !retry to attempt again.");
				}
			}
		} catch (IOException | JSONException | URISyntaxException e) {
			e.printStackTrace();
			nextbeatmap(lobby);
		}
	}

	public void getRandomWithinSettings(Lobby lobby, Consumer<JSONObject> callback)
			throws URISyntaxException, IOException, JSONException, SocketTimeoutException {
		RequestConfig defaultRequestConfig = RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(10000)
				.setConnectionRequestTimeout(10000).build();

		HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).build();
		// http://osusearch.com/search/?genres=Anime
		// &languages=Japanese&statuses=Ranked
		// &modes=Standard&star=(3.60,6.40)&min_length=30
		// &max_length=300&query_order=play_count

		// http://osusearch.com/query/?genres=Anime&languages=Japanese&statuses=Ranked&modes=Standard&star=(4.1,4.3)&min_length=30&max_length=300&query_order=play_count
		String status = "Ranked";
		if (!lobby.getStatusTypes().get(-2)) {
			status = "Ranked";
		} else {
			status = "Ranked,Qualified,Unranked";
		}
		String mode = "Standard";
		String maxAR = "12";
		// 0 = osu!, 1 = Taiko, 2 = CtB, 3 = osu!mania
		if (lobby.getType().equals("1")) {
			mode = "Taiko";
		} else if (lobby.getType().equals("0")) {
			mode = "Standard";
		} else if (lobby.getType().equals("2")) {
			mode = "CtB";
		} else if (lobby.getType().equals("3")) {
			mode = "Mania";
		}
		if (lobby.getMaxAR() > 0.0) {
			maxAR = "" + lobby.getMaxAR();
		}
		String date_start = "2000-1-1";
		String date_end = "2020-1-1";
		if (lobby.getLimitDate()) {
			date_start = lobby.getMinYear() + "-1-1";
			date_end = lobby.getMaxYear() + "-1-1";
		}
		String maxcs = "11";
		String mincs = "0";
		if (lobby.getType().equals("3")) {
			if (lobby.getKeyLimit()) {
				maxcs = "" + lobby.getKeys();
				mincs = "" + lobby.getKeys();
			}
		}

		String maxduration = "300";
		maxduration = "" + lobby.getMaxLength();
		String minduration = "0";
		minduration = "" + lobby.getMinLength();

		URI uri = new URIBuilder().setScheme("http").setHost("osusearch.com").setPath("/random/")
				.setParameter("statuses", status).setParameter("modes", mode).setParameter("order", "-difficulty")
				.setParameter("max_length", maxduration).setParameter("min_length", minduration)
				.setParameter("star", "( " + lobby.getMinDifficulty() + "," + lobby.getMaxDifficulty() + ")")
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
			m_client.sendMessage(lobby.getChannel(), "Random returned 0 results. Fucked up?");
			pick = 0;
		}
		callback.accept(array.length() > 0 ? (JSONObject) array.get(pick) : null);
	}

	public String searchBeatmap(String name, Lobby lobby, String sender, String author) {
		try {
			RequestConfig defaultRequestConfig = RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(10000)
					.setConnectionRequestTimeout(10000).build();
			HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).build();
			String status = "Ranked";
			if (!lobby.getStatusTypes().get(-2)) {
				status = "Ranked";
			} else {
				status = "Ranked,Qualified,Unranked";
			}
			String mode = "Standard";
			String maxAR = "12";
			// 0 = osu!, 1 = Taiko, 2 = CtB, 3 = osu!mania
			if (lobby.getType().equals("1")) {
				mode = "Taiko";
			} else if (lobby.getType().equals("0")) {
				mode = "Standard";
			} else if (lobby.getType().equals("2")) {
				mode = "CtB";
			} else if (lobby.getType().equals("3")) {
				mode = "Mania";
			}
			if (lobby.getMaxAR() > 0.0) {
				maxAR = "" + lobby.getMaxAR();
			}
			String date_start = "2000-1-1";
			String date_end = "2020-1-1";
			URIBuilder uriBuilder = new URIBuilder().setScheme("http").setHost("osusearch.com").setPath("/query/")
					.setParameter("statuses", status).setParameter("modes", mode).setParameter("order", "-difficulty")
					.setParameter("max_length", "" + lobby.getMaxLength()).setParameter("title", name)
					.setParameter("star", "( " + lobby.getMinDifficulty() + "," + lobby.getMaxDifficulty() + ")")
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
					m_client.sendMessage(lobby.getChannel(),
							sender + " This beatmap set doesnt have any difficulty matching the lobby's range!");
				} else if (askForWhich.bids.size() == 1) {
					m_client.sendMessage(lobby.getChannel(),
							sender + " Selecting the only matching difficulty from the linked set");
					addBeatmap(lobby, askForWhich.beatmaps.get(askForWhich.bids.iterator().next()));
				} else {
					if (askForWhich.bids.size() > 4) {
						lobby.getRequests().put(sender, askForWhich);
						m_client.sendMessage(lobby.getChannel(),
								sender + " I'll be PMing you with all the results as to not spam the lobby.");
						m_client.sendMessage(sender, sender
								+ " Please pick one of the following difficulties using !select [number] (In the lobby channel). | E.g. '!select 1'");
						for (int a = 0; a < askForWhich.bids.size(); a++) {
							m_client.sendMessage(sender, "[" + a + "] || "
									+ askForWhich.beatmaps.get(askForWhich.bids.get(a)).artist + " - "
									+ askForWhich.beatmaps.get(askForWhich.bids.get(a)).title + " "
									+ "[[https://osu.ppy.sh/b/"
									+ askForWhich.beatmaps.get(askForWhich.bids.get(a)).beatmap_id + " "
									+ askForWhich.beatmaps.get(askForWhich.bids.get(a)).difficulty_name + "]] - "
									+ round(askForWhich.beatmaps.get(askForWhich.bids.get(a)).difficulty, 2) + "*");
						}
					} else {
						if (lobby.getRequests().containsKey(sender)) {
							lobby.getRequests().remove(sender);
						}
						lobby.getRequests().put(sender, askForWhich);
						m_client.sendMessage(lobby.getChannel(), sender
								+ " Please pick one of the following difficulties using !select [number] | E.g. '!select 1'");
						for (int a = 0; a < askForWhich.bids.size(); a++) {
							m_client.sendMessage(lobby.getChannel(), "[" + a + "] || "
									+ askForWhich.beatmaps.get(askForWhich.bids.get(a)).artist + " - "
									+ askForWhich.beatmaps.get(askForWhich.bids.get(a)).title + " "
									+ "[[https://osu.ppy.sh/b/"
									+ askForWhich.beatmaps.get(askForWhich.bids.get(a)).beatmap_id + " "
									+ askForWhich.beatmaps.get(askForWhich.bids.get(a)).difficulty_name + "]] - "
									+ round(askForWhich.beatmaps.get(askForWhich.bids.get(a)).difficulty, 2) + "*");
						}
					}
				}
			} else if (size == 0) {
				m_client.sendMessage(lobby.getChannel(), sender + ": 0 beatmaps found in current difficulty range!");
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
		lobby.getVoteskip().clear();
		lobby.getVoteStart().clear();
		lobby.setPlaying(false);
		m_client.sendMessage(lobby.getChannel(), "!mp map " + next.beatmap_id + " " + lobby.getType());
		lobby.setPreviousBeatmap(lobby.getCurrentBeatmap());
		lobby.setCurrentBeatmap(next);
		lobby.setCurrentBeatmapAuthor(next.artist);
		lobby.setCurrentBeatmapName(next.title);
		lobby.getTimer().continueTimer();
		if (next.DT) {
			if (!lobby.getDoubleTime()) {
				m_client.sendMessage(lobby.getChannel(), "!mp moFds DT Freemod");
				lobby.setDoubleTime(true);
			}
		} else if (next.HT) {
			if (!lobby.getHalfTime()) {
				lobby.setHalfTime(true);
				m_client.sendMessage(lobby.getChannel(), "!mp mods HT Freemod");
			}
		} else if (next.NC) {
			if (!lobby.getNightCore()) {
				lobby.setNightCore(true);
				m_client.sendMessage(lobby.getChannel(), "!mp mods NC Freemod");
			}
		} else {
			if (lobby.getDoubleTime() || lobby.getHalfTime() || lobby.getNightCore()) {
				{
					m_client.sendMessage(lobby.getChannel(), "!mp mods Freemod");
					lobby.setDoubleTime(false);
					lobby.setHalfTime(false);
					lobby.setNightCore(false);
				}
			}
		}
		if (lobby.getType().equals("3")) {
			m_client.sendMessage(lobby.getChannel(),
					"Up next: [https://osu.ppy.sh/b/" + next.beatmap_id + " " + next.artist + " - " + next.title + "] ["
							+ round(next.difficulty, 2) + "*] || Keys: " + next.difficulty_cs + "K");

		} else {
			m_client.sendMessage(lobby.getChannel(), "Up next: [https://osu.ppy.sh/b/" + next.beatmap_id + " " + next.artist
					+ " - " + next.title + "] [" + round(next.difficulty, 2) + "*]");

		}

		String md = "";
		beatmapFile pplife = null;
		try {
			pplife = getPeppyPoints(next.beatmap_id, lobby);
		} catch (BrokenBeatmap e) {
			if (e.getMessage().equals("broken-tag")) {
				m_client.sendMessage(lobby.getChannel(), "Beatmap has no 'general' tag. Is it broken?");
				pplife = null;
			}
		}
		System.gc();
		if (pplife == null) {
			if (!lobby.getType().equals("2")) {
				m_client.sendMessage(lobby.getChannel(), "Beatmap was unable to be analyzed. Does it exist? Skipping");
				nextbeatmap(lobby);
				return;
			} else {
				m_client.sendMessage(lobby.getChannel(), "CTB analyzer currently doesn't work. Sorry about that.");
			}
		}
		if (lobby.getDoubleTime())
			md = md + "DT";
		if (lobby.getNightCore())
			md = md + "NC";
		if (lobby.getHalfTime())
			md = md + "HT";
		if (pplife != null) {
			if (pplife.ppvalues[0] != 0) {
				m_client.sendMessage(lobby.getChannel(),
						md + "SS: " + String.format("%.02f", pplife.ppvalues[0]) + "pp || " + md + "HD: "
								+ String.format("%.02f", pplife.ppvalues[1]) + "pp || " + md + "HR: "
								+ String.format("%.02f", pplife.ppvalues[2]) + "pp || " + md + "HDHR: "
								+ String.format("%.02f", pplife.ppvalues[3]) + "pp");
			}
		}
		lobby.getTimer().resetTimer();
		lobby.getBeatmapPlayed().add(next);
		pplife = null;
	}

	public void nextbeatmap(Lobby lobby) {
		lobby.getVoteskip().clear();
		lobby.getVoteStart().clear();
		lobby.getPlaying();
		lobby.getBeatmapPlayed().add(lobby.getCurrentBeatmap());
		Beatmap next = lobby.getBeatmapQueue().poll();
		if (next == null) {
			if (lobby.getTrueRandom()) {
				m_client.sendMessage(lobby.getChannel(),
						"Queue is empty. Selecting a random beatmap matching this lobby...");
				try {
					getRandomBeatmap(lobby);
				} catch (BrokenBeatmap e) {
					if (e.getMessage().equals("timed-out")) {
						m_client.sendMessage(lobby.getChannel(),
								"Due to timed-out beatmap request, lobby is halted. Please vote skip to attempt again");
					}
				}
				return;
			} else {
				next = lobby.getBeatmapPlayed().poll();
				if (next == null) {
					lobby.setCurrentBeatmap(null);
					m_client.sendMessage(lobby.getChannel(), "Played Queue is Empty. Please add some maps ;(");
					return;
				}
				m_client.sendMessage(lobby.getChannel(), "Queue is empty. Selecting the oldest map played.");
			}
		}

		m_client.sendMessage(lobby.getChannel(), "!mp map " + next.beatmap_id + " " + lobby.getType());
		lobby.setPreviousBeatmap(lobby.getCurrentBeatmap());
		lobby.setCurrentBeatmap(next);
		lobby.setCurrentBeatmapAuthor(next.artist);
		lobby.setCurrentBeatmapName(next.title);
		lobby.getTimer().continueTimer();
		if (next.DT) {
			if (!lobby.getDoubleTime()) {
				m_client.sendMessage(lobby.getChannel(), "!mp mods DT Freemod");
				lobby.setDoubleTime(true);
			}
		} else if (next.HT) {
			if (!lobby.getHalfTime()) {
				lobby.setHalfTime(true);
				m_client.sendMessage(lobby.getChannel(), "!mp mods HT Freemod");
			}
		} else if (next.NC) {
			if (!lobby.getNightCore()) {
				lobby.setNightCore(true);
				m_client.sendMessage(lobby.getChannel(), "!mp mods NC Freemod");
			}
		} else {
			if (lobby.getDoubleTime() || lobby.getHalfTime() || lobby.getNightCore()) {
				{
					m_client.sendMessage(lobby.getChannel(), "!mp mods Freemod");
					lobby.setDoubleTime(false);
					lobby.setHalfTime(false);
					lobby.setNightCore(false);
				}
			}
		}
		m_client.sendMessage(lobby.getChannel(), "Up next: [https://osu.ppy.sh/b/" + next.beatmap_id + " " + next.artist
				+ " - " + next.title + "] [" + round(next.difficulty, 2) + "*]");

		String md = "";

		beatmapFile pplife = null;
		try {
			pplife = getPeppyPoints(next.beatmap_id, lobby);
		} catch (BrokenBeatmap e) {
			if (e.getMessage().equals("broken-tag")) {
				m_client.sendMessage(lobby.getChannel(), "Beatmap has no 'general' tag. Is it broken?");
			}
		}
		if (lobby.getDoubleTime())
			md = md + "DT";
		if (lobby.getNightCore())
			md = md + "NC";
		if (lobby.getHalfTime())
			md = md + "HT";
		if (pplife != null)
			if (pplife.ppvalues[0] != 0) {
				m_client.sendMessage(lobby.getChannel(),
						md + "SS: " + String.format("%.02f", pplife.ppvalues[0]) + "pp || " + md + "HD: "
								+ String.format("%.02f", pplife.ppvalues[1]) + "pp || " + md + "HR: "
								+ String.format("%.02f", pplife.ppvalues[2]) + "pp || " + md + "HDHR: "
								+ String.format("%.02f", pplife.ppvalues[3]) + "pp");
			}
		lobby.getTimer().resetTimer();
		lobby.getBeatmapPlayed().add(next);
		pplife = null;

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
				 * .setConnectTimeout(10000).setConnectionRequestTimeout(10000). build();
				 * HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(
				 * defaultRequestConfig).build(); URI uri = new URIBuilder() .setScheme("http")
				 * .setHost("osu.ppy.sh") .setPath("/api/get_user") .setParameter("k",
				 * m_config.apikey) .setParameter("u", "" + name) .setParameter("type",
				 * "string") .build(); HttpGet request = new HttpGet(uri); HttpResponse response
				 * = httpClient.execute(request); InputStream content =
				 * response.getEntity().getContent(); String stringContent =
				 * IOUtils.toString(content, "UTF-8"); JSONArray array = new
				 * JSONArray(stringContent); id = array.getJSONObject(0).getInt("user_id");
				 */
			} catch (JSONException | URISyntaxException | IOException e) {
				e.printStackTrace();
				e.printStackTrace(m_writer);
				m_writer.flush();
			}

			if (id != 0) {
				usernames.put(id, name);
				System.out.println("New user: |" + name + "|ID: |" + id + "|");
			}
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
		if (lobby.isOP(getId(sender))) {
			return false;
		}
		int senderID = getId(sender);
		for (Beatmap beatmap : lobby.getBeatmapQueue()) {
			if (beatmap.RequestedBy == senderID) {
				return true;
			}
		}
		return false;
	}
}
