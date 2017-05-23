package autohost;

import java.io.IOException;
import java.net.UnknownHostException;


public class Autohost {
    public static Autohost    AutoHost;
    public Config             config;
    public IRCBot irc;

    public static void main(String[] args) throws Exception {
        AutoHost = new Autohost();
    }

    public Autohost() throws IOException {
        this.config = new Config("config.properties");
        this.irc = new IRCBot(this, config);
    }

    public void ReconnectAutoHost() {
        try {
            this.irc.stopIRC();
            this.irc = new IRCBot(this, config, irc.getLobbies(), irc.LobbyCreation, irc.DeadLobbies, irc.usernames);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }
}
