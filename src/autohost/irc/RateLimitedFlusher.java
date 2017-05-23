package autohost.irc;

class RateLimitedFlusher extends Thread {
    private IRCClient m_client;
    private int       m_delay;

    RateLimitedFlusher(IRCClient client, int delay) {
        m_client = client;
        m_delay = delay;
    }

    @Override
    public void run() {
        try {
            for (RateLimitedChannel limiter : m_client.getChannels().values()) {
                if (limiter.hasNext()) {
                    String line = limiter.poll();
                    if (line != null)
                        m_client.write(line);
                }
            }

            Thread.sleep(m_delay);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
