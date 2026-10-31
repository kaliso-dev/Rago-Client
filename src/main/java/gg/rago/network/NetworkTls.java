package gg.rago.network;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * HTTPS for Rago Network: the Java's own trusted roots plus Let's Encrypt's two roots (ISRG Root X1
 * and X2, in assets/rago/certs), which the old Java 8 builds that launchers use for 1.8.9 do not
 * have. Only Rago Network's connections use it; the rest of the game keeps Java's defaults.
 */
final class NetworkTls {
	static final String[] BUNDLED = {"isrg-root-x1.pem", "isrg-root-x2.pem"};
	private static volatile SSLSocketFactory factory;

	private NetworkTls() {
	}

	/** The socket factory, made once; Java's default one if the bundled roots cannot be added. */
	static SSLSocketFactory factory() {
		SSLSocketFactory f = factory;
		if (f == null) {
			try {
				SSLContext ctx = SSLContext.getInstance("TLS");
				ctx.init(null, new TrustManager[] {trustManager()}, null);
				f = ctx.getSocketFactory();
			} catch (Exception e) {
				f = (SSLSocketFactory) SSLSocketFactory.getDefault();
			}
			factory = f;
		}
		return f;
	}

	/** A trust manager for Java's roots and the bundled ones. */
	static X509TrustManager trustManager() throws Exception {
		KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
		store.load(null, null);
		TrustManagerFactory jvm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		jvm.init((KeyStore) null);
		int n = 0;
		for (TrustManager tm : jvm.getTrustManagers()) {
			if (tm instanceof X509TrustManager) {
				for (X509Certificate c : ((X509TrustManager) tm).getAcceptedIssuers()) {
					store.setCertificateEntry("jvm-" + n++, c);
				}
			}
		}
		CertificateFactory certs = CertificateFactory.getInstance("X.509");
		for (String name : BUNDLED) {
			try (InputStream in = NetworkTls.class.getResourceAsStream("/assets/rago/certs/" + name)) {
				if (in != null) {
					Certificate c = certs.generateCertificate(in);
					store.setCertificateEntry("rago-" + name, c);
				}
			} catch (Exception e) {
				// a root this Java cannot read (X2 is an EC key) is left out; X1 covers Let's Encrypt
			}
		}
		TrustManagerFactory both = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		both.init(store);
		for (TrustManager tm : both.getTrustManagers()) {
			if (tm instanceof X509TrustManager) {
				return (X509TrustManager) tm;
			}
		}
		throw new IllegalStateException("no X.509 trust manager");
	}
}
