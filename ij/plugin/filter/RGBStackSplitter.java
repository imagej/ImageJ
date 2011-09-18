package ij.plugin.filter;
import ij.*;
import ij.process.*;
import ij.plugin.ChannelSplitter;

/** Deprecated; replaced by ij.plugin.ChannelSplitter. */
public class RGBStackSplitter implements PlugInFilter {
	ImagePlus imp;
	public ImageStack red, green, blue;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		(new ChannelSplitter()).run(arg);
		return DONE;
	}

	public void run(ImageProcessor ip) {
	}

	/** Deprecated; replaced by ij.plugin.ChannelSplitter. */
	public void split(ImagePlus imp) {
		WindowManager.setTempCurrentImage(imp);
		(new ChannelSplitter()).run("");
	}

	/** Deprecated; replaced by ChannelSplitter.splitRGB(). */
	public void split(ImageStack rgb, boolean keepSource) {
		ImageStack[] channels = ChannelSplitter.splitRGB(rgb, keepSource);
		red = channels[0];
		green = channels[1];
		blue = channels[2];
	}
	
}



