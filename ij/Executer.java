package ij;

import java.awt.*;
import java.net.URL;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import java.awt.event.KeyEvent;
import ij.io.*;
import ij.process.*;
import ij.gui.*;
import ij.util.*;
import ij.text.TextWindow;
import ij.plugin.frame.*;
import ij.macro.Interpreter;

/** Runs menu commands in a separate thread.*/
public class Executer implements Runnable {

	private static String previousCommand;

	private String command;
	private ImagePlus iplus;
	private ImageJ ij;
	private Thread thread;
	
	/** Create an Executer to run the specified menu command
		in this thread using the active image. */
	public Executer(String cmd) {
		command = cmd;
		iplus = WindowManager.getCurrentImage();
		ij = IJ.getInstance();
	}

	/** Create an Executer that runs the specified menu command
		in a separate thread using the specified image. */
	public Executer(String cmd, ImagePlus imp) {
		iplus = imp;
		if (cmd.startsWith("Repeat"))
			command = previousCommand;
		else {
			command = cmd;
			if (!(cmd.equals("Undo")||cmd.equals("Close")))
				previousCommand = cmd;
		}
		ij = IJ.getInstance();
		IJ.resetEscape();
		thread = new Thread(this, cmd);
		thread.setPriority(Math.max(thread.getPriority()-2, Thread.MIN_PRIORITY));
		thread.start();
	}

	public void run() {
		if (command==null) return;
		ImagePlus imp = iplus;
		iplus = null; // maybe this will help get the image GC'd
		try {
			if (Recorder.record) {
				Recorder.setCommand(command);
				runCommand(command, imp);
				if (command!=null) Recorder.saveCommand();
			} else
				runCommand(command, imp);
		} catch(Throwable e) {
			IJ.showStatus("");
			IJ.showProgress(1.0);
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
				if (ij!=null)
					new TextWindow("Exception", s, 350, 250);
				else
					IJ.log(s);
			}
		}
	}
	
	/*
	void save(String s) {
		PrintWriter pw = null;
		try {
			FileOutputStream fos = new FileOutputStream("exception.txt");
			BufferedOutputStream bos = new BufferedOutputStream(fos);
			pw = new PrintWriter(bos);
		}
		catch (IOException e) {
			IJ.error("" + e);
			return;
		}
		pw.println(s);
		pw.close();
	}
	*/

    public void runCommand(String cmd, ImagePlus imp) {
		if (cmd.equals("New..."))
			new NewImage();
		else if (cmd.equals("Open...")) {
			if (Prefs.useJFileChooser && !IJ.macroRunning())
				new Opener().openMultiple();
			else
				new Opener().open();
		} else if (cmd.equals("Close"))
			close(imp);
		else if (cmd.equals("Cut"))
			copy(imp, true);
		else if (cmd.equals("Copy"))
			copy(imp, false);
		else if (cmd.equals("ImageJ [enter]"))
			ij.toFront();
		else if (cmd.equals("Put Behind [tab]"))
			WindowManager.putBehind();
		else if (cmd.equals("Quit")) {
			ImageJ ij = IJ.getInstance();
			if (ij!=null) ij.quit();
		} else {
			Hashtable table = Menus.getCommands();
			String plugIn = (String)table.get(cmd);
			if (plugIn!=null)
				runPlugIn(cmd, plugIn);
			else
				runImageCommand(cmd, imp);
		} 
    }
    

   /** Run commands that process images. */
    public void runImageCommand(String cmd, ImagePlus imp) {
        ImageWindow win = null;
    	if (imp!=null) {
			if (!imp.lock())
				return;   // exit if image is in use
    	}
		if (cmd.equals("Revert"))
			{if (imp!=null) imp.revert(); else IJ.noImage();}
		else if (cmd.equals("Save"))
			{if (imp!=null) new FileSaver(imp).save(); else IJ.noImage();}
		else if (cmd.equals("Paste"))
			{if (imp!=null) imp.paste(); else IJ.noImage();}
		else if (cmd.equals("Undo"))
			{if (imp!=null) Undo.undo(); else IJ.noImage();}
		else
	 		IJ.error("Unrecognized command: " + cmd);
		if (imp!=null)
			imp.unlock();
    }

	void runPlugIn(String cmd, String className) {
		String arg = "";
		if (className.endsWith("\")")) {
			// extract string argument (e.g. className("arg"))
			int argStart = className.lastIndexOf("(\"");
			if (argStart>0) {
				arg = className.substring(argStart+2, className.length()-2);
				className = className.substring(0, argStart);
			}
		}
		IJ.runPlugIn(cmd, className, arg);
	}
	
	void roiRequired() {
		IJ.error("Selection required");
	}

	void copy(ImagePlus imp, boolean cut) {
		if (imp==null) {
	 		IJ.noImage();
	 		return;
	 	} else
	 		imp.copy(cut);
	}
	
	void close(ImagePlus imp) {
		Frame frame = WindowManager.getFrontWindow();
		if (frame==null || (Interpreter.isBatchMode() && frame instanceof ImageWindow))
			closeImage(imp);
		else if (frame instanceof PlugInFrame)
			((PlugInFrame)frame).close();
		else if (frame instanceof TextWindow)
			((TextWindow)frame).close();
		else
			closeImage(imp);
	}

	void closeImage(ImagePlus imp) {
		if (imp==null) {
			IJ.noImage();
			return;
		}
		imp.close();
		if (Recorder.record) {
			Recorder.record("close");
			command = null;
		}
	}

	/** Returns the last command executed. Returns null
		if no command has been executed. */
	public static String getCommand() {
		return previousCommand;
	}
	
}


