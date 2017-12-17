package moe.autohost.shared.database.model;

import moe.autohost.shared.database.ColumnRowMapper;
import org.springframework.jdbc.core.RowMapper;

import java.util.Map;

public class AccountSession extends ModelBase {
	public static final String TABLE_NAME = "account_session";

	public static final String HASH_KEY = "hash_key";
	public static final String ACCOUNT_ID = "account_id";
	public static final String LOGIN_TIME = "login_time";
	public static final String LAST_SEEN_AT = "last_seen_at";

	public static final RowMapper<AccountSession> ROW_MAPPER =
			(rs, rowNum) -> new AccountSession(ColumnRowMapper.INSTANCE.mapRow(rs, rowNum));

	private AccountSession(Map<String, Object> map) {
		super(map);
	}

	@Override
	public String getTableName() {
		return TABLE_NAME;
	}
}
