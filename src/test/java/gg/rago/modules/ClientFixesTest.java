package gg.rago.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientFixesTest {
	@Test
	void serverPackLinks() {
		assertTrue(ClientFixes.safePackUrl("https://example.com/pack.zip"));
		assertTrue(ClientFixes.safePackUrl("http://example.com/pack.zip"));
		assertTrue(ClientFixes.safePackUrl("level://My World/resources.zip"));
		assertFalse(ClientFixes.safePackUrl("level://../../.minecraft/options.txt"));
		assertFalse(ClientFixes.safePackUrl("level://%2E%2E/%2E%2E/secret/resources.zip"));
		assertFalse(ClientFixes.safePackUrl("level://world/../../x/resources.zip"));
		assertFalse(ClientFixes.safePackUrl("level://C:/Users/x/resources.zip"));
		assertFalse(ClientFixes.safePackUrl("file:///etc/passwd"));
		assertFalse(ClientFixes.safePackUrl("level://world/options.txt"));
	}
}
