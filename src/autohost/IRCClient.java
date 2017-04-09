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
import java.util.List;
import java.util.Map;
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

public class IRCClient {

	// Every single IRC client i tried fails, so i decided to make my own with
	// blackjack & hookers.
	// Blackjack
	// Hookers
	private static String server;
	private static int port = 6667;
	private static String user;
	private static String password;
	private static Socket connectSocket;
	private static PrintStream out;
	Map<String, Lobby> Lobbies = new HashMap<>();
	public List<RateLimiter> limiters = new ArrayList<>();
	// private RateLimiterThread rate;
	private Thread inputThread; // for debugging
	private RateLimiterThread rate;
	private int RateLimit;
	Config configuration;
	public Map<Integer, String> usernames = new HashMap<>();

	@SuppressWarnings("static-access")
	public IRCClient(Config config) throws UnknownHostException, IOException {
		// Define all settings. Meh.
		this.configuration = config;
		this.server = config.server;
		this.port = 6667;
		this.user = config.user;
		this.password = config.password;
		this.RateLimit = config.rate;
		// Mods definition, ignore
		// Connect
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
		inputThread = new InputDumper(connectSocket.getInputStream(), this);

		inputThread.start();
		rate = new RateLimiterThread(this, RateLimit);
		rate.start();
	}

	public void log(String line) {
		if (line.contains("cho@ppy.sh QUIT :") || (line.contains("PING cho.ppy.sh"))
				|| (line.contains("PONG cho.ppy.sh"))) {
			return;
		}
		System.out.println(line);
		//:cho.ppy.sh 401 AutoHost #mp_32349656 :No such nick
		Pattern ChannelNo = Pattern.compile(":cho.ppy.sh 401 (.+) #mp_(.+) :No such nick");
		Matcher channelded = ChannelNo.matcher(line);
			if (channelded.matches()){
				if (Lobbies.containsKey("#mp_"+channelded.group(2))){
					Lobby lobby = Lobbies.get("#mp_"+channelded.group(2));
					if (lobby.channel.equalsIgnoreCase("#mp_"+channelded.group(2))){
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
				Lobby lobby = new Lobby(lobbyChannel);
				Lobbies.put(lobbyChannel, lobby);
				SendMessage(lobbyChannel, "!mp settings");
				SendMessage(lobbyChannel, "!mp unlock");
				SendMessage(lobbyChannel, "!mp password");
				SendMessage(lobbyChannel, "!mp mods Freemod");
				lobby.LobbySize = 16;
				lobby.type = "0";
				lobby.Graveyard = 1;
				lobby.maxDifficulty = 5;
				lobby.minDifficulty = 4;
				for (int op : configuration.ops) {
					lobby.OPs.add(op);
				}
				lobby.timer = new TimerThread(this, lobby);
				lobby.timer.start();
			}
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
					if (Lobbies.containsKey(channel)){
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
			Pattern roomName = Pattern.compile("Room name: (.+), History: https://osu.ppy.sh/mp/(.+)");
			Matcher rNM = roomName.matcher(message);

			System.out.println("Room name matching:" + message);
			if (rNM.matches()) {
				System.out.println("New room name! " + rNM.group(1));
				lobby.name = rNM.group(1);
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
					SendMessage(lobby.channel, "Warning: Player count mismatch!");
				}
			}

			// Slot info on players... generally should be empty on start.. but
			// who knows.
			Pattern slot = Pattern.compile("Slot: (\\d+) (.+) \\(https://osu.ppy.sh/u/(\\d+)\\) (.+)");
			Matcher sM = slot.matcher(message);
			if (sM.matches()) {
				int slotN = Integer.valueOf(sM.group(1));
				if (lobby.slots.containsKey(slotN)) {
					Slot slotM = lobby.slots.get(slotN);
					slotM.status = sM.group(2);
					slotM.id = slotN;
					slotM.playerid = Integer.valueOf(sM.group(3));
					slotM.name = sM.group(4);
					lobby.slots.replace(slotN, slotM);
				} else {
					Slot slotM = new Slot();
					slotM.status = sM.group(2);
					slotM.id = slotN;
					slotM.playerid = Integer.valueOf(sM.group(3));
					slotM.name = sM.group(4);
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
				for (int ID : lobby.OPs) {
					if (ID == getId(playerName)) {
						SendMessage(lobby.channel, "Operator " + playerName + " has joined. Welcome!");
						SendMessage(lobby.channel, "!mp addref " + playerName);
					}
				}
			}

			Pattern move = Pattern.compile("(.+) moved to slot (\\d+)");
			Matcher moveMatcher = move.matcher(message);
			if (moveMatcher.matches()) {
				int playerId = 0;
				playerId = getId(moveMatcher.group(1));
				Slot player = new Slot(Integer.valueOf(moveMatcher.group(2)), moveMatcher.group(1), playerId, "Not Ready");
				for (int i = 1; i < (lobby.LobbySize + 1); i++) {
					if (lobby.slots.containsKey(i)) {
						if (lobby.slots.get(i).name.equalsIgnoreCase(moveMatcher.group(1))) {
							player = lobby.slots.get(i);
							lobby.slots.remove(i);
							}
						}
					}
				lobby.slots.put(Integer.valueOf(moveMatcher.group(2)), player);
			}

			Pattern left = Pattern.compile("(.+) left the game");
			Matcher leftMatcher = left.matcher(message);
			if (leftMatcher.matches()) {
				for (int i = 1; i < (lobby.LobbySize + 1); i++) {
					if (lobby.slots.get(i).name.equalsIgnoreCase(moveMatcher.group(1))) {
						lobby.slots.remove(i);
					}
				}
			}

			if (message.equalsIgnoreCase("All players are ready")) {
				SendMessage(lobby.channel, "!mp start");
				lobby.timer.stopTimer();
			}

			if (message.equalsIgnoreCase("The match has started!")) {
				lobby.scores.clear();
				lobby.Playing = true;
			}

			if (message.equalsIgnoreCase("The match has finished!")) {
				// Chech for player scores -- TODO
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
				lobby.scores.put(Integer.valueOf(scoreMatcher.group(2)), scoreMatcher.group(1));
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
		if (message.startsWith("!")) {
			message = message.substring(1);
			String[] args = message.split(" ");
			if (args[0].equals("add")) {
				int id = 0;
				Pattern maprequest = Pattern.compile("add (\\d+)");
				Matcher mapR = maprequest.matcher(message);
				Pattern mapURL = Pattern.compile("add (.+)osu.ppy.sh/b/(\\d+)(.*)");
				Matcher mapU = mapURL.matcher(message);
				Pattern mapURLS = Pattern.compile("add (.+)osu.ppy.sh/s/(\\d+)(.*)");
				Matcher mapUS = mapURLS.matcher(message);
				if (mapR.matches()) {
				id = Integer.valueOf(mapR.group(1));
				}
				else if (mapU.matches()) {
					id = Integer.valueOf(mapU.group(2));
				}
				else if (mapUS.matches()){
					SendMessage(lobby.channel,
							Sender + " You introduced a beatmap set link, processing beatmaps... (for a direct difficulty add use the /b/ link)");
					int bid = Integer.valueOf(mapUS.group(2));
					askForConfirmation(Sender, bid, lobby);
					return;
				}
				if (id == 0){
					SendMessage(lobby.channel, Sender+" Incorrect Arguments for !add. Please use the beatmap URL. !add [url]");
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
						if (beatmap.graveyard < lobby.Graveyard) {
							SendMessage(lobby.channel, Sender
									+ "That beatmap is not within ranking criteria for this lobby! (Ranked/loved/etc)");
							return;
						}

						if (lobby.onlyGenre) {
							if (!beatmap.genre.equalsIgnoreCase(lobby.genre)) {
								SendMessage(lobby.channel, Sender + "This lobby is set to only play "
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

			} else if (args[0].equalsIgnoreCase("r") || args[0].equalsIgnoreCase("ready")) {
				if (lobby.Playing) {
					SendMessage(Sender, "The lobby is currently playing, you cant vote for starting right now.");
					return;
				}
				if (lobby.voteStart.contains(getId(Sender))) {
					SendMessage(Sender, "You already voted for starting!");
				} else {
					lobby.voteStart.add(getId(Sender));
					SendMessage(lobby.channel, Sender + " voted for starting! (" + lobby.voteStart.size() + "/"
							+ round(lobby.slots.size() * 0.75, 0) + ")");
					if (lobby.voteStart.size() >= round(lobby.slots.size() * 0.75, 0)) {
						start(lobby);
					}
				}
			} else if (args[0].equalsIgnoreCase("skip") || args[0].equalsIgnoreCase("s")) {
				if (lobby.Playing) {
					SendMessage(Sender, "The lobby is currently playing, you cant vote for skipping right now.");
					return;
				}
				if (lobby.voteskip.contains(getId(Sender))) {
					SendMessage(Sender, "You already voted for skipping!");
				} else {
					SendMessage(lobby.channel, Sender + " voted for skipping! (" + lobby.voteskip.size() + "/"
							+ round(lobby.slots.size() * 0.6, 0) + ")");
					lobby.voteskip.add(getId(Sender));
					if (lobby.voteskip.size() / lobby.slots.size() >= 0.6) {
						nextbeatmap(lobby);
					}
				}
			} else if (args[0].equalsIgnoreCase("info")) {
				SendMessage(lobby.channel,
						"This is an in-development IRC version of autohost developed by HyPeX. Do !commands to know them ;)");
			} else if (args[0].equalsIgnoreCase("commands")) {
				SendMessage(lobby.channel, "C.List: !add [beatmap] || !ready (or !r) || !skip (or !s) || !queue (or !playlist)");
			} else if (args[0].equalsIgnoreCase("playlist") || args[0].equalsIgnoreCase("queue")) {
				String playlist ="Queue: "+lobby.beatmapQueue.size()+" || ";
				for (Beatmap bm : lobby.beatmapQueue){
					playlist = playlist + "[https://osu.ppy.sh/b/"+bm.beatmap_id+" "+bm.artist+" - "+bm.title+"] ["+round(bm.difficulty,2)+"*] || ";
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
						Pattern maxdiff = Pattern.compile("maxdiff (\\d+)");
						Matcher diffM = maxdiff.matcher(message);
						if (diffM.matches()) {
							lobby.maxDifficulty = Integer.valueOf(diffM.group(1));
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
			} else if (args[0].equalsIgnoreCase("kick")) {
				for (int ID : lobby.OPs) {
					if (ID == (getId(Sender))) {
						Pattern id = Pattern.compile("kick (\\d+)");
						Pattern name = Pattern.compile("kick (.+)");
						Matcher idmatch = id.matcher(message);
						Matcher namematch = name.matcher(message);
						if (idmatch.matches()) {
							SendMessage(lobby.channel, "!mp kick #" + idmatch.group(1));
						} else if (namematch.matches()) {
							for (int i = 0; i < 16; i++) {
								Slot slot = lobby.slots.get(i);
								if (slot.name.toLowerCase().contains(namematch.group(1).toLowerCase())) {
									SendMessage(lobby.channel, "!mp kick #" + slot.id);
								}
							}
						}
					}
				}
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
			} else if (args[0].equalsIgnoreCase("mindiff")) {
				for (int ID : lobby.OPs) {
					if (ID == (getId(Sender))) {
						Pattern maxdiff = Pattern.compile("mindiff (\\d+)");
						Matcher diffM = maxdiff.matcher(message);
						if (diffM.matches()) {
							lobby.minDifficulty = Integer.valueOf(diffM.group(1));
						}

					}
				}
			} else if (args[0].equalsIgnoreCase("hostme")) {
				for (int ID : lobby.OPs) {
					if (ID == (getId(Sender))) {

						SendMessage(lobby.channel, "!mp host #" + ID);
					}
				}
			}
			/*
			 * -- TODO does this exist? else if
			 * (args[0].equalsIgnoreCase("rename")){ for (int ID : lobby.OPs){
			 * if (ID == (getId(Sender))){ Pattern maxdiff =
			 * Pattern.compile("mindiff (\\D+)"); Matcher diffM =
			 * maxdiff.matcher(message); if (diffM.matches()){
			 * lobby.minDifficulty = Integer.valueOf(diffM.group(1)); }
			 * 
			 * } } }
			 */
			else if (args[0].equalsIgnoreCase("closeroom")) {
				for (int ID : lobby.OPs) {
					if (ID == (getId(Sender))) {
						SendMessage(lobby.channel, "!mp close");
						removeLobby(lobby);
					}
				}
			}
		}

	}
	
	void removeLobby(Lobby lobby) {
		  synchronized(Lobbies) {
		    Lobbies.remove(lobby.channel);
		    lobby.timer.stopTimer();
		  }
		}

	private void addBeatmap(Lobby lobby, Beatmap beatmap) {
		lobby.beatmapQueue.add(beatmap);
		SendMessage(lobby.channel, beatmap.artist + " - " + beatmap.title + "("+beatmap.difficulty_name+")" + " [" + round(beatmap.difficulty, 2)
				+ "*] was added to the queue! Pos: " + lobby.beatmapQueue.size());
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
				System.out.println("Array has #objects: "+array.length());
				for (int i = 0; i < array.length(); i++) {
					JSONObject obj = array.getJSONObject(i);
					Boolean block = false;
					String mode = obj.getString("mode");
					if (!mode.equals(lobby.type)) {
						SendMessage(lobby.channel, Sender + " That beatmap does not fit the lobby's current gamemode!");
						return;
					}
					Beatmap beatmap = new Beatmap(obj);

					if (lobby.onlyDifficulty) { // Does the lobby have
												// locked difficulty limits?
						if (!(beatmap.difficulty >= lobby.minDifficulty && beatmap.difficulty <= lobby.maxDifficulty)) {
							//SendMessage(lobby.channel, Sender+ " Difficulty [https://osu.ppy.sh/b/"+beatmap.beatmap_id+" "+beatmap.difficulty_name+"] doesnt match the lobby difficulty.");
							block = true;
						}
					}
					if (beatmap.graveyard < lobby.Graveyard) {
						if (!block){
						//SendMessage(lobby.channel, Sender+ "That beatmap is not within ranking criteria for this lobby! (Ranked/loved/etc)");
						block = true;}
					}

					if (lobby.onlyGenre) {
						if (!beatmap.genre.equalsIgnoreCase(lobby.genre)) {
							if (!block){
							//SendMessage(lobby.channel, Sender + "This lobby is set to only play "
							//		+ lobby.genres[Integer.valueOf(lobby.genre)] + " genre!");
							block = true;
							}
						}
					}
					if (!block){
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
								"[" + i + "] " + "[https://osu.ppy.sh/b/"+request.beatmaps.get(request.bids.get(i)).beatmap_id+" "+request.beatmaps.get(request.bids.get(i)).difficulty_name + "] - "
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
		for (int ss : lobby.scores.keySet()) {
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

	public void tryStart(Lobby lobby) {
		int ready = 0;
		int players = 0;
		for (int i = 0; i < 16; i++) {
			if (lobby.slots.get(i) != null) {
				if (lobby.slots.get(i).playerid != 0) {
					for (int id : lobby.voteStart) {
						if (id == lobby.slots.get(i).playerid) {
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

		if (ready / players >= 0.75) {
			SendMessage(lobby.channel, ready + "/" + players + " have voted to start the game, starting.");
			start(lobby);
		}
		if (ready / players < 0.75) {
			SendMessage(lobby.channel, ready + "/" + round(players * 0.75, 2)
					+ " votes to start the game. Please do !ready (or !r) if you're ready.");
		}
		lobby.timer.resetTimer();
	}

	public void start(Lobby lobby) {
		SendMessage(lobby.channel, "!mp start 5");
		lobby.timer.stopTimer();
	}

	public void nextbeatmap(Lobby lobby) {
		lobby.voteskip.clear();
		lobby.voteStart.clear();
		lobby.Playing = false;
		Beatmap next = lobby.beatmapQueue.poll();
		if (next != null) {
			SendMessage(lobby.channel, "!mp map " + next.beatmap_id);
			lobby.currentBeatmap = next.beatmap_id;
			lobby.currentBeatmapAuthor = next.artist;
			lobby.currentBeatmapName = next.title;
			lobby.timer.continueTimer();
			lobby.beatmapPlayed.add(next);
		} else {
			lobby.currentBeatmap = null;
			SendMessage(lobby.channel, "There are no more beatmaps in queue!");
		}
	}

	public void PrivateMessage(String target, String sender, String message) {
		System.out.println(sender + ": " + message);
		message = message.trim();
		if (message.startsWith("!")) {
			message = message.substring(1);
			String[] args = message.split(" ");
			if (args[0].equalsIgnoreCase("help")) {
				SendMessage(sender, configuration.pmhelp);
				int i = 0;
				for (Lobby lobby : Lobbies.values()) {
					i++;
					SendMessage(sender, "Lobby [" + i + "] || Name: " + lobby.name + " || Stars: " + lobby.minDifficulty
							+ "* - " + lobby.maxDifficulty + "* || Slots: [" + lobby.slots.size() + "/16]");
				}

				return;
			}
			if (args[0].equalsIgnoreCase("reloadRooms")) {
				for (Lobby lobby : Lobbies.values()) {
					SendMessage(lobby.channel, "!mp settings");
					System.out.println("Reloading " + lobby.channel);
				}
				return;
			}
			if (args[0].equalsIgnoreCase("moveme")) {
				if (args.length >= 2) {
					Pattern moveme = Pattern.compile("moveme (\\d+)");
					Matcher matchMove = moveme.matcher(message);
					if (!matchMove.matches()) {
						SendMessage(sender, "Wrong format, please use !moveme [lobby number provided by help]");
						return;
					}
					int moveMe = Integer.valueOf(matchMove.group(1));

					int i=0;
					for (Lobby lobby : Lobbies.values()) {
						i++;
						if (i == moveMe) {
							if (lobby.slots.size() < 16) {
								SendMessage(lobby.channel, "!mp move " + sender);
							} else {
								SendMessage(sender, "Lobby is full, sorry");
							}
						}
					}
					return;
				}
			}
			if (args[0].equalsIgnoreCase("createroom")) {
				for (int ID : configuration.ops) {
					if (ID == (getId(sender))) {
						if (args.length <= 1) {
							SendMessage(sender, "Please include a lobby name. Usage: !createroom <name>");
							return;
						}
						String roomName = "";
						for (int i = 1; i < args.length; i++) {
							roomName = roomName + " " + args[i];
						}
						SendMessage("BanchoBot", "!mp make " + roomName);
						SendMessage(sender, "Creating room, please wait 1 second and pm me !help to ask for a move");
						return;
					}
				}
				SendMessage(sender, "You're not an Operator");
			}
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
			if (lobby.Graveyard == 1) {

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
