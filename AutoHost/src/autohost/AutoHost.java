package autohost;

import moe.autohost.shared.Global;

import java.io.IOException;

public class AutoHost {
	public static final String VERSION = "3.0";

	public static AutoHost AutoHost;
	public Config          config;
	public IRCBot          irc;

	public static void main(String[] args) throws Exception {
		AutoHost = new AutoHost();
	}

	public AutoHost() throws IOException {
		this.config = new Config(Global.WORKING_DIRECTORY + "config.properties");
		this.irc = new IRCBot(this, config);
	}
}
