package gg.rago.accounts;

import com.github.mrebhan.ingameaccountswitcher.MR;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.Session;
import ru.vidtu.iasfork.msauth.AuthSys;
import ru.vidtu.iasfork.msauth.MSAuthHandler;
import ru.vidtu.iasfork.msauth.MicrosoftAccount;
import the_fireplace.iasencrypt.EncryptionTools;

import java.util.ArrayList;
import java.util.List;

/**
 * The calls into In-Game Account Switcher. Only loaded once {@link Accounts#available()} has
 * confirmed the mod is present. Tokens stay in the mod's own encrypted store ({@code .iasms}).
 */
final class IasBackend {
	private IasBackend() {
	}

	static List<String> names() {
		List<String> out = new ArrayList<>();
		for (MicrosoftAccount a : MicrosoftAccount.msaccounts) {
			out.add(a.alias());
		}
		return out;
	}

	private static MicrosoftAccount find(String name) {
		for (MicrosoftAccount a : MicrosoftAccount.msaccounts) {
			if (a.alias().equalsIgnoreCase(name)) {
				return a;
			}
		}
		return null;
	}

	static void add(Accounts.Listener listener) {
		AuthSys.start(handler(listener));
	}

	static void login(String name, Accounts.Listener listener) {
		MicrosoftAccount a = find(name);
		if (a == null) {
			listener.error(name);
			return;
		}
		AuthSys.start(EncryptionTools.decode(a.accessToken), EncryptionTools.decode(a.refreshToken), handler(listener));
	}

	static void remove(String name) {
		MicrosoftAccount a = find(name);
		if (a != null) {
			MicrosoftAccount.msaccounts.remove(a);
			MicrosoftAccount.save(Minecraft.getMinecraft());
		}
	}

	static void cancel() {
		AuthSys.stop();
	}

	private static MSAuthHandler handler(Accounts.Listener listener) {
		return new MSAuthHandler() {
			@Override
			public void setState(String key) {
				listener.state(I18n.format(key));
			}

			@Override
			public void error(Throwable t) {
				String m = t.getLocalizedMessage();
				listener.error(m == null || m.isEmpty() ? t.getClass().getSimpleName() : I18n.format(m));
			}

			@Override
			public void success(String name, String uuid, String accessToken, String refreshToken) {
				Minecraft mc = Minecraft.getMinecraft();
				mc.addScheduledTask(() -> {
					// keep the newest tokens, so the account signs in again next time without the browser
					MicrosoftAccount a = find(name);
					if (a == null) {
						MicrosoftAccount.msaccounts.add(new MicrosoftAccount(name, EncryptionTools.encode(accessToken), EncryptionTools.encode(refreshToken)));
					} else {
						a.accessToken = EncryptionTools.encode(accessToken);
						a.refreshToken = EncryptionTools.encode(refreshToken);
					}
					MicrosoftAccount.save(mc);
					MR.setSession(new Session(name, uuid, accessToken, "mojang"));
					listener.done(name);
				});
			}

			@Override
			public void cancellble(boolean cancellable) {
				listener.cancellable(cancellable);
			}
		};
	}
}
