package moe.autohost.web.pages;

import moe.autohost.shared.database.Database;
import moe.autohost.shared.database.model.SystemConfig;

import java.util.Map;

class Index extends Page {
	Index() {}

	@Override
	String getTitle() {
		return "Welcome to AutoHost";
	}

	@Override
	void process(Map<String, Object> templateInput) {
		Database database = Database.getInstance();
		SystemConfig config = database.getSystemConfig();
		if (config == null) {
			templateInput.put("schemaVersion", "null?");
		} else {
			templateInput.put("schemaVersion", config.getSchemaVersion());
		}
	}
}
