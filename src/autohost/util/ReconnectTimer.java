package autohost.util;

import autohost.IRCBot;

public class ReconnectTimer extends Thread {
	private IRCBot m_bot;
	private boolean stopped = false;
	private long prevTime = System.currentTimeMillis();
	private long startTime;
	private long Timeout = 128 * 1000;
	private boolean added = false;

	public ReconnectTimer(IRCBot bot) {
		m_bot = bot;
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

			if (currTime-m_bot.LastConnection > Timeout){
				m_bot.reconnect();
			}
			ThreadUtils.sleepQuietly(1000);
			prevTime = currTime;
		}
	}
}
