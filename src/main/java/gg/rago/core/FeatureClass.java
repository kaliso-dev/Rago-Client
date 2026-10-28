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
	/** Changes what, when or whether packets are sent. Never allowed in Rago. */
	PACKET
}
