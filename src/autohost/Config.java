package autohost;

//import autohost.utils.RateLimiterThread;
import autohost.utils.IRCClient;

public class Config {
	
	public String channel = "#";
	public static String authName = "HyPeX";
	public static String authToken = "";
	
	public static void main(String[] args) throws Exception {
		//Autohost bot = new Autohost ();
		//RateLimiterThread rate = new RateLimiterThread(bot);
		//bot.setVerbose(true);
		//bot.connect("irc.ppy.sh",6667,authToken);
		IRCClient irc = new IRCClient("irc.ppy.sh",6667,"HyPeX","56b0e8d1");

		
		
		//if (bot.isConnected()) {
		//	System.out.println("Connected!");
		//}
	}
}
