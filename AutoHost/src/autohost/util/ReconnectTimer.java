package autohost.util;

import autohost.IRCBot;

import static autohost.util.TimeUtils.SECOND;

public class ReconnectTimer extends Thread {
	private static final long MESSAGE_TIMEOUT = 100 * SECOND;
	private static final long PING_TIMEOUT = 128 * SECOND;

	private final Object m_lockObject = new Object();
	private final IRCBot m_bot;

	private long    m_lastMessageAt = System.currentTimeMillis();
	private boolean m_waitingForPong = false;
	private long    m_pingSentAt = 0;

	public ReconnectTimer(IRCBot bot) {
		m_bot = bot;
	}

	public void messageReceived() {
		synchronized (m_lockObject) {
			m_waitingForPong = false;
			m_lastMessageAt = System.currentTimeMillis();
		}
	}

	public void run() {
		while (true) {
			synchronized (m_lockObject) {
				long now = System.currentTimeMillis();
				if (m_waitingForPong) {
					if (now - m_pingSentAt > PING_TIMEOUT) {
						System.out.println("Bancho didn't reply to our ping. Reconnecting.");
						m_waitingForPong = false;
						m_lastMessageAt = System.currentTimeMillis();
						m_bot.reconnect();
					}
				} else {
					if (now - m_lastMessageAt > MESSAGE_TIMEOUT) {
						System.out.println("Sending a ping! (" + now + ")");
						m_waitingForPong = true;
						m_pingSentAt = now;
						m_bot.getClient().write("PING " + now);
					}
				}
				ThreadUtils.sleepQuietly(SECOND);
			}
		}
	}
}
