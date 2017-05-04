package autohost.utils;

import autohost.Autohost;
import autohost.IRCClient;
import autohost.Lobby;

public class ReconnectTimer extends Thread {

	private Autohost client;
	private IRCClient lobby;
	private boolean stopped = false;
	private long prevTime = System.currentTimeMillis();
	private long startTime;
	private long Timeout = 128 * 1000;
	private boolean added = false;

	public ReconnectTimer(IRCClient client, Autohost host) {
		this.lobby = client;
		this.client = host;
	}

	public void stopTimer() {
		stopped = true;
	}
	
	public void continueTimer(){
		stopped = false;
	}

	public boolean extendTimer() {
		if (added)
			return false;

		added = true;
		startTime = startTime + 1 * 60 * 1000;
		return true;

	}

	public void skipEvents() {
		startTime = System.currentTimeMillis() - 5000;
	}

	public void run() {
		while (!stopped) {
			// System.out.println("tick");
			long currTime = System.currentTimeMillis();
			
			if (currTime-lobby.LastConnection > Timeout){
				client.ReconnectAutoHost();
			}
			try {
				Thread.sleep(1000);
			} catch (Exception e) {
			}
			prevTime = currTime;
		}
	}
}
