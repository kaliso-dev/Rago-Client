package gg.rago.loader.patch;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.objectweb.asm.Opcodes.*;

class AccessTest {
	private static final String GAME = "net/minecraft/client/renderer/entity/RendererLivingEntity";

	/** A game class with a protected method, a private final field and a private method nobody uses. */
	private static byte[] game() {
		ClassWriter w = new ClassWriter(0);
		w.visit(V1_8, ACC_PUBLIC | ACC_SUPER, GAME, null, "java/lang/Object", null);
		w.visitField(ACC_PRIVATE | ACC_FINAL, "field_77045_g", "I", null, null).visitEnd();
		MethodVisitor m = w.visitMethod(ACC_PROTECTED, "func_177094_a", "(Ljava/lang/Object;)Z", null, null);
		m.visitCode();
		m.visitInsn(ICONST_1);
		m.visitInsn(IRETURN);
		m.visitMaxs(1, 2);
		m.visitEnd();
		m = w.visitMethod(ACC_PRIVATE, "func_99999_z", "()V", null, null);
		m.visitCode();
		m.visitInsn(RETURN);
		m.visitMaxs(0, 1);
		m.visitEnd();
		w.visitEnd();
		return w.toByteArray();
	}

	/** A mod class that calls the protected method through a subclass and writes the final field. */
	private static byte[] mod() {
		ClassWriter w = new ClassWriter(0);
		w.visit(V1_8, ACC_PUBLIC | ACC_SUPER, "gg/example/Cosmetics", null, "java/lang/Object", null);
		MethodVisitor m = w.visitMethod(ACC_PUBLIC | ACC_STATIC, "add", "(L" + GAME + ";)V", null, null);
		m.visitCode();
		m.visitVarInsn(ALOAD, 0);
		m.visitInsn(ACONST_NULL);
		m.visitMethodInsn(INVOKEVIRTUAL, "net/minecraft/client/renderer/entity/RenderPlayer", "func_177094_a", "(Ljava/lang/Object;)Z", false);
		m.visitInsn(POP);
		m.visitVarInsn(ALOAD, 0);
		m.visitInsn(ICONST_2);
		m.visitFieldInsn(PUTFIELD, GAME, "field_77045_g", "I");
		m.visitInsn(RETURN);
		m.visitMaxs(2, 1);
		m.visitEnd();
		w.visitEnd();
		return w.toByteArray();
	}

	private static ClassNode read(byte[] bytes) {
		ClassNode c = new ClassNode();
		new ClassReader(bytes).accept(c, 0);
		return c;
	}

	@Test
	void widensWhatModsUse() {
		Access access = new Access();
		access.scan(mod());
		ClassNode c = read(access.transform(GAME.replace('/', '.'), game()));
		MethodNode used = c.methods.stream().filter(m -> m.name.equals("func_177094_a")).findFirst().orElseThrow();
		MethodNode unused = c.methods.stream().filter(m -> m.name.equals("func_99999_z")).findFirst().orElseThrow();
		FieldNode field = c.fields.get(0);
		assertEquals(ACC_PUBLIC, used.access);
		assertEquals(ACC_PRIVATE, unused.access);
		assertEquals(ACC_PUBLIC, field.access, "public, and no longer final since the mod writes it");
	}

	@Test
	void leavesClassesAloneWhenNoModUsesThem() {
		byte[] bytes = game();
		assertSame(bytes, new Access().transform(GAME.replace('/', '.'), bytes));
	}
}
