package moe.autohost.web;

import moe.autohost.shared.database.Database;

public class Context {
	private Database m_database;

	public void setDatabase(Database database) {
		m_database = database;
	}

	public Database getDatabase() {
		return m_database;
	}
}
