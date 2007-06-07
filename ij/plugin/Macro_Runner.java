package ij.plugin;
import ij.*;
import ij.io.*;
import ij.macro.*;
import ij.text.*;
import ij.util.*;
import java.io.*;

/** Opens and runs a macro file. */
public class Macro_Runner implements PlugIn {

	/** Opens and runs the specified macro file on the current thread. Displays a
		file open dialog if <code>name</code> is an empty string. Loads the
		macro from a JAR file in the plugins folder if <code>name</code> starts
		with "JAR:". Otherwise, loads the specified macro from the plugins folder
		or subfolder. */
	public void run(String name) {
		String path = null;
		if (name.equals("")) {
			OpenDialog od = new OpenDialog("Run Macro...", path);
			String directory = od.getDirectory();
			name = od.getFileName();
			if (name!=null)
				runMacro(new File(directory+name));
		} else if (name.startsWith("JAR:"))
			runMacroFromJar(name);
		else {
			path = Menus.getPlugInsPath() + name;
			File file = new File(path);
			runMacro(file);
		}
	}
        
    void runMacroFromJar(String name) {
    	name = name.substring(4);
    	String macro = null;
        try {
            // get macro text as a stream
			PluginClassLoader pcl = new PluginClassLoader(Menus.getPlugInsPath());
			InputStream is = pcl.getResourceAsStream("/"+name);
            if (is==null) {
            	IJ.showMessage("Macro Runner", "Unable to load \""+name+"\" from jar file");
            	return;
            }
            InputStreamReader isr = new InputStreamReader(is);
            
            StringBuffer sb = new StringBuffer();
            char [] b = new char [8192];
            int n;
            //read a block and append any characters
            while ((n = isr.read(b)) > 0)
                sb.append(b,0, n);
            
            // display the text in a TextWindow
            macro = sb.toString();
            //new TextWindow("Macro Runner", sb.toString(), 450, 450);
        }
        catch (IOException e) {
            String msg = e.getMessage();
            if (msg==null || msg.equals(""))
                msg = "" + e;	
            IJ.showMessage("Macro Runner", msg);
        }
		if (macro!=null)
			runMacro(macro);
    }

	void runMacro(File file) {
		int size = (int)file.length();
		if (size<=0)
			return;
		try {
			byte[] buffer = new byte[size];
			FileInputStream in = new FileInputStream(file);
			in.read(buffer, 0, size);
			String macro = new String(buffer, 0, size, "ISO8859_1");
			in.close();
			runMacro(macro);
		}
		catch (Exception e) {
			IJ.error(e.getMessage());
			return;
		}
	}

	void runMacro(String macro) {
		try {
			new Interpreter().run(macro);
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
			//Don't show exceptions resulting from window being closed
			if (!(s.indexOf("NullPointerException")>=0 && s.indexOf("ij.process")>=0))
				new TextWindow("Exception", s, 350, 250);
		}
	}

}
