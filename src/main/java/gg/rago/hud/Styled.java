package gg.rago.hud;

/** An element with style presets; settings saved before presets existed start from the default preset. */
public interface Styled {
	void applyStyle(String preset);

	String defaultStyle();
}
