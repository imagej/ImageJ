package ij.plugin.filter;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.text.*;
import java.awt.*;
import java.util.*;

/** This plugin implements ImageJ's Gaussian Blur command. */
public class GaussianBlur implements PlugInFilter {

	private static final int BYTE=0, SHORT=1, FLOAT=2, RGB=3;
	private ImagePlus imp;
	private boolean canceled;
	private int slice;
	private ImageWindow win;
	private boolean displayKernel;
	private static int radius = 2;
	
	public int setup(String arg, ImagePlus imp) {
 		IJ.register(GaussianBlur.class);
		this.imp = imp;
		if (imp!=null) {
			win = imp.getWindow();
			win.running = true;
		}
		if (imp!=null && !showDialog())
			return DONE;
		else
			return IJ.setupDialog(imp, DOES_ALL);
	}

	public void run(ImageProcessor ip) {
		if (canceled)
			return;
		if (win.running!=true)
			{canceled=true; IJ.beep(); return;}
		slice++;
		if (slice==1) {
			if (imp.getType()==ImagePlus.GRAY32 && imp.getStackSize()==1)
				Undo.setup(Undo.COMPOUND_FILTER, imp);
		} else
			IJ.showStatus("Gaussian Blur: "+slice+"/"+imp.getStackSize());
		blur(ip, radius);
	}
	
    public void blur(ImageProcessor ip, double radius) {
        int type;
        if (ip instanceof ByteProcessor)
            type = BYTE;
        else if (ip instanceof ShortProcessor)
            type = SHORT;
        else if (ip instanceof FloatProcessor)
            type = FLOAT;
        else
            type = RGB;
		 float[] kernel = makeKernel(radius);
 		if (slice==1 && displayKernel) {
			TextWindow tw = new TextWindow("Kernel", "", 150, 300);
			for (int i=0; i<kernel.length; i++)
				tw.append(i+"  "+IJ.d2s(kernel[i],3));
		}
		if (type==RGB) {
				blurRGB(ip, kernel);
				return;
		}
        ImageProcessor ip2=ip.convertToFloat();
        blurFloat(ip2, kernel);
         switch (type) {
            case BYTE:
                ip2 = ip2.convertToByte(false);
                byte[] pixels = (byte[])ip.getPixels();
                byte[] pixels2 = (byte[])ip2.getPixels();
                System.arraycopy(pixels2, 0, pixels, 0, pixels.length);
                break;
            case SHORT:
                ip2 = ip2.convertToShort(false);
                short[] pixels16 = (short[])ip.getPixels();
                short[] pixels16b = (short[])ip2.getPixels();
                System.arraycopy(pixels16b, 0, pixels16, 0, pixels16.length);
                break;
            case FLOAT:
                break;
        }
    }

	void blurFloat(ImageProcessor ip, float[] kernel) {
		new Convolver().convolve(ip, kernel, kernel.length, 1);
		ip.snapshot();
		new Convolver().convolve(ip, kernel,1, kernel.length);
	}
	
    public void blurRGB(ImageProcessor ip, float[] kernel) {
        int width = ip.getWidth();
        int height = ip.getHeight();
        int size = width*height;
        byte[] r = new byte[size];
        byte[] g = new byte[size];
        byte[] b = new byte[size];
        ((ColorProcessor)ip).getRGB(r,g,b);
        ImageProcessor rip = new ByteProcessor(width, height, r, null);
        ImageProcessor gip = new ByteProcessor(width, height, g, null);
        ImageProcessor bip = new ByteProcessor(width, height, b, null);
        ImageProcessor ip2 = rip.convertToFloat();
        blurFloat(ip2, kernel);
        ImageProcessor r2 = ip2.convertToByte(false);
         ip2 = gip.convertToFloat();
        blurFloat(ip2, kernel);
        ImageProcessor g2 = ip2.convertToByte(false);
        ip2 = bip.convertToFloat();
        blurFloat(ip2, kernel);
        ImageProcessor b2 = ip2.convertToByte(false);
        ((ColorProcessor)ip).setRGB((byte[])r2.getPixels(), (byte[])g2.getPixels(), (byte[])b2.getPixels());
    }

	public float[] makeKernel(double radius) {
		radius += 1;
		int size = (int)radius*2+1;
		float[] kernel = new float[size];
		double v;
		for (int i=0; i<size; i++)
			kernel[i] = (float)Math.exp(-0.5*(sqr((i-radius)/(radius*2)))/sqr(0.2));
		float[] kernel2 = new float[size-2];
		for (int i=0; i<size-2; i++)
			kernel2[i] = kernel[i+1];
		if (kernel2.length==1)
			kernel2[0] = 1f;
		return kernel2;
	}

	double sqr(double x) {return x*x;}
	
	public boolean showDialog() {
		GenericDialog gd = new GenericDialog("Guassian Blur...");
		gd.addNumericField("Radius (pixels)", radius,0);
		gd.addCheckbox("Show Kernel", displayKernel);
		gd.showDialog();
		if (gd.wasCanceled()) {
			canceled = true;
			return false;
		}
		radius = (int)gd.getNextNumber();
		displayKernel = gd.getNextBoolean();
		return true;
	}

}


