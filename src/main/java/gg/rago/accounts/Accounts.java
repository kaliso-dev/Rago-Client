package gg.rago.accounts;

import java.util.Collections;
import java.util.List;

/**
 * Microsoft accounts the player can switch between in game. The sign-in and the stored accounts
 * belong to In-Game Account Switcher, which ships inside Rago (see {@code libs/ias/README.md});
 * this class only checks it is there before anything touches its classes.
 */
public final class Accounts {
	private static Boolean available;

	private Accounts() {
	}

	/** What a sign-in reports while it runs. Calls may come from any thread. */
	public interface Listener {
		/** A step of the sign-in, already translated. */
		void state(String text);

		void error(String message);

		/** Signed in as {@code name}; the game now uses this account. */
		void done(String name);

		/** Whether the sign-in can still be cancelled. */
		void cancellable(boolean cancellable);
	}

	public static boolean available() {
		if (available == null) {
			try {
				Class.forName("ru.vidtu.iasfork.msauth.AuthSys", false, Accounts.class.getClassLoader());
				Class.forName("ru.vidtu.iasfork.msauth.MicrosoftAccount", false, Accounts.class.getClassLoader());
				available = true;
			} catch (Throwable e) {
				available = false;
			}
		}
		return available;
	}

	/** Names of the saved Microsoft accounts. */
	public static List<String> names() {
		return available() ? IasBackend.names() : Collections.emptyList();
	}

	/** Opens the Microsoft sign-in page in the browser to add an account. */
	public static void add(Listener listener) {
		if (available()) {
			IasBackend.add(listener);
		}
	}

	/** Switches the game to a saved account, refreshing its sign-in. */
	public static void login(String name, Listener listener) {
		if (available()) {
			IasBackend.login(name, listener);
		}
	}

	public static void remove(String name) {
		if (available()) {
			IasBackend.remove(name);
		}
	}

	public static void cancel() {
		if (available()) {
			IasBackend.cancel();
		}
	}
}
