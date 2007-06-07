import ij.*;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import java.awt.*;

/** This sample ImageJ plugin filter inverts 8-bit images.

A few things to note:
	1) Filter plugins must implement the PlugInFilter interface.
	2) Plugins located in the plugins folder must not use
	the package statement;
	3) Plugins residing in the plugins folder that have at
	least one underscore in their name are automatically
	installed in the Plugins menu.
	4) Plugins can be installed in other menus be using
	the Plugins/Hot Keys/Install Plugin command..
	5) The Install Plugin command automatically installs plugins
	with a showAbout() method in the Help/About/Plugins submenu.
	6) The class name and file name must be the same.
	7) This filter works with ROIs, including non-rectangular ROIs.
	5) It will be called repeatedly to process all the slices in a stack.
	6) This plugin can't be named "Invert_" because that would
	conflict with the built in command of the same name.
*/

public class Inverter_ implements PlugInFilter {

	public int setup(String arg, ImagePlus imp) {
		if (arg.equals("about"))
			{showAbout(); return DONE;}
		return DOES_8G+DOES_STACKS+SUPPORTS_MASKING;
	}

	public void run(ImageProcessor ip) {
		byte[] pixels = (byte[])ip.getPixels();
		int width = ip.getWidth();
		Rectangle r = ip.getRoi();
		int offset, i;
		for (int y=r.y; y<(r.y+r.height); y++) {
			offset = y*width;
			for (int x=r.x; x<(r.x+r.width); x++) {
				i = offset + x;
				pixels[i] = (byte)(255-pixels[i]);
			}
		}
	}

	void showAbout() {
		IJ.showMessage("About Inverter_...",
			"This sample plugin filter inverts 8-bit images. Look\n" +
			"at the 'Inverter_.java' source file to see how easy it is\n" +
			"in ImageJ to process non-rectangular ROIs, to process\n" +
			"all the slices in a stack, and to display an About box."
		);
	}
}

