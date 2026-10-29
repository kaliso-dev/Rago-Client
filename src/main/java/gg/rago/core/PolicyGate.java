package gg.rago.core;

/**
 * Registration-time rules from the Stage 1 plan:
 * <ul>
 *   <li>{@link FeatureClass#PACKET} modules are refused outright.</li>
 *   <li>{@link FeatureClass#INPUT} modules must be off by default, so a player
 *       always opts in knowingly (and can check the server's rules first).</li>
 * </ul>
 */
public final class PolicyGate {
	private PolicyGate() {
	}

	public static void check(Module module) {
		switch (module.featureClass()) {
			case PACKET:
				throw new IllegalArgumentException(
					"Module '" + module.id() + "' is packet-affecting; Rago does not allow those");
			case INPUT:
				if (module.defaultEnabled()) {
					throw new IllegalArgumentException(
						"Input-affecting module '" + module.id() + "' must be disabled by default");
				}
				break;
			default:
				break;
		}
	}
}
