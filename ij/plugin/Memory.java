package ij.plugin;
import ij.*;
//import ij.process.*;
import ij.gui.*;
//import java.awt.*;
import java.io.*;
import ij.util.Tools;
import java.lang.reflect.*;


/** This plugin implements the Edit/Options/Memory command. */
public class Memory implements PlugIn {
	String s;
	int index1, index2;
	File f;

	public void run(String arg) {
		changeMemoryAllocation();
		//IJ.log("setting="+getMemorySetting()/(1024*1024)+"MB");
		//IJ.log("maxMemory="+maxMemory()/(1024*1024)+"MB");
	}

	void changeMemoryAllocation() {
		IJ.maxMemory(); // forces IJ to cache old limit
		int max = (int)(getMemorySetting()/1048576L);
		if (max==0)
			{showError(); return;}
		GenericDialog gd = new GenericDialog("Memory");
		gd.addNumericField("Maximum Memory: ", max, 0, 4, "MB");
		gd.showDialog();
		if (gd.wasCanceled()) return;
		int max2 = (int)gd.getNextNumber();
		if (gd.invalidNumber()) {
			IJ.showMessage("Memory", "The number entered was invalid.");
			return;
		}
		if (max2<32 && IJ.isMacOSX()) max2 = 32;
		if (max2<8 && IJ.isWindows()) max2 = 8;
		if (max2==max) return;
		if (max2>=1700) {
			if (!IJ.showMessageWithCancel("Memory", 
			"Note: setting the memory limit to a value greater\n"
			+"than 1700MB may cause ImageJ to fail to start."))
				return;
		}
		try {
			String s2 = s.substring(0, index1) + max2 + s.substring(index2);
			FileOutputStream fos = new FileOutputStream(f);
			PrintWriter pw = new PrintWriter(fos);
			pw.print(s2);
			pw.close();
		} catch (IOException e) {
			String error = e.getMessage();
			if (error==null || error.equals("")) error = ""+e;
			String name = IJ.isMacOSX()?"Info.plist":"ImageJ.cfg";
			String msg = 
				   "Unable to update the file \"" + name + "\".\n"
				+ " \n"
				+ "\"" + error + "\"\n";
			IJ.showMessage("Memory", msg);
			return;
		}
		IJ.showMessage("Memory", "The new " + max2 +"MB limit will take effect after ImageJ is restarted.");		
	}

	public long getMemorySetting() {
		if (IJ.getApplet()!=null) return 0L;
		long max = 0L;
		if (IJ.isMacOSX()) {
			max = getMemorySetting("ImageJ.app/Contents/Info.plist");
		} else
			max = getMemorySetting("ImageJ.cfg");		
		return max;
	}

	void showError() {
		int max = (int)(maxMemory()/1048576L);
		String msg =
			   "ImageJ is unable to change the memory limit. For \n"
			+ "more information, refer to the installation notes. \n"
			+ " \n";
		if (max>0)
			msg += "Current limit: " + max + "MB";
		IJ.showMessage("Memory", msg);
	}

	long getMemorySetting(String file) {
		String path = Prefs.getHomeDir()+File.separator+file;
		f = new File(path);
		if (!f.exists()) return 0L;
		long max = 0L;
		try {
			int size = (int)f.length();
			byte[] buffer = new byte[size];
			FileInputStream in = new FileInputStream(f);
			in.read(buffer, 0, size);
			s = new String(buffer, 0, size, "ISO8859_1");
			in.close();
			index1 = s.indexOf("-mx");
			if (index1==-1) index1 = s.indexOf("-Xmx");
			if (index1==-1) return 0L;
			if (s.charAt(index1+1)=='X') index1+=4; else index1+=3;
			index2 = index1;
			while (index2<s.length()-1 && Character.isDigit(s.charAt(++index2))) {}
			String s2 = s.substring(index1, index2);
			max = (long)Tools.parseDouble(s2, 0.0)*1024*1024;
		}
		catch (Exception e) {
			IJ.log(""+e);
			return 0L;
		}
		return max;
	}

	/** With Java 1.4.1 or later, returns the maximum amount of memory
		that this JVM will attempt to use, otherwise, returns zero. */
	public long maxMemory() {
		// Call maxMemory using reflection so this class can be compiled with Java 1.3
		long max = 0L;
		try {
			Runtime rt = Runtime.getRuntime();
			Class c = rt.getClass();
			Method maxMemory = c.getDeclaredMethod("maxMemory", new Class[0]);
			Long l = (Long)maxMemory.invoke(rt, new Object[] {});
			max = l.longValue();
		} catch (Exception e) {}
		return max;
	}
	
}
