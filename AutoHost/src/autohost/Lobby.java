package autohost;

import autohost.util.Beatmap;
import autohost.util.Request;
import autohost.util.Slot;
import autohost.util.TimerThread;
import org.json.JSONObject;

import java.util.*;

public class Lobby {
	private String 				channel = "";
	private Integer 			lobbySize = 16;
	private Integer 			lobbyNumber;

	private List<Integer> 		ops = new LinkedList<>();


	private List<String> 		voteStart = new LinkedList<>();
	private List<String> 		voteSkip = new LinkedList<>();

	private Boolean 			retryForMap = false;
	private TimerThread 		timer;
	private String 				name = "";
	private Boolean 			opLobby = false;
	private String 				creatorName = "";

	private int 				maxLength = 300;
	private int 				minLength = 0;

	private String 				password = "";
	private Boolean 			playing = false;
	private Integer 			mpID;


	private Boolean 			rejoined = false;

	private String 				currentBeatmapAuthor = "HyPeX";
	private String 				currentBeatmapName = "Some random shit";
	private Beatmap 			currentBeatmap = null;

	private Double 				maxAR = 0.0;

	private Integer 			keys = 3;
	private Boolean 			keyLimit = false;

	private Boolean 			trueRandom = true;

	private Queue<Beatmap> 		beatmapQueue = new LinkedList<>();
	private Queue<Beatmap> 		beatmapPlayed = new LinkedList<>();
	private Boolean 			doubleTime = false;
	private Boolean 			nightCore = false;
	private Boolean 			halfTime = false;

	private Map<Integer, Slot> 		slots = new HashMap<>();
	//public Map<Integer, lt.ekgame.beatmap_analyzer.beatmap.Beatmap> beatmaps = new HashMap<>();
	private Map<String, Request> 	requests = new HashMap<>();
	private Map<String, Integer> 	afk = new HashMap<>();
	private Map<String, Integer> 	scores = new HashMap<>();

	private String[] 				genres = new String[]{"any", "unspecified", "video game", "anime", "rock", "pop", "other", "novelty", "error", "hip hop", "electronic"};
	private Map<Integer, Boolean> 	statusTypes = new HashMap<>();

	/*
	 * Lobby Specific Settings
	 * 	These set up the lobbies to differ from other lobbies
	 */

	// limit to X year beatmaps. Why not.
	private Boolean					limitDate = false;
	private int						minYear = 2011;
	private int 					maxYear = 2100;

	private Boolean lockAdding = false;

	private String teamgamemode; // Team Type of the lobby. Solo? TeamVs?

	private String winCondition; // Win type. Score, Scorev2, etc

	private Integer status; // 4 = loved, 3 = qualified, 2 = approved, 1 = ranked, 0 = pending, -1 = WIP, -2 = graveyard

	private Boolean loved = true;
	private Boolean qualified = true;
	private Boolean approved = true;
	private Boolean ranked = true;
	private Boolean pending = false;
	private Boolean WIP = false;
	private Boolean graveyard = false;

	private Boolean onlyType = true; // Lock lobby to type
	private String type; // 0 = osu!, 1 = Taiko, 2 = CtB, 3 = osu!mania


	private Boolean onlyTags = false; // Lock lobby to tags
	private Boolean inclusiveTags = false; // Songs must contain ALL tags instead of one of the listed
	private String[] Tags;


	private Boolean onlyGenre = false; // Unrecommended. This is usually // inaccurate
	private String genre = "0"; // 0 = any, 1 = unspecified, 2 = video game, 3 =
	// anime, 4 = rock, 5 = pop, 6 = other, 7 =
	// novelty, 9 = hip hop, 10 = electronic (note
	// that there's no 8)


	private Boolean onlyDifficulty = true; // Lock lobby to difficulty
	private Double minDifficulty = (double) 4;
	private Double maxDifficulty = (double) 5;
	private Beatmap previousBeatmap;
	private boolean permanent = false;


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

	public Boolean votestarted(String user) {
		Boolean didvote = false;
		for (String player : voteStart) {
			if (user.equalsIgnoreCase(player))
				didvote = true;
		}
		return didvote;
	}

	public Boolean votedskip(String user) {
		Boolean didvote = false;
		for (String player : voteSkip) {
			if (user.equalsIgnoreCase(player))
				didvote = true;
		}
		return didvote;
	}

	public boolean isOP(int userId) {
		for (int id : ops) {
			if (id == userId) return true;
		}
		return false;
	}

	@Override
	public String toString() {
		return (new JSONObject(this).toString());
	}

	public String getChannel() {
		return channel;
	}

	public Integer getLobbySize() {
		return lobbySize;
	}

	public Integer getLobbyNumber() {
		return lobbyNumber;
	}

	public List<Integer> getOPs() {
		return ops;
	}

	public List<String> getVoteStart() {
		return voteStart;
	}

	public List<String> getVoteskip() {
		return voteSkip;
	}

	public Boolean getRetryForMap() {
		return retryForMap;
	}

	public TimerThread getTimer() {
		return timer;
	}

	public String getName() {
		return name;
	}

	public Boolean getOPLobby() {
		return opLobby;
	}

	public String getCreatorName() {
		return creatorName;
	}

	public int getMaxLength() {
		return maxLength;
	}

	public int getMinLength() {
		return minLength;
	}

	public String getPassword() {
		return password;
	}

	public Boolean getPlaying() {
		return playing;
	}

	public Integer getMpID() {
		return mpID;
	}

	public Boolean getRejoined() {
		return rejoined;
	}

	public String getCurrentBeatmapAuthor() {
		return currentBeatmapAuthor;
	}

	public String getCurrentBeatmapName() {
		return currentBeatmapName;
	}

	public Beatmap getCurrentBeatmap() {
		return currentBeatmap;
	}

	public Double getMaxAR() {
		return maxAR;
	}

	public Integer getKeys() {
		return keys;
	}

	public Boolean getKeyLimit() {
		return keyLimit;
	}

	public Boolean getTrueRandom() {
		return trueRandom;
	}

	public Queue<Beatmap> getBeatmapQueue() {
		return beatmapQueue;
	}

	public Queue<Beatmap> getBeatmapPlayed() {
		return beatmapPlayed;
	}

	public Boolean getDoubleTime() {
		return doubleTime;
	}

	public Boolean getNightCore() {
		return nightCore;
	}

	public Boolean getHalfTime() {
		return halfTime;
	}

	public Map<Integer, Slot> getSlots() {
		return slots;
	}

	public Map<String, Request> getRequests() {
		return requests;
	}

	public Map<String, Integer> getAfk() {
		return afk;
	}

	public Map<String, Integer> getScores() {
		return scores;
	}

	public String[] getGenres() {
		return genres;
	}

	public Map<Integer, Boolean> getStatusTypes() {
		return statusTypes;
	}

	public Boolean getLimitDate() {
		return limitDate;
	}

	public int getMinYear() {
		return minYear;
	}

	public int getMaxYear() {
		return maxYear;
	}

	public Boolean getLockAdding() {
		return lockAdding;
	}

	public String getTeamgamemode() {
		return teamgamemode;
	}

	public String getWinCondition() {
		return winCondition;
	}

	public Integer getStatus() {
		return status;
	}

	public Boolean getLoved() {
		return loved;
	}

	public Boolean getQualified() {
		return qualified;
	}

	public Boolean getApproved() {
		return approved;
	}

	public Boolean getRanked() {
		return ranked;
	}

	public Boolean getPending() {
		return pending;
	}

	public Boolean getWIP() {
		return WIP;
	}

	public Boolean getGraveyard() {
		return graveyard;
	}

	public Boolean getOnlyType() {
		return onlyType;
	}

	public String getType() {
		return type;
	}

	public Boolean getOnlyTags() {
		return onlyTags;
	}

	public Boolean getInclusiveTags() {
		return inclusiveTags;
	}

	public String[] getTags() {
		return Tags;
	}

	public Boolean getOnlyGenre() {
		return onlyGenre;
	}

	public String getGenre() {
		return genre;
	}

	public Boolean getOnlyDifficulty() {
		return onlyDifficulty;
	}

	public Double getMinDifficulty() {
		return minDifficulty;
	}

	public Double getMaxDifficulty() {
		return maxDifficulty;
	}

	public Beatmap getPreviousBeatmap() {
		return previousBeatmap;
	}

	public boolean isPermanent() {
		return permanent;
	}

	public void setChannel(String channel) {
		this.channel = channel;
	}

	public void setLobbySize(Integer lobbySize) {
		lobbySize = lobbySize;
	}

	public void setLobbyNumber(Integer lobbyNumber) {
		this.lobbyNumber = lobbyNumber;
	}

	public void setOPs(List<Integer> OPs) {
		this.ops = OPs;
	}

	public void setVoteStart(List<String> voteStart) {
		this.voteStart = voteStart;
	}

	public void setVoteskip(List<String> voteskip) {
		this.voteSkip = voteskip;
	}

	public void setRetryForMap(Boolean retryForMap) {
		this.retryForMap = retryForMap;
	}

	public void setTimer(TimerThread timer) {
		this.timer = timer;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setOPLobby(Boolean OPLobby) {
		this.opLobby = OPLobby;
	}

	public void setCreatorName(String creatorName) {
		this.creatorName = creatorName;
	}

	public void setMaxLength(int maxLength) {
		this.maxLength = maxLength;
	}

	public void setMinLength(int minLength) {
		this.minLength = minLength;
	}

	public void setPassword(String password) {
		password = password;
	}

	public void setPlaying(Boolean playing) {
		playing = playing;
	}

	public void setMpID(Integer mpID) {
		this.mpID = mpID;
	}

	public void setRejoined(Boolean rejoined) {
		this.rejoined = rejoined;
	}

	public void setCurrentBeatmapAuthor(String currentBeatmapAuthor) {
		this.currentBeatmapAuthor = currentBeatmapAuthor;
	}

	public void setCurrentBeatmapName(String currentBeatmapName) {
		this.currentBeatmapName = currentBeatmapName;
	}

	public void setCurrentBeatmap(Beatmap currentBeatmap) {
		this.currentBeatmap = currentBeatmap;
	}

	public void setMaxAR(Double maxAR) {
		this.maxAR = maxAR;
	}

	public void setKeys(Integer keys) {
		this.keys = keys;
	}

	public void setKeyLimit(Boolean keyLimit) {
		this.keyLimit = keyLimit;
	}

	public void setTrueRandom(Boolean trueRandom) {
		trueRandom = trueRandom;
	}

	public void setBeatmapQueue(Queue<Beatmap> beatmapQueue) {
		this.beatmapQueue = beatmapQueue;
	}

	public void setBeatmapPlayed(Queue<Beatmap> beatmapPlayed) {
		this.beatmapPlayed = beatmapPlayed;
	}

	public void setDoubleTime(Boolean doubleTime) {
		doubleTime = doubleTime;
	}

	public void setNightCore(Boolean nightCore) {
		nightCore = nightCore;
	}

	public void setHalfTime(Boolean halfTime) {
		halfTime = halfTime;
	}

	public void setSlots(Map<Integer, Slot> slots) {
		this.slots = slots;
	}

	public void setRequests(Map<String, Request> requests) {
		this.requests = requests;
	}

	public void setAfk(Map<String, Integer> afk) {
		this.afk = afk;
	}

	public void setScores(Map<String, Integer> scores) {
		this.scores = scores;
	}

	public void setGenres(String[] genres) {
		this.genres = genres;
	}

	public void setStatusTypes(Map<Integer, Boolean> statusTypes) {
		this.statusTypes = statusTypes;
	}

	public void setLimitDate(Boolean limitDate) {
		this.limitDate = limitDate;
	}

	public void setMinYear(int minyear) {
		this.minYear = minyear;
	}

	public void setMaxYear(int maxyear) {
		this.maxYear = maxyear;
	}

	public void setLockAdding(Boolean lockAdding) {
		this.lockAdding = lockAdding;
	}

	public void setTeamgamemode(String teamgamemode) {
		this.teamgamemode = teamgamemode;
	}

	public void setWinCondition(String winCondition) {
		this.winCondition = winCondition;
	}

	public void setStatus(Integer status) {
		this.status = status;
	}

	public void setLoved(Boolean loved) {
		this.loved = loved;
	}

	public void setQualified(Boolean qualified) {
		this.qualified = qualified;
	}

	public void setApproved(Boolean approved) {
		this.approved = approved;
	}

	public void setRanked(Boolean ranked) {
		this.ranked = ranked;
	}

	public void setPending(Boolean pending) {
		this.pending = pending;
	}

	public void setWIP(Boolean WIP) {
		this.WIP = WIP;
	}

	public void setGraveyard(Boolean graveyard) {
		this.graveyard = graveyard;
	}

	public void setOnlyType(Boolean onlyType) {
		this.onlyType = onlyType;
	}

	public void setType(String type) {
		this.type = type;
	}

	public void setOnlyTags(Boolean onlyTags) {
		this.onlyTags = onlyTags;
	}

	public void setInclusiveTags(Boolean inclusiveTags) {
		this.inclusiveTags = inclusiveTags;
	}

	public void setTags(String[] tags) {
		Tags = tags;
	}

	public void setOnlyGenre(Boolean onlyGenre) {
		this.onlyGenre = onlyGenre;
	}

	public void setGenre(String genre) {
		this.genre = genre;
	}

	public void setOnlyDifficulty(Boolean onlyDifficulty) {
		this.onlyDifficulty = onlyDifficulty;
	}

	public void setMinDifficulty(Double minDifficulty) {
		this.minDifficulty = minDifficulty;
	}

	public void setMaxDifficulty(Double maxDifficulty) {
		this.maxDifficulty = maxDifficulty;
	}

	public void setPreviousBeatmap(Beatmap previousBeatmap) {
		this.previousBeatmap = previousBeatmap;
	}

	public void setPermanent(boolean permanent) {
		this.permanent = permanent;
	}
}
