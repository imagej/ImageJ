package ij.plugin.filter;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.plugin.ImageInfo;

/**
* @deprecated
* replaced by ij.plugin.ImageInfo
*/
public class Info implements PlugInFilter {
    private ImagePlus imp;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_ALL+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
	}
	
	public String getImageInfo(ImagePlus imp, ImageProcessor ip) {
		ImageInfo info = new ImageInfo();
		return info.getImageInfo(imp);
	}

}
