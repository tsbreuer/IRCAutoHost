package autohost.util;

import autohost.IRCBot;
import autohost.Lobby;

import static autohost.util.TimeUtils.MINUTE;
import static autohost.util.TimeUtils.SECOND;

public class LobbyChecker extends Thread {

	private IRCBot m_bot;
	public Lobby lobby;
	public boolean stopped = false;
	public long    startTime;
	
	public LobbyChecker(IRCBot bot, Lobby lobby) {
		m_bot = bot;
		this.lobby = lobby;
	}

	private void sendMessage(String message) {
		m_bot.getClient().sendMessage(lobby.channel, message);
	}

	public void run() {
		while (!stopped) {
			long currTime = System.currentTimeMillis();
			long mark = startTime + 10 * MINUTE;


			if (currTime >= mark) {
				startTime = currTime;
				sendMessage("!mp settings");
			}
			ThreadUtils.sleepQuietly(SECOND);
		}
	}

}
