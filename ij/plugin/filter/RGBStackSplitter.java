package ij.plugin.filter;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.Calibration;
import ij.plugin.HyperStackReducer;
import java.awt.*;
import java.util.Vector;

/** Splits an RGB image or stack into three 8-bit grayscale images or stacks. */
public class RGBStackSplitter implements PlugInFilter {
    ImagePlus imp;
    /** These are the three stacks created by the split(ImageStack) method. */
    public ImageStack red, green, blue;


    public int setup(String arg, ImagePlus imp) {
        this.imp = imp;
        if (imp!=null && imp.isComposite()) {
        	ImagePlus[] channels = splitChannels(imp);
        	for (int i=0; i<channels.length; i++)
        		channels[i].show();
			imp.changes = false;
			imp.close();
        	return DONE;
        }
        if (imp!=null && imp.getBitDepth()!=24) {
        	IJ.error("Split Channels", "Multichannel image required");
        	return DONE;
        }
        return DOES_RGB+NO_UNDO;
    }

    public void run(ImageProcessor ip) {
        split(imp);
    }

    /** Splits the specified RGB image or stack into three 8-bit grayscale images or stacks. */
    public void split(ImagePlus imp) {
    	boolean keepSource = IJ.altKeyDown();
        String title = imp.getTitle();
        Calibration cal = imp.getCalibration();
        split(imp.getStack(), keepSource);
        if (!keepSource)
            {imp.unlock(); imp.changes=false; imp.close();}
        ImagePlus rImp = new ImagePlus(title+" (red)",red);
        rImp.setCalibration(cal);
        rImp.show();
        if (IJ.isMacOSX()) IJ.wait(500);
        ImagePlus gImp = new ImagePlus(title+" (green)",green);
        gImp.setCalibration(cal);
        gImp.show();
        if (IJ.isMacOSX()) IJ.wait(500);
        ImagePlus bImp = new ImagePlus(title+" (blue)",blue);
        bImp.setCalibration(cal);
        bImp.show();
    }

    /** Splits the specified RGB stack into three 8-bit grayscale stacks. 
    	Deletes the source stack if keepSource is false. */
    public void split(ImageStack rgb, boolean keepSource) {
         int w = rgb.getWidth();
         int h = rgb.getHeight();
         red = new ImageStack(w,h);
         green = new ImageStack(w,h);
         blue = new ImageStack(w,h);
         byte[] r,g,b;
         ColorProcessor cp;
         int slice = 1;
         int inc = keepSource?1:0;
         int n = rgb.getSize();
         for (int i=1; i<=n; i++) {
             IJ.showStatus(i+"/"+n);
             r = new byte[w*h];
             g = new byte[w*h];
             b = new byte[w*h];
             cp = (ColorProcessor)rgb.getProcessor(slice);
             slice += inc;
             cp.getRGB(r,g,b);
             if (!keepSource)
             	rgb.deleteSlice(1);
             red.addSlice(null,r);
             green.addSlice(null,g);
             blue.addSlice(null,b);
             IJ.showProgress((double)i/n);
        }
    }
    
    public static ImagePlus[] splitChannels(ImagePlus imp) {
		int width = imp.getWidth();
		int height = imp.getHeight();
		int channels = imp.getNChannels();
		int slices = imp.getNSlices();
		int frames = imp.getNFrames();
		int bitDepth = imp.getBitDepth();
		int size = slices*frames;
		Vector images = new Vector();
		HyperStackReducer reducer = new HyperStackReducer(imp);
		for (int c=1; c<=channels; c++) {
			ImageStack stack2 = new ImageStack(width, height, size); // create empty stack
			stack2.setPixels(imp.getProcessor().getPixels(), 1); // can't create ImagePlus will null 1st image
			ImagePlus imp2 = new ImagePlus("C"+c+"-"+imp.getTitle(), stack2);
			stack2.setPixels(null, 1);
			imp.setPosition(c, 1, 1);
			imp2.setDimensions(1, slices, frames);
			imp2.setCalibration(imp.getCalibration());
			reducer.reduce(imp2);
			if (imp.isComposite() && ((CompositeImage)imp).getMode()==CompositeImage.GRAYSCALE)
				IJ.run(imp2, "Grays", "");
			if (imp2.getNDimensions()>3)
				imp2.setOpenAsHyperStack(true);
			images.add(imp2);
		}
    	ImagePlus[] array = new ImagePlus[images.size()];
    	return (ImagePlus[])images.toArray(array);
    }

}



