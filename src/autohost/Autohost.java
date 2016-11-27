package autohost;

import org.jibble.pircbot.*;

public class Autohost extends PircBot {

	public Autohost (){
		this.setName(Config.authName);
	}
	
	@Override
	public void onMessage(String channel, String sender, String login, String hostname, String message){	
		System.out.println("#"+channel+" - "+sender+" : "+message);
	}
	

	public void onPrivateMessage( String sender, String login, String hostname, String message){	
		System.out.println(sender+" : "+message);
		if (sender.equalsIgnoreCase("AutoHost")) {
			this.sendRawLine("PRIVMSG BanchoBot !help");
		}
	}
}