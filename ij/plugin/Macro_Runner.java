package ij.plugin;
import ij.*;
import ij.io.*;
import ij.macro.*;
import ij.text.*;
import ij.util.*;
import ij.plugin.frame.*;
import ij.gui.GenericDialog;
import java.io.*;
import java.lang.reflect.*;

/** This class runs macros and scripts installed in the Plugins menu as well as
	macros and scripts opened using the Plugins/Macros/Run command. */
public class Macro_Runner implements PlugIn {
	private static String filePath;
	
	/** Opens and runs the specified macro file (.txt or .ijm) or script file (.js, .bsh or .py)  
		on the current thread. Displays a file open dialog if <code>name</code> 
		is an empty string. The macro or script is assumed to be in the ImageJ 
		plugins folder if  <code>name</code> is not a full path. */
	public void run(String name) {
		if (IJ.debugMode)
			IJ.log("Macro_Runner.run(): "+name);
		Thread thread = Thread.currentThread();
		String threadName = thread.getName();
		if (!threadName.endsWith("Macro$")) {
			if (name.endsWith(".js")||name.endsWith(".bsh")||name.endsWith(".py"))
				thread.setName(threadName+"_Script_Macro$");
			else
				thread.setName(threadName+"_Macro$");
		}
		String path = null;
		if (name.equals("")) {
			OpenDialog od = new OpenDialog("Run Macro or Script...", path);
			String directory = od.getDirectory();
			name = od.getFileName();
			if (name!=null) {
				path = directory+name;
				runMacroFile(path, null);
				if (Recorder.record) {
					if (Recorder.scriptMode())
						Recorder.recordCall("IJ.runMacroFile(\""+path+"\");");
					else
						Recorder.record("runMacro", path);
				}
			}
		} else if (name.startsWith("JAR:"))
			runMacroFromJar(name.substring(4), null);
		else if (name.startsWith("ij.jar:"))
			runMacroFromIJJar(name, null);
		else if (name.endsWith("Tool.ijm") || name.endsWith("Tool.txt")
		|| name.endsWith("Menu.ijm") || name.endsWith("Menu.txt"))
			(new MacroInstaller()).installTool(Menus.getPlugInsPath()+name);
		else {
			boolean fullPath = name.startsWith("/") || name.startsWith("\\") || name.indexOf(":\\")==1 || name.indexOf(":/")==1;
			if (fullPath)
				path = name;
			else
				path = Menus.getPlugInsPath() + name;
			runMacroFile(path, null);
		}
	}
        
	/** Opens and runs the specified macro or script on the current
		thread. The file is assumed to be in the ImageJ/macros folder
		unless 'name' is a full path. The macro or script can use the
		getArgument() function to retrieve the string argument.
    */
	public String runMacroFile(String name, String arg) {
		if (arg==null) arg = "";
		if (name.startsWith("ij.jar:"))
			return runMacroFromIJJar(name, arg);
        boolean fullPath = name.startsWith("/") || name.startsWith("\\") || name.indexOf(":\\")==1 || name.indexOf(":/")==1;
		String path = name;
		boolean exists = false;
        if (!fullPath) {
        	String macrosDir = Menus.getMacrosPath();
        	if (macrosDir!=null)
        		path = Menus.getMacrosPath() + name;
        }
		File f = new File(path);
		boolean hasExtension = f.getName().contains(".");
		if (hasExtension)
			exists = f.exists();
		if (!exists && !fullPath && !hasExtension) {
			String path2 = path+".txt";
			f = new File(path2);
			exists = f.exists();
			if (exists)
				path = path2;
			else {
				path2 = path+".ijm";
				f = new File(path2);
				exists = f.exists();
				if (exists) path=path2;
			}
		}
		if (!exists) {
			f = new File(path);
			exists = f.exists();
		}
		if (!exists && !fullPath) {
			String path2 = System.getProperty("user.dir") + File.separator + name;
			if (!hasExtension) {
				String path3 = path2 +".txt";
				f = new File(path3);
				exists = f.exists();
				if (exists)
					path = path3;
				else {
					path3 = path2+".ijm";
					f = new File(path3);
					exists = f.exists();
					if (exists) path=path3;
				}
			}
			if (!exists) {
				f = new File(path2);
				exists = f.exists();
				if (exists) path=path2;
			}
		}
		if (IJ.debugMode) IJ.log("runMacro: "+path+" ("+name+")");
		if (!exists || f==null) {
            IJ.error("RunMacro", "Macro or script not found:\n \n"+path);
			return null;
		}
		filePath = path;
		try {
			int size = (int)f.length();
			byte[] buffer = new byte[size];
			FileInputStream in = new FileInputStream(f);
			in.read(buffer, 0, size);
			String macro = new String(buffer, 0, size, "ISO8859_1");
			in.close();
			OpenDialog.setLastDirectory(f.getParent()+File.separator);
			OpenDialog.setLastName(f.getName());
			if (name.endsWith(".js"))
				return runJavaScript(macro, arg);
			else if (name.endsWith(".bsh"))
				return runBeanShell(macro, arg);
			else if (name.endsWith(".py"))
				return runPython(macro, arg);
			else
				return runMacro(macro, arg);
		}
		catch (Exception e) {
			if (!Macro.MACRO_CANCELED.equals(e.getMessage()))
				IJ.error(e.getMessage());
			return null;
		}
	}

    /** Runs the specified macro on the current thread. Macros can retrieve 
    	the optional string argument by calling the getArgument() macro function. 
    	Returns the string value returned by the macro, null if the macro does not
    	return a value, or "[aborted]" if the macro was aborted due to an error. */
	public String runMacro(String macro, String arg) {
		Interpreter interp = new Interpreter();
		try {
			return interp.run(macro, arg);
		} catch(Throwable e) {
			interp.abortMacro();
			IJ.showStatus("");
			IJ.showProgress(1.0);
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp!=null) imp.unlock();
			String msg = e.getMessage();
			if (e instanceof RuntimeException && msg!=null && e.getMessage().equals(Macro.MACRO_CANCELED))
				return  "[aborted]";
			IJ.handleException(e);
		}
		return  "[aborted]";
	}
	
	/** Runs the specified macro from a JAR file in the plugins folder,
		passing it the specified argument. Returns the String value returned
		by the macro, null if the macro does not return a value, or "[aborted]"
		if the macro was aborted due to an error. The macro can reside anywhere
		in the plugins folder, in or out of a JAR file, so name conflicts are possible.
		To avoid name conflicts, it is a good idea to incorporate the plugin
		or JAR file name in the macro name (e.g., "Image_5D_Macro1.ijm"). */
	public static String runMacroFromJar(String name, String arg) {
		String macro = null;
		try {
			ClassLoader pcl = IJ.getClassLoader();
			InputStream is = pcl.getResourceAsStream(name);
			if (is==null) {
				IJ.error("Macro Runner", "Unable to load \""+name+"\" from jar file");
				return null;
			}
			InputStreamReader isr = new InputStreamReader(is);
			StringBuffer sb = new StringBuffer();
			char [] b = new char [8192];
			int n;
			while ((n = isr.read(b)) > 0)
				sb.append(b,0, n);
			macro = sb.toString();
			is.close();
		} catch (IOException e) {
			IJ.error("Macro Runner", ""+e);
		}
		if (macro!=null)
			return (new Macro_Runner()).runMacro(macro, arg);
		else
			return null;
	}

	public String runMacroFromIJJar(String name, String arg) {
		ImageJ ij = IJ.getInstance();
		//if (ij==null) return null;
		Class c = ij!=null?ij.getClass():(new ImageStack()).getClass();
		name = name.substring(7);
		String macro = null;
        try {
			InputStream is = c .getResourceAsStream("/macros/"+name+".txt");
			//IJ.log(is+"  "+("/macros/"+name+".txt"));
			if (is==null)
				return runMacroFile(name, arg);
            InputStreamReader isr = new InputStreamReader(is);
            StringBuffer sb = new StringBuffer();
            char [] b = new char [8192];
            int n;
            while ((n = isr.read(b)) > 0)
                sb.append(b,0, n);
            macro = sb.toString();
        }
        catch (IOException e) {
            String msg = e.getMessage();
            if (msg==null || msg.equals(""))
                msg = "" + e;	
            IJ.showMessage("Macro Runner", msg);
        }
		if (macro!=null)
			return runMacro(macro, arg);
		else
			return null;
	}
	
	/** Runs a JavaScript script on the current thread, passing a string argument, 
		which the script can retrieve using the getArgument() function. Returns,
		as a string, the last expression evaluated by the script. */
	public String runJavaScript(String script, String arg) {
		Object js = null;
		if ((IJ.isJava16() && !(IJ.isMacOSX()&&!IJ.is64Bit()))) {
			// Use JavaScript engine built into Java 6 and later.
			js = IJ.runPlugIn("ij.plugin.JavaScriptEvaluator", "");
		} else {
			js = IJ.runPlugIn("JavaScript", "");
			if (js==null) {
				boolean ok = downloadJar("/download/tools/JavaScript.jar");
				if (ok)
					js = IJ.runPlugIn("JavaScript", "");
			}
		}
		script = Editor.getJSPrefix(arg)+script;
		if (IJ.isJava18())
			script = "load(\"nashorn:mozilla_compat.js\");" + script;
		if (js!=null)
			return runScript(js, script, arg);
		else
			return null;
	}
	
	private static String runScript(Object plugin, String script, String arg) {
		if (plugin instanceof PlugInInterpreter) {
			PlugInInterpreter interp = (PlugInInterpreter)plugin;
			if (IJ.debugMode)
				IJ.log("Running "+interp.getName()+" script; arg=\""+arg+"\"");
			interp.run(script, arg);
			return interp.getReturnValue();
		} else { // call run(script,arg) method using reflection
			try {
				Class c = plugin.getClass();
				Method m = c.getMethod("run", new Class[] {script.getClass(), arg.getClass()});
				String s = (String)m.invoke(plugin, new Object[] {script, arg});			
			} catch(Exception e) {
				if ("Jython".equals(plugin.getClass().getName()))
					IJ.runPlugIn("Jython", script);
			}
			return ""+plugin;
		}
	}
	
	/** Runs a BeanShell script on the current thread, passing a string argument, 
		which the script can retrieve using the getArgument() function. Returns,
		as a string, the last expression evaluated by the script.
		Uses the plugin at http://imagej.nih.gov/ij/plugins/bsh/
		to run the script.
	*/
	public static String runBeanShell(String script, String arg) {
		if (arg==null)
			arg = "";
		Object bsh = IJ.runPlugIn("bsh", "");
		if (bsh==null) {
			boolean ok = downloadJar("/plugins/bsh/BeanShell.jar");
			if (ok)
				bsh = IJ.runPlugIn("bsh", "");
		}
		if (bsh!=null)
			return runScript(bsh, script, arg);
		else
			return null;
	}
	
	/** Runs a Python script on the current thread, passing a string argument, 
		which the script can retrieve using the getArgument() function. Returns,
		as a string, the value of the variable 'result'. For example, a Python script  
		containing the line "result=123" will return the string "123".
		Uses the plugin at http://imagej.nih.gov/ij/plugins/jython/
		to run the script.
	*/
	public static String runPython(String script, String arg) {
		if (arg==null)
			arg = "";
		Object jython = IJ.runPlugIn("Jython", "");
		if (jython==null) {
			boolean ok = downloadJar("/plugins/jython/Jython.jar");
			if (ok)
				jython = IJ.runPlugIn("Jython", "");
		}
		if (jython!=null)
			return runScript(jython, script, arg);
		else
			return null;
	}

	public static boolean downloadJar(String url) {
		String name = url.substring(url.lastIndexOf("/")+1);
		boolean ok = false;
		String msg = name+" was not found in the plugins\nfolder or it is outdated. Click \"OK\" to download\nit from the ImageJ website.";
		GenericDialog gd = new GenericDialog("Download "+name+"?");
		gd.addMessage(msg);
		gd.showDialog();
		if (!gd.wasCanceled()) {
			ok = (new PluginInstaller()).install(IJ.URL+url);
			if (!ok)
				IJ.error("Unable to download "+name+" from "+IJ.URL+url);
		}
		return ok;
	}
	
	/** Returns the file path of the most recently loaded macro or script. */
	public static String getFilePath() {
		return filePath;
	}

	public static void setFilePath(String path) {
		filePath = path;
	}

}
