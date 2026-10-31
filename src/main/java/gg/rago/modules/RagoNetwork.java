package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.StringOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import gg.rago.hooks.Hooks;
import gg.rago.network.Loadout;
import gg.rago.network.NetworkClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.Session;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Rago Network: other Rago players see the Rago logo next to your name and the cosmetics you wear,
 * and you see theirs. Talks only to the Rago Network server set here (nothing when it is empty),
 * never to the Minecraft server: you sign in the way Minecraft servers check a player, so your
 * Minecraft token goes to Mojang only. It sends your name, your cosmetics and Rago's version, and,
 * to know who uses Rago, the Minecraft IDs of the players in your tab list; the server keeps none
 * of the IDs asked about. "Show that I use Rago" off hides you from the others; "See other Rago
 * players" off sends nothing about the players around you.
 */
public final class RagoNetwork extends Module {
	/** The logo's colour for the creator of Rago, partners and staff (everyone else: Rago's red). */
	public static final int CREATOR = 0xFFFFC94D;
	public static final int PARTNER = 0xFFB57BFF;
	public static final int STAFF = 0xFF4DD0E1;
	private static final Logger LOG = LogManager.getLogger("Rago");

	private final StringOption url = addOption(new StringOption("network_url", "", 120));
	private final BooleanOption visible = addOption(new BooleanOption("network_visible", true));
	private final BooleanOption others = addOption(new BooleanOption("network_others", true));
	private final NetworkClient client = new NetworkClient(this::execute, System::currentTimeMillis,
		RagoNetwork::version, LOG::info);
	private final List<UUID> around = new ArrayList<>();
	private ExecutorService worker;
	private SessionAccount account;

	public RagoNetwork() {
		super("rago_network", FeatureClass.VISUAL, true);
	}

	@Override
	public Category category() {
		return Category.VISUAL;
	}

	@Override
	public void tick() {
		Cosmetics cosmetics = Hooks.cosmetics;
		RagoCape cape = Hooks.cape;
		boolean show = visible.get();
		int signature = (show ? 1 : 0) + 31 * (cosmetics != null ? cosmetics.sharedSignature(cape) : 0);
		client.tick(url.get(), account(), show, others.get(), signature,
			() -> cosmetics != null ? cosmetics.shared(cape) : Loadout.NONE, this::around);
	}

	@Override
	protected void onDisable() {
		client.forget();
	}

	/** What Rago Network says about the player {@code id}; null for someone not using Rago, or when off. */
	public NetworkClient.Player player(UUID id) {
		return isEnabled() && others.get() ? client.player(id) : null;
	}

	/** Your role (user, partner, staff, creator) once signed in, else null. */
	public String ownRole() {
		return isEnabled() ? client.role() : null;
	}

	/** Rago players online, -1 when not known. */
	public int online() {
		return isEnabled() ? client.online() : -1;
	}

	/** One line for /rago network. */
	public String status() {
		if (!isEnabled()) {
			return "off";
		}
		if (NetworkClient.normalize(url.get()).isEmpty()) {
			return url.get().trim().isEmpty() ? "no server set" : "not an https address: " + url.get();
		}
		String problem = client.problem();
		return (client.signedIn() ? "signed in as " + client.role() + ", " + client.online() + " online" : "not signed in")
			+ (problem != null ? " (last problem: " + problem + ")" : "");
	}

	/** The logo's colour for a player with {@code role}; {@code user} for everyone else. */
	public static int roleColor(String role, int user) {
		if (role == null) {
			return user;
		}
		switch (role) {
			case "creator":
				return CREATOR;
			case "partner":
				return PARTNER;
			case "staff":
				return STAFF;
			default:
				return user;
		}
	}

	private static String version() {
		return RagoClient.version();
	}

	private void execute(Runnable job) {
		if (worker == null) {
			worker = Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "Rago Network");
				t.setDaemon(true);
				return t;
			});
		}
		worker.execute(job);
	}

	private NetworkClient.Account account() {
		Session session = Minecraft.getMinecraft().getSession();
		if (account == null || account.session != session) {
			account = new SessionAccount(session, RagoClient.ownId());
		}
		return account;
	}

	/** The players in the tab list, asked only when a lookup is due. */
	private Collection<UUID> around() {
		around.clear();
		NetHandlerPlayClient net = Minecraft.getMinecraft().getNetHandler();
		if (net != null) {
			for (NetworkPlayerInfo info : net.getPlayerInfoMap()) {
				around.add(info.getGameProfile().getId());
			}
		}
		return around;
	}

	/** The signed-in account; joinServer goes to Mojang's session server, as when joining a server. */
	private static final class SessionAccount implements NetworkClient.Account {
		final Session session;
		final UUID id;

		SessionAccount(Session session, UUID id) {
			this.session = session;
			this.id = id;
		}

		@Override
		public String name() {
			return session.getUsername();
		}

		@Override
		public UUID id() {
			return id;
		}

		@Override
		public void joinServer(String serverId) throws Exception {
			Minecraft.getMinecraft().getSessionService().joinServer(session.getProfile(), session.getToken(), serverId);
		}
	}
}
