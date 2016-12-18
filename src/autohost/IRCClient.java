package autohost;

import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.SchemaOutputResolver;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
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
import autohost.utils.Slot;
import autohost.utils.TimerThread;

public class IRCClient {
	
	
	// Every single IRC client i tried fails, so i decided to make my own with blackjack & hookers.
	// Blackjack
	// Hookers
	private static String server;
	private static int port = 6667;
	private static String user;
	private static String password;
	private static Socket connectSocket;
	private static PrintStream out;
	List<Lobby> Lobbies = new ArrayList<>();
	public List<RateLimiter> limiters = new ArrayList<>();
	//private RateLimiterThread rate;
	private Thread inputThread; // for debugging
	private RateLimiterThread rate;
	private int RateLimit;
	Config configuration;
	public Map<Integer, String> usernames = new HashMap<>();
	
	@SuppressWarnings("static-access")
	public IRCClient ( Config config) throws UnknownHostException, IOException {
		 // Define all settings. Meh.
		 this.configuration = config; 
		 this.server = config.server;
		 this.port = 6667;
		 this.user = config.user;
		 this.password = config.password;
		 this.RateLimit = config.rate;
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
			connectSocket = new Socket( server, port );
			out = new PrintStream( connectSocket.getOutputStream() );
		
		    // for debugging
		    inputThread = new InputDumper( connectSocket.getInputStream(), this );
		    
		    inputThread.start();
		    rate = new RateLimiterThread(this,RateLimit);
		    rate.start();
	}
	
	public void log(String line){
		if (line.contains("cho@ppy.sh QUIT :") || (line.contains("PING cho.ppy.sh")) || (line.contains("PONG cho.ppy.sh"))) {
			return;
		}
		System.out.println(line);
		
		Pattern channel = Pattern.compile(":(.+)!cho@ppy.sh PRIVMSG (.+) :(.+)");
		Matcher channelmatch = channel.matcher(line);
		if (channelmatch.find())
		{
		//:AutoHost!cho@ppy.sh PRIVMSG #lobby :asd
			String user = channelmatch.group(1);
			String target = channelmatch.group(2);
			String message = channelmatch.group(3);
				if (target.startsWith("#"))
					ChannelMessage(target, user, message);
					else
					PrivateMessage(target,user,message);
		}
		
		
		Pattern pattern = Pattern.compile("JOIN :#mp_\\d+");
		Matcher matcher = pattern.matcher(line);
		if (matcher.find()){
			//System.out.println(line);
			String lobbyChannel = line.substring(matcher.start()+6);
			Lobby lobby = new Lobby(lobbyChannel);
			Lobbies.add(lobby);
			SendMessage(lobbyChannel,"!mp settings");
			SendMessage(lobbyChannel,"!mp unlock");		
			SendMessage(lobbyChannel,"!mp password");
			lobby.LobbySize = 16;
			lobby.type = "Standard";
			lobby.Graveyard = 1;
			lobby.maxDifficulty = 5;
			lobby.minDifficulty = 4;
			for (int op : configuration.ops){
				lobby.OPs.add(op);	
			}
			lobby.timer = new TimerThread(this, lobby);
		}
	}
	  
	public void ChannelMessage(String channel, String Sender, String message){
		Pattern pattern = Pattern.compile("#mp_(\\d+)"); // Is this a multi lobby channel?
		Matcher matcher = pattern.matcher(channel);
			if (matcher.matches()){
				for (Lobby lobby : Lobbies) {
					if (lobby.channel.equalsIgnoreCase(channel)){ // Is it an autohosted (by us) channel?
						ParseChannelMessage(lobby, Sender, message);
					}
					else
					{
						System.out.println("Warning: Channel not loaded? C: "+channel);
					}
				}
			}
			// If not a lobby channel, then why the fuck we care?
	}
	
	public void ParseChannelMessage(Lobby lobby, String Sender, String message){ 
		if (Sender.equalsIgnoreCase("BanchoBot")){
		
		// Room name and ID, important (?)
		Pattern roomName = Pattern.compile("Room Name: (.+), History: http://osu.ppy.sh/mp/(.+)");
		Matcher rNM = roomName.matcher(message);
		if (rNM.matches()){
			lobby.name = rNM.group(1);
			lobby.mpID = Integer.valueOf(rNM.group(2));
		}
		
		// Win condition... meh
		Pattern teamMode = Pattern.compile("Team Mode: (.+), Win condition: (.+)");
		Matcher rTM = teamMode.matcher(message);
		if (rTM.matches()){
			lobby.gamemode = rTM.group(1);
			lobby.winCondition = rTM.group(2);
		}
		
		// Beatmap change... i guess thats k?
		Pattern beatmap = Pattern.compile("Beatmap: https://osu.ppy.sh/b/(\\d+) (.+)- (.+)");
		Matcher bM = beatmap.matcher(message);
		if (bM.matches()){
			lobby.currentBeatmap = Integer.valueOf(bM.group(1));
			lobby.currentBeatmapAuthor = bM.group(2);
			lobby.currentBeatmapName = bM.group(3);
		}
		
		// Is this one even worth adding?
		Pattern players = Pattern.compile("Players: (\\d+)");
		Matcher pM = players.matcher(message);
		if (pM.matches()){
			if (lobby.slots.size() != Integer.valueOf(pM.group(1))){
				SendMessage(lobby.channel, "Warning: Player count mismatch!");
			}
		}
		
		// Slot info on players... generally should be empty on start.. but who knows.
		Pattern slot = Pattern.compile("Slot: (\\d+) (.+) \\(https://osu.ppy.sh/u/(\\d+)\\) (.+)");
		Matcher sM = slot.matcher(message);
		if (sM.matches()){
			int slotN = Integer.valueOf(sM.group(1));
			 if (lobby.slots.containsKey(slotN)){
				 Slot slotM = lobby.slots.get(slotN);
				 slotM.status = sM.group(2);
				 slotM.id = slotN;
				 slotM.playerid = Integer.valueOf(sM.group(3));
				 slotM.name = sM.group(4);
				 lobby.slots.replace(slotN, slotM);
			 }
			 else
			 {
				 Slot slotM = new Slot();
				 slotM.status = sM.group(2);
				 slotM.id = slotN;
				 slotM.playerid = Integer.valueOf(sM.group(3));
				 slotM.name = sM.group(4);
				 lobby.slots.put(slotN, slotM);
			 }
		}
		
		Pattern join = Pattern.compile("(.+) joined in slot (\\d+).");
		//:BanchoBot!cho@ppy.sh PRIVMSG #mp_29691447 :HyPeX joined in slot 1.
		Matcher joinMatch = join.matcher(message);
		if (joinMatch.matches()){
			String playerName = joinMatch.group(1);
			int jslot = Integer.valueOf(joinMatch.group(2));
			int playerId = getId(playerName);
			String status = "Not Ready";
			Slot newSlot = new Slot(jslot, playerName, playerId, status);
				if (lobby.slots.containsKey(jslot)){
					lobby.slots.replace(jslot, newSlot);
				}
				else
				{
					lobby.slots.put(jslot,newSlot);
				}
		}
		
		Pattern move = Pattern.compile("(.+) moved to slot (\\d+)");
		Matcher moveMatcher = move.matcher(message);
		if (moveMatcher.matches()){
			for (int i=1 ; i<(lobby.LobbySize+1) ; i++){
				if (lobby.slots.get(i).name.equalsIgnoreCase(moveMatcher.group(1))){
					if (lobby.slots.containsKey(Integer.valueOf(moveMatcher.group(2)))){
					lobby.slots.replace(Integer.valueOf(moveMatcher.group(2)), lobby.slots.get(i));
					}
					else
					{
					lobby.slots.put(Integer.valueOf(moveMatcher.group(2)), lobby.slots.get(i));
					}
					
					lobby.slots.remove(i);
				}
			}
		}
		
		Pattern left = Pattern.compile("(.+) left the game");
		Matcher leftMatcher = left.matcher(message);
		if (leftMatcher.matches()){
			for (int i=1 ; i<(lobby.LobbySize+1) ; i++){
				if (lobby.slots.get(i).name.equalsIgnoreCase(moveMatcher.group(1))){				
					lobby.slots.remove(i);
				}
			}
		}
		
		
		if (message.equalsIgnoreCase("All players are ready")){
			SendMessage(lobby.channel,"!mp start");
		}
		
		if (message.equalsIgnoreCase("The match has started!"))
		{
			lobby.scores.clear();
			// check for player scores - TODO
		}
		
		if (message.equalsIgnoreCase("The match has finished!")){
			// Chech for player scores -- TODO
			nextbeatmap(lobby);
			Integer orderedScores[] = new Integer[(lobby.LobbySize-1)]; 
			orderedScores = orderScores(lobby);
			for (int i=0 ; i<3 ; i++){
				String player = lobby.scores.get(orderedScores[i]);
				SendMessage(lobby.channel, player+" finished "+(i+1)+"!");
			}
		}
		
		Pattern score = Pattern.compile("(.+) has finished playing \\(Score: (.\\d), (.\\D)\\)");
		Matcher scoreMatcher = score.matcher(message);
		if (scoreMatcher.matches()){
			lobby.scores.put(Integer.valueOf(scoreMatcher.group(2)), scoreMatcher.group(1));
		}
		
		return;
		} // End of BanchoBot message filtering
		
		if (message.toLowerCase().contains("hi")){
			SendMessage(lobby.channel, "Hi "+Sender+"!");
		}
		
	}
	
	public Integer[] orderScores(Lobby lobby){
		Integer score[] = new Integer[(lobby.LobbySize-1)];
		int i = 0;
		for (int ss : lobby.scores.keySet()){
			score[i] = ss;
			i++;
		}
		
		Comparator<Integer> comp = new Comparator<Integer>(){
			@Override
			public int compare(Integer x, Integer y) {
	            return y-x;
			}};
	    Arrays.sort(score,comp);

		return score;
	}
	
	public void tryStart(Lobby lobby){
		
	}
	
	public void nextbeatmap(Lobby lobby) {
		Beatmap next = lobby.beatmapQueue.poll();
		if (next != null){
			
		}
	}

	public void PrivateMessage(String target, String sender, String message){
		System.out.println(sender+": "+message);
		message = message.trim();
		if (message.startsWith("!")){
			message = message.substring(1);
			String[] args = message.split(" ");
				if (args[0].equalsIgnoreCase("help")){
					SendMessage(sender, configuration.pmhelp);
					return;
				}	
				if (args[0].equalsIgnoreCase("reloadRooms")){
					for (Lobby lobby : Lobbies ){
						SendMessage(lobby.channel, "!mp settings");
						System.out.println("Reloading "+lobby.channel);
					}
					return;
				}
				if (args[0].equalsIgnoreCase("createroom")){
						if (args.length <= 1) {
							SendMessage(sender, "Please include a lobby name. Usage: !createroom <name>");
							return;
						}
					String roomName = "";
					for (int i=1 ; i<args.length ; i++){
						roomName = roomName+" "+args[i];
						}
					SendMessage("BanchoBot", "!mp make "+roomName);
					return;
				}
				SendMessage(sender, "Unrecognized Command. Please check !help, or !commands");
				
		}
		else
		{
			if (!sender.equalsIgnoreCase("BanchoBot"))
			SendMessage(sender, "This account is a bot. Command prefix is !. Send me !help for more info.");
			
		}
	}
	
	public void register() throws InterruptedException {
		Write( "PASS" + " " + password);
		Write( "NICK" + " " + user);
	    Write( "USER" + " " + user + " HyPeX irc.ppy.sh : Osu! Autohost Bot");
	  }
	
	public void SendMessage(String target, String message){
		Boolean exists = false;
		for (RateLimiter limiter : this.limiters){
			if (limiter.target.equals(target)){
				limiter.addMessage(message);
				exists = true;
			}
		}
		if (!exists){
			//System.out.println("New target. Add.");
			RateLimiter rlimiter = new RateLimiter(target, RateLimit);
			rlimiter.addMessage(message);
			limiters.add(rlimiter);		
		}
	}
	
	public void Write(String message){
		if (!message.contains("PASS")){
		System.out.println(message);
		}
		out.println(message);
	}
	
	public String searchBeatmap(String name, Lobby lobby, String sender){		
			try { 
			RequestConfig defaultRequestConfig = RequestConfig.custom()
				    .setSocketTimeout(10000)
				    .setConnectTimeout(10000)
				    .setConnectionRequestTimeout(10000)
				    .build();
			HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).build();
			String ranked = "Ranked";
			String modes = lobby.type;
			if (lobby.Graveyard == 1){
				
			}
			
			URI uri = new URIBuilder()
					.setScheme("http")
					.setHost("osusearch.com")
					.setPath("/query/")
					.setParameter("title", name)
					.setParameter("statuses", "Ranked")
					.setParameter("modes", modes)
					.setParameter("order", "play_count")
					.setParameter("star", "( "+ lobby.minDifficulty + "," + lobby.maxDifficulty + ")")
					.build(); 
			HttpGet request = new HttpGet(uri);
			HttpResponse response = httpClient.execute(request);
			InputStream content = response.getEntity().getContent();
			String stringContent = IOUtils.toString(content, "UTF-8");
			JSONObject obj = new JSONObject(stringContent);
			JSONArray Info = obj.getJSONArray("beatmaps");
			int size = 0;
			for (int i=0; i < Info.length(); i++) {
				size = size + 1;
			};
			if ( size > 1 ) {
				if (size > 3) {
				SendMessage(lobby.channel,sender + ": "+"Found "+size+" maps, please be more precise!");
				} else if (size < 4) {
					SendMessage(lobby.channel,sender + ": "+"Please retry being more specific from the one of the following maps and use !add:");
					String returnMaps = "";
					for (int i=0; i < Info.length(); i++) {
						String str = ""+Info.get(i);
						JSONObject beatmap = new JSONObject(str);
						int id = beatmap.getInt("beatmap_id");
						String artist = beatmap.getString("artist");
						String title = beatmap.getString("title");
						String difficulty = beatmap.getString("difficulty_name");
						String result = artist + " - " + title + " ("+difficulty+")";
						String urllink = "http://osu.ppy.sh/b/"+id;
						returnMaps = returnMaps+" || ["+urllink+" "+result+"]"; 
					};
					SendMessage(lobby.channel,sender + ": "+returnMaps);
				}
			}		
			else if (size == 0){
				SendMessage(lobby.channel,sender + ": 0 beatmaps found in current difficulty range!");
			}
			else if (size == 1) {
				//bot.bancho.sendMessage(sender, "Correct!");
				//int result = Info.getInt(1);
				String str = ""+Info.get(0);
				JSONObject beatmap = new JSONObject(str);
				String artist = beatmap.getString("artist");
				String title = beatmap.getString("title");
				String difficulty = beatmap.getString("difficulty_name");
				String rating = BigDecimal.valueOf(Math.round( (beatmap.getDouble("difficulty")*100d) )/100d).toPlainString();
				int bID = beatmap.getInt("beatmap_id");
				String result = artist + " - " + title + " [ "+difficulty+" ] - [ "+rating+"* ]";
				String result2 = "[http://osu.ppy.sh/b/"+bID+" Link]";
			}
			} catch ( JSONException | URISyntaxException | IOException e) {
				e.printStackTrace();
				SendMessage(sender, sender + ": Error");
			}
		return "";
	}
	
	public int getId(String name){
		int id = 0;
		for (Map.Entry<Integer, String> entry : usernames.entrySet()) {
			  if (entry.getValue().equals(name))			  
			  {
				  id = entry.getKey();
			  }
			}
		if (id == 0)
		{
			try {
				RequestConfig defaultRequestConfig = RequestConfig.custom()
					    .setSocketTimeout(10000)
					    .setConnectTimeout(10000)
					    .setConnectionRequestTimeout(10000)
					    .build();
				HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).build();
				URI uri = new URIBuilder()
						.setScheme("http")
						.setHost("osu.ppy.sh")
						.setPath("/api/get_user")
						.setParameter("k", configuration.apikey)
						.setParameter("u", ""+name)
						.setParameter("type", "string")
						.build(); 
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
	public String getUsername(int userId) {
	  if (usernames.containsKey(userId) && (!usernames.get(userId).equals("")) )
	    return usernames.get(userId);
	  
	  String username = ""; // get username with api
		try {
		RequestConfig defaultRequestConfig = RequestConfig.custom()
			    .setSocketTimeout(10000)
			    .setConnectTimeout(10000)
			    .setConnectionRequestTimeout(10000)
			    .build();
		HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).build();
		URI uri = new URIBuilder()
				.setScheme("http")
				.setHost("osu.ppy.sh")
				.setPath("/api/get_user")
				.setParameter("k", configuration.apikey)
				.setParameter("u", ""+userId)
				.setParameter("type", "id")
				.build(); 
		HttpGet request = new HttpGet(uri);
		HttpResponse response = httpClient.execute(request);
		InputStream content = response.getEntity().getContent();
		String stringContent = IOUtils.toString(content, "UTF-8");
		JSONArray array = new JSONArray(stringContent);
			if (array.length()>0)
				username = array.getJSONObject(0).getString("username");	
			} catch (URISyntaxException | JSONException | IOException e) {
			e.printStackTrace();
		}
	  usernames.put(userId, username);
	  return username;
	}
}