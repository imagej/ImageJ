package ij.io;

import ij.IJ;
import java.io.*;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.net.*;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.Map;

/** ImageJ uses this class loader to load plugins and resources from the
 * plugins directory and immediate subdirectories. This class loader will
 * also load classes and resources from JAR files.
 *
 * <p> The class loader searches for classes and resources in the following order:
 * <ol>
 *  <li> Plugins directory</li>
 *  <li> Subdirectories of the Plugins directory</li>
 *  <li> JAR and ZIP files in the plugins directory and subdirectories</li>
 * </ol>
 * <p> The class loader does not recurse into subdirectories beyond the first level.
*/
public class PluginClassLoader extends URLClassLoader {
    protected String path;
    private static final Map<URI, CodeSource> METADATA_CACHE = new HashMap<>();
    private static final Map<ClassDesc, ClassDesc> APPLET_REMAP = Map.of(
        ClassDesc.of("java.applet.Applet"), ClassDesc.of("ij.stub.Applet"),
        ClassDesc.of("java.applet.AppletContext"), ClassDesc.of("ij.stub.AppletContext"),
        ClassDesc.of("java.applet.AppletStub"), ClassDesc.of("ij.stub.AppletStub"),
        ClassDesc.of("java.applet.AudioClip"), ClassDesc.of("ij.stub.AudioClip")
    );
    private static final ClassTransform APPLET_TRANSFORMER = createTransform();

    static {
        registerAsParallelCapable();
    }

    /**
     * Creates a new PluginClassLoader that searches in the directory path
     * passed as a parameter. The constructor automatically finds all JAR and ZIP
     * files in the path and first level of subdirectories. The JAR and ZIP files
     * are stored in a Vector for future searches.
     * @param path the path to the plugins directory.
     */
	public PluginClassLoader(String path) {
		super(new URL[0], IJ.class.getClassLoader());
		init(path);
	}
	
	/** This version of the constructor is used when ImageJ is launched using Java WebStart. */
	public PluginClassLoader(String path, boolean callSuper) {
		super(new URL[0], Thread.currentThread().getContextClassLoader());
		init(path);
	}

	void init(String path) {
		this.path = path;

		//find all JAR files on the path and subdirectories
		File f = new File(path);
        try {
            // Add plugin directory to search path
            addURL(f.toURI().toURL());
        } catch (MalformedURLException e) {
            ij.IJ.log("PluginClassLoader: "+e);
        }
		String[] list = f.list();
		if (list==null)
			return;
		for (int i=0; i<list.length; i++) {
			if (list[i].equals(".rsrc"))
				continue;
			File f2=new File(path, list[i]);
			if (f2.isDirectory())
				addDirectory(f2);
			else 
				addJar(f2);
		}
		addDirectory(f, "jars"); // add ImageJ/jars; requested by Wilhelm Burger
	}

	private void addDirectory(File f) {
		//if (IJ.debugMode) IJ.log("PluginClassLoader.addDirectory: "+f);
		try {
			// Add first level subdirectories to search path
			addURL(f.toURI().toURL());
		} catch (MalformedURLException e) {
			ij.IJ.log("PluginClassLoader: "+e);
		}
		String[] innerlist = f.list();
		if (innerlist==null)
			return;
		for (int j=0; j<innerlist.length; j++) {
			File g = new File(f,innerlist[j]);
			if (g.isFile())
				addJar(g);
		}
	}

    private void addJar(File f) {
        if (f.getName().endsWith(".jar") || f.getName().endsWith(".zip")) {
			//if (IJ.debugMode) IJ.log("PluginClassLoader.addJar: "+f);
            try {
                addURL(f.toURI().toURL());
            } catch (MalformedURLException e) {
				ij.IJ.log("PluginClassLoader: "+e);
            }
        }
    }

	private void addDirectory(File f, String name) {
		f = f.getParentFile();
		if (f==null)
			return;
		f = new File(f, name);
		if (f==null)
			return;
		if (f.isDirectory())
			addDirectory(f);
	}

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // Exclude java classes from remapping
        if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("sun.")) {
            return super.findClass(name);
        }

        var resourceName = name.replace('.', '/').concat(".class");

        var resourceUrl = findResource(resourceName);
        if (resourceUrl == null) {
            IO.println("Resource not found: " + resourceName);
            throw new ClassNotFoundException(name);
        }

        try (InputStream in = resourceUrl.openStream()) {
            var bytes = in.readAllBytes();
            var remapped = maybeRemapAppletRefs(bytes);

            var lastDot = name.lastIndexOf('.');
            if (lastDot != -1) {
                var pkg = name.substring(0, lastDot);
                if (getDefinedPackage(pkg) == null) {
                    definePackage(pkg, null, null, null, null, null, null, null);
                }
            }

            var codeSource = getCodeSource(resourceUrl, resourceName);

            //Files.write(Paths.get(name + ".class"), remapped);

            return defineClass(name, remapped, 0, remapped.length, codeSource);
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
    }

    private static byte[] maybeRemapAppletRefs(byte[] classBytes) {
        var cf = ClassFile.of();
        return cf.transformClass(cf.parse(classBytes), APPLET_TRANSFORMER);
    }

    private static ClassTransform createTransform() {
        CodeTransform codeTransform = (codeBuilder, e) -> {
            switch (e) {
                case InvokeInstruction i -> {
                    var owner = i.owner().asSymbol();
                    var type = i.typeSymbol();
                    var rOwner = APPLET_REMAP.get(i.owner().asSymbol());
                    var rType = APPLET_REMAP.get(type.returnType());

                    if (rOwner != null || rType != null) {
                        if (rType != null) {
                            type = type.changeReturnType(rType);
                        }
                        if (rOwner != null) {
                            owner = rOwner;
                        }
                        codeBuilder.invoke(i.opcode(), owner, i.name().stringValue(), type, i.isInterface());
                    } else {
                        codeBuilder.accept(i);
                    }
                }
                case FieldInstruction f -> {
                    var type = APPLET_REMAP.get(f.typeSymbol());
                    if (type != null) {
                        codeBuilder.fieldAccess(f.opcode(), f.owner().asSymbol(), f.name().stringValue(), type);
                    } else {
                        codeBuilder.accept(f);
                    }
                }
                default -> codeBuilder.accept(e);
            }
        };

        var methodTransform = MethodTransform.transformingCode(codeTransform);

        ClassTransform fieldRemapper = (classBuilder, e) -> {
            switch (e) {
                case FieldModel f -> {
                    var rf = APPLET_REMAP.get(f.fieldTypeSymbol());
                    if (rf != null) {
                        classBuilder.withField(f.fieldName().stringValue(), rf, f.flags().flagsMask());
                    } else {
                        classBuilder.accept(f);
                    }
                }
                default -> classBuilder.accept(e);
            }
        };

        return fieldRemapper.andThen(ClassTransform.transformingMethods(methodTransform));
    }

    private CodeSource getCodeSource(URL classUrl, String className) {
        if (classUrl == null) {
            return null;
        }

        try {
            var base = getCodeSourcePath(classUrl, className);

            return METADATA_CACHE.computeIfAbsent(base, (URI uri) -> {
                try {
                    var url = uri.toURL();
                    var con = url.openConnection();

                    if (con instanceof JarURLConnection jarURLConnection) {
                        return new CodeSource(jarURLConnection.getJarFileURL(), jarURLConnection.getCertificates());
                    }

                    var path = Path.of(uri);

                    if (Files.isDirectory(path)) {
                        return new CodeSource(path.toUri().toURL(), (Certificate[]) null);
                    }
                } catch (IOException e) {
                    if (IJ.debugMode) {
                        e.printStackTrace();
                    }
                }

                return null;
            });
        } catch (IllegalStateException e) {
            if (IJ.debugMode) {
                e.printStackTrace();
            }

            return null;
        }
    }

    private URI getCodeSourcePath(URL url, String className) throws IllegalStateException {
        try {
            if ("jar".equals(url.getProtocol())) {
                var file = url.getFile();
                var sep = file.indexOf("!/");

                if (sep == -1) {
                    throw new IllegalStateException("Invalid jar url: " + url);
                }

                return new URI("jar:" + file.substring(0, sep) + "!/");
            } else {
                var path = Path.of(url.toURI());
                var classPath = Path.of(className);

                if (!path.endsWith(classPath)) {
                    throw new IllegalStateException("URL does not end with resource: " + className);
                }

                var base = path;
                for (int i = 0; i < classPath.getNameCount(); i++) {
                    base = base.getParent();
                    if (base == null) break;
                }

                if (base == null) {
                    throw new IllegalStateException("Cannot determine code source for " + url);
                }

                return base.toUri();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to get CodeSource path", e);
        }
    }
}
