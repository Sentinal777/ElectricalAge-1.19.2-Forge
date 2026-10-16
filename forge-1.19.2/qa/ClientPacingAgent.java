import java.lang.instrument.*;
import java.security.ProtectionDomain;
import jdk.internal.org.objectweb.asm.*;
/** Isolated automated-render QA workaround. Never included in or applied by the mod JAR. */
public final class ClientPacingAgent {
    public static void premain(String args,Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader,String name,Class<?> type,ProtectionDomain domain,byte[] bytes) {
                if(name==null)name=new ClassReader(bytes).getClassName();
                boolean glfw=name.replace('.','/').equals("org/lwjgl/glfw/GLFW");
                if(!glfw&&!name.replace('.','/').equals("com/mojang/blaze3d/systems/RenderSystem"))return null;
                ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,0);
                reader.accept(new ClassVisitor(Opcodes.ASM8,writer) {
                    @Override public MethodVisitor visitMethod(int access,String method,String descriptor,String signature,String[] exceptions) {
                        boolean skip=glfw&&(method.equals("glfwPollEvents")||method.equals("glfwWaitEvents")||method.equals("glfwWaitEventsTimeout"));
                        MethodVisitor m=super.visitMethod(skip?access&~Opcodes.ACC_NATIVE:access,method,descriptor,signature,exceptions);
                        if(skip) { m.visitCode();m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,descriptor.equals("(D)V")?2:0);m.visitEnd();return null; }
                        if(glfw||!method.equals("limitDisplayFPS")||!descriptor.equals("(I)V"))return m;
                        m.visitCode();m.visitLdcInsn(20000000L);m.visitMethodInsn(Opcodes.INVOKESTATIC,"java/util/concurrent/locks/LockSupport","parkNanos","(J)V",false);
                        m.visitInsn(Opcodes.RETURN);m.visitMaxs(2,1);m.visitEnd();return null;
                    }
                },0);
                System.out.println("ELN_CLIENT_QA isolated event-wait workaround enabled");return writer.toByteArray();
            }
        });
    }
}
