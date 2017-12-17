package autohost.util;

import java.util.HashMap;

public class User {
	
	private HashMap<Integer,String> c_roomList;
	@SuppressWarnings("unused")
	private String name;
	@SuppressWarnings("unused")
	private int id;
	
	public User(String name, int id) {
		this.name = name;
		this.id = id;
	}
	
	public void newRoomList() {
		this.c_roomList = new HashMap<Integer, String>();
	}

	public HashMap<Integer, String> getRoomList() {	
		return this.c_roomList;
	}

	public void saveRoomList(HashMap<Integer, String> list) {
		this.c_roomList = list;
	}

}
