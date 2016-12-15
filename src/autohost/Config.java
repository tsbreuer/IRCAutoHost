package autohost;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;


public class Config {
	public String user;
	public String password;
	public String server;
	
	public Config(String path) throws IOException,FileNotFoundException  {
		Properties prop = new Properties();
		FileInputStream input = new FileInputStream(path);
		prop.load(input);
		this.user = prop.getProperty("user");
		this.password = prop.getProperty("password");
		this.server = prop.getProperty("server");
	}
}

