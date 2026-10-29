package gg.rago.settings;

/** A row with a single action button, e.g. opening OptiFine's own video settings. */
public final class Button extends Setting {
	private final Runnable action;

	public Button(String id, Tab tab, String section, Runnable action) {
		super(id, tab, section);
		this.action = action;
		wideRow();
	}

	public void press() {
		action.run();
	}
}
