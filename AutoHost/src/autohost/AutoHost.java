package autohost;

import moe.autohost.shared.Global;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

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


	// Ghetto webservice for our website
	private static class WebSocket{

		private AutoHost bot;
		private Boolean isRunning = true;

		public WebSocket(AutoHost bot) {
			this.bot = bot;

			try (ServerSocket socket = new ServerSocket(4500)) {
				Socket client = socket.accept();
				while (isRunning){

					DataInputStream input = new DataInputStream(client.getInputStream());
					DataOutputStream output = new DataOutputStream(client.getOutputStream());
				if (!client.getInetAddress().equals(InetAddress.getLocalHost())){
					output.writeUTF("I only accept localhost connections.");
					return;
				}
					if (input.readUTF().equals("getAllLobbies")){


					output.writeUTF(String.valueOf(bot.irc.getLobbies()));
					}

					client = socket.accept();
				}
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}


		}
	}
}
