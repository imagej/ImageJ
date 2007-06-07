package ij;
import java.io.*;
import java.util.*;
import java.applet.*;
import java.net.URL;
import java.awt.Color;
import java.applet.Applet;
import ij.io.*;
import ij.util.Tools;
import ij.gui.*;
import ij.plugin.filter.*;
import ij.process.ImageConverter;
/**
This class contains the ImageJ preferences, which are 
loaded from the "IJ_Props.txt" and "IJ_Prefs.txt" files.
@see ImageJ
*/
public class Prefs {

	public static final String PROPS_NAME = "IJ_Props.txt";
	public static final String PREFS_NAME = "IJ_Prefs.txt";
	public static final String DIR_IMAGE = "dir.image";
	public static final String FCOLOR = "fcolor";
	public static final String BCOLOR = "bcolor";
	public static final String ROICOLOR = "roicolor";
	public static final String JPEG = "jpeg";
	public static final String USE_POINTER = "pcursor";
	public static final String SCALE_CONVERSIONS = "scale";

	static String separator = System.getProperty("file.separator");
	static Properties prefs = new Properties();
	static Properties props = new Properties(prefs);
	static String prefsDir;
	static String imagesURL;
	static String homeDir; // ImageJ folder

	/** Finds and loads the ImageJ configuration file, "IJ_Props.txt".
		@return	an error message if "IJ_Props.txt" not found.
	*/
	public static String load(ImageJ ij, Applet applet) {
		InputStream f = ij.getClass().getResourceAsStream("/"+PROPS_NAME);
		if (applet!=null)
			return loadAppletProps(f,applet);
		homeDir = System.getProperty("user.dir");
		String userHome = System.getProperty("user.home");
		String osName = System.getProperty("os.name");
		if (osName.indexOf("Windows",0)>-1)
			prefsDir = homeDir; //ImageJ folder on Windows
		else
			prefsDir = userHome; // Mac Preferences folder or Unix home dir 
		if (f==null) {
			try {f = new FileInputStream(homeDir+"/"+PROPS_NAME);}
			catch (FileNotFoundException e) {f=null;}
		}
		if (f==null)
			return PROPS_NAME+" not found in ij.jar or in "+homeDir;
		f = new BufferedInputStream(f);
		try {props.load(f); f.close();}
		catch (IOException e) {return("Error loading "+PROPS_NAME);}
		imagesURL = props.getProperty("images.location");
		loadPreferences();
		return "";
	}
	
	static String loadAppletProps(InputStream f, Applet applet) {
		if (f==null)
			return PROPS_NAME+" not found in ij.jar";
		try {
			props.load(f);
			f.close();
		}
		catch (IOException e) {return("Error loading "+PROPS_NAME);}
		try {
			URL url = new URL(applet.getDocumentBase(), "images/");
			imagesURL = url.toString();
		}
		catch (Exception e) {}
		return "";
	}
	
	/** Returns the URL for the ImageJ sample images. */
	public static String getImagesURL() {
		return imagesURL;
	}
	
	/** Returns the path to the directory containing IJ_Props.txt. */
	public static String getHomeDir() {
		return homeDir;
	}

	/** Finds an string in IJ_Props or IJ_Prefs.txt. */
	public static String getString(String key) {
		return props.getProperty(key);
	}
	
	/** Finds a boolean in IJ_Props or IJ_Prefs.txt. */
	public static boolean getBoolean(String key, boolean defaultValue) {
		if (props==null) return defaultValue;			
		String s = props.getProperty(key);
		if (s==null)
			return defaultValue;
		else
			return s.equals("true");
	}

	/** Finds an int in IJ_Props or IJ_Prefs.txt. */
	public static int getInt(String key, int defaultValue) {
		if (props==null) //workaround for Netscape JIT bug
			return defaultValue;			
		String s = props.getProperty(key);
		if (s!=null) {
			try {
				return Integer.decode(s).intValue();
			} catch (NumberFormatException e) {IJ.write(""+e);}
		}	
		return defaultValue;
	}

	/** Looks up a real number in IJ_Props or IJ_Prefs.txt. */
	public static double getDouble(String key, double defaultValue) {
		if (props==null)
			return defaultValue;			
		String s = props.getProperty(key);
		Double d = null;
		if (s!=null) {
			try {d = new Double(s);}
			catch (NumberFormatException e){d = null;}
			if (d!=null)
				return(d.doubleValue());
		}	
		return defaultValue;
	}

	/** Finds a color in IJ_Props or IJ_Prefs.txt. */
	public static Color getColor(String key, Color defaultColor) {
		int i = getInt(key, 0xaaa);
		if (i == 0xaaa)
			return defaultColor;
		return new Color((i >> 16) & 0xFF, (i >> 8) & 0xFF, i & 0xFF);
	}
	
	/** Returns the file.separator system property. */
	public static String getFileSeparator() {
		return separator;
	}
	
	/** Opens the IJ_Prefs.txt file. */
	static void loadPreferences() {
		String path = prefsDir+separator+PREFS_NAME;
		try {
			InputStream is = new BufferedInputStream(new FileInputStream(path));
			prefs.load(is);
			is.close();
		} catch (Exception e) {
			return;
		}
	}

	/** Saves user preferences in the IJ_Prefs.txt properties file. */
	static void savePreferences() {
		try {
			Properties prefs = new Properties();
			String dir = OpenDialog.getDefaultDirectory();
			if (dir!=null)
				prefs.put(DIR_IMAGE, escapeBackSlashes(dir));
			prefs.put(ROICOLOR, Tools.c2hex(Roi.getColor()));
			prefs.put(FCOLOR, Tools.c2hex(Toolbar.getForegroundColor()));
			prefs.put(BCOLOR, Tools.c2hex(Toolbar.getBackgroundColor()));
			prefs.put(JPEG, Integer.toString(JpegEncoder.getQuality()));
			prefs.put(USE_POINTER, ImageCanvas.usePointer?"true":"false");
			prefs.put(SCALE_CONVERSIONS, ImageConverter.getDoScaling()?"true":"false");
			Menus.savePreferences(prefs);
			ParticleAnalyzer.savePreferences(prefs);
			Analyzer.savePreferences(prefs);
			ImportDialog.savePreferences(prefs);
			PlotWindow.savePreferences(prefs);
			String path = prefsDir+separator+PREFS_NAME;
			savePrefs(prefs, path);
		} catch (Exception e) {
			//CharArrayWriter caw = new CharArrayWriter();
			//PrintWriter pw = new PrintWriter(caw);
			//e.printStackTrace(pw);
			//IJ.write(caw.toString());
			IJ.write("<<Unable to save preferences>>");
			IJ.wait(2000);
		}
	}
			
	static void savePrefs(Properties prefs, String path) throws IOException{
		FileOutputStream fos = new FileOutputStream(path);
		BufferedOutputStream bos = new BufferedOutputStream(fos);
		PrintWriter pw = new PrintWriter(bos);
		pw.println("# ImageJ "+ImageJ.VERSION+" Preferences");
		pw.println("# "+new Date());
		pw.println("");
		for (Enumeration e=prefs.keys(); e.hasMoreElements();) {
			String key = (String)e.nextElement();
			pw.print(key);
			pw.write('=');
			pw.println((String)prefs.get(key));
		}
		pw.close();
	}
	
	static String escapeBackSlashes (String s) {
		StringBuffer sb = new StringBuffer(s.length()+10);
		char[] chars = s.toCharArray();
		for (int i=0; i<chars.length; i++) {
			sb.append(chars[i]);
			if (chars[i]=='\\')
				sb.append('\\');
		}
		return sb.toString();
	}

}

