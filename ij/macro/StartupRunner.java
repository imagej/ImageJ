package ij.macro;
import ij.IJ;
import ij.plugin.MacroInstaller;
import ij.plugin.Startup;

/** Runs the RunAtStartup (created by Edit/Options/Startup) and AutoRun (in StartupMacros) macros. */
public class StartupRunner implements Runnable {

	/** Runs the RunAtStartup and AutoRun macros, on the current thread
		if 'batchMode' true, otherwise on a separate thread. */
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

