package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;

/** This plugin implements the Plugins/Utilities/Capture Screen
    and Plugins/Utilities/Capture Image commands. Note that these
    commands may not work on Linux if windows translucency or 
    special effects are enabled in the windows manager. */
public class ScreenGrabber implements PlugIn {
	private static int delay = 10;

	public void run(String arg) {
		ImagePlus imp2 = null;
		if (arg.equals("image") || arg.equals("flatten"))
			imp2 = captureImage();
		else if (arg.equals("delay"))
			imp2 = captureDelayed();
		else
			imp2 = captureScreen();
		if (imp2!=null)
			imp2.show();
	}
	
	private ImagePlus captureDelayed() {
		GenericDialog gd = new GenericDialog("Delayed Capture");
		gd.addNumericField("Delay (seconds):", delay, 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return null;
		int delay = (int)gd.getNextNumber();
		if (delay<0) return null;
		if (delay>60) delay=60;
		for (int i=0; i<delay; i++) {
			IJ.wait(1000);
			IJ.showStatus("Delayed capture: "+(i+1)+"/"+delay);
			if (delay>4 && i==delay-2) IJ.beep();
		}
		return captureScreen();
	}

    
	/** Captures the entire screen and returns it as an ImagePlus. */
	public ImagePlus captureScreen() {
		ImagePlus imp = null;
		try {
			Robot robot = new Robot();
			Rectangle r = GUI.getScreenBounds(IJ.getInstance()); // screen showing "ImageJ" window
			Image img = robot.createScreenCapture(r);
			if (img!=null) imp = new ImagePlus("Screenshot", img);
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
		ImageWindow win = imp.getWindow();
		if (win==null) return null;
		win.toFront();
		IJ.wait(500);
		Point loc = win.getLocation();
		ImageCanvas ic = win.getCanvas();
		Rectangle bounds = ic.getBounds();
		loc.x += bounds.x;
		loc.y += bounds.y;
		Rectangle r = new Rectangle(loc.x, loc.y, bounds.width, bounds.height);
		ImagePlus imp2 = null;
		Image img = null;
		boolean wasHidden = ic.hideZoomIndicator(true);
		IJ.wait(250);
		try {
			Robot robot = new Robot();
			img = robot.createScreenCapture(r);
		} catch(Exception e) { }
		ic.hideZoomIndicator(wasHidden);
		if (img!=null) {
			String title = WindowManager.getUniqueName(imp.getTitle());
			imp2 = new ImagePlus(title, img);
		}
		return imp2;
	}

}

