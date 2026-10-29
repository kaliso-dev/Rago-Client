package gg.rago.core;

/**
 * What a module is allowed to change. Every module declares exactly one class and
 * {@link PolicyGate} enforces the rules at registration.
 */
public enum FeatureClass {
	/** Changes only what is drawn (crosshair, HUD layout, animations). */
	VISUAL,
	/** Reads state the client already has and displays it (FPS, CPS, ping, armor). */
	INFORMATIONAL,
	/** Changes how inputs become actions (toggle sprint). Must default to off. */
	INPUT,
	/**
	 * Sends chat messages on its own (Auto GG, Auto Tip): what is sent is a normal chat line the
	 * player wrote or chose, never a changed packet. Must default to off, like input modules.
	 */
	AUTO_CHAT,
	/** Changes what, when or whether packets are sent. Never allowed in Rago. */
	PACKET
}
