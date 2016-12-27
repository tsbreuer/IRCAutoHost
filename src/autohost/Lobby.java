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
	public List<Integer> OPs = new LinkedList<>(); ;
	
	public TimerThread timer;
	public String gamemode;
	public String name;
	public Integer minDifficulty;
	public Integer maxDifficulty;
	public Integer Graveyard;  // 4 = loved, 3 = qualified, 2 = approved, 1 = ranked, 0 = pending, -1 = WIP, -2 = graveyard
	public String type; // 0 = osu!, 1 = Taiko, 2 = CtB, 3 = osu!mania
	public Integer mpID;
	public String winCondition;
	public String currentBeatmapAuthor;
	public String currentBeatmapName;
	public Integer currentBeatmap;
	public Queue<Beatmap> beatmapQueue = new LinkedList<>();
	public Queue<Beatmap> beatmapPlayed = new LinkedList<>();
	public List<Integer> voteStart = new LinkedList<>();
	public Map<Integer, Slot> slots = new HashMap<>();
	public Map<Integer, String> scores = new HashMap<>();
	
	public Lobby (String channel){this.channel = channel;}
	
}
