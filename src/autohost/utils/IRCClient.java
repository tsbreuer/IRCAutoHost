package autohost.utils;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	  
	//private static 
	 public IRCClient ( String server, int port, String user, String password) throws UnknownHostException, IOException {
		 // Define all settings. Meh.
		 this.server = server;
		 this.port = port;
		 this.user = user;
		 this.password = password;
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
	}
	
	public void log(String line){
		if (line.contains("cho@ppy.sh QUIT :") || (line.contains("PING cho.ppy.sh")) || (line.contains("PONG cho.ppy.sh"))) {
			return;
		}
		System.out.println(line);
		Pattern pattern = Pattern.compile("JOIN :#mp_\\d+");
		Matcher matcher = pattern.matcher(line);
		if (matcher.find()){
			//System.out.println(line);
			String lobbyChannel = line.substring(matcher.start()+6);
			Lobby lobby = new Lobby(lobbyChannel);
			Lobbies.add(lobby);
			Write("PRIVMSG "+lobbyChannel+" !mp settings");
		}
	}
	  
	public void register() throws InterruptedException {
		Write( "PASS" + " " + password);
		Write( "NICK" + " " + user);
	    Write( "USER" + " " + user + " HyPeX irc.ppy.sh : Osu! Autohost Bot");
	  }
	public void Write(String message){
		System.out.println(message);
		out.println(message);
	}
}
