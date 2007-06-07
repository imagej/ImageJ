package ij;

import java.awt.*;
import java.net.URL;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import ij.io.*;
import ij.process.*;
import ij.gui.*;
import ij.util.*;

/** Runs menu commands in a separate thread.*/
public class Executer implements Runnable {

	private static String previousCommand;

	private String command;
	private ImagePlus iplus;
	private ImageJ ij;
	private Thread thread;
	
	/** Create an Executer to run the specified menu command
		in this thread using the active image. */
	Executer(String cmd) {
		command = cmd;
		iplus = WindowManager.getCurrentImage();
		ij = IJ.getInstance();
	}

	/** Create an Executer that runs the specified menu command
		in a separate thread using the specified image. */
	Executer(String cmd, ImagePlus imp) {
		iplus = imp;
		if (cmd.startsWith("Repeat"))
			command = previousCommand;
		else {
			command = cmd;
			if (!(cmd.equals("Undo")||cmd.equals("Close")))
				previousCommand = cmd;
		}
			
		ij = IJ.getInstance();
		thread = new Thread(this, cmd);
		thread.setPriority(Math.max(thread.getPriority()-2, Thread.MIN_PRIORITY));
		thread.start();
	}

	public void run() {
		if (command==null) return;
		ImagePlus imp = iplus;
		iplus = null; // maybe this will help get the image GC'd
		try {runCommand(command, imp);}
		catch(Throwable e) {
			IJ.showStatus("");
			IJ.showProgress(1.0);
			if (imp!=null) imp.unlock();
			if (e instanceof OutOfMemoryError)
				IJ.outOfMemory(command);
			else {
				CharArrayWriter caw = new CharArrayWriter();
				PrintWriter pw = new PrintWriter(caw);
				e.printStackTrace(pw);
				String s = caw.toString();
				if (IJ.isMacintosh())
					s = Tools.fixNewLines(s);
				IJ.write(s);
			}
		}
	}

    public void runCommand(String cmd, ImagePlus imp) {
		if (cmd.equals("New..."))
			new NewImage();
		else if (cmd.equals("Open..."))
			new Opener().openImage();
		else if (cmd.equals("Close"))
			closeImage(imp);
		else if (cmd.equals("About ImageJ..."))
			ij.showAboutBox();
		else if (cmd.equals("Cut"))
			copy(imp, true);
		else if (cmd.equals("Copy"))
			copy(imp, false);
		else if (cmd.equals("ImageJ [enter]"))
			ij.toFront();
		else if (cmd.equals("Put Behind [tab]"))
			WindowManager.putBehind();
		else if (cmd.equals("Quit"))
			IJ.getInstance().quit();
		else {
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
    		win = imp.getWindow();
    	}

		if (cmd.equals("Revert"))
			{if (win!=null) imp.revert(); else IJ.noImage();}
		else if (cmd.equals("Save"))
			{if (win!=null) new FileSaver(imp).save(); else IJ.noImage();}
		else if (cmd.equals("Paste"))
			{if (win!=null) win.paste(); else IJ.noImage();}
		else if (cmd.equals("Select All"))
			{if (win!=null) imp.setRoi(0,0,imp.getWidth(),imp.getHeight()); else IJ.noImage();}
		else if (cmd.equals("Select None"))
			{if (win!=null) imp.killRoi(); else IJ.noImage();}
		else if (isConversionCommand(cmd))
			{if (win!=null) new Converter(imp).convert(cmd); else IJ.noImage();}
		else if (cmd.equals("Histogram"))
			{if (win!=null) {new HistogramWindow(imp);} else IJ.noImage();}
		else if (cmd.startsWith("Restore"))
			{if (win!=null) imp.restoreRoi(); else IJ.noImage();}
		else if (cmd.equals("Undo"))
			{if (win!=null) Undo.undo(); else IJ.noImage();}
		else
			//runFilter(cmd);
	 		IJ.error("Unrecognized command: " + cmd);
		if (imp!=null)
			imp.unlock();
    }

	private boolean isConversionCommand(String cmd) {
		return (cmd.equals("8-bit") || cmd.equals("16-bit")
			|| cmd.equals("32-bit") || cmd.equals("8-bit Color")
			|| cmd.equals("RGB Color") || cmd.equals("RGB Stack") || cmd.equals("HSB Stack"));
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
		if (ij.copyText(cut)>0)
			return;
		else if (imp==null) {
	 		IJ.noImage();
	 		return;
	 	} else
	 		imp.getWindow().copy(cut);
	}
	
	void closeImage(ImagePlus imp) {
		if (imp==null)
			IJ.noImage();
		else {
			ImageWindow win = imp.getWindow();
			if (win!=null) win.close();
		}
	}

	/** Returns the last command executed. Returns null
		if no command has been executed. */
	public static String getCommand() {
		return previousCommand;
	}
	
}


