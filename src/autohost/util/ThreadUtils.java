package autohost.util;

public abstract class ThreadUtils {
	public static void sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
		} catch (Exception e) {
			// Do nothing
		}
	}
}
