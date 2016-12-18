package autohost;

<<<<<<< HEAD
//import autohost.utils.RateLimiterThread;
import autohost.utils.IRCClient;
=======
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

>>>>>>> 59a28bb224cb54d1921b76ae731f2771ea56f8e4

public class Config {
	public String user;
	public String password;
	public String server;
	public int rate;
	public String info;
	public String author;
	public String pmhelp;
	public List<Integer> ops = new ArrayList<>();
	
<<<<<<< HEAD
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
=======
	public Config(String path) throws IOException,FileNotFoundException  {
		Properties prop = new Properties();
		FileInputStream input = new FileInputStream(path);
		prop.load(input);
		this.user = prop.getProperty("user");
		this.password = prop.getProperty("password");
		this.server = prop.getProperty("server");
		this.rate = Integer.parseInt(prop.getProperty("rate"));
		this.info = prop.getProperty("info");
		this.pmhelp = prop.getProperty("pmhelp");
		this.author = prop.getProperty("author");
		String op = prop.getProperty("operators");
		String[] opList = op.trim().split(",");
		for (String id : opList){
			int playerid = Integer.valueOf(id);
			ops.add(playerid);
		}
>>>>>>> 59a28bb224cb54d1921b76ae731f2771ea56f8e4
	}
}

