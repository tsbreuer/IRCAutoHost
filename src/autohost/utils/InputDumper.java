package autohost.utils;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import autohost.Autohost;
import autohost.IRCClient;

public class InputDumper extends Thread {
	private BufferedReader reader;
	private IRCClient client;
	private Autohost autohost;
	public Boolean stopped = false;

	public InputDumper(InputStream in, IRCClient client, Autohost autohost) {
		this.client = client;
		this.reader = new BufferedReader(new InputStreamReader(in));
		this.autohost = autohost;
	}

	public void stopReading() throws IOException{
		this.stopped = true;
		this.interrupt();
		this.reader.close();
	}
	public void run() {
		if (stopped){
			return;
			}
		try {
			String msg = "";
			while ((msg = reader.readLine()) != null) {
				if (msg.indexOf("001") >= 0) {
					if (!msg.contains("cho@ppy.sh QUIT")) {
						System.out.println("Logged in");
						System.out.println("Line: " + msg);
					}
				}
				if (!msg.contains("cho@ppy.sh QUIT")) {
				//System.out.println("Line: " + msg);
				}
				if (msg.contains("PING")) {
					if (!msg.contains("cho@ppy.sh QUIT")) {
						String pingRequest = msg.substring(msg.indexOf("PING") + 5);
						client.Write("PONG " + pingRequest);
						client.LastConnection = System.currentTimeMillis();
					}
				} else if (msg.contains("PONG")) {
					if (!msg.contains("cho@ppy.sh QUIT")) {
						client.LastConnection = System.currentTimeMillis();
							if (msg.contains(client.LastMessagePING)){
							
							}
					}
				}
				client.log(msg);
				if ((System.currentTimeMillis() - client.LastConnection) > (70 * 1000)) {
					if (System.currentTimeMillis()-client.LastRequested > (5 * 1000)){			
					client.Write("PING " + System.currentTimeMillis());
					client.LastMessagePING = ""+System.currentTimeMillis();
					client.LastRequested = System.currentTimeMillis();
					System.out.println((System.currentTimeMillis() - client.LastConnection));
					}		
					if ((System.currentTimeMillis()-client.LastConnection) > (100*1000)){
						autohost.ReconnectAutoHost();
						System.out.println("Connection to bancho Lost. Reconnecting...");
					}
				}
				// if (!msg.contains("QUIT"))
				// System.out.println(msg);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}