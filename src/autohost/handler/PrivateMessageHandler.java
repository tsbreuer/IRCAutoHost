package autohost.handler;

import autohost.IRCBot;
import autohost.Lobby;
import autohost.irc.IRCClient;
import autohost.util.RegexUtils;

import java.util.Iterator;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PrivateMessageHandler {
	private final IRCBot    m_bot;
	private final IRCClient m_client;

	public PrivateMessageHandler(IRCBot bot) {
		m_bot = bot;
		m_client = m_bot.getClient();
	}

	public void handle(String sender, String message) {
		System.out.println(sender + ": " + message);

		if (sender.equalsIgnoreCase(m_client.getUser())) {
			return;
		}

		if (!message.startsWith("!")) {
			if (!sender.equalsIgnoreCase("BanchoBot")) {
				m_client.sendMessage(sender,
						"This account is a bot. Command prefix is !. Send me !help for more info.");
			}
			return;
		}

		message = message.trim().substring(1);
		String[] args = message.split(" ");
		switch (args[0]) {
		case "help":
		case "info":
			handleInfo(sender);
			break;
		case "reloadRooms":
			handleReloadRooms(sender);
			break;
		case "commands":
			handleCommands(sender);
			break;
		case "globalsay":
			handleGlobalSay(sender, message);
			break;
		case "recreate":
			handleRecreate(sender);
			break;
		case "droplobby":
			handleDropLobby(sender);
			break;
		case "reconnection":
			handleReconnection(sender);
			break;
		case "moveme":
			handleMoveMe(sender, message);
			break;
		case "createroom":
			handleCreateRoom(sender, message, args);
			break;
		case "reconnect":
			handleReconnect(sender, message);
			break;
		default:
			m_client.sendMessage(sender,
					"Unrecognized Command. Please check !help, or !commands");
		}
	}

	private void handleInfo(String sender) {
		m_client.sendMessage(sender, m_bot.getConfig().pmhelp);
		int i = 0;
		for (Lobby lobby : m_bot.getLobbies().values()) {
			i++;
			String password;
			if (lobby.Password.equalsIgnoreCase("")) {
				password = "Password: Disabled";
			} else {
				password = "Password: Enabled";
			}

			m_client.sendMessage(sender,
					"Lobby [" + i + "] || Name: " + lobby.name
							+ " || Stars: " + lobby.minDifficulty + "* - " + lobby.maxDifficulty
							+ "* || Slots: [" + lobby.slots.size() + "/16] || "
							+ password);
		}
	}

	private void handleReloadRooms(String sender) {
		if (!m_bot.isOP(sender)) return;

		for (Lobby lobby : m_bot.getLobbies().values()) {
			lobby.slots.clear();
			m_client.sendMessage(lobby.channel, "!mp settings");
			System.out.println("Reloading " + lobby.channel);
		}
	}

	private void handleCommands(String sender) {
		m_client.sendMessage(sender,
				"Commands: !createroom [name] | !droplobby | !recreate | !moveme [id/password]");
	}

	private void handleGlobalSay(String sender, String message) {
		if (!m_bot.isOP(sender)) return;

		Matcher globalmatch = RegexUtils.matcher(
				"globalsay (.+)",
				message);
		if (globalmatch.matches()) {
			m_client.sendMessage(sender, "Message sent");
			for (Lobby lobby : m_bot.getLobbies().values()) {
				m_client.sendMessage(lobby.channel,
						"GlobalMessage: " + globalmatch.group(1));
			}
		} else {
			m_client.sendMessage(sender,
					"Syntax error. Please use !globalsay [message]");
		}
	}

	private void handleRecreate(String sender) {
		Queue<Lobby> deadLobbies = m_bot.getDeadLobbies();
		Iterator<Lobby> iter = deadLobbies.iterator();
		while (iter.hasNext()) {
			Lobby lobby = iter.next();
			if (lobby.creatorName != null || lobby.creatorName.equals("")) {
				if (lobby.creatorName.equalsIgnoreCase(sender)) {
					iter.remove();
					m_bot.createNewLobby(lobby.name, lobby.minDifficulty, lobby.maxDifficulty, lobby.creatorName,
							lobby.OPLobby);
					m_client.sendMessage(sender, "Lobby is being created. Please wait...");
					return;
				}
			} else {
				iter.remove();
			}
		}
		for (Lobby lobby : deadLobbies) {
			if (lobby.creatorName.equalsIgnoreCase(sender)) {
				deadLobbies.remove(lobby);
				m_bot.createNewLobby(lobby.name, lobby.minDifficulty, lobby.maxDifficulty,
						lobby.creatorName, lobby.OPLobby);
				return;
			}
		}
	}

	private void handleDropLobby(String sender) {
		Queue<Lobby> deadLobbies = m_bot.getDeadLobbies();
		for (Lobby lobby : deadLobbies) {
			if (lobby.creatorName.equalsIgnoreCase(sender)) {
				deadLobbies.remove(lobby);
				m_client.sendMessage(sender,
						"Lobby dropped. You're now able to create a new one!");
				return;
			}
		}
	}

	private void handleReconnection(String sender) {
		if (!m_bot.isOP(sender)) return;

		try {
			m_bot.reconnect();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void handleMoveMe(String sender, String message) {
		if (!message.contains(" ")) return;

		Matcher matchMove = RegexUtils.matcher(
				"moveme (\\d+)",
				message);

		if (matchMove.matches()) {
			int moveMe = Integer.valueOf(matchMove.group(1));
			int i = 0;
			for (Lobby lobby : m_bot.getLobbies().values()) {
				i++;
				if (i == moveMe) {
					if (lobby.slots.size() < 16) {
						if (lobby.Password.equals("")) {
							m_client.sendMessage(lobby.channel, "!mp add " + sender);
						} else {
							if (matchMove.groupCount() < 2) {
								m_client.sendMessage(sender,
										"The lobby you selected has a password. Please use !moveme [lobby] [pw]");
							} else {
								if (matchMove.group(2).equals(lobby.Password)) {
									m_client.sendMessage(lobby.channel, "!mp add " + sender);
								}
							}
						}
					} else {
						m_client.sendMessage(sender, "Lobby is full, sorry");
					}
				}
			}
		} else {
			Matcher matchPW = RegexUtils.matcher(
					"moveme (.+)",
					message);
			if (!matchPW.matches()) {
				m_client.sendMessage(sender,
						"Wrong format, please use !moveme [lobby number provided by help]");
			} else {
				for (Lobby lobby : m_bot.getLobbies().values()) {
					if (lobby.Password.equals(matchPW.group(1))) {
						if (lobby.slots.size() < lobby.LobbySize) {
							m_client.sendMessage(lobby.channel, "!mp add " + sender);
						} else
							m_client.sendMessage(sender, "Lobby is full, try again later ;)");

						return;
					}

				}
				m_client.sendMessage(sender, "No lobby matched your password.");
			}
		}
	}

	private void handleCreateRoom(String sender, String message, String[] args) {
		if (args.length <= 1) {
			m_client.sendMessage(sender,
					"Please include all arguments. Usage: !createroom <name>");
			return;
		}
		boolean isOP = m_bot.isOP(sender);
		if (!isOP) {
			for (Lobby lobby : m_bot.getLobbies().values()) {
				if (lobby.creatorName.equalsIgnoreCase(sender)) {
					m_client.sendMessage(sender, "You already have a live lobby!");
					return;
				}
			}
			for (Lobby lobby : m_bot.getDeadLobbies()) {
				if (lobby.creatorName.equalsIgnoreCase(sender)) {
					m_client.sendMessage(sender,
							"You already have an older lobby, please do !recreate to revive it, or !droplobby to remove it from the list.");
					return;
				}
			}
		}
		Pattern roomNamePattern = Pattern.compile("createroom (.+)");
		Matcher roomNameMatcher = roomNamePattern.matcher(message);
		if (roomNameMatcher.matches()) {
			// --TODO
			String roomName = roomNameMatcher.group(1);
			double mindiff = 4;
			double maxdiff = 5;
			m_bot.createNewLobby(roomName, mindiff, maxdiff, sender, isOP);
			m_client.sendMessage(sender,
					"Creating room, please wait 1 second and pm me !help to ask for a move");
		} else {
			m_client.sendMessage(sender,
					"Incorrect Syntax. Please use !createroom <name>");
		}
	}

	private void handleReconnect(String sender, String message) {
		if (!message.contains(" ")) {
			m_client.sendMessage(sender,
					"Please include a lobby id. Usage: !reconnect <mp id>");
			return;
		}

		boolean isOP = m_bot.isOP(sender);

		Matcher roomIDMatcher = RegexUtils.matcher(
				"reconnect (.+)",
				message);
		if (roomIDMatcher.matches()) {
			m_bot.reconnectLobby(sender, roomIDMatcher.group(1), isOP);
		} else {
			m_client.sendMessage(sender,
					"Incorrect Syntax. Usage: !reconnect <mp id>");
		}
	}
}
