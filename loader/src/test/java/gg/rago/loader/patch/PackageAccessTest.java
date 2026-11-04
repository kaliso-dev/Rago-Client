package gg.rago.loader.patch;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.objectweb.asm.Opcodes.*;

class PackageAccessTest {
	@Test
	void opensPackageAndProtectedButNotPrivate() {
		ClassWriter w = new ClassWriter(0);
		w.visit(V1_8, ACC_SUPER, "net/minecraft/client/gui/GuiScreen", null, "java/lang/Object", null);
		w.visitField(ACC_PROTECTED, "field_146297_k", "Ljava/lang/Object;", null, null).visitEnd();
		w.visitField(0, "field_1_a", "I", null, null).visitEnd();
		w.visitField(ACC_PRIVATE | ACC_FINAL, "field_2_b", "I", null, null).visitEnd();
		w.visitEnd();
		ClassNode c = new ClassNode();
		new ClassReader(new PackageAccess().transform("net.minecraft.client.gui.GuiScreen", w.toByteArray())).accept(c, 0);
		assertEquals(ACC_PUBLIC | ACC_SUPER, c.access);
		assertEquals(ACC_PUBLIC, c.fields.get(0).access);
		assertEquals(ACC_PUBLIC, c.fields.get(1).access);
		assertEquals(ACC_PRIVATE | ACC_FINAL, c.fields.get(2).access);
	}
}
