import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.filter.*;

/**This filter plugin runs for ~10 seconds or until the user 
    presses the Esc key.  It requires ImageJ 1.33m or later. */
public class Escape_ implements PlugInFilter {
	ImagePlus imp;
	ImageWindow win;
	boolean done;
	int slice;
	long startTime,timePerSlice;

	public int setup(String arg, ImagePlus imp) {
		if (IJ.versionLessThan("1.33m"))
			return DONE;
		this.imp = imp;
		startTime = System.currentTimeMillis();
		return DOES_ALL+DOES_STACKS;
	}

	public void run(ImageProcessor ip) {
		if (done)
			return;
		slice++;
		long sliceStartTime = System.currentTimeMillis();
		while (true) {
			long time = System.currentTimeMillis();
			long ellapsedTime = time-startTime;
			IJ.showStatus("Time remaining: "+(10-(time-startTime)/1000) + " sec. (press esc to abort)");
			if ((time-sliceStartTime)>=10000/imp.getStackSize())
				break;
			if (IJ.escapePressed())
				{IJ.beep(); done=true; break;}
			IJ.wait(10);
		}
	}

}
