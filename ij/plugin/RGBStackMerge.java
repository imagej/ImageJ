package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;

public class RGBStackMerge implements PlugIn {

    private ImagePlus imp;
    private byte[] blank;
 
    /** Merges one, two or three 8-bit or RGB stacks into a single RGB stack. */
    public void run(String arg) {
        imp = WindowManager.getCurrentImage();
        mergeStacks();
    }

    /** Combines three grayscale stacks into one RGB stack. */
    public void mergeStacks() {
        int[] wList = WindowManager.getIDList();
        if (wList==null) {
            IJ.error("No images are open.");
            return;
        }

        String[] titles = new String[wList.length+1];
        for (int i=0; i<wList.length; i++) {
            ImagePlus imp = WindowManager.getImage(wList[i]);
            titles[i] = imp!=null?imp.getTitle():"";
        }
        String none = "*None*";
        titles[wList.length] = none;

        GenericDialog gd = new GenericDialog("RGB Merge");
        gd.addChoice("Red:", titles, titles[0]);
        gd.addChoice("Green:", titles, titles[1]);
        String title3 = titles.length>2?titles[2]:none;
        gd.addChoice("Blue:", titles, title3);
        gd.addCheckbox("Keep source images", false);
        gd.showDialog();
        if (gd.wasCanceled())
            return;
        int[] index = new int[3];
        index[0] = gd.getNextChoiceIndex();
        index[1] = gd.getNextChoiceIndex();
        index[2] = gd.getNextChoiceIndex();
        boolean keep = gd.getNextBoolean();

        ImagePlus[] image = new ImagePlus[3];
        int stackSize = 0;
        int width = 0;
        int height = 0;
        for (int i=0; i<3; i++) {
            if (index[i]<wList.length) {
                image[i] = WindowManager.getImage(wList[index[i]]);
                width = image[i].getWidth();
                height = image[i].getHeight();
                stackSize = image[i].getStackSize();
            }
        }
        if (width==0) {
            IJ.error("There must be at least one source image or stack.");
            return;
        }
        for (int i=0; i<3; i++) {
            ImagePlus img = image[i];
            if (img!=null) {
                if (img.getStackSize()!=stackSize) {
                    IJ.error("The source stacks must all have the same number of slices.");
                    return;
                }
                //if (!(img.getType()==ImagePlus.GRAY8||img.getType()==ImagePlus.COLOR_RGB)) {
                //    IJ.error("The source stacks must be 8-bit grayscale or RGB.");
                //    return;
                //}
                if (img.getWidth()!=width || image[i].getHeight()!=height) {
                    IJ.error("The source images or stacks must have the same width and height.");
                    return;
                }
            }
        }

        ImageStack red = image[0]!=null?image[0].getStack():null;
        ImageStack green = image[1]!=null?image[1].getStack():null;
        ImageStack blue = image[2]!=null?image[2].getStack():null;
        ImageStack rgb = mergeStacks(width, height, stackSize, red, green, blue, keep);
        if (!keep)
            for (int i=0; i<3; i++) {
                if (image[i]!=null) {
                    image[i].changes = false;
                    image[i].close();
                }
            }
        ImagePlus imp2 = new ImagePlus("RGB", rgb);
        if (image[0]!=null)
            imp2.setCalibration(image[0].getCalibration());
        imp2.show();
    }
    
    public ImageStack mergeStacks(int w, int h, int d, ImageStack red, ImageStack green, ImageStack blue, boolean keep) {
        ImageStack rgb = new ImageStack(w, h);
        int inc = d/10;
        if (inc<1) inc = 1;
        ColorProcessor cp;
        int slice = 1;
        blank = new byte[w*h];
        byte[] redPixels, greenPixels, bluePixels;
        boolean invertedRed = red!=null?red.getProcessor(1).isInvertedLut():false;
        boolean invertedGreen = green!=null?green.getProcessor(1).isInvertedLut():false;
        boolean invertedBlue = blue!=null?blue.getProcessor(1).isInvertedLut():false;
        try {
            for (int i=1; i<=d; i++) {
            cp = new ColorProcessor(w, h);
                redPixels = getPixels(red, slice, 0);
                greenPixels = getPixels(green, slice, 1);
                bluePixels = getPixels(blue, slice, 2);
                if (invertedRed) redPixels = invert(redPixels);
                if (invertedGreen) greenPixels = invert(greenPixels);
                if (invertedBlue) bluePixels = invert(bluePixels);
                cp.setRGB(redPixels, greenPixels, bluePixels);
            if (keep) {
                slice++;
                //if (invertedRed) invert(redPixels);
                //if (invertedGreen) invert(greenPixels);
                //if (invertedBlue) invert(bluePixels);
            } else {
                    if (red!=null) red.deleteSlice(1);
                if (green!=null &&green!=red) green.deleteSlice(1);
                if (blue!=null&&blue!=red && blue!=green) blue.deleteSlice(1);
                //System.gc();
            }
            rgb.addSlice(null, cp);
            if ((i%inc) == 0) IJ.showProgress((double)i/d);
            }
        IJ.showProgress(1.0);
        } catch(OutOfMemoryError o) {
            IJ.outOfMemory("Merge Stacks");
            IJ.showProgress(1.0);
        }
        return rgb;
    }
    
     byte[] getPixels(ImageStack stack, int slice, int color) {
         if (stack==null)
            return blank;
        Object pixels = stack.getPixels(slice);
        if (!(pixels instanceof int[])) {
        	if (pixels instanceof byte[])
            	return (byte[])pixels;
            else {
            	ImageProcessor ip = stack.getProcessor(slice);
            	ip = ip.convertToByte(true);
            	return (byte[])ip.getPixels();
            }
        } else { //RGB
            byte[] r,g,b;
            int size = stack.getWidth()*stack.getHeight();
            r = new byte[size];
            g = new byte[size];
            b = new byte[size];
            ColorProcessor cp = (ColorProcessor)stack.getProcessor(slice);
            cp.getRGB(r, g, b);
            switch (color) {
                case 0: return r;
                case 1: return g;
                case 2: return b;
            }
        }
        return null;
    }

    byte[] invert(byte[] pixels) {
        byte[] pixels2 = new byte[pixels.length];
        System.arraycopy(pixels, 0, pixels2, 0, pixels.length);
        for (int i=0; i<pixels2.length; i++)
            pixels2[i] = (byte)(255-pixels2[i]&255);
        return pixels2;
    }

}

