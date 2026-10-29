package gg.rago.settings;

/** A row with a single action button, e.g. opening OptiFine's own video settings. */
public final class Button extends Setting {
	private final Runnable action;
	private boolean primary;

	public Button(String id, Tab tab, String section, Runnable action) {
		super(id, tab, section);
		this.action = action;
		wideRow();
	}

	public void press() {
		action.run();
	}

	/** Shows the button in the accent colour, for the main action of a section. */
	public Button primary() {
		primary = true;
		return this;
	}

	public boolean isPrimary() {
		return primary;
	}
}
