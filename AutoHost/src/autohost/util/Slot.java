package autohost.util;

public class Slot {
	public int id;
	public int playerid;
	public String name;
	public String status;

	public Slot(String name, int slot) {
		this.name = name;
		this.id = slot;
		this.status = "";
		this.playerid = 0;
	}

	public Slot() {
	}

	public Slot(int jslot, String playerName, int playerId, String status) {
		this.id = jslot;
		this.name = playerName;
		this.playerid = playerId;
		this.status = status;
	}
}
