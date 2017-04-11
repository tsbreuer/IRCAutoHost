package autohost.utils;

import autohost.IRCClient;
import autohost.Lobby;

public class TimerThread extends Thread {

	private IRCClient client;
	private Lobby lobby;
	private boolean stopped = false;
	private long prevTime = System.currentTimeMillis();
	private long startTime;
	private long startAfter = 2 * 60 * 1000;
	private boolean added = false;

	public TimerThread(IRCClient handler, Lobby lobby) {
		this.client = handler;
		this.lobby = lobby;
	}

	public void stopTimer() {
		stopped = true;
	}
	
	public void continueTimer(){
		stopped = false;
		resetTimer();
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

	public void resetTimer() {
		added = false;
		startTime = System.currentTimeMillis() + startAfter + 200;
	}

	private void sendMessage(String message) {
		client.SendMessage(lobby.channel, message);
	}

	public void run() {
		resetTimer();
		while (!stopped) {
			// System.out.println("tick");
			long currTime = System.currentTimeMillis();
			long min3mark = startTime - 3 * 60 * 1000;
			long min2mark = startTime - 2 * 60 * 1000;
			long min1mark = startTime - 1 * 60 * 1000;
			long sec10mark = startTime - 10 * 1000;
			if (currTime >= min3mark && prevTime < min3mark) {
				sendMessage("Starting in 3 minutes. Please use !r or !ready if you're ready to start.");
			}
			if (currTime >= min2mark && prevTime < min2mark) {
				sendMessage("Starting in 2 minutes. Please use !r or !ready if you're ready to start.");
			}
			if (currTime >= min1mark && prevTime < min1mark) {
				sendMessage("Starting in 1 minute. Please use !r or !ready if you're ready to start.");
			}
			if (currTime >= sec10mark && prevTime < sec10mark) {
				sendMessage("!mp settings");
				sendMessage("Starting in 10 seconds.");
				
			}
			if (currTime >= startTime && prevTime <= startTime) {			
				client.tryStart(lobby);
			}
			try {
				Thread.sleep(1000);
			} catch (Exception e) {
			}
			prevTime = currTime;
		}
	}

	public void waitTimer() {
		this.resetTimer();
	}
}
