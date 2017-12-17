package moe.autohost.shared.database;

import moe.autohost.shared.Global;
import moe.autohost.shared.database.model.SystemConfig;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public class Database {
	private static final String PROTOCOL = "jdbc:derby:";
	private static final String DB_NAME = "AutoHostDB";

	private static Database m_instance;

	private DriverManagerDataSource m_dataSource;
	private JdbcTemplate            m_jdbcTemplate;

	public static Database getInstance() {
		if (m_instance == null) {
			m_instance = new Database();
		}
		return m_instance;
	}

	private Database() {
		m_dataSource = new DriverManagerDataSource();
		m_dataSource.setDriverClassName("org.apache.derby.jdbc.EmbeddedDriver");
		m_dataSource.setUrl(PROTOCOL + Global.WORKING_DIRECTORY + DB_NAME + ";create=true");
		m_dataSource.setUsername("");
		m_dataSource.setPassword("");
		m_jdbcTemplate = new JdbcTemplate(m_dataSource);
		SchemaManager.initSchema(this);
	}

	public void executeSql(String sql) {
		m_jdbcTemplate.execute(sql);
	}

	public SystemConfig getSystemConfig() {
		try {
			return m_jdbcTemplate.queryForObject("SELECT * FROM system_config", SystemConfig.ROW_MAPPER);
		} catch (EmptyResultDataAccessException | BadSqlGrammarException e) {
			return null;
		}
	}

	public void setSchemaVersion(int version) {
		m_jdbcTemplate.update("UPDATE system_config SET schema_version = ?", version);
	}
}
