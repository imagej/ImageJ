package ij.plugin.filter;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.Calibration;
import java.awt.*;

/** Splits an RGB image or stack into three 8-bit grayscale images or stacks. */
public class RGBStackSplitter implements PlugInFilter {
    ImagePlus imp;
    /** These are the three stacks created by the split(ImageStack) method. */
    public ImageStack red, green, blue;


    public int setup(String arg, ImagePlus imp) {
        this.imp = imp;
        return DOES_RGB+NO_UNDO;
    }

    public void run(ImageProcessor ip) {
        split(imp);
    }

    /** Splits the specified RGB image or stack into three 8-bit grayscale images or stacks. */
    public void split(ImagePlus imp) {
        split(imp.getStack(), true);
        String title = imp.getTitle();
        Calibration cal = imp.getCalibration();
        if (!IJ.altKeyDown())
        	imp.hide();
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

}



