package ij;
import ij.util.Tools;
import ij.text.TextWindow;
import ij.plugin.MacroInstaller;
import ij.plugin.Duplicator;
import ij.plugin.frame.Recorder;
import ij.plugin.frame.Editor;
import ij.io.OpenDialog;
import java.io.*;
import java.util.*;
import java.awt.event.KeyEvent;
import java.awt.Menu;
import java.awt.GraphicsEnvironment;


/** Runs ImageJ menu commands in a separate thread.*/
public class Executer implements Runnable {

	private static String previousCommand;
	private static CommandListener listener;
	private static Vector listeners = new Vector();

	private String command;
	private Thread thread;
	private boolean repeatingCommand;

	/** Create an Executer to run the specified menu command
		in this thread using the active image. */
	public Executer(String cmd) {
		command = cmd;
	}

	/** Create an Executer that runs the specified menu
		command in a separate thread using the specified image,
		or using the active image if 'imp' is null. */
	public Executer(String cmd, ImagePlus imp) {
		if (cmd.startsWith("Repeat")) {
			command = previousCommand;
			IJ.setKeyUp(KeyEvent.VK_SHIFT);
			repeatingCommand = true;
		} else {
			command = cmd;
			if (!(cmd.equals("Undo")||cmd.equals("Close")))
				previousCommand = cmd;
		}
		IJ.resetEscape();
		thread = new Thread(this, cmd);
		thread.setPriority(Math.max(thread.getPriority()-2, Thread.MIN_PRIORITY));
		if (imp!=null)
			WindowManager.setTempCurrentImage(thread, imp);
		thread.start();
	}

	public void run() {
		if (command==null)
			return;
		if (listeners.size()>0) synchronized (listeners) {
			for (int i=0; i<listeners.size(); i++) {
				CommandListener listener = (CommandListener)listeners.elementAt(i);
				command = listener.commandExecuting(command);
				if (command==null) return;
			}
		}
		try {
			if (Recorder.record) {
				Recorder.setCommand(command);
				runCommand(command);
				Recorder.saveCommand();
			} else
				runCommand(command);
			int len = command.length();
			if (len>0 && command.charAt(len-1)!=']')
				IJ.setKeyUp(IJ.ALL_KEYS);  // set keys up except for "<", ">", "+" and "-" shortcuts
		} catch(Throwable e) {
			IJ.showStatus("");
			IJ.showProgress(1, 1);
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp!=null) imp.unlock();
			String msg = e.getMessage();
			if (e instanceof OutOfMemoryError)
				IJ.outOfMemory(command);
			else if (e instanceof RuntimeException && msg!=null && msg.equals(Macro.MACRO_CANCELED))
				; //do nothing
			else {
				CharArrayWriter caw = new CharArrayWriter();
				PrintWriter pw = new PrintWriter(caw);
				e.printStackTrace(pw);
				String s = caw.toString();
				if (IJ.isMacintosh()) {
					if (s.indexOf("ThreadDeath")>0)
						return;
					s = Tools.fixNewLines(s);
				}
				int w=500, h=340;
				if (s.indexOf("UnsupportedClassVersionError")!=-1) {
					if (s.indexOf("version 49.0")!=-1) {
						s = e + "\n \nThis plugin requires Java 1.5 or later.";
						w=700; h=150;
					}
					if (s.indexOf("version 50.0")!=-1) {
						s = e + "\n \nThis plugin requires Java 1.6 or later.";
						w=700; h=150;
					}
					if (s.indexOf("version 51.0")!=-1) {
						s = e + "\n \nThis plugin requires Java 1.7 or later.";
						w=700; h=150;
					}
					if (s.indexOf("version 52.0")!=-1) {
						s = e + "\n \nThis plugin requires Java 1.8 or later.";
						w=700; h=150;
					}
				}
				if (IJ.getInstance()!=null) {
					s = IJ.getInstance().getInfo()+"\n \n"+s;
					new TextWindow("Exception", s, w, h);
				} else
					IJ.log(s);
			}
		} finally {
			if (thread!=null)
				WindowManager.setTempCurrentImage(null);
		}
	}

	void runCommand(String cmd) {
		Hashtable table = Menus.getCommands();
		String className = (String)table.get(cmd);
		if (className!=null) {
			String arg = "";
			if (className.endsWith("\")")) {
				// extract string argument (e.g. className("arg"))
				int argStart = className.lastIndexOf("(\"");
				if (argStart>0) {
					arg = className.substring(argStart+2, className.length()-2);
					className = className.substring(0, argStart);
				}
			}
			if (Prefs.nonBlockingFilterDialogs) {
				// we have the plugin class name, let us see whether it is allowed to run it
				ImagePlus imp = WindowManager.getCurrentImage();
				boolean imageLocked = imp!=null && imp.isLockedByAnotherThread();
				if (imageLocked && !allowedWithLockedImage(className)) {
					IJ.beep();
					IJ.showStatus("\""+cmd + "\" blocked because \"" + imp.getTitle() + "\" is locked");
					return;
				}
			}
			// run the plugin
			if (IJ.shiftKeyDown() && className.startsWith("ij.plugin.Macro_Runner") && !Menus.getShortcuts().contains("*"+cmd))
    			IJ.open(IJ.getDirectory("plugins")+arg);
    		else
				IJ.runPlugIn(cmd, className, arg);
		} else { // command is not a plugin
			// is command in the Plugins>Macros menu?
			if (MacroInstaller.runMacroCommand(cmd))
				return;
			// is it in the Image>Lookup Tables menu?
			if (loadLut(cmd))
				return;
			// is it in the File>Open Recent menu?
			if (openRecent(cmd))
				return;
			// is it an example in Help>Examples menu?
			if (IJ.getInstance()!=null && !GraphicsEnvironment.isHeadless()) {
				Editor.openExample(cmd);
				return;
			}
			if ("Auto Threshold".equals(cmd)&&(String)table.get("Auto Threshold...")!=null)
				runCommand("Auto Threshold...");
			else if ("Enhance Local Contrast (CLAHE)".equals(cmd)&&(String)table.get("CLAHE ")!=null)
				runCommand("CLAHE ");
			else {
				if ("Table...".equals(cmd))
					IJ.runPlugIn("ij.plugin.NewPlugin", "table");
				else {
					if (repeatingCommand)
						IJ.runMacro(previousCommand);
					else {
						if (!extraCommand(cmd))
							IJ.error("Unrecognized command: \"" + cmd+"\"");
					}
				}
			}
	 	}
    }
    
	private boolean extraCommand(String cmd) {
		if (cmd!=null && cmd.equals("Duplicate Image...")) {
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp!=null) {
				Duplicator.ignoreNextSelection();
				IJ.run(imp, "Duplicate...", "");
			} else
				IJ.noImage();
			return true;
		} else
			return false;
	}

	/** If the foreground image is locked during a filter operation with NonBlockingGenericDialog,
	 *  the following plugins are allowed */
	boolean allowedWithLockedImage(String className) {
		return className.equals("ij.plugin.Zoom") ||
				className.equals("ij.plugin.frame.ContrastAdjuster") ||
				className.equals("ij.plugin.SimpleCommands") ||  //includes Plugins>Utiltites>Reset (needed to reset a locked image)
				className.equals("ij.plugin.WindowOrganizer") ||
				className.equals("ij.plugin.URLOpener");
	}

    /** Opens a .lut file from the ImageJ/luts directory and returns 'true' if successful. */
    public static boolean loadLut(String name) {
		String path = IJ.getDirectory("luts")+name.replace(" ","_")+".lut";
		File f = new File(path);
		if (!f.exists()) {
			path = IJ.getDirectory("luts")+name+".lut";
			f = new File(path);
		}
		if (!f.exists()) {
			path = IJ.getDirectory("luts")+name.toLowerCase().replace(" ","_")+".lut";
			f = new File(path);
		}
		if (!f.exists() && Character.isLowerCase(name.charAt(0))) {
			String name2 = name.substring(0,1).toUpperCase()+name.substring(1);
			path = IJ.getDirectory("luts")+name2+".lut";
			f = new File(path);
		}
		if (!f.exists() && name.toLowerCase().equals("viridis")) {
			path = IJ.getDirectory("luts")+"mpl-viridis.lut";  //Fiji version
			f = new File(path);
		}
		if (f.exists()) {
			String dir = OpenDialog.getLastDirectory();
			IJ.open(path);
			OpenDialog.setLastDirectory(dir);
			return true;
		}
		return false;
    }

    /** Opens a file from the File/Open Recent menu
 	      and returns 'true' if successful. */
    boolean openRecent(String cmd) {
		Menu menu = Menus.getOpenRecentMenu();
		if (menu==null) return false;
		for (int i=0; i<menu.getItemCount(); i++) {
			if (menu.getItem(i).getLabel().equals(cmd)) {
				IJ.open(cmd);
				return true;
			}
		}
		return false;
    }

	/** Returns the last command executed. Returns null
		if no command has been executed. */
	public static String getCommand() {
		return previousCommand;
	}

	public static void setAsRepeatCommand(String cmd) {
		previousCommand = cmd;
	}

	/** Adds the specified command listener. */
	public static void addCommandListener(CommandListener listener) {
		listeners.addElement(listener);
	}

	/** Removes the specified command listener. */
	public static void removeCommandListener(CommandListener listener) {
		listeners.removeElement(listener);
	}

	public static int getListenerCount() {
		return listeners.size();
	}

}


