package autohost.irc;

import java.util.LinkedList;
import java.util.Queue;

class RateLimitedChannel {
	private int           m_delay;
	private String        m_channel;
	private Queue<String> m_messages;
	private long          m_lastSentTime;

	RateLimitedChannel(String channel, int delay) {
		m_channel = channel;
		m_delay = delay;
		m_messages = new LinkedList<>();
	}

	void addMessage(String message) {
		m_messages.add(message);
	}

	String poll() {
		try {
			long currentTime = System.currentTimeMillis();
			if ((currentTime - m_lastSentTime) >= m_delay) {
				String msg = m_messages.poll();
				if (msg != null) {
					m_lastSentTime = currentTime;
					return "PRIVMSG " + m_channel + " " + msg;

				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	boolean hasNext() {
		return !m_messages.isEmpty();
	}
}
