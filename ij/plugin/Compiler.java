package ij.plugin;
import java.awt.*;
import java.io.*;
import ij.*;
import ij.gui.*;
import ij.io.*;
import ij.util.*;
import ij.plugin.frame.Editor;
import ij.text.TextWindow;

/** Compiles and runs plugins using the javac compiler. */
public class Compiler implements PlugIn, FilenameFilter {

	private static sun.tools.javac.Main javac;
	private static ByteArrayOutputStream output;
	private static String dir, name;
	private static Editor errors;

	public void run(String arg) {
		IJ.register(Compiler.class);
		if (arg.equals("edit"))
			edit();
		else
			compileAndRun(arg);
	 }
	 
	void edit() {
		if (open("", "Open macro or plugin")) {
			Editor ed = (Editor)IJ.runPlugIn("ij.plugin.frame.Editor", "");
			if (ed!=null) ed.open(dir, name);
		}
	}
	
	void compileAndRun(String path) {
		if (!isJavac())
			return;
		if (!open(path, "Compile and Run Plugin..."))
			return;
		if (compile(dir+name))
			runPlugin(name);
	}
	 
	boolean isJavac() {
		try {
			if (javac==null) {
				output = new ByteArrayOutputStream(4096);
				javac = new sun.tools.javac.Main(output, "javac");
			}
		} catch (NoClassDefFoundError e) {
			IJ.error("This JVM does not include the javac compiler. Javac is\n"
					+"included with the Windows, OS X and Linux versions of\n"
 					+"ImageJ. Mac OS 9 users must install Apple's Java SDK.");
 			return false;
		}
		return true;
	}

	boolean compile(String path) {
		IJ.showStatus("compiling: "+path);
		String classpath = System.getProperty("java.class.path");
		//IJ.write("classpath: " + classpath);
		output.reset();
		boolean compiled = javac.compile(new String[] {"-deprecation","-classpath",classpath,path});
		String s = output.toString();
		boolean errors = (!compiled || areErrors(s));
		if (errors)
			showErrors(s);
		else
			IJ.showStatus("done");
		return compiled;
	 }

	boolean areErrors(String s) {
		boolean errors = s!=null && s.length()>0;
		if (errors && s.startsWith("Note: sun.tools.javac") && s.indexOf("error")==-1)
			errors = false;
		return errors;
	}
	
	void showErrors(String s) {
		if (errors==null || !errors.isVisible())
			errors = (Editor)IJ.runPlugIn("ij.plugin.frame.Editor", "");
		if (errors!=null)
			errors.display("Errors", s);
		IJ.showStatus("done (errors)");
	}

	 // open the .java source file
	 boolean open(String path, String msg) {
	 	boolean okay;
		String fileName, directory;
	 	if (path.equals("")) {
			if (dir==null)
				dir = Menus.getPlugInsPath();
			OpenDialog od = new OpenDialog(msg, dir, name);
			directory = od.getDirectory();
			fileName = od.getFileName();
			okay = fileName!=null;
			if (okay && !(fileName.endsWith(".java")||fileName.endsWith(".txt"))) {
				IJ.error("File name must end with \".java\", or \".txt\".");
				okay = false;
			}
		} else {
			int i = path.lastIndexOf('/');
			if (i==-1)
				i = path.lastIndexOf('\\');
			if (i>0) {
				directory = path.substring(0, i+1);
				fileName = path.substring(i+1);
			} else {
				directory = "";
				fileName = path;
			}
			okay = true;
		}
		if (okay) {
			name = fileName;
			dir = directory;
		}
		return okay;
	}

	// only show files with names ending in ".java"
	// doesn't work with Windows
	public boolean accept(File dir, String name) {
		return name.endsWith(".java")||name.endsWith(".macro")||name.endsWith(".txt");
	}
	
	// run the plugin using a new class loader
	void runPlugin(String name) {
		name = name.substring(0,name.length()-5); // remove ".java"
		new PlugInExecuter(name);
	}
	
}


class PlugInExecuter implements Runnable {

	private String plugin;
	private Thread thread;

	/** Create a new object that runs the specified plugin
		in a separate thread. */
	PlugInExecuter(String plugin) {
		this.plugin = plugin;
		thread = new Thread(this, plugin);
		thread.setPriority(Math.max(thread.getPriority()-2, Thread.MIN_PRIORITY));
		thread.start();
	}

	public void run() {
		try {
			ImageJ ij = IJ.getInstance();
			if (ij!=null) ij.runUserPlugIn(plugin, plugin, "", true);
		} catch(Throwable e) {
			IJ.showStatus("");
			IJ.showProgress(1.0);
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp!=null) imp.unlock();
			String msg = e.getMessage();
			if (e instanceof RuntimeException && msg!=null && e.getMessage().equals("Macro canceled"))
				return;
			CharArrayWriter caw = new CharArrayWriter();
			PrintWriter pw = new PrintWriter(caw);
			e.printStackTrace(pw);
			String s = caw.toString();
			if (IJ.isMacintosh())
				s = Tools.fixNewLines(s);
			new TextWindow("Exception", s, 350, 250);
		}
	}

}
