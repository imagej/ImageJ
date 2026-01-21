package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.io.Opener;
import ij.text.TextWindow;
import ij.measure.ResultsTable;
import ij.plugin.frame.Editor;
import java.awt.Desktop;
import java.awt.Frame;
import java.io.File;

/** This plugin implements the Plugins/Utilities/Unlock, Image/Rename
	and Plugins/Utilities/Search commands. */
public class SimpleCommands implements PlugIn {
	static String searchArg;
    private static String[] choices = {"Locked Image", "Clipboard", "Undo Buffer"};
    private static int choiceIndex = 0;

	public void run(String arg) {
		if (arg.equals("search"))
			search();
		else if (arg.equals("import")) 
			Opener.openResultsTable("");
		else if (arg.equals("table")) 
			Opener.openTable("");
		else if (arg.equals("rename"))
			rename();	
		else if (arg.equals("reset"))
			reset();
		else if (arg.equals("about"))
			aboutPluginsHelp();
		else if (arg.equals("install"))
			installation();
		else if (arg.equals("set"))
			setSliceLabel();
		else if (arg.equals("remove"))
			removeStackLabels();
		else if (arg.equals("itor"))
			imageToResults();
		else if (arg.equals("rtoi"))
			resultsToImage();
		else if (arg.equals("display"))
			IJ.runMacroFile("ij.jar:ShowAllLuts", null);
		else if (arg.equals("missing"))
			showMissingPluginsMessage();
		else if (arg.equals("fonts"))
			showFonts();
		else if (arg.equals("opencp"))
			openControlPanel();
		else if (arg.equals("magic"))
			installMagicMontageTools();
		else if (arg.equals("interactive"))
			openInteractiveModeEditor();
		else if (arg.startsWith("showdir"))
			showDirectory(arg.replace("showdir", ""));
		else if (arg.equals("measure"))
			measureStack();
		else if (arg.equals("invert"))
			IJ.runMacroFile("ij.jar:InvertAllLuts", null);

	}
	
	private synchronized void showFonts() {
		Thread t = new Thread(new Runnable() {
			public void run() {IJ.runPlugIn("ij.plugin.Text", "");}
		});
		t.start();
	}

	private void reset() {
		GenericDialog gd = new GenericDialog("");
		gd.addChoice("Reset:", choices, choices[choiceIndex]);
		gd.showDialog();
		if (gd.wasCanceled()) return;
		choiceIndex = gd.getNextChoiceIndex();
		switch (choiceIndex) {
			case 0: unlock(); break;
			case 1: resetClipboard(); break;
			case 2: resetUndo(); break;
		}
	}
	
	private void unlock() {
		ImagePlus imp = IJ.getImage();
		boolean wasUnlocked = imp.lockSilently();
		if (wasUnlocked)
			IJ.showStatus("\""+imp.getTitle()+"\" is not locked");
		else {
			IJ.showStatus("\""+imp.getTitle()+"\" is now unlocked");
			IJ.beep();
		}
		imp.unlock();
	}

	private void resetClipboard() {
		ImagePlus.resetClipboard();
		IJ.showStatus("Clipboard reset");
	}
	
	private void resetUndo() {
		Undo.setup(Undo.NOTHING, null);
		IJ.showStatus("Undo reset");
	}
	
	private void rename() {
		ImagePlus imp = IJ.getImage();
		GenericDialog gd = new GenericDialog("Rename");
		gd.addStringField("Title:", imp.getTitle(), 30);
		gd.showDialog();
		if (!gd.wasCanceled())
			imp.setTitle(gd.getNextString());
	}
		
	private void search() {
		searchArg = IJ.runMacroFile("ij.jar:Search", searchArg);
	}
		
	private void installation() {
		String url = IJ.URL2+"/docs/install/";
		if (IJ.isMacintosh())
			url += "osx.html";
		else if (IJ.isWindows())
			url += "windows.html";
		else if (IJ.isLinux())
			url += "linux.html";
		IJ.runPlugIn("ij.plugin.BrowserLauncher", url);
	}
	
	private void aboutPluginsHelp() {
		IJ.showMessage("\"About Plugins\" Submenu", 
			"Plugins packaged as JAR files can add entries\n"+
			"to this submenu. There is an example at\n \n"+
			IJ.URL2+"/plugins/jar-demo.html");
	}
	
	private void setSliceLabel() {
		ImagePlus imp = IJ.getImage();
		ImageStack stack = imp.getStack();
		int n = imp.getCurrentSlice();
		String label = stack.getSliceLabel(n);
		String label2 = label;
		if (label2==null)
			label2 = "";
		GenericDialog gd = new GenericDialog("Set Slice Label ("+n+")");
		gd.addStringField("Label:", label2, 30);
		gd.showDialog();
		if (!gd.wasCanceled()) {
			label2 = gd.getNextString();
			if (label2!=null && !label2.equals(label)) {
				if (label2.length()==0)
					label2 = null;
				stack.setSliceLabel(label2, n);
				imp.setProp("Slice_Label", label2);	
				imp.repaintWindow();
			}
		}
	}

	private void removeStackLabels() {
		ImagePlus imp = IJ.getImage();
		ImageStack stack = imp.getStack();
		int size = imp.getStackSize();
		for (int i=1; i<=size; i++)
			stack.setSliceLabel(null, i);
		if (size==1)
			imp.setProp("Slice_Label", null);				
		imp.repaintWindow();
	}
	
	private void imageToResults() {
		ResultsTable rt = ResultsTable.createTableFromImage(IJ.getImage());
		if (rt!=null)
			rt.show("Results");
	}
	
	private void resultsToImage() {
		ResultsTable rt = ResultsTable.getResultsTable();
		if (rt==null || rt.size()==0) {
			IJ.error("Results to Image", "The Results table is empty");
			return;
		}
		ImageProcessor ip = rt.getTableAsImage();
		if (ip==null) return;
		new ImagePlus("Results Table", ip).show();
	}
	
	private void openControlPanel() {
		Prefs.set("Control_Panel.@Main", "51 22 92 426");
		Prefs.set("Control_Panel.Help.Examples", "144 107 261 373");
		IJ.run("Control Panel...", "");
	}

	private void showMissingPluginsMessage() {
		IJ.showMessage("Path Randomization", 
			"Plugins were not loaded due to macOS Path Randomization.\n"+
			"To work around this problem, move ImageJ.app out of the\n"+
			"ImageJ folder and then copy it back. More information is at\n \n"+
			IJ.URL2+"/docs/install/osx.html#randomization");
	}
	
	private void installMagicMontageTools() {
		String name = "MagicMontageTools.txt";
		String path = "/macros/"+name;
		MacroInstaller mi = new MacroInstaller();
		if (IJ.shiftKeyDown())
			 Toolbar.showCode(name, mi.openFromIJJar(path));
		else
			try {
				mi.installFromIJJar(path);
			} catch (Exception e) {}
	}
	
	private void openInteractiveModeEditor() {
		Editor ed = new Editor();
		ed.setSize(600, 500);
		ed.create(Editor.INTERACTIVE_NAME, "");
	}
	
	private void showDirectory(String arg) {
		arg = arg.toLowerCase();
		String path = IJ.getDir(arg);
		if (path == null) {
			if (arg.equals("image")) {
				if (WindowManager.getCurrentImage()==null)
					IJ.noImage();
				else
					IJ.error("No file is associated with front image");
			} else
				IJ.error("Folder not found: " + arg);
			return;
		}		
		File dir = new File(path);
		if (!dir.exists()) {
			IJ.error("Folder not found: " + arg);
			return;
		}
		if (arg.equals("image")&& IJ.getImage()!=null) {
			File imgPath = new File(IJ.getDir("image"));
			if (!imgPath.exists()) {
				IJ.error("Image not found");
				return;
			}
		}
		if (IJ.debugMode) IJ.log("Show Folder: arg="+arg+", path="+path);
		String msg1 = "";
		if (IJ.isLinux()) try {
			if (IJ.debugMode) IJ.log("  trying xdg-open "+path);
			Runtime.getRuntime().exec(new String[] {"xdg-open", path} );
			return;
		} catch (Exception e2) {
			msg1 = "xdg-open error: "+e2;
		}
		try {
			if (IJ.debugMode) IJ.log("  trying Desktop.open "+dir);
			Desktop desktop = Desktop.getDesktop();
			desktop.open(dir);
		} catch (Exception e) {
			String msg2 = "Desktop.open error: "+e;
			if (msg1.length()>0)
				msg2 = msg1+"\n"+msg2;
			IJ.error("Show Folder", msg2);
		}
	}

	private void measureStack() {
		ImagePlus imp = IJ.getImage();
		if (imp.isLocked()) {
			IJ.showStatus("Image is locked: \""+imp.getTitle()+"\"");
			IJ.beep();
		} else
			IJ.runMacroFile("ij.jar:MeasureStack", null);
		return;
	}

}
