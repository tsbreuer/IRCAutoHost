package autohost.util;

import org.json.JSONException;
import org.json.JSONObject;

public class Beatmap {
	public int RequestedBy;
	public String artist;
	public int play_length;
	public String gamemode;
	public int pass_count;
	public int total_length;
	public int beatmapset_id;
	public String genre; // 0 = any, 1 = unspecified, 2 = video game, 3 = anime,
							// 4 = rock, 5 = pop, 6 = other, 7 = novelty, 9 =
							// hip hop, 10 = electronic (note that there's no 8)
	public String mapper;
	public String date;
	public String source;
	public int beatmap_id;
	public int beatmap_status;
	public int map_count;
	public int favorites;
	public String language; // 0 = any, 1 = other, 2 = english, 3 = japanese, 4
							// = chinese, 5 = instrumental, 6 = korean, 7 =
							// french, 8 = german, 9 = swedish, 10 = spanish, 11
							// = italian
	public String title;
	public String difficulty_name;
	public double difficulty;
	public double difficulty_cs;
	public double difficulty_od;
	public double difficulty_ar;
	public double difficulty_hp;
	public double bpm;
	public int play_count;
	public int graveyard;
	public Boolean ignored;
	public String mods;
	public String[] tags;
	public int maxcombo;
	public Boolean DT;
	public Boolean NC;
	public Boolean HT;

	public Beatmap() {
	};

	public Beatmap(JSONObject obj) throws JSONException, BrokenBeatmap {
		this.DT = this.NC = this.HT = false;
		this.gamemode = obj.getString("mode");
		this.graveyard = obj.getInt("approved");
		this.artist = obj.getString("artist");
		this.title = obj.getString("title");
		this.mapper = obj.getString("creator");
		this.beatmap_id = obj.getInt("beatmap_id");
		this.beatmapset_id = obj.getInt("beatmapset_id");
		this.bpm = obj.getDouble("bpm");
		this.difficulty_name = obj.getString("version");
		this.difficulty = obj.getDouble("difficultyrating");
		this.difficulty_ar = obj.getDouble("diff_approach");
		this.difficulty_cs = obj.getDouble("diff_size");
		this.difficulty_od = obj.getDouble("diff_overall");
		this.difficulty_hp = obj.getDouble("diff_drain");
		this.play_length = obj.getInt("hit_length");
		this.date = obj.getString("last_update");
		this.source = obj.getString("source");
		this.genre = obj.getString("genre_id");
		this.total_length = obj.getInt("total_length");
		this.tags = obj.getString("tags").split(" ");
		this.play_count = obj.getInt("playcount");
		this.pass_count = obj.getInt("passcount");
		try {
			String maxcombo = obj.getString("max_combo");
			if (maxcombo.equalsIgnoreCase("null")){
				throw new BrokenBeatmap();
			}
			this.maxcombo = obj.getInt("max_combo");
		} catch (JSONException e) {
			if (!this.gamemode.equalsIgnoreCase("3") || !this.gamemode.equalsIgnoreCase("1"))
				e.printStackTrace();
			this.maxcombo = 0;
		}
	}
// Parsing from osusearch is different. Y u do dis osusearch
	//{"date": "2011-11-09T08:37:50", "favorites": 1623, "beatmap_status": 1, "language": "Japanese", "pass_count": 140819,
	//"difficulty_cs": 4.0, "play_length": 147, "ignored": null, "map_count": 4, "genre": "Anime",
	//"difficulty_ar": 9.0, "beatmapset": 29860, "source": "Mirai Nikki", "total_length": 155, "difficulty_hp": 8.0,
	//"beatmap_id": 124701, "title": "Kuusou Mesorogiwi", "mapper": "osuplayer111", "beatmapset_id": 39031,
	//"difficulty": 4.14548778533936, "bpm": 230, "play_count": 1383011, "artist": "Yousei Teikoku", "difficulty_od": 7.0,
	//"difficulty_name": "Insane", "gamemode": 0}
	public Beatmap(JSONObject obj, boolean b) throws JSONException {
		this.DT = this.NC = this.HT = false;
		this.gamemode = ""+obj.getInt("gamemode");
		this.graveyard = obj.getInt("beatmap_status");
		this.artist = obj.getString("artist");
		this.title = obj.getString("title");
		this.mapper = obj.getString("mapper");
		this.beatmap_id = obj.getInt("beatmap_id");
		this.beatmapset_id = obj.getInt("beatmapset");
		this.bpm = obj.getDouble("bpm");
		this.difficulty_name = obj.getString("difficulty_name");
		this.difficulty = obj.getDouble("difficulty");
		this.difficulty_ar = obj.getDouble("difficulty_ar");
		this.difficulty_cs = obj.getDouble("difficulty_cs");
		this.difficulty_od = obj.getDouble("difficulty_od");
		this.difficulty_hp = obj.getDouble("difficulty_hp");
		this.play_length = obj.getInt("play_length");
		this.date = obj.getString("date");
		this.source = obj.getString("source");
		this.genre = obj.getString("genre");
		this.total_length = obj.getInt("total_length");
		//this.tags = obj.getString("tags").split(" "); No tags. Yeah who uses this anyway
		this.play_count = obj.getInt("play_count");
		this.pass_count = obj.getInt("pass_count");

		/* No max combo ;(
		try {
			this.maxcombo = obj.getInt("max_combo");
		} catch (JSONException e) {
			if (!this.gamemode.equalsIgnoreCase("3") || !this.gamemode.equalsIgnoreCase("1"))
				e.printStackTrace();
			this.maxcombo = 0;
		}*/
	};
}
