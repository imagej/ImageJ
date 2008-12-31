package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;

/** This plugin implements the Plugins/Utilities/Capture Screen
	and Plugins/Utilities/Capture Image commands. */
public class ScreenGrabber implements PlugIn {
    
    public void run(String arg) {
    	ImagePlus imp = null;
    	if (arg.equals("image"))
    		imp = captureImage();
    	else
			imp = captureScreen();
    	if (imp!=null) imp.show();
    }
    
	/** Captures the entire screen and returns it as an ImagePlus. */
	public ImagePlus captureScreen() {
		ImagePlus imp = null;
		try {
			Robot robot = new Robot();
			Dimension dimension = IJ.getScreenSize();
			Rectangle r = new Rectangle(dimension);
			Image img = robot.createScreenCapture(r);
			if (img!=null) imp = new ImagePlus("Screen", img);
		} catch(Exception e) {}
		return imp;
	}

	/** Captures the active image window and returns it as an ImagePlus. */
	public ImagePlus captureImage() {
		ImagePlus imp = IJ.getImage();
		if (imp==null) {
			IJ.noImage();
			return null;
		}
		ImagePlus imp2 = null;
		try {
			ImageWindow win = imp.getWindow();
			if (win==null) return null;
			Point loc = win.getLocation();
			ImageCanvas ic = win.getCanvas();
			Rectangle bounds = ic.getBounds();
			loc.x += bounds.x;
			loc.y += bounds.y;
			Rectangle r = new Rectangle(loc.x, loc.y, bounds.width, bounds.height);
			Robot robot = new Robot();
			Image img = robot.createScreenCapture(r);
			if (img!=null)
				imp2 = new ImagePlus("Grab of "+imp.getTitle(), img);
		} catch(Exception e) {}
		return imp2;
	}

}

