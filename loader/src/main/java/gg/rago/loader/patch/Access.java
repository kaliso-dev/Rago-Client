package gg.rago.loader.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.HashSet;
import java.util.Set;

/**
 * What Forge's access transformer does for mods, worked out from the mods themselves: every game
 * member a mod uses is made public, and every game field a mod writes loses {@code final}.
 *
 * <p>Forge widens a fixed list of members for every mod; this widens exactly what the mods running
 * now use, read from their bytecode when the game starts. Game members keep their SRG names
 * ({@code func_12345_a}, {@code field_12345_a}), which are the same for a method and every
 * override of it, so a use through a subclass widens the member where it is declared too.
 */
public final class Access {
	private static final String GAME = "net/minecraft/";

	private final Set<String> members = new HashSet<>();
	private final Set<String> written = new HashSet<>();
	private final Set<String> classes = new HashSet<>();

	/** Records what one mod class uses of the game. */
	public void scan(byte[] modClass) {
		new ClassReader(modClass).accept(new ClassVisitor(Opcodes.ASM9) {
			@Override
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				type(superName);
				if (interfaces != null) {
					for (String i : interfaces) {
						type(i);
					}
				}
			}

			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				return new MethodVisitor(Opcodes.ASM9) {
					@Override
					public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
						if (owner.startsWith(GAME)) {
							member(owner, name, descriptor);
							if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC) {
								written.add(key(owner, name, descriptor));
							}
						}
					}

					@Override
					public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
						if (owner.startsWith(GAME)) {
							member(owner, name, descriptor);
						}
					}

					@Override
					public void visitTypeInsn(int opcode, String type) {
						type(type);
					}

					@Override
					public void visitLdcInsn(Object value) {
						constant(value);
					}

					@Override
					public void visitInvokeDynamicInsn(String name, String descriptor, Handle bsm, Object... args) {
						for (Object a : args) {
							constant(a);
						}
					}
				};
			}
		}, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
	}

	private void constant(Object value) {
		if (value instanceof Handle h && h.getOwner().startsWith(GAME)) {
			member(h.getOwner(), h.getName(), h.getDesc());
		} else if (value instanceof Type t && t.getSort() == Type.OBJECT) {
			type(t.getInternalName());
		}
	}

	private void type(String internalName) {
		if (internalName != null && internalName.startsWith(GAME)) {
			classes.add(internalName);
		}
	}

	private void member(String owner, String name, String descriptor) {
		type(owner);
		members.add(key(owner, name, descriptor));
	}

	/** SRG names stand alone; other names (constructors, Java's own methods) only with their class. */
	private static String key(String owner, String name, String descriptor) {
		return name.startsWith("func_") || name.startsWith("field_") ? name : owner + "." + name + descriptor;
	}

	public boolean isEmpty() {
		return members.isEmpty() && classes.isEmpty();
	}

	private static int open(int access) {
		return access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
	}

	private boolean wants(String owner, String name, String descriptor, int access) {
		return (access & Opcodes.ACC_PUBLIC) == 0 && members.contains(key(owner, name, descriptor));
	}

	public byte[] transform(String name, byte[] bytes) {
		String internal = name.replace('.', '/');
		ClassReader reader = new ClassReader(bytes);
		boolean[] needed = {false};
		// a first pass over the headers only: most classes have nothing to widen
		reader.accept(new ClassVisitor(Opcodes.ASM9) {
			@Override
			public void visit(int version, int access, String n, String signature, String superName, String[] interfaces) {
				needed[0] |= (access & Opcodes.ACC_PUBLIC) == 0 && classes.contains(internal);
			}

			@Override
			public FieldVisitor visitField(int access, String n, String descriptor, String signature, Object value) {
				needed[0] |= wants(internal, n, descriptor, access)
					|| (access & Opcodes.ACC_FINAL) != 0 && written.contains(key(internal, n, descriptor));
				return null;
			}

			@Override
			public MethodVisitor visitMethod(int access, String n, String descriptor, String signature, String[] exceptions) {
				needed[0] |= wants(internal, n, descriptor, access);
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
				super.visit(version, classes.contains(internal) ? open(access) : access, n, signature, superName, interfaces);
			}

			@Override
			public void visitInnerClass(String n, String outer, String inner, int access) {
				super.visitInnerClass(n, outer, inner, n.equals(internal) && classes.contains(internal) ? open(access) : access);
			}

			@Override
			public FieldVisitor visitField(int access, String n, String descriptor, String signature, Object value) {
				int a = wants(internal, n, descriptor, access) ? open(access) : access;
				if (written.contains(key(internal, n, descriptor))) {
					a &= ~Opcodes.ACC_FINAL;
				}
				return super.visitField(a, n, descriptor, signature, value);
			}

			@Override
			public MethodVisitor visitMethod(int access, String n, String descriptor, String signature, String[] exceptions) {
				return super.visitMethod(wants(internal, n, descriptor, access) ? open(access) : access, n, descriptor, signature, exceptions);
			}
		}, 0);
		return writer.toByteArray();
	}
}
