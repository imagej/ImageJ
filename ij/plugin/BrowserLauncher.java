package ij.plugin;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.applet.Applet;

/**
 * This plugin implements ImageJ's Help/ImageJ Web Site command. It is a slightly modified
 * version of Eric Albert's cross-platform (Windows, Mac, Mac OS X and Unix) BrowserLauncher class.
 * <p>
 * BrowserLauncher is a class that provides one static method, openURL, which opens the default
 * web browser for the current user of the system to the given URL.  It may support other
 * protocols depending on the system -- mailto, ftp, etc. -- but that has not been rigorously
 * tested and is not guaranteed to work.
 * <p>
 * Yes, this is platform-specific code, and yes, it may rely on classes on certain platforms
 * that are not part of the standard JDK.  What we're trying to do, though, is to take something
 * that's frequently desirable but inherently platform-specific -- opening a default browser --
 * and allow programmers (you, for example) to do so without worrying about dropping into native
 * code or doing anything else similarly evil.
 * <p>
 * Anyway, this code is completely in Java and will run on all JDK 1.1-compliant systems without
 * modification or a need for additional libraries.  All classes that are required on certain
 * platforms to allow this to run are dynamically loaded at runtime via reflection and, if not
 * found, will not cause this to do anything other than returning an error when opening the
 * browser.
 * <p>
 * There are certain system requirements for this class, as it's running through Runtime.exec(),
 * which is Java's way of making a native system call.  Currently, this requires that a Macintosh
 * have a Finder which supports the GURL event, which is true for Mac OS 8.0 and 8.1 systems that
 * have the Internet Scripting AppleScript dictionary installed in the Scripting Additions folder
 * in the Extensions folder (which is installed by default as far as I know under Mac OS 8.0 and
 * 8.1), and for all Mac OS 8.5 and later systems.  On Windows, it only runs under Win32 systems
 * (Windows 95, 98, and NT 4.0, as well as later versions of all).  On other systems, this drops
 * back from the inherently platform-sensitive concept of a default browser and simply attempts
 * to launch Netscape via a shell command.
 * <p>
 * This code is Copyright 1999-2001 by Eric Albert (ejalbert@cs.stanford.edu) and may be
 * redistributed or modified in any form without restrictions as long as the portion of this
 * comment from this paragraph through the end of the comment is not removed.  The author
 * requests that he be notified of any application, applet, or other binary that makes use of
 * this code, but that's more out of curiosity than anything and is not required.  This software
 * includes no warranty.  The author is not repsonsible for any loss of data or functionality
 * or any adverse or unexpected effects of using this software.
 * <p>
 * Credits:
 * <br>Steven Spencer, JavaWorld magazine (<a href="http://www.javaworld.com/javaworld/javatips/jw-javatip66.html">Java Tip 66</a>)
 * <br>Thanks also to Ron B. Yeh, Eric Shapiro, Ben Engber, Paul Teitlebaum, Andrea Cantatore,
 * Larry Barowski, Trevor Bedzek, Frank Miedrich, and Ron Rabakukk
 *
 * @author Eric Albert (<a href="mailto:ejalbert@cs.stanford.edu">ejalbert@cs.stanford.edu</a>)
 * @version 1.4b1 (Released June 20, 2001)
 */
public class BrowserLauncher implements PlugIn {

	/** Opens the specified URL (default is the ImageJ home page). */
	public void run(String theURL) {
		if (theURL==null || theURL.equals(""))
			theURL = "http://rsb.info.nih.gov/ij/";
		else if (theURL.equals("online"))
			theURL = "http://rsb.info.nih.gov/ij/docs";
		Applet applet = ij.IJ.getApplet();
		if (applet!=null) {
			try {
				applet.getAppletContext().showDocument(new URL(theURL), "_blank" );
			} catch (Exception e) {}
			return;
		}
		try {openURL(theURL);}
		catch (IOException e) {}
	}

	/**
	 * The Java virtual machine that we are running on.  Actually, in most cases we only care
	 * about the operating system, but some operating systems require us to switch on the VM. */
	private static int jvm;

	/** The browser for the system */
	private static Object browser;

	/**
	 * Caches whether any classes, methods, and fields that are not part of the JDK and need to
	 * be dynamically loaded at runtime loaded successfully.
	 * <p>
	 * Note that if this is <code>false</code>, <code>openURL()</code> will always return an
	 * IOException.
	 */
	private static boolean loadedWithoutErrors;

	/** The com.apple.mrj.MRJFileUtils class */
	private static Class mrjFileUtilsClass;

	/** The com.apple.mrj.MRJOSType class */
	private static Class mrjOSTypeClass;

	/** The com.apple.MacOS.AEDesc class */
	private static Class aeDescClass;
	
	/** The <init>(int) method of com.apple.MacOS.AETarget */
	private static Constructor aeTargetConstructor;
	
	/** The <init>(int, int, int) method of com.apple.MacOS.AppleEvent */
	private static Constructor appleEventConstructor;
	
	/** The <init>(String) method of com.apple.MacOS.AEDesc */
	private static Constructor aeDescConstructor;
	
	/** The findFolder method of com.apple.mrj.MRJFileUtils */
	private static Method findFolder;

	/** The getFileCreator method of com.apple.mrj.MRJFileUtils */
	private static Method getFileCreator;
	
	/** The getFileType method of com.apple.mrj.MRJFileUtils */
	private static Method getFileType;
	
	/** The openURL method of com.apple.mrj.MRJFileUtils */
	private static Method openURL;
	
	/** The makeOSType method of com.apple.MacOS.OSUtils */
	private static Method makeOSType;
	
	/** The putParameter method of com.apple.MacOS.AppleEvent */
	private static Method putParameter;
	
	/** The sendNoReply method of com.apple.MacOS.AppleEvent */
	private static Method sendNoReply;
	
	/** Actually an MRJOSType pointing to the System Folder on a Macintosh */
	private static Object kSystemFolderType;

	/** The keyDirectObject AppleEvent parameter type */
	private static Integer keyDirectObject;

	/** The kAutoGenerateReturnID AppleEvent code */
	private static Integer kAutoGenerateReturnID;
	
	/** The kAnyTransactionID AppleEvent code */
	private static Integer kAnyTransactionID;

	/** The linkage object required for JDirect 3 on Mac OS X. */
	private static Object linkage;
	
	/** The framework to reference on Mac OS X */
	private static final String JDirect_MacOSX = "/System/Library/Frameworks/Carbon.framework/Frameworks/HIToolbox.framework/HIToolbox";

	/** JVM constant for MRJ 2.0 */
	private static final int MRJ_2_0 = 0;
	
	/** JVM constant for MRJ 2.1 or later */
	private static final int MRJ_2_1 = 1;

	/** JVM constant for Java on Mac OS X 10.0 (MRJ 3.0) */
	private static final int MRJ_3_0 = 3;
	
	/** JVM constant for MRJ 3.1 */
	private static final int MRJ_3_1 = 4;

	/** JVM constant for any Windows JVM */
	private static final int WINDOWS = 5;
	
	/** JVM constant for any Mac OS X JVM */
    private static final int MACOSX = 6;

	/** JVM constant for any other platform */
	private static final int OTHER = -1;

	/**
	 * The file type of the Finder on a Macintosh.  Hardcoding "Finder" would keep non-U.S. English
	 * systems from working properly.
	 */
	private static final String FINDER_TYPE = "FNDR";

	/**
	 * The creator code of the Finder on a Macintosh, which is needed to send AppleEvents to the
	 * application.
	 */
	private static final String FINDER_CREATOR = "MACS";

	/** The name for the AppleEvent type corresponding to a GetURL event. */
	private static final String GURL_EVENT = "GURL";

	/**
	 * The shell parameters for Netscape that opens a given URL in an already-open copy of Netscape
	 * on many command-line systems.
	 */
	private static final String NETSCAPE_REMOTE_PARAMETER = "-remote";
	private static final String NETSCAPE_OPEN_PARAMETER_START = "'openURL(";
	private static final String NETSCAPE_OPEN_PARAMETER_END = ")'";
	
	/**
	 * The message from any exception thrown throughout the initialization process.
	 */
	private static String errorMessage;

	/**
	 * An initialization block that determines the operating system and loads the necessary
	 * runtime data.
	 */
	static {
		loadedWithoutErrors = true;
		String osName = System.getProperty("os.name");
		if (osName.startsWith("Mac OS X") )
            jvm  = MACOSX;
		else if (osName.startsWith("Mac OS")) {
			String mrjVersion = System.getProperty("mrj.version");
			String majorMRJVersion = mrjVersion.substring(0, 3);
			try {
				double version = Double.valueOf(majorMRJVersion).doubleValue();
				if (version == 2) {
					jvm = MRJ_2_0;
				} else if (version >= 2.1 && version < 3) {
					// Assume that all 2.x versions of MRJ work the same.  MRJ 2.1 actually
					// works via Runtime.exec() and 2.2 supports that but has an openURL() method
					// as well that we currently ignore.
					jvm = MRJ_2_1;
				} else if (version == 3.0) {
					jvm = MRJ_3_0;
				} else if (version >= 3.1) {
					// Assume that all 3.1 and later versions of MRJ work the same.
					jvm = MRJ_3_1;
				} else {
					loadedWithoutErrors = false;
					errorMessage = "Unsupported MRJ version: " + version;
				}
			} catch (NumberFormatException nfe) {
				loadedWithoutErrors = false;
				errorMessage = "Invalid MRJ version: " + mrjVersion;
			}
		} else if (osName.startsWith("Windows"))
				jvm = WINDOWS;
		else
			jvm = OTHER;
		
		if (loadedWithoutErrors) {	// if we haven't hit any errors yet
			loadedWithoutErrors = loadClasses();
		}
	}

	/**
	 * This class should be never be instantiated; this just ensures so.
	 */
	public BrowserLauncher() { }
	
	/**
	 * Called by a static initializer to load any classes, fields, and methods required at runtime
	 * to locate the user's web browser.
	 * @return <code>true</code> if all intialization succeeded
	 *			<code>false</code> if any portion of the initialization failed
	 */
	private static boolean loadClasses() {
		switch (jvm) {
			case MRJ_2_1:
				try {
					mrjFileUtilsClass = Class.forName("com.apple.mrj.MRJFileUtils");
					mrjOSTypeClass = Class.forName("com.apple.mrj.MRJOSType");
					Field systemFolderField = mrjFileUtilsClass.getDeclaredField("kSystemFolderType");
					kSystemFolderType = systemFolderField.get(null);
					findFolder = mrjFileUtilsClass.getDeclaredMethod("findFolder", new Class[] { mrjOSTypeClass });
					getFileCreator = mrjFileUtilsClass.getDeclaredMethod("getFileCreator", new Class[] { File.class });
					getFileType = mrjFileUtilsClass.getDeclaredMethod("getFileType", new Class[] { File.class });
				} catch (ClassNotFoundException cnfe) {
					errorMessage = cnfe.getMessage();
					return false;
				} catch (NoSuchFieldException nsfe) {
					errorMessage = nsfe.getMessage();
					return false;
				} catch (NoSuchMethodException nsme) {
					errorMessage = nsme.getMessage();
					return false;
				} catch (SecurityException se) {
					errorMessage = se.getMessage();
					return false;
				} catch (IllegalAccessException iae) {
					errorMessage = iae.getMessage();
					return false;
				}
				break;
			case MACOSX:
			    try {
                    if (new File("/System/Library/Java/com/apple/cocoa/application/NSWorkspace.class").exists()){
                         ClassLoader classLoader = new URLClassLoader(new URL[]{new File("/System/Library/Java").toURL()});
                         mrjFileUtilsClass = Class.forName("com.apple.cocoa.application.NSWorkspace", true, classLoader);
                    } else {
                         mrjFileUtilsClass = Class.forName("com.apple.cocoa.application.NSWorkspace");
                    }
                    openURL = mrjFileUtilsClass.getDeclaredMethod("openURL", new Class[] { java.net.URL.class });
			    } catch (Exception e) {
					ij.IJ.log("MaxOSX: "+e);
					errorMessage = e.getMessage();
			        return false;
			    }
			default:
			    break;
		}
		return true;
	}

	/**
	 * Attempts to locate the default web browser on the local system.  Caches results so it
	 * only locates the browser once for each use of this class per JVM instance.
	 * @return The browser for the system.  Note that this may not be what you would consider
	 *			to be a standard web browser; instead, it's the application that gets called to
	 *			open the default web browser.  In some cases, this will be a non-String object
	 *			that provides the means of calling the default browser.
	 */
	private static Object locateBrowser() {
		if (browser != null) {
			return browser;
		}
		switch (jvm) {
			case MRJ_2_0:
				return null;
			case MRJ_2_1:
				File systemFolder;
				try {
					systemFolder = (File) findFolder.invoke(null, new Object[] { kSystemFolderType });
				} catch (IllegalArgumentException iare) {
					browser = null;
					errorMessage = iare.getMessage();
					return browser;
				} catch (IllegalAccessException iae) {
					browser = null;
					errorMessage = iae.getMessage();
					return browser;
				} catch (InvocationTargetException ite) {
					browser = null;
					errorMessage = ite.getTargetException().getClass() + ": " + ite.getTargetException().getMessage();
					return browser;
				}
				String[] systemFolderFiles = systemFolder.list();
				// Avoid a FilenameFilter because that can't be stopped mid-list
				for(int i = 0; i < systemFolderFiles.length; i++) {
					try {
						File file = new File(systemFolder, systemFolderFiles[i]);
						if (!file.isFile()) {
							continue;
						}
						// We're looking for a file with a creator code of 'MACS' and
						// a type of 'FNDR'.  Only requiring the type results in non-Finder
						// applications being picked up on certain Mac OS 9 systems,
						// especially German ones, and sending a GURL event to those
						// applications results in a logout under Multiple Users.
						Object fileType = getFileType.invoke(null, new Object[] { file });
						if (FINDER_TYPE.equals(fileType.toString())) {
							Object fileCreator = getFileCreator.invoke(null, new Object[] { file });
							if (FINDER_CREATOR.equals(fileCreator.toString())) {
								browser = file.toString();	// Actually the Finder, but that's OK
								return browser;
							}
						}
					} catch (IllegalArgumentException iare) {
						browser = browser;
						errorMessage = iare.getMessage();
						return null;
					} catch (IllegalAccessException iae) {
						browser = null;
						errorMessage = iae.getMessage();
						return browser;
					} catch (InvocationTargetException ite) {
						browser = null;
						errorMessage = ite.getTargetException().getClass() + ": " + ite.getTargetException().getMessage();
						return browser;
					}
				}
				browser = null;
				break;
			case MRJ_3_0:
			case MRJ_3_1:
			case WINDOWS:
				browser = "";	// not used; return something non-null
				break;
			case OTHER:
			default:
				browser = "netscape";
				break;
		}
		return browser;
	}

	/**
	 * Attempts to open the default web browser to the given URL.
	 * @param url The URL to open
	 * @throws IOException If the web browser could not be located or does not run
	 */
	public static void openURL(String url) throws IOException {
		if (!loadedWithoutErrors) {
			throw new IOException("Exception in finding browser: " + errorMessage);
		}
		Object browser = locateBrowser();
		if (browser == null) {
			throw new IOException("Unable to locate browser: " + errorMessage);
		}
		
		switch (jvm) {
			case MRJ_2_1:
				Runtime.getRuntime().exec(new String[] { (String) browser, url } );
				break;
			case MACOSX:
			    try {
			        Method aMethod = mrjFileUtilsClass.getDeclaredMethod("sharedWorkspace", new Class[] {});
			        Object aTarget = aMethod.invoke( mrjFileUtilsClass, new Object[] {});
			        openURL.invoke(aTarget, new Object[] { new java.net.URL( url )}); 
			    } catch (Exception e) {
					errorMessage = ""+e;
			    }
			    break;
		    case WINDOWS:
				String cmd = "rundll32 url.dll,FileProtocolHandler " + url;
				if (ij.IJ.debugMode) {ij.IJ.log("Exec: "+cmd);}
				Process process = Runtime.getRuntime().exec(cmd);
				// This avoids a memory leak on some versions of Java on Windows.
				// That's hinted at in <http://developer.java.sun.com/developer/qow/archive/68/>.
				try {
					process.waitFor();
					process.exitValue();
				} catch (InterruptedException ie) {
					throw new IOException("InterruptedException while launching browser: " + ie.getMessage());
				}
				break;
			case OTHER:
				// Assume that we're on Unix and that Netscape is installed
				
				// First, attempt to open the URL in a currently running session of Netscape
				process = Runtime.getRuntime().exec(new String[] { (String) browser,
													NETSCAPE_REMOTE_PARAMETER,
													NETSCAPE_OPEN_PARAMETER_START +
													url +
													NETSCAPE_OPEN_PARAMETER_END });
				try {
					int exitCode = process.waitFor();
					if (exitCode != 0) {	// if Netscape was not open
						Runtime.getRuntime().exec(new String[] { (String) browser, url });
					}
				} catch (InterruptedException ie) {
					throw new IOException("InterruptedException while launching browser: " + ie.getMessage());
				}
				break;
			default:
				// This should never occur, but if it does, we'll try the simplest thing possible
				Runtime.getRuntime().exec(new String[] { (String) browser, url });
				break;
		}
	}

	/**
	 * Methods required for Mac OS X.  The presence of native methods does not cause
	 * any problems on other platforms.
	 */
	private native static int ICStart(int[] instance, int signature);
	private native static int ICStop(int[] instance);
	private native static int ICLaunchURL(int instance, byte[] hint, byte[] data, int len,
											int[] selectionStart, int[] selectionEnd);
}

