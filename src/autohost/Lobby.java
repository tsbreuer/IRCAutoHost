package autohost;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import autohost.utils.Beatmap;
import autohost.utils.Slot;
import autohost.utils.TimerThread;

public class Lobby {
	public String channel;
	public Integer LobbySize = 16;
	public List<Integer> OPs = new LinkedList<>();;

	public List<Integer> voteStart = new LinkedList<>();
	public List<Integer> voteskip  = new LinkedList<>();;
	
	public TimerThread timer;
	public String name;

	public Integer mpID;
	public String currentBeatmapAuthor = "HyPeX";
	public String currentBeatmapName = "Some random shit";
	public Integer currentBeatmap = 0;
	public Queue<Beatmap> beatmapQueue = new LinkedList<>();
	public Queue<Beatmap> beatmapPlayed = new LinkedList<>();

	public Map<Integer, Slot> slots = new HashMap<>();
	public Map<Integer, String> scores = new HashMap<>();
	public String[] genres = new String[]{ "any" , "unspecified", "video game", "anime", "rock", "pop", "other", "novelty", "error", "hip hop", "electronic"};
	
	
	/* 
	 * Lobby Specific Settings
	 * 	These set up the lobbies to differ from other lobbies
	 */
	
	
	public String teamgamemode; // Team Type of the lobby. Solo? TeamVs?
	
	public String winCondition; // Win type. Score, Scorev2, etc
	
	public Integer Graveyard; // 4 = loved, 3 = qualified, 2 = approved, 1 = ranked, 0 = pending, -1 = WIP, -2 = graveyard

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
	public Integer minDifficulty = 4;
	public Integer maxDifficulty = 5;

	public Lobby(String channel) {
		this.channel = channel;
	}
	
}
