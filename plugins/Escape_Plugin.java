import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;

/**This plugin runs for ~10 seconds or until the user 
    presses the Esc key.  It requires ImageJ 1.33m or later. */
public class Escape_Plugin implements PlugIn {

	public void run(String arg) {
		long startTime = System.currentTimeMillis();
		int maxTime = 10;
		while (true) {
			long time = System.currentTimeMillis();
			long ellapsedTime = time-startTime;
			long remaining = 10-(time-startTime)/1000;
			IJ.showStatus("Time remaining: "+ remaining + " sec. (press esc to abort)");
			if (IJ.escapePressed()) {IJ.beep(); IJ.showStatus("Aborted"); return;}
			if (remaining<=0) break;
			IJ.wait(10);
		}
		IJ.showStatus("");
		//IJ.run("Escape Filter");
		//IJ.run("Escape Filter");
	}

}
