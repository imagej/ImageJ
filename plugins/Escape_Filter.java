import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.filter.*;

/**This filter plugin runs for ~10 seconds or until the user 
    presses the Esc key.  It requires ImageJ 1.33m or later. */
public class Escape_Filter implements PlugInFilter {
	ImagePlus imp;
	ImageWindow win;
	boolean done;
	int slice;
	long startTime;
	int maxTime = 10; //seconds

	public int setup(String arg, ImagePlus imp) {
		if (IJ.versionLessThan("1.33m"))
			return DONE;
		this.imp = imp;
		startTime = System.currentTimeMillis();
		return DOES_ALL+DOES_STACKS;
	}

	public void run(ImageProcessor ip) {
		slice++;
		long runStart = System.currentTimeMillis();
		while (!done) {
			if (!doSimulatedProcessing(imp, runStart)) return;
			if (IJ.escapePressed())
				{IJ.beep(); done=true; break;}
		}
	}

	public boolean doSimulatedProcessing(ImagePlus imp, long runStart) {
		long time = System.currentTimeMillis();
		long timeLeft = maxTime - (time-startTime)/1000;
		int nSlices = imp.getStackSize();
		IJ.showStatus("Time remaining: "+timeLeft + " sec. "+"Press 'Esc' to abort.");
		if ((time-runStart)>=(maxTime*1000)/nSlices) return false;
		IJ.wait(10);
		return true;
	}

}
