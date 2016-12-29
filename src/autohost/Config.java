package autohost;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Config {
	public String user;
	public String password;
	public String server;
	public int rate;
	public String info;
	public String author;
	public String pmhelp;
	public String apikey;
	public List<Integer> ops = new ArrayList<>();

	public Config(String path) throws IOException, FileNotFoundException {
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
		this.apikey = prop.getProperty("apikey");
		String op = prop.getProperty("operators");
		String[] opList = op.trim().split(",");
		for (String id : opList) {
			int playerid = Integer.valueOf(id);
			ops.add(playerid);
		}
	}
}
