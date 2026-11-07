package gg.rago.fixes;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TaskErrorsTest {
	private final List<String> logged = new ArrayList<>();
	private final Logger logger = (Logger) Proxy.newProxyInstance(Logger.class.getClassLoader(), new Class<?>[] {Logger.class},
		(proxy, method, args) -> {
			if (method.getName().equals("fatal") || method.getName().equals("warn")) {
				logged.add(method.getName() + " " + Arrays.deepToString(args));
			}
			return null;
		});

	@BeforeEach
	void reset() {
		TaskErrors.reset();
	}

	private static RuntimeException thrownHere() {
		return new IllegalStateException("team gone");
	}

	private static RuntimeException thrownThere() {
		return new IllegalStateException("team gone");
	}

	@Test
	void firstErrorInFullThenOneLineAMinute() {
		TaskErrors.report(logger, "Error executing task", thrownHere(), 0);
		assertEquals(1, logged.size());
		assertEquals(true, logged.get(0).startsWith("fatal "));
		for (int i = 1; i <= 100; i++) {
			TaskErrors.report(logger, "Error executing task", thrownHere(), i * 100);
		}
		assertEquals(1, logged.size(), "repeats within a minute are only counted");
		TaskErrors.report(logger, "Error executing task", thrownHere(), 60_000);
		assertEquals(2, logged.size());
		assertEquals(true, logged.get(1).startsWith("warn "));
		assertEquals(true, logged.get(1).contains("101"), logged.get(1));
		TaskErrors.report(logger, "Error executing task", thrownHere(), 60_001);
		assertEquals(2, logged.size(), "the count starts again after a report");
	}

	@Test
	void eachKindIsReportedOnce() {
		TaskErrors.report(logger, "Error executing task", thrownHere(), 0);
		TaskErrors.report(logger, "Error executing task", thrownThere(), 0);
		TaskErrors.report(logger, "Error executing task", new NullPointerException(), 0);
		assertEquals(3, logged.size());
	}

	@Test
	void executionExceptionsAreKeyedByTheirCause() {
		RuntimeException cause = thrownHere();
		assertEquals(TaskErrors.key(cause), TaskErrors.key(thrownHere()));
		assertNotEquals(TaskErrors.key(cause), TaskErrors.key(thrownThere()));
		TaskErrors.report(logger, "Error executing task", new ExecutionException(cause), 0);
		TaskErrors.report(logger, "Error executing task", thrownHere(), 1);
		assertEquals(1, logged.size(), "the wrapped error and the bare one are the same kind");
	}
}
