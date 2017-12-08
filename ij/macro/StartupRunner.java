package ij.macro;
import ij.IJ;
import ij.plugin.MacroInstaller;
import ij.plugin.Startup;

/** Runs the RunAtStartup and AutoRun macros, on a separate thread
	unless ImageJ is running a command line (batch mode) macro.*/
public class StartupRunner implements Runnable {

	public void run(boolean batchMode) {
		if (IJ.debugMode) IJ.log("StartupRunner: "+batchMode);
		if (batchMode)
			run();
		else {
			Thread thread = new Thread(this, "StartupRunner");
			thread.start();
		}
	}

	public void run() {
 		String macro = (new Startup()).getStartupMacro();
 		if (macro!=null && macro.length()>4)
 			IJ.runMacro(macro);
		MacroInstaller.autoRun();
 	}
 	
}

