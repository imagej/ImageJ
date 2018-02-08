package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.io.Opener;
import ij.text.TextWindow;
import ij.measure.ResultsTable;
import java.awt.Frame;

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
		String url = IJ.URL+"/docs/install/";
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
			IJ.URL+"/plugins/jar-demo.html");
	}
	
	private void setSliceLabel() {
		ImagePlus imp = IJ.getImage();
		int size = imp.getStackSize();
		if (size==1) {
			IJ.error("Stack required");
			return;
		}
		ImageStack stack = imp.getStack();
		int n = imp.getCurrentSlice();
		String label = stack.getSliceLabel(n);
		String label2 = label;
		if (label2==null)
			label2 = "";
		GenericDialog gd = new GenericDialog("Set Slice Label ("+n+")");
		gd.addStringField("Label:", label2, 30);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		label2 = gd.getNextString();
		if (label2!=label) {
			stack.setSliceLabel(label2, n);
			imp.repaintWindow();
		}
	}

	private void removeStackLabels() {
		ImagePlus imp = IJ.getImage();
		int size = imp.getStackSize();
		if (size==1)
			IJ.error("Stack required");
		else {
			ImageStack stack = imp.getStack();
			for (int i=1; i<=size; i++)
				stack.setSliceLabel(null, i);
			imp.repaintWindow();
		}
	}
	
	private void imageToResults() {
		ImagePlus imp = IJ.getImage();
		ImageProcessor ip = imp.getProcessor();
		ResultsTable rt = ResultsTable.createTableFromImage(ip);
		rt.showRowNumbers(false);
		rt.show("Results");
	}
	
	private void resultsToImage() {
		ResultsTable rt = ResultsTable.getResultsTable();
		if (rt==null || rt.getCounter()==0) {
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
			IJ.URL+"/docs/install/osx.html#randomization");
	}

		
}
