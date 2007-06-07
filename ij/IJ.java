package ij;
import ij.gui.*;
import ij.text.TextPanel;
import ij.io.*;
import ij.plugin.*;
import ij.plugin.filter.*;
import java.awt.event.*;
import java.text.DecimalFormat;	
import java.awt.Color;	
import java.awt.Frame;	
import java.applet.Applet;

/** This class consists of static utility methods. */
public class IJ {
	public static boolean debugMode = false;
	
	private static ImageJ ij;
	private static java.applet.Applet applet;
	private static ProgressBar progressBar;
	private static TextPanel textPanel;
	private static boolean isMac=System.getProperty("os.name").startsWith("Mac");
	private static boolean altDown, spaceDown;
		
	static void init(ImageJ imagej, Applet theApplet, TextPanel tp) {
		ij = imagej;
		applet = theApplet;
		progressBar = ij.getProgressBar();
		textPanel = tp;
	}

	/**Returns a reference to the "ImageJ" frame.*/
	public static ImageJ getInstance() {
		return ij;
	}
	
	/** Runs the specified plug-in and returns a reference to it. */
	public static Object runPlugIn(String className, String arg) {
		return runPlugIn("", className, arg);
	}
	
	/** Runs the specified plug-in and returns a reference to it. */
	static Object runPlugIn(String commandName, String className, String arg) {
		if (!className.startsWith("ij"))
			return ij.runUserPlugIn(commandName, className, arg, false);
		Object thePlugIn=null;
		try {
			Class c = Class.forName(className);
 			thePlugIn = c.newInstance();
 			if (thePlugIn instanceof PlugIn)
				((PlugIn)thePlugIn).run(arg);
 			else
				ij.runFilterPlugIn(thePlugIn, commandName, arg);
		}
		catch (ClassNotFoundException e) {write("Plugin not found: " + className);}
		catch (InstantiationException e) {write("Unable to load plugin (ins)");}
		catch (IllegalAccessException e) {write("Unable to load plugin (acc)");}
		return thePlugIn;
	}
	       
    /** Starts executing a menu command in a separete thread and returns immediately. */
	public static void doCommand(String command) {
		if (ij!=null)
			ij.doCommand(command);
	}
	
    /** Runs a menu command in the current thread. Does not
    	return until the command has finished executing. */
	public static void run(String command) {
		Executer e = new Executer(command);
		e.run();
	}
	
	/**Returns the Applet that created this ImageJ or null if running as an application.*/
	public static java.applet.Applet getApplet() {
		return applet;
	}
	
	/**Displays a message in the ImageJ status bar.*/
	public static void showStatus(String s) {
		if (ij!=null) ij.showStatus(s);
	}

	/** Displays a line of text in the ImageJ window. Uses
		System.out.println if ImageJ is not present. */
	public static void write(String s) {
		if (textPanel!=null)
				textPanel.append(s);
		else
			System.out.println(s);
	}

	/** Clears the worksheet and sets the column headings to
		those in the tab-delimited 'headings' String. */
	public static void setColumnHeadings(String headings) {
		if (textPanel!=null)
			textPanel.setColumnHeadings(headings);
	}

	/** Returns a reference to the ImageJ text panel. */
	public static TextPanel getTextPanel() {
		return textPanel;
	}

	/**Displays a "no images are open" dialog box.*/
	public static void noImage() {
		showMessage("No Image", "There are no images open.");
	}

	/**Displays an "out of memory" message in the ImageJ window.*/
	public static void outOfMemory(String name) {
		write("<<" + name + ": out of memory>>");
	}

	/**	Updates the progress bar. Does nothing if the
		ImageJ window is not present. */
	public static void showProgress(double progress) {
		if (progressBar!=null) progressBar.show(progress);
	}

	/**	Displays a message in a dialog box with the specified title.
		Writes the Java console if ImageJ is not present. */
	public static void showMessage(String title, String msg) {
		if (ij!=null)
			new MessageDialog(ij, title, msg);
		else
			System.out.println(msg);
	}

	/** Displays a message in a dialog box titled "Message".
		Writes the Java console if ImageJ is not present. */
	public static void showMessage(String msg) {
		showMessage("Message", msg);
	}

	/** Displays a message in a dialog box titled "Error". Writes
		to the Java console if ImageJ is not present. */
	public static void error(String msg) {
		if (ij!=null)
			new MessageDialog(ij, "ImageJ", msg);
		else
			System.out.println(msg);
	}
	
	/** Displays a message in a dialog box with the specified title.
	   Returns false if the user pressed "Cancel". */
	public static boolean showMessageWithCancel(String title, String msg) {
		GenericDialog gd = new GenericDialog(title);
		gd.addMessage(msg);
		gd.showDialog();
		return !gd.wasCanceled();
	}

	public static final int CANCELED = Integer.MIN_VALUE;

	/** Allows the user to enter a number in a dialog box. Returns the	
	    value IJ.CANCELED (-2,147,483,648) if the user cancels the dialog box*/
	public static double getNumber(String prompt, double defaultNumber) {
		Frame win = WindowManager.getCurrentWindow();
		if (win==null) win = ij;
		GenericDialog gd = new GenericDialog("Enter a Number", win);
		gd.addNumericField(prompt, defaultNumber, 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return CANCELED;
		return gd.getNextNumber();
	}

	/** Allows the user to enter a string in a dialog box. Returns
	    "" if the user cancels the dialog box. */
	public static String getString(String prompt, String defaultString) {
		Frame win = WindowManager.getCurrentWindow();
		if (win==null) win = ij;
		GenericDialog gd = new GenericDialog("Enter a String", win);
		gd.addStringField(prompt, defaultString, 20);
		gd.showDialog();
		if (gd.wasCanceled())
			return "";
		return gd.getNextString();
	}

	/**Delays 'msecs' milliseconds.*/
	public synchronized static void wait(int msecs) {
		try {Thread.sleep(msecs);}
		catch (InterruptedException e) { }
	}
	
	/** Emits an audio beep. */
	public static void beep() {
		java.awt.Toolkit.getDefaultToolkit().beep();
	}
	

	public static String freeMemory() {
		System.gc();
		long freeMem = Runtime.getRuntime().freeMemory();
		long totMem = Runtime.getRuntime().totalMemory();
		return  "Memory: " + (totMem-freeMem)/1024 + "K";
	}
	
	public static void showTime(ImagePlus imp, long start, String str) {
	    long elapsedTime = System.currentTimeMillis() - start;
		double seconds = elapsedTime / 1000.0;
		long pixels = imp.getWidth() * imp.getHeight();
		int rate = (int)((double)pixels/seconds);
		String str2;
		if (rate>1000000000)
			str2 = "";
		else if (rate<1000000)
			str2 = ", "+rate+" pixels/second";
		else
			str2 = ", "+d2s(rate/1000000.0,1)+" million pixels/second";
		showStatus(str+seconds+" seconds"+str2);
	}
	
	/** Converts a number to a formatted string using
		2 digits to the right of the decimal point. */
	public static String d2s(double n) {
		return d2s(n, 2);
	}
	
	private static DecimalFormat df = new DecimalFormat("0.00");
	private static int dfDigits = 2;

	/** Converts a number to a rounded formatted string.
		The 'precision' argument specifies the number of
		digits to the right of the decimal point. */
	public static String d2s(double n, int precision) {
		if (n==Float.MAX_VALUE) // divide by 0 in FloatProcessor
			return "3.4e38";
		boolean negative = n<0.0;
		if (negative)
			n = -n;
		double whole = Math.round(n * Math.pow(10, precision));
		double rounded = whole/Math.pow(10, precision);
		if (negative)
			rounded = -rounded;
		if (precision!=dfDigits)
			switch (precision) {
				case 0: df.applyPattern("0"); dfDigits=0; break;
				case 1: df.applyPattern("0.0"); dfDigits=1; break;
				case 2: df.applyPattern("0.00"); dfDigits=2; break;
				case 3: df.applyPattern("0.000"); dfDigits=3; break;
				case 4: df.applyPattern("0.0000"); dfDigits=4; break;
				case 5: df.applyPattern("0.00000"); dfDigits=5; break;
				case 6: df.applyPattern("0.000000"); dfDigits=6; break;
				case 7: df.applyPattern("0.0000000"); dfDigits=7; break;
				case 8: df.applyPattern("0.00000000"); dfDigits=8; break;
			}
		String s = df.format(rounded);
		return s;
	}

	/** Adds the specified class to a Vector to keep it from being garbage
	collected, which would cause the classes static fields to be reset. */
	public static void register(Class c) {
		if (ij!=null) ij.register(c);
	}
	
	/** Returns true if the space bar is down. */
	public static boolean spaceBarDown() {
		return spaceDown;
	}

	/** Returns true if the alt key is down. */
	public static boolean altKeyDown() {
		return altDown;
	}

	static void setKeyDown(int key) {
		switch (key) {
			case KeyEvent.VK_ALT: altDown=true; break;
			case KeyEvent.VK_SPACE: {
				spaceDown=true;
				ImageWindow win = WindowManager.getCurrentWindow();
				if (win!=null) win.getCanvas().setCursor(-1,-1);
				break;
			}
		}
	}
	
	static void setKeyUp(int key) {
		switch (key) {
			case KeyEvent.VK_ALT: altDown=false; break;
			case KeyEvent.VK_SPACE: {
				spaceDown=false;
				ImageWindow win = WindowManager.getCurrentWindow();
				if (win!=null) win.getCanvas().setCursor(-1,-1);
				break;
			}
		}
	}

	/** Returns true if this machine is a Macintosh. */
	public static boolean isMacintosh() {
		return isMac && ij!=null;
	}
	
	/** Displays an error message and returns false if the
		ImageJ version is less than the one specified. */
	public static boolean versionLessThan(String version) {
		boolean lessThan = ImageJ.VERSION.compareTo(version)<0;
		if (lessThan)
			error("This plugin requires ImageJ "+version+" or later.");
		return lessThan;
	}
}