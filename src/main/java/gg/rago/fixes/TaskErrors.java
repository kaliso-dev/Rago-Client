package gg.rago.fixes;

import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Errors of the game's scheduled tasks (mostly server packets the game cannot apply, such as
 * Hypixel's scoreboard team updates for players already gone). Vanilla logs each one as fatal with
 * its whole stack trace, which on some servers means many per second: a growing log and stutter
 * while it is written. Here each kind of error is logged in full the first time, then only counted,
 * with one line a minute while it keeps coming.
 */
final class TaskErrors {
	private static final long SUMMARY_EVERY_MS = 60_000;
	private static final int KINDS_KEPT = 64;

	private static final class Kind {
		int unreported;
		long lastSummary;
	}

	private static final Map<String, Kind> KINDS = new LinkedHashMap<String, Kind>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Kind> eldest) {
			return size() > KINDS_KEPT;
		}
	};

	private TaskErrors() {
	}

	static synchronized void report(Logger logger, String message, Throwable error, long now) {
		Throwable cause = error instanceof ExecutionException && error.getCause() != null ? error.getCause() : error;
		String key = key(cause);
		Kind kind = KINDS.get(key);
		if (kind == null) {
			kind = new Kind();
			kind.lastSummary = now;
			KINDS.put(key, kind);
			logger.fatal(message, error);
			return;
		}
		kind.unreported++;
		if (now - kind.lastSummary >= SUMMARY_EVERY_MS) {
			logger.warn("{} ({} more times since the last report): {}", message, kind.unreported, cause.toString());
			kind.unreported = 0;
			kind.lastSummary = now;
		}
	}

	/** What makes two errors the same kind: the exception's class and where it was thrown. */
	static String key(Throwable cause) {
		StackTraceElement[] trace = cause.getStackTrace();
		String where = trace.length > 0 ? trace[0].getClassName() + "." + trace[0].getMethodName() : "?";
		return cause.getClass().getName() + "@" + where;
	}

	/** For tests. */
	static synchronized void reset() {
		KINDS.clear();
	}
}
