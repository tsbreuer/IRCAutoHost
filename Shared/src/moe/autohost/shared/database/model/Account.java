package moe.autohost.shared.database.model;

import moe.autohost.shared.database.ColumnRowMapper;
import org.springframework.jdbc.core.RowMapper;

import java.util.Map;

public class Account extends ModelBase {
	public static final String TABLE_NAME = "account";

	public static final String ID = "id";
	public static final String CREATED_AT = "created_at";
	public static final String OSU_ID = "osu_id";
	public static final String OSU_USER = "osu_user";
	public static final String PASSWORD = "password";
	public static final String IS_ADMIN = "is_admin";
	public static final String IS_OP = "is_op";

	public static final RowMapper<Account> ROW_MAPPER =
			(rs, rowNum) -> new Account(ColumnRowMapper.INSTANCE.mapRow(rs, rowNum));

	private Account(Map<String, Object> map) {
		super(map);
	}

	@Override
	public String getTableName() {
		return TABLE_NAME;
	}
}
