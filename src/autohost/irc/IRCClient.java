package autohost.irc;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class IRCClient {
    private static final int DEFAULT_DELAY = 200;

    private final String m_address;
    private final int    m_port;
    private final String m_user;
    private final String m_password;

    private Socket      m_socket;
    private PrintStream m_outStream;

    private RateLimitedFlusher              m_flusher;
    private Map<String, RateLimitedChannel> m_channels;
    private int                             m_delay;

    public IRCClient(String address, int port, String user, String password) {
        m_address = address;
        m_port = port;
        m_user = user;
        m_password = password;

        m_channels = new HashMap<>();
        m_delay = DEFAULT_DELAY;
    }

    public void setDelay(int delay) {
        m_delay = delay;
    }

    public InputStream getInputStream() throws IOException {
        return m_socket.getInputStream();
    }

    public String getUser() { return m_user; }

    Map<String, RateLimitedChannel> getChannels() { return m_channels; }

    public void connect() throws IOException {
        m_socket = new Socket(m_address, m_port);
        m_outStream = new PrintStream(m_socket.getOutputStream());

        m_flusher = new RateLimitedFlusher(this, m_delay);
        m_flusher.start();
    }

    public void disconnect() throws IOException {
        m_outStream.close();
        m_socket.close();
    }

    public void write(String message) {
        write(message, false);
    }

    private void write(String message, boolean censor) {
        if (!censor) {
            System.out.println(message);
        }
        m_outStream.println(message);
    }

    public void register() {
        write("PASS" + " " + m_password, true);
        write("NICK" + " " + m_user);
        write("USER" + " " + m_user + " HyPeX irc.ppy.sh : Osu! Autohost Bot");
    }

    public void sendMessage(String channel, String message) {
        if (!m_channels.containsKey(channel)) {
            m_channels.put(channel, new RateLimitedChannel(channel, m_delay));
        }

        m_channels.get(channel).addMessage(message);
    }
}
