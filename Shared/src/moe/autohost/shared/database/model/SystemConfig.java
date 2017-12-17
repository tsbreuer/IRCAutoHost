package moe.autohost.shared.database.model;

import moe.autohost.shared.database.ColumnRowMapper;
import org.springframework.jdbc.core.RowMapper;

import java.util.Map;

public class SystemConfig extends ModelBase {
	public static final String TABLE_NAME = "system_config";

	public static final String ID = "id";
	public static final String SCHEMA_VERSION = "schema_version";

	public static final RowMapper<SystemConfig> ROW_MAPPER =
			(rs, rowNum) -> new SystemConfig(ColumnRowMapper.INSTANCE.mapRow(rs, rowNum));

	private SystemConfig(Map<String, Object> map) {
		super(map);
	}

	@Override
	public String getTableName() {
		return TABLE_NAME;
	}

	public Integer getSchemaVersion() {
		return (Integer) m_map.get(SCHEMA_VERSION);
	}
}
