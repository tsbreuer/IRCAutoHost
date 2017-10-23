package autohost.irc;

import java.util.ConcurrentModificationException;
import java.util.Iterator;

import autohost.util.ThreadUtils;

class RateLimitedFlusher extends Thread {
	private IRCClient m_client;
	private int m_delay;

	RateLimitedFlusher(IRCClient client, int delay) {
		m_client = client;
		m_delay = delay;
	}

	@Override
	public void run() {
		try {
			while (true) {
				Iterator<RateLimitedChannel> it = m_client.getChannels().values().iterator();
				synchronized (it) {
					while (it.hasNext()) {
						RateLimitedChannel limiter = it.next();
						String line = limiter.poll();
						if (line != null)
							m_client.write(line);
					}
					ThreadUtils.sleepQuietly(m_delay);
				}
			}
		} catch (ConcurrentModificationException e) {
			e.printStackTrace();
		}
	}
}
