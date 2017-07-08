package autohost.irc;

import autohost.util.ThreadUtils;

class RateLimitedFlusher extends Thread {
	private IRCClient m_client;
	private int       m_delay;

	RateLimitedFlusher(IRCClient client, int delay) {
		m_client = client;
		m_delay = delay;
	}

	@Override
	public void run() {
		while (true) {
			for (RateLimitedChannel limiter : m_client.getChannels().values()) {
				if (limiter.hasNext()) {
					String line = limiter.poll();
					if (line != null)
						m_client.write(line);
				}
			}
			ThreadUtils.sleepQuietly(m_delay);
		}
	}
}
