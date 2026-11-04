package gg.rago.loader.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Makes the game's package-private and protected classes and members public.
 *
 * <p>Mojang's jar has all its classes in one package, so a class may use any package-private or
 * protected member of another: {@code GuiConnecting$1} reads {@code GuiScreen.mc}, for one. Once
 * renamed into MCP's packages that access fails ({@link IllegalAccessError}, on the connection
 * thread: "Connecting to the server..." for ever). Forge never had this, as it runs the game with
 * its original names. Private members stay private: those are reached through accessors.
 */
public final class PackageAccess {
	private static final int HIDDEN = Opcodes.ACC_PRIVATE;

	static int open(int access) {
		if ((access & HIDDEN) != 0) {
			return access;
		}
		return access & ~Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC;
	}

	private static boolean closed(int access) {
		return (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE)) == 0;
	}

	public byte[] transform(String name, byte[] bytes) {
		ClassReader reader = new ClassReader(bytes);
		boolean[] needed = {false};
		reader.accept(new ClassVisitor(Opcodes.ASM9) {
			@Override
			public void visit(int version, int access, String n, String signature, String superName, String[] interfaces) {
				needed[0] |= closed(access);
			}

			@Override
			public void visitInnerClass(String n, String outer, String inner, int access) {
				needed[0] |= closed(access);
			}

			@Override
			public FieldVisitor visitField(int access, String n, String descriptor, String signature, Object value) {
				needed[0] |= closed(access);
				return null;
			}

			@Override
			public MethodVisitor visitMethod(int access, String n, String descriptor, String signature, String[] exceptions) {
				needed[0] |= closed(access);
				return null;
			}
		}, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		if (!needed[0]) {
			return bytes;
		}
		ClassWriter writer = new ClassWriter(reader, 0);
		reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
			@Override
			public void visit(int version, int access, String n, String signature, String superName, String[] interfaces) {
				super.visit(version, open(access), n, signature, superName, interfaces);
			}

			@Override
			public void visitInnerClass(String n, String outer, String inner, int access) {
				super.visitInnerClass(n, outer, inner, open(access));
			}

			@Override
			public FieldVisitor visitField(int access, String n, String descriptor, String signature, Object value) {
				return super.visitField(open(access), n, descriptor, signature, value);
			}

			@Override
			public MethodVisitor visitMethod(int access, String n, String descriptor, String signature, String[] exceptions) {
				return super.visitMethod(open(access), n, descriptor, signature, exceptions);
			}
		}, 0);
		return writer.toByteArray();
	}
}
