package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.io.*;
import ij.macro.*;
import java.io.*;
import java.net.URL;
import java.net.*;
import java.util.*;

/** Installs plugins dragged and dropped on the "ImageJ" window, or plugins,
	macros or scripts opened using the Plugins/Install command. */
public class PluginInstaller implements PlugIn {
	public static final String[] validExtensions = {".txt",".ijm",".js",".bsh",".class",".jar",".zip",".java",".py"};

	public void run(String arg) {
		OpenDialog od = new OpenDialog("Install Plugin, Macro or Script...", arg);
		String directory = od.getDirectory();
		String name = od.getFileName();
		if (name==null)
				return;
		if (!validExtension(name)) {
			IJ.error("Plugin Installer", errorMessage());
			return;
		}
		String path = directory + name;
		install(path);
	}
	
	public boolean install(String path) {
		boolean isURL = path.contains("://");
		String lcPath = path.toLowerCase();
		if (isURL)
			path = Opener.updateUrl(path);
		boolean isTool = lcPath.endsWith("tool.ijm") || lcPath.endsWith("tool.txt")
			|| lcPath.endsWith("tool.class") || lcPath.endsWith("tool.jar");
		boolean isMacro = lcPath.endsWith(".txt") || lcPath.endsWith(".ijm");
		byte[] data = null;
		String name = path;
		if (isURL) {
			int index = path.lastIndexOf("/");
			if (index!=-1 && index<=path.length()-1)
				name = path.substring(index+1);
			data = download(path, name);
		} else {
			File f = new File(path);
			name = f.getName();
			data = download(f);
		}
		if (data==null)
			return false;
		if (name.endsWith(".txt") && !name.contains("_"))
			name = name.substring(0,name.length()-4) + ".ijm";
		if (name.endsWith(".zip")) {
			if (!name.contains("_")) {
				IJ.error("Plugin Installer", "No underscore in file name:\n \n  "+name);
				return false;
			}
			name = name.substring(0,name.length()-4) + ".jar";
		}
		String dir = null;
		boolean isLibrary = name.endsWith(".jar") && !name.contains("_");
		if (isLibrary) {
			dir = Menus.getPlugInsPath()+"jars";
			File f = new File(dir);
			if (!f.exists()) {
				boolean ok = f.mkdir();
				if (!ok)
					dir = Menus.getPlugInsPath();
			}
		}
		if (isTool) {
			dir = Menus.getPlugInsPath()+"Tools" + File.separator;
			File f = new File(dir);
			if (!f.exists()) {
				boolean ok = f.mkdir();
				if (!ok) dir=null;
			}
			if (dir!=null && isMacro) {
				String name2 = getToolName(data);
				if (name2!=null)
					name = name2;
			}
		}
		if (dir==null) {
			SaveDialog sd = new SaveDialog("Save Plugin, Macro or Script...", Menus.getPlugInsPath(), name, null);
			String name2 = sd.getFileName();
			if (name2==null)
				return false;
			dir = sd.getDirectory();
		}
		//IJ.log(dir+"   "+Menus.getPlugInsPath());
		if (!savePlugin(new File(dir,name), data))
			return false;
		if (name.endsWith(".java"))
			IJ.runPlugIn("ij.plugin.Compiler", dir+name);
		Menus.updateImageJMenus();
		if (isTool) {
			if (isMacro)
				IJ.runPlugIn("ij.plugin.Macro_Runner", "Tools/"+name);
			else if (name.endsWith(".class")) {
				name = name.replaceAll("_"," ");
				name = name.substring(0,name.length()-6);
				IJ.run(name);
			}
		}
		return true;
	}
	
	private String getToolName(byte[] data) {
		String text = new String(data);
		String name = null;
		Tokenizer tok = new Tokenizer();
		Program pgm = tok.tokenize(text);
		int[] code = pgm.getCode();
		Symbol[] symbolTable = pgm.getSymbolTable();
		for (int i=0; i<code.length; i++) {
			int token = code[i]&MacroConstants.TOK_MASK;
			if (token==MacroConstants.MACRO) {
				int nextToken = code[i+1]&MacroConstants.TOK_MASK;
				if (nextToken==MacroConstants.STRING_CONSTANT) {
					int address = code[i+1]>>MacroConstants.TOK_SHIFT;
					Symbol symbol = symbolTable[address];
					name = symbol.str;
					break;
				}
			}
		}
		if (name==null)
			return null;
		int index = name.indexOf("Tool");
		if (index==-1)
			return null;
		name = name.substring(0, index+4);
		name = name.replaceAll(" ","_");
		name = name + ".ijm";
		return name;
	}
	
	boolean savePlugin(File f, byte[] data) {
		try {
			FileOutputStream out = new FileOutputStream(f);
			out.write(data, 0, data.length);
			out.close();
		} catch (IOException e) {
			IJ.error("Plugin Installer", ""+e);
			return false;
		}
		return true;
	}

	public static byte[] download(String urlString, String name) {
		int maxLength = 52428800; //50MB
		URL url = null;
		boolean unknownLength = false;
		byte[] data = null;;
		int n = 0;
		try {
			url = new URL(urlString);
			if (IJ.debugMode) IJ.log("PluginInstaller: "+urlString+"  " +url);
			if (url==null)
				return null;
			URLConnection uc = url.openConnection();
			int len = uc.getContentLength();
			unknownLength = len<0;
			if (unknownLength) len = maxLength;
			if (name!=null)
				IJ.showStatus("Downloading "+url.getFile());
			InputStream in = uc.getInputStream();
			data = new byte[len];
			int lenk = len/1024;
			while (n<len) {
				int count = in.read(data, n, len-n);
				if (count<0)
					break;
				n += count;
				if (name!=null)
					IJ.showStatus("Downloading "+name+" ("+(n/1024)+"/"+lenk+"k)");
				IJ.showProgress(n, len);
			}
			in.close();
		} catch (Exception e) {
			String msg = "" + e;
			if (!msg.contains("://"))
				msg += "\n   "+urlString;
			IJ.error("Plugin Installer", msg);
			return null;
		} finally {
			IJ.showProgress(1.0);
		}
		if (name!=null) IJ.showStatus("");
		if (unknownLength) {
			byte[] data2 = data;
			data = new byte[n];
			for (int i=0; i<n; i++)
				data[i] = data2[i];
		}
		return data;
	}
	
	byte[] download(File f) {
		if (!f.exists()) {
			IJ.error("Plugin Installer", "File not found: "+f);
			return null;
		}
		byte[] data = null;
		try {
			int len = (int)f.length();
			InputStream in = new BufferedInputStream(new FileInputStream(f));
			DataInputStream dis = new DataInputStream(in);
			data = new byte[len];
			dis.readFully(data);
			dis.close();
		}
		catch (Exception e) {
			IJ.error("Plugin Installer", ""+e);
			data = null;
		}
		return data;
	}
	
	private boolean validExtension(String name) {
		name = name.toLowerCase(Locale.US);
		boolean valid = false;
		for (int i=0; i<validExtensions.length; i++) {
			if (name.endsWith(validExtensions[i]))
				return true;
		}
		return false;
	}
	
	private String errorMessage() {
		String s = "File name must end in ";
		int len = validExtensions.length;
		for (int i=0; i<len; i++) {
			if (i==len-2)
				s += "\""+validExtensions[i]+"\" or ";
			else if (i==len-1)
				s += "\""+validExtensions[i]+"\".";
			else
				s += "\""+validExtensions[i]+"\", ";
		}
		return s;
	}
	
}
