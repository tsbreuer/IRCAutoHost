package autohost;

import java.util.ArrayList;
import java.util.List;

import org.jibble.pircbot.*;
import autohost.RateLimiter;
import autohost.utils.RateLimiterThread;
import java.util.regex.*;


public class Autohost extends PircBot {
	
	List<Lobby> Lobbies = new ArrayList<>();
	public List<RateLimiter> limiters = new ArrayList<>();
	private RateLimiterThread rate;
	public Autohost instance;
	
	public Autohost (){
		setName(Config.authName);	
		instance = this;
	}
	
	@Override
	public void onMessage(String channel, String sender, String login, String hostname, String message){	
		//System.out.println(">Channel>"+channel+" - "+sender+" : "+message);
			Pattern pattern = Pattern.compile("#mp_\\d"); // Is this a multi lobby channel?
			Matcher matcher = pattern.matcher(channel);
				if (matcher.matches()){
					for (Lobby lobby : Lobbies) {
						if (lobby.channel.equalsIgnoreCase(channel)){ // Is it an autohosted (by us) channel?
							ParseChannelMessage(lobby, message);
						}
						else
						{
							System.out.println("Warning: Channel not loaded? C:"+channel);
						}
					}
				}
	}
	
	@Override
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
			this.sendRawLine("PRIVMSG "+lobbyChannel+" !mp settings");
		}
	}
	public void onPrivateMessage(String sender, String login, String hostname, String message){	
		System.out.println(sender+" : "+message);
		message = message.trim();
		if (sender.equalsIgnoreCase("AutoHost")) {
			//this.sendRawLine("PRIVMSG BanchoBot !help");.
			//this.sendRawLine("PRIVMSG BanchoBot !mp make test");
		}
		else if (sender.equalsIgnoreCase("BanchoBot")) {
			//message.
		}
		if (message.startsWith("!")){
			message = message.substring(1);
			String[] args = message.split(" ");
			if (args.length == 1){
				if (args[0].equals("help")){
					this.sendRawLine("PRIVMSG "+sender+" This is a help message.");
				}
			}
			else if (args.length == 2){
				
			}
				
			
		}
		else
		{
			//this.sendRawLine("PRIVMSG "+sender+" This account is a bot. Command prefix is !. Send me !help for more info.");
			sendRawMessage(sender, "This account is a bot. Command prefix is !. Send me !help for more info.");
			
		}
	}
	public void NewLobbyMessage(String channel,String sender, String message)
	{
		
	}
	
	public void ParseChannelMessage(Lobby lobby, String message)
	{
		if (message.contains("Closed the match")){
			Lobbies.remove(lobby);
			System.out.println("Closed Match "+lobby.channel);
		}
	}
	
	public void sendRawMessage(String target, String message) {
		if (rate == null){
			rate = new RateLimiterThread(this.instance);
			rate.start();
			this.sendRawLine("PRIVMSG AutoHost return");
			System.out.println("New RateLimiter");
			
		}
		System.out.println(this.rate.getState());
		Boolean exists = false;
		for (RateLimiter limiter : this.limiters){
			if (limiter.target.equals(target)){
				limiter.addMessage(message);
				exists = true;
			}
		}
		if (!exists){
			System.out.println("New target. Add.");
			RateLimiter rlimiter = new RateLimiter(target, 200);
			rlimiter.addMessage(message);
			limiters.add(rlimiter);		
		}
	}
	
	public void IgnoreSend(String line){
		this.sendRawLine(line);
	}
	
	
}

