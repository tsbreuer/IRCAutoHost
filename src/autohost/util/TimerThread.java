package autohost.util;

import autohost.IRCBot;
import autohost.Lobby;

import static autohost.util.TimeUtils.MINUTE;
import static autohost.util.TimeUtils.SECOND;

public class TimerThread extends Thread {
	private IRCBot  m_bot;
	private Lobby   lobby;
	private boolean stopped = false;
	private long    prevTime = System.currentTimeMillis();
	private long    startTime;
	public long    startAfter = 2 * MINUTE;
	public boolean added = false;
	public boolean starting = false;
	public long startingTime = System.currentTimeMillis();
	public boolean askedAlready= false;
	private long min3mark;
	private long min2mark;
	private long min1mark;
	private long sec10mark;
	private long currTime;

	public TimerThread(IRCBot bot, Lobby lobby) {
		m_bot = bot;
		this.lobby = lobby;
	}

	public void stopTimer() {
		stopped = true;
	}

	public void continueTimer() {
		stopped = false;
		resetTimer();
	}

	public boolean extendTimer() {
		if (added)
			return false;

		added = true;
		startTime = startTime + MINUTE;
		return true;

	}

	public void skipEvents() {
		startTime = System.currentTimeMillis() - 5000;
	}

	public void resetTimer() {
		added = false;
		starting = false;
		askedAlready = false;
		startTime = System.currentTimeMillis() + startAfter + 200;
	}

	private void sendMessage(String message) {
		m_bot.getClient().sendMessage(lobby.channel, message);
	}

	public void run() {
		resetTimer();
		while (!stopped) {
			currTime = System.currentTimeMillis();
			if (currTime-startingTime >= 1*MINUTE) {
				askedAlready = true;
				if (starting) {
				m_bot.start(lobby);
				}
			}
			min3mark = startTime - 3 * MINUTE;
			min2mark = startTime - 2 * MINUTE;
			min1mark = startTime - 1 * MINUTE;
			sec10mark = startTime - 10 * SECOND;
			if (currTime >= min3mark && prevTime < min3mark) {
				sendMessage("Starting in 3 minutes. Please use !r or !ready if you're ready to start.");
			}
			if (currTime >= min2mark && prevTime < min2mark) {
				sendMessage("Starting in 2 minutes. Please use !r or !ready if you're ready to start.");
			}
			if (currTime >= min1mark && prevTime < min1mark) {
				sendMessage("Starting in 1 minute. Please use !r or !ready if you're ready to start. If you need more time, do !wait.");
			}
			if (currTime >= sec10mark && prevTime < sec10mark) {
				lobby.slots.clear();
				sendMessage("!mp settings");
				sendMessage("Starting in 10 seconds.");

			}
			if (currTime >= startTime && prevTime <= startTime) {
				m_bot.tryStart(lobby);
			}
			ThreadUtils.sleepQuietly(SECOND);
			prevTime = currTime;
		}
	}

	public void waitTimer() {
		this.resetTimer();
	}
}
