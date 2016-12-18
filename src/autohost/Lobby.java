package autohost;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Lobby {
	public String channel;
	public Integer LobbySize;
	public List<Integer> OPs;
	
	public String gamemode;
	public String name;
	public Integer minDifficulty;
	public Integer maxDifficulty;
	public Integer Graveyard;
	public String type;
	public Integer mpID;
	public String winCondition;
	public String currentBeatmapAuthor;
	public String currentBeatmapName;
	public Integer currentBeatmap;
	public List<Integer> beatmapQueue;
	public List<Integer> beatmapPlayed;
	public List<Integer> voteStart;
	public Map<Integer, Slot> slots = new HashMap<>();

	public Lobby (String channel){this.channel = channel;}
}
