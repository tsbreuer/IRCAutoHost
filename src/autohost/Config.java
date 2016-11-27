package autohost;

public class Config {
	
	public String channel = "#";
	public static String authName = "HyPeX";
	public static String authToken = "";
	
	public static void main(String[] args) throws Exception {
		Autohost bot = new Autohost ();
		//bot.setVerbose(true);
		bot.connect("irc.ppy.sh",6667,authToken);
		
		
		if (bot.isConnected()) {
			System.out.println("Connected!");
		}
	}
}
