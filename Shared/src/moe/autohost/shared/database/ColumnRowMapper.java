package moe.autohost.shared.database;

import org.springframework.jdbc.core.ColumnMapRowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;

public class ColumnRowMapper extends ColumnMapRowMapper {
	public static final ColumnRowMapper INSTANCE = new ColumnRowMapper();

	private ColumnRowMapper() {
	}

	@Override
	protected Object getColumnValue(ResultSet rs, int index) throws SQLException {
		Object value = super.getColumnValue(rs, index);
		if (value instanceof Timestamp) {
			return new Date(((Timestamp) value).getTime());
		}
		if (value instanceof java.sql.Date) {
			return new Date(((java.sql.Date) value).getTime());
		}
		return value;
	}
}
