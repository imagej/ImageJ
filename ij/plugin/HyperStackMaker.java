package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.util.Tools;
import ij.process.*;
import java.awt.*;

/** This plugin implements the File/New/Hyperstack command. */
public class HyperStackMaker implements PlugIn {
	private static String defaults = "8-bit Color 400 300 3 4 5 1";
	private static String[] types = {"8-bit", "16-bit", "32-bit", "RGB"};
	private static String[] modes = {"Composite", "Color", "Grayscale"};
	private static String title = "HyperStack";
	private String type, mode;
	private int width, height, c, z, t;
	private boolean label;

	public void run(String arg) {
		String[] prefs = Tools.split(Prefs.get("hyperstack.new", defaults));
		if (prefs.length<8)
			prefs = Tools.split(defaults);
		type = prefs[0];
		mode = prefs[1];
		width = (int)Tools.parseDouble(prefs[2], 400);
		height = (int)Tools.parseDouble(prefs[3], 300);
		c = (int)Tools.parseDouble(prefs[4], 3);
		z = (int)Tools.parseDouble(prefs[5], 4);
		t = (int)Tools.parseDouble(prefs[6], 5);
		int labelInt = (int)Tools.parseDouble(prefs[7], 1);
		label = labelInt==1?true:false;
		if (!showDialog())
			return;
		String type2 = type;
		if (label)
			type2 += " ramp label";
		if (mode.equals("Composite"))
			type2 += " composite";
		else if (mode.equals("Color"))
			type2 += " color";
		else if (mode.equals("Grayscale"))
			type2 += " grayscale";
		ImagePlus imp = IJ.createImage(title, type2, width, height, c, z, t);
		imp.show();
		if (!IJ.isMacro()) {
			String defaults2 = type+" "+mode+" "+width+" "+height+" "+c+" "+z+" "+t+" "+(label?"1":"0");
			Prefs.set("hyperstack.new", defaults2);
		}
	}
	
	private boolean showDialog() {
		GenericDialog gd = new GenericDialog("New Hyperstack...");
		gd.addStringField("Name:", title, 12);
		gd.addChoice("Type:", types, type);
		gd.addChoice("Display mode:", modes, mode);
		gd.addNumericField("Width:", width, 0, 5, "pixels");
		gd.addNumericField("Height:", height, 0, 5, "pixels");
		gd.addNumericField("Channels (c):", c, 0, 5, "");
		gd.addNumericField("Slices (z):", z, 0, 5, "");
		gd.addNumericField("Frames (t):", t, 0, 5, "");
		gd.addCheckbox("Label images", label);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		title = gd.getNextString();
		type = gd.getNextChoice();
		mode = gd.getNextChoice();
		width = (int)gd.getNextNumber();
		height = (int)gd.getNextNumber();
		c = (int)gd.getNextNumber();
		if (c<1) c=1;
		z = (int)gd.getNextNumber();
		if (z<1) z=1;
		t = (int)gd.getNextNumber();
		if (t<1) t=1;
		label = gd.getNextBoolean();
		if (width<1 || height<1) {
			IJ.error("New Image", "Width and height must be >0");
			return false;
		} else
			return true;
	}
	
	public static void labelHyperstack(ImagePlus imp) {
		int width = imp.getWidth();
		int height = imp.getHeight();
		int c = imp.getNChannels();
		int z = imp.getNSlices();
		int t = imp.getNFrames();
		int yloc = 30;
		ImageStack stack = imp.getStack();
		int n = stack.getSize();
		int channel=1, slice=1, frame=1;
		for (int i=1; i<=n; i++) {
			IJ.showProgress(i, n);
			ImageProcessor ip = stack.getProcessor(i);
			ip.setAntialiasedText(true);
			ip.setColor(Color.black);
			ip.setRoi(0, 0, width, yloc);
			ip.fill();
			ip.setRoi(0, yloc+25, width, height-(yloc+25));
			ip.fill();
			ip.setColor(Color.white);
			ip.setFont(new Font("SansSerif",Font.PLAIN,24));
			ip.drawString("c="+IJ.pad(channel,3)+", z="+IJ.pad(slice,3)+", t="+IJ.pad(frame,3), 5, yloc);
			if (i<=c) {
				ip.setFont(new Font("SansSerif",Font.PLAIN,12));
				String msg = "Press shift-z (Image>Color>Channels Tool)\n"+
					"to open the \"Channels\" window, which will\n"+
					"allow you switch to composite color mode\n"+
					"and to enable/disable channels.\n";
				ip.drawString(msg, 25, 80);
			}
			channel++;
			if (channel>c) {
				channel = 1;
				slice++;
				if (slice>z) {
					slice = 1;
					frame++;
					if (frame>t) frame = 1;
				}
			}
		}
	}

}

