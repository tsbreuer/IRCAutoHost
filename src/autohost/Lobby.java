package autohost;

import autohost.util.Beatmap;
import autohost.util.Request;
import autohost.util.Slot;
import autohost.util.TimerThread;

import java.util.*;

public class Lobby {
	public String channel = "";
	public Integer LobbySize = 16;
	public List<Integer> OPs = new LinkedList<>();

	public List<String> voteStart = new LinkedList<>();
	public List<String> voteskip  = new LinkedList<>();

	public TimerThread timer;
	public String name = "";
	public Boolean OPLobby = false;
	public String creatorName;

	public int maxLength = 360;


	public String Password = "";
	public Boolean Playing = false;
	public Integer mpID;


	public Boolean rejoined = false;

	public String currentBeatmapAuthor = "HyPeX";
	public String currentBeatmapName = "Some random shit";
	public Integer currentBeatmap = 0;

	public Double maxAR =  0.0;

	public Boolean TrueRandom = true;

	public Queue<Beatmap> beatmapQueue = new LinkedList<>();
	public Queue<Beatmap> beatmapPlayed = new LinkedList<>();
	public Boolean DoubleTime = false;
	public Boolean NightCore = false;
	public Boolean HalfTime = false;

	public Map<Integer, Slot> slots = new HashMap<>();
	public Map<Integer, lt.ekgame.beatmap_analyzer.beatmap.Beatmap> beatmaps = new HashMap<>();
	public Map<String, Request> requests = new HashMap<>();

	public Map<String, Integer> afk = new HashMap<>();
	public Map<String, Integer> scores = new HashMap<>();

	public String[] genres = new String[]{ "any" , "unspecified", "video game", "anime", "rock", "pop", "other", "novelty", "error", "hip hop", "electronic"};
	public Map<Integer, Boolean> statusTypes =  new HashMap<>();

	/*
	 * Lobby Specific Settings
	 * 	These set up the lobbies to differ from other lobbies
	 */

	// limit to X year beatamps. Why not.
	public Boolean limitDate = false;
	public int minyear = 2011;
	public int maxyear = 2100;

	public Boolean lockAdding = false;

	public String teamgamemode; // Team Type of the lobby. Solo? TeamVs?

	public String winCondition; // Win type. Score, Scorev2, etc

	public Integer status; // 4 = loved, 3 = qualified, 2 = approved, 1 = ranked, 0 = pending, -1 = WIP, -2 = graveyard

	public Boolean loved = true;
	public Boolean qualified = true;
	public Boolean approved = true;
	public Boolean ranked = true;
	public Boolean pending = false;
	public Boolean WIP = false;
	public Boolean graveyard = false;

	public Boolean onlyType = true; // Lock lobby to type
	public String type; // 0 = osu!, 1 = Taiko, 2 = CtB, 3 = osu!mania


	public Boolean onlyTags = false; // Lock lobby to tags
	public Boolean inclusiveTags = false; // Songs must contain ALL tags instead of one of the listed
	public String[] Tags;


	public Boolean onlyGenre = false; // Unrecommended. This is usually // inaccurate
	public String genre = "0"; // 0 = any, 1 = unspecified, 2 = video game, 3 =
								// anime, 4 = rock, 5 = pop, 6 = other, 7 =
								// novelty, 9 = hip hop, 10 = electronic (note
								// that there's no 8)


	public Boolean onlyDifficulty = true; // Lock lobby to difficulty
	public Double minDifficulty = (double) 4;
	public Double maxDifficulty = (double) 5;
	public Integer previousBeatmap;


	public Lobby(String channel) {
		this.channel = channel;
		this.statusTypes.put(4, loved);
		this.statusTypes.put(3, qualified);
		this.statusTypes.put(2, approved);
		this.statusTypes.put(1, ranked);
		this.statusTypes.put(0, pending);
		this.statusTypes.put(-1, WIP);
		this.statusTypes.put(-2, graveyard);
	}

	public Lobby() {
		// 4 = loved, 3 = qualified, 2 = approved, 1 = ranked, 0 = pending, -1 = WIP, -2 = graveyard
		this.statusTypes.put(4, loved);
		this.statusTypes.put(3, qualified);
		this.statusTypes.put(2, approved);
		this.statusTypes.put(1, ranked);
		this.statusTypes.put(0, pending);
		this.statusTypes.put(-1, WIP);
		this.statusTypes.put(-2, graveyard);
	}

	public Boolean votestarted(String user){
		Boolean didvote = false;
		for (String player : voteStart){
			if (user.equalsIgnoreCase(player))
				didvote = true;
		}
		return didvote;
	}
	public Boolean votedskip(String user){
		Boolean didvote = false;
		for (String player : voteskip){
			if (user.equalsIgnoreCase(player))
				didvote = true;
		}
		return didvote;
	}

	public boolean isOP(int userId) {
		for (int id : OPs) {
			if (id == userId) return true;
		}
		return false;
	}
}
