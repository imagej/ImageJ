package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.io.*;
import ij.plugin.frame.*;
import ij.text.TextWindow;
import ij.macro.Interpreter;
import ij.plugin.Compiler;
import java.awt.Window;
import java.io.File;
import java.applet.Applet;
	
/**	Runs miscellaneous File and Window menu commands. */
public class Commands implements PlugIn {
	
	public void run(String cmd) {
		if (cmd.equals("new")) {
			if (IJ.altKeyDown())
				IJ.runPlugIn("ij.plugin.HyperStackMaker", "");
			else
				new NewImage();
		} else if (cmd.equals("open")) {
			if (Prefs.useJFileChooser && !IJ.macroRunning())
				new Opener().openMultiple();
			else
				new Opener().open();
		} else if (cmd.equals("close"))
			close();
		else if (cmd.equals("close-all"))
			closeAll();
		else if (cmd.equals("save"))
			save();
		else if (cmd.equals("revert"))
			revert();
		else if (cmd.equals("undo"))
			undo();
		else if (cmd.equals("ij")) {
			ImageJ ij = IJ.getInstance();
			if (ij!=null) ij.toFront();
		} else if (cmd.equals("tab"))
			WindowManager.putBehind();
		else if (cmd.equals("quit")) {
			ImageJ ij = IJ.getInstance();
			if (ij!=null) ij.quit();
		} else if (cmd.equals("startup"))
			openStartupMacros();
    }
    
    void revert() {
    	ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null)
			imp.revert();
		else
			IJ.noImage();
	}

    void save() {
    	ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null) {
			if (imp.getStackSize()>1) {
				imp.setIgnoreFlush(true);
				new FileSaver(imp).save();
				imp.setIgnoreFlush(false);
			} else
				new FileSaver(imp).save();
		} else
			IJ.noImage();
	}
	
    void undo() {
    	ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null)
			Undo.undo();
		else
			IJ.noImage();
	}

	void close() {
    	ImagePlus imp = WindowManager.getCurrentImage();
		Window win = WindowManager.getActiveWindow();
		if (win==null || (Interpreter.isBatchMode() && win instanceof ImageWindow))
			closeImage(imp);
		else if (win instanceof PlugInFrame && !"Commands".equals(((PlugInFrame)win).getTitle()))
			((PlugInFrame)win).close();
		else if (win instanceof PlugInDialog)
			((PlugInDialog)win).close();
		else if (win instanceof TextWindow)
			((TextWindow)win).close();
		else
			closeImage(imp);
	}

	/** Closes all image windows, or returns 'false' if the user cancels the unsaved changes dialog box. */
	public static boolean closeAll() {
    	int[] list = WindowManager.getIDList();
    	if (list!=null) {
    		int imagesWithChanges = 0;
			for (int i=0; i<list.length; i++) {
				ImagePlus imp = WindowManager.getImage(list[i]);
				if (imp!=null && imp.changes) imagesWithChanges++;
			}
			if (imagesWithChanges>0 && !IJ.macroRunning()) {
				GenericDialog gd = new GenericDialog("Close All");
				String msg = null;
				String pronoun = null;
				if (imagesWithChanges==1) {
					msg = "There is one image";
					pronoun = "It";
				} else {
					msg = "There are "+imagesWithChanges+" images";
					pronoun = "They";
				}
				gd.addMessage(msg+" with unsaved changes. "+pronoun
					+" will\nbe closed without being saved if you click \"OK\".");
				gd.showDialog();
				if (gd.wasCanceled())	
					return false;
			}
			Prefs.closingAll = true;
			for (int i=0; i<list.length; i++) {
				ImagePlus imp = WindowManager.getImage(list[i]);
				if (imp!=null) {
					imp.changes = false;
					imp.close();
				}
			}
			Prefs.closingAll = false;
    	}
    	return true;
	}

	void closeImage(ImagePlus imp) {
		if (imp==null) {
			IJ.noImage();
			return;
		}
		imp.close();
		if (Recorder.record && !IJ.isMacro()) {
			if (Recorder.scriptMode())
				Recorder.recordCall("imp.close();");
			else
				Recorder.record("close");
			Recorder.setCommand(null); // don't record run("Close")
		}
	}
	
	// Plugins>Macros>Open Startup Macros command
	void openStartupMacros() {
		Applet applet = IJ.getApplet();
		if (applet!=null)
			IJ.run("URL...", "url="+IJ.URL+"/applet/StartupMacros.txt");
		else {
			String path = IJ.getDirectory("macros")+"StartupMacros.txt";
			File f = new File(path);
			if (!f.exists()) {
				path = IJ.getDirectory("macros")+"StartupMacros.ijm";
				f = new File(path);
			}
			if (!f.exists()) {
				path = IJ.getDirectory("macros")+"StartupMacros.fiji.ijm";
				f = new File(path);
			}
			if (!f.exists())
				IJ.error("\"StartupMacros.txt\" not found in ImageJ/macros/");
			else
				IJ.open(path);
		}
	}
		
}



