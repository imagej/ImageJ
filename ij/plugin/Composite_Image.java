package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.awt.image.*;
import ij.plugin.frame.ContrastAdjuster;

public class Composite_Image implements PlugIn {

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		if (imp instanceof CompositeImage)
			return;
		int z = imp.getStackSize();
		if (imp.getBitDepth()==24) {
			imp.hide();
			new CompositeImage(imp, 3).show();
		} else if (z>=2 && z<=7) {
			imp.hide();
			new CompositeImage(imp, z).show();
		} else
			IJ.error("To convert to composite color, the current\nimage must be a stack or be in RGB format.");
	}

}
