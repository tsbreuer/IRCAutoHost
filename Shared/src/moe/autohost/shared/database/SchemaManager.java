package moe.autohost.shared.database;

import moe.autohost.shared.database.model.AccountSession;
import moe.autohost.shared.database.model.SystemConfig;
import moe.autohost.shared.database.model.Account;

public abstract class SchemaManager {
	private static final int DATABASE_VERSION = 0;

	private static final String[] SCHEMA_0 = {
			"CREATE TABLE " + SystemConfig.TABLE_NAME + " ("
					+ "id integer NOT NULL PRIMARY KEY DEFAULT 1 CHECK (id = 1),"
					+ "schema_version integer NOT NULL DEFAULT 0"
					+ ")",
			"INSERT INTO " + SystemConfig.TABLE_NAME + " (schema_version) VALUES (0)",
			"CREATE TABLE " + Account.TABLE_NAME + " ("
					+ "id integer NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1),"
					+ "created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,"
					+ "osu_id bigint NOT NULL UNIQUE,"
					+ "osu_user varchar(128) NOT NULL UNIQUE,"
					+ "password varchar(512) NOT NULL,"
					+ "is_admin boolean NOT NULL DEFAULT false,"
					+ "is_op boolean NOT NULL DEFAULT false"
					+ ")",
			"CREATE TABLE " + AccountSession.TABLE_NAME + " ("
					+ "hash_key varchar(512) NOT NULL PRIMARY KEY,"
					+ "account_id integer references " + Account.TABLE_NAME + " ON DELETE CASCADE,"
					+ "login_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,"
					+ "last_seen_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP"
					+ ")"
	};

	private static final String[][] SCHEMA = {
			SCHEMA_0
	};

	public static void initSchema(Database database) {
		SystemConfig systemConfig = database.getSystemConfig();
		int currentSchema = -1;
		if (systemConfig != null) {
			currentSchema = systemConfig.getSchemaVersion();
		}

		if (currentSchema == DATABASE_VERSION) {
			System.out.println("Schema at version " + DATABASE_VERSION);
			return;
		}

		if (currentSchema > DATABASE_VERSION) {
			System.err.print("Unexpected high schema version");
			return;
		}

		for (int i = currentSchema + 1; i < SCHEMA.length; ++i) {
			String[] commands = SCHEMA[i];
			for (String s : commands) {
				database.executeSql(s);
			}
		}

		database.setSchemaVersion(DATABASE_VERSION);
	}
}
