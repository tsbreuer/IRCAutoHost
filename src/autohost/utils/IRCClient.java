package autohost.utils;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import autohost.Config;
import autohost.Lobby;
import autohost.RateLimiter;
// Okay, technically this isnt an until, since the entire fucking bot works here.
// But moving this to main would mean work.
// And no one likes work
// Especially a lazy 19 year old in vacations
// Which means, fuck it, i'll just leave it here.

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
	  
	
	@SuppressWarnings("static-access")
	public IRCClient ( Config config) throws UnknownHostException, IOException {
		 // Define all settings. Meh.
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
		Pattern roomName = Pattern.compile("Room Name: (.+), History: http://osu.ppy.sh/mp/(.+)");
		Pattern teamMode = Pattern.compile("Team Mode: (.+), Win condition: (.+)");
		Pattern beatmap = Pattern.compile("Beatmap: https://osu.ppy.sh/b/(\\d+) (.+)");
		Pattern players = Pattern.compile("Players: (\\d+)");
		Pattern slot = Pattern.compile("Slot: (\\d+) (.+) (https://osu.ppy.sh/u/(\\d+)) (.+)");
		
		//Matcher RoomNameMatch = roomName.matcher(message);
	}
	
	public void PrivateMessage(String target, String sender, String message){
		System.out.println(sender+": "+message);
		message = message.trim();
		if (message.startsWith("!")){
			message = message.substring(1);
			String[] args = message.split(" ");
				if (args[0].equalsIgnoreCase("help")){
					SendMessage(sender, "This is a help message.");
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
}
