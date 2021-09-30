/*
 * Copyright 2019-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.aot.factories;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.springframework.aot.BootstrapContributor;
import org.springframework.aot.BuildContext;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.ClassWriter;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.nativex.AotOptions;

/**
 * Modify the {@link SpringApplication} on the classpath with enhancements for
 * Aot and put the result in the project output folder.
 *
 * @author Andy Clement
 * @author Sebastien Deleuze
 */
public class BuildTimeSubstitutionApplier implements BootstrapContributor {

	private final static String[] substitutions = new String[]{
		"org.springframework.nativex.substitutions.boot.Target_SpringApplication"
	};
	
	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE - 1;
	}
	
	public static void main(String[] args) {
		new BuildTimeSubstitutionApplier().apply();
	}
	
	private Map<String,byte[]> apply() {
		Map<String,byte[]> results = new HashMap<>();
		for (String substitution: substitutions) {
			try {
				Class<?> substitutionClass = Class.forName(substitution);
				findMethodsOfInterest(substitutionClass);
				walkTarget(results, substitutionClass);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return results;
	}

	

	private void walkTarget(Map<String, byte[]> results, Class<?> sub) throws Exception {
		String target = findTarget(sub);
		InputStream stream = BuildTimeSubstitutionApplier.class.getClassLoader().getResourceAsStream(target.replace(".","/")+".class");
		ClassReader cr = new ClassReader(stream);
		SpringApplicationClassRewriter springApplicationClassRewriter = new SpringApplicationClassRewriter(sub);
		cr.accept(springApplicationClassRewriter, 0);
		byte[] bytes = springApplicationClassRewriter.getBytes();
		FileOutputStream fos = new FileOutputStream(new File("/tmp/X.class"));
		fos.write(bytes);
		fos.close();
	}
	
	enum Action {
		SUBSTITUTE, DELETE;
	}

	Map<String, Action> moi = new HashMap<>();
	
	private void findMethodsOfInterest(Class<?> sc) {
		Method[] declaredMethods = sc.getDeclaredMethods();
		for (Method dm: declaredMethods) {
			if (hasAnnotation(dm, "com.oracle.svm.core.annotate.Substitute")) {
				moi.put(dm.getName()+toDesc(dm.getParameterTypes()), Action.SUBSTITUTE);
			}
			if (hasAnnotation(dm, "com.oracle.svm.core.annotate.Delete")) {
				moi.put(dm.getName()+toDesc(dm.getParameterTypes()), Action.DELETE);
			}
		}

		Constructor[] ctors = sc.getDeclaredConstructors();
		for (Constructor dm: ctors) {
			if (hasAnnotation(dm, "com.oracle.svm.core.annotate.Substitute")) {
				moi.put("<init>"+toDesc(dm.getParameterTypes()), Action.SUBSTITUTE);
			}
			if (hasAnnotation(dm, "com.oracle.svm.core.annotate.Delete")) {
				moi.put("<init>"+dm.getName()+toDesc(dm.getParameterTypes()), Action.DELETE);
			}
		}
		System.out.println("Methods Of Interest "+moi);
	}
		

	private String toDesc(Class<?>[] parameterTypes) {
		StringBuilder s = new StringBuilder();
		s.append("(");
		for (Class<?> c: parameterTypes) {
			if (c.isArray()) {
				Class<?> d = c;
				while (d.isArray()) {
					s.append("[");
					d = d.getComponentType();
				}
				if (d.isPrimitive()) {
					throw new IllegalStateException("nyi");
					
				} else {
					s.append("L"+d.getName().replace(".","/")+";");
				}
			} else if (c.isPrimitive()) {
				throw new IllegalStateException("nyi");
			} else {
				s.append("L"+c.getName().replace(".","/")+";");
			}
		}
		s.append(")");
		return s.toString();
	}

	private static boolean hasAnnotation(Method dm, String string) {
		for (Annotation a: dm.getDeclaredAnnotations()) {
			if (a.annotationType().getName().equals(string)) {
				return true;
			}
		}
		return false;
	}
	private static boolean hasAnnotation(Constructor dm, String string) {
		for (Annotation a: dm.getDeclaredAnnotations()) {
			if (a.annotationType().getName().equals(string)) {
				return true;
			}
		}
		return false;
	}

	private static String findTarget(Class<?> sc) throws Exception {
		Annotation[] annotations = sc.getDeclaredAnnotations();
		for (Annotation a: annotations) {
			if (a.annotationType().getName().equals("com.oracle.svm.core.annotate.TargetClass")) {
				Class<?> c = a.annotationType();
				Method m = c.getDeclaredMethod("className");
				Object invoke = m.invoke(a);
				return (String)invoke;
			}
		}
		return null;
	}

	@Override
	public void contribute(BuildContext context, AotOptions aotOptions) {
//		Resource resource = context.getTypeSystem().getResourceLoader()
//				.getResource("org/springframework/boot/SpringApplication.class");
//		SpringApplicationClassRewriter ca = modify(resource);
//		byte[] bytes = ca.getBytes();
//		if (bytes != null) {
//			Path path = Paths.get("org/springframework/boot/SpringApplication.class");
//			context.addResources(new ResourceFile() {
//				@Override
//				public void writeTo(Path resourcesPath) throws IOException {
//					Path relativeFolder = path.getParent();
//					Path filename = path.getFileName();
//					Path absoluteFolder = resourcesPath.resolve(relativeFolder);
//					Files.createDirectories(absoluteFolder);
//					Path absolutePath = absoluteFolder.resolve(filename);
//					try (FileOutputStream fos = new FileOutputStream(absolutePath.toFile())) {
//						fos.write(bytes);
//					}
//				}
//			});
//		}
	}
//
//	private SpringApplicationClassRewriter modify(Resource resource) {
//		try {
//			ClassReader cr = new ClassReader(resource.getInputStream());
//			SpringApplicationClassRewriter springApplicationClassRewriter = new SpringApplicationClassRewriter();
//			cr.accept(springApplicationClassRewriter, 0);
//			return springApplicationClassRewriter;
//		} catch (IOException ioe) {
//			throw new IllegalStateException("Problem accessing and rewriting SpringApplication.class", ioe);
//		}
//	}
	
	class DonorVisitor extends ClassVisitor implements Opcodes {

		public MethodVisitor found;
		private String name;
		private String desc;
		private SpringApplicationClassRewriter cv;
		public boolean patched = false;

		public DonorVisitor(String name, String desc, SpringApplicationClassRewriter cv) {
			super(ASM5);
			this.name = name;
			this.desc = desc;
			this.cv = cv;
		}
		
		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
				String[] exceptions) {
			if (this.name.equals(name) && this.desc.equals(desc) && isSubstituted(name,desc)) {
				System.out.println("Applying patch to "+name+desc);
				MethodVisitor visitMethod = cv.visitMethod2(access, name, descriptor, signature, exceptions);
				patched = true;
				return visitMethod;
			}
			return null;
		}

		private boolean isSubstituted(String name, String desc) {
			String key = name +  desc.substring(0,desc.indexOf(")")+1);
			Action action = moi.get(key);
			return action != null && action==Action.SUBSTITUTE;
		}

	}

	class SpringApplicationClassRewriter extends ClassVisitor implements Opcodes {

		private Class<?> sub;
		private String name;

		public SpringApplicationClassRewriter(Class<?> sub) {
			super(ASM5, new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS));
			this.sub = sub;
			
		}

		public byte[] getBytes() {
			return ((ClassWriter) cv).toByteArray();
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName,
				String[] interfaces) {
			this.name = name;
			super.visit(version, access, name, signature, superName, interfaces);
		}

		public MethodVisitor visitMethod2(int access, String name, String desc, String signature, String[] exceptions) {
			return new Replacer(super.visitMethod(access, name, desc, signature, exceptions), sub.getName(), name);
		}
		
		
		public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
			boolean donated = findDonor(sub, name,desc, this);
			if (!donated) {
				return super.visitMethod(access, name, desc, signature, exceptions);
			} else {
				return null; 
			}
		}

		private boolean findDonor(Class<?> sub2, String name, String desc, SpringApplicationClassRewriter cv) {
			InputStream stream = BuildTimeSubstitutionApplier.class.getClassLoader().getResourceAsStream(sub2.getName().replace(".","/")+".class");
			try {
				ClassReader cr = new ClassReader(stream);
				DonorVisitor d = new DonorVisitor(name, desc, cv);
				cr.accept(d, 0);
				return d.patched;
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		}

		class Replacer extends MethodVisitor implements Opcodes {
			String from;
			String to;
			public Replacer(MethodVisitor mv, String from, String to) {
				super(ASM5,mv);
				this.from = from.replace(".","/");
				this.to = to.replace(".","/");
			}
			// todo should replace entries in descriptors, what else?
			@Override
			public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
				if (owner.equals(from)) {
					owner = to;
				}
				super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
			}
			@Override
			public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
				if (owner.equals(from)) {
					owner = to;
				}
				super.visitFieldInsn(opcode, owner, name, descriptor);
			}
		}
		class PatchingMethodVisitor extends MethodVisitor implements Opcodes {
			public PatchingMethodVisitor() {
				super(ASM5);
			}
		}
	}	

}
