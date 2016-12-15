package autohost.utils;

import java.io.*;
import java.net.*;


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
		 register();
	}
	
	public void connect() throws UnknownHostException, IOException {
			// Connect to the server
			connectSocket = new Socket( server, port );
			out = new PrintStream( connectSocket.getOutputStream() );
		
		    // for debugging
		    inputThread = new InputDumper( connectSocket.getInputStream() );
		    inputThread.setDaemon( true );
		    inputThread.start();
	}
	  
	public void register() {
		Write( "PASS" + " " + password);
	    Write( "USER" + " " + user + " "+ user +" "+ server +" :realname");
	  }
	public void Write(String message){
		System.out.println(message);
		out.println(message);
	}
}
