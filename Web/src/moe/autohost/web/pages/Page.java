package moe.autohost.web.pages;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

public abstract class Page {
	private enum Pages {
		INDEX("/index.html", new Index()),
		ERROR("/error.html", new Error());

		private String m_path;
		private Page   m_page;

		Pages(String path, Page page) {
			m_path = path;
			m_page = page;
		}

		public String getPath() {
			return m_path;
		}

		public Page getPage() {
			return m_page;
		}
	}

	abstract String getTitle();
	abstract void process(Map<String, Object> templateInput);

	public static Response servePage(IHTTPSession session, Configuration cfg) {
		String uri = session.getUri();
		Pages page = null;
		if (uri.equals("/")) {
			page = Pages.INDEX;
		} else {
			for (Pages pages : Pages.values()) {
				if (pages.getPath().equals(uri)) {
					page = pages;
					break;
				}
			}
		}
		if (page == null) {
			return null;
		}

		Map<String, Object> templateInput = new HashMap<>();
		templateInput.put("title", page.getPage().getTitle());

		page.getPage().process(templateInput);

		Template template;
		try {
			template = cfg.getTemplate(page.getPath());
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}

		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		Writer byteWriter = new OutputStreamWriter(bos);
		try {
			template.process(templateInput, byteWriter);
		} catch (IOException | TemplateException e) {
			e.printStackTrace();
			return null;
		}

		byte[] bytes = bos.toByteArray();
		ByteArrayInputStream bis = new ByteArrayInputStream(bytes);

		return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/html", bis,
				bytes.length);
	}

	public static Response serveError(IHTTPSession session, int errorCode) {
		return NanoHTTPD.newFixedLengthResponse("Error " + errorCode);
	}
}
