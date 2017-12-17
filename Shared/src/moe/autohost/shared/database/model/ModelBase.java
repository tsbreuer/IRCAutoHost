package moe.autohost.shared.database.model;

import java.util.Map;

public abstract class ModelBase {
	protected final Map<String, Object> m_map;

	protected ModelBase(Map<String, Object> map) {
		m_map = map;
	}

	public Map<String, Object> asMap() {
		return m_map;
	}

	public abstract String getTableName();
}
