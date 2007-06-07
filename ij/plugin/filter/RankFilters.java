package ij.plugin.filter;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.util.*;

public class RankFilters implements PlugInFilter {

	static final int BYTE=0, SHORT=1, FLOAT=2, RGB=3;
	static final int MEDIAN=0, MEAN=1, MIN=2, MAX=3;
	ImagePlus imp;
	int filterType = MEDIAN;
  	String title;
	static boolean createSelection = false;
	int kw, kh;
	static float[] kernel;
	static int slice;
	static boolean canceled;
	static ImageWindow win;
	static double radius = 2.0;

	public int setup(String arg, ImagePlus imp) {
 		IJ.register(RankFilters.class);
		this.imp = imp;
		if (arg.equals("min"))
			filterType = MIN;
		else if (arg.equals("max"))
			filterType = MAX;
		else if (arg.equals("mean"))
			filterType = MEAN;
		slice = 0;
		canceled = false;
		if (imp!=null) {
			win = imp.getWindow();
			win.running = true;
		}
 		switch (filterType) {
			case MIN: title = "Minimum"; break;
			case MAX: title = "Maximum"; break;
			case MEAN: title = "Mean"; break;
			default: title = "Median";
		}
		IJ.showStatus(title+", radius="+radius+" (esc to abort)");
		if (imp!=null && !showDialog())
			return DONE;
		else
			return IJ.setupDialog(imp, DOES_ALL);
	}

	public void run(ImageProcessor ip) {
		if (canceled)
			return;
		slice++;
		if (win.running!=true)
			{canceled=true; IJ.beep(); return;}
		rank(ip, radius, filterType);
		if (slice>1)
			IJ.showStatus(title+": "+slice+"/"+imp.getStackSize());
		if (slice==imp.getStackSize())
			ip.resetMinAndMax();
	}
	
	int getType(ImageProcessor ip) {
		int type;
		if (ip instanceof ByteProcessor)
			type = BYTE;
		else if (ip instanceof ShortProcessor)
			type = SHORT;
		else if (ip instanceof FloatProcessor)
			type = FLOAT;
		else
			type = RGB;
		return type;
	}

	public void convertBack(ImageProcessor ip2, ImageProcessor ip, int type) {
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
   	 
	boolean showDialog() {
		GenericDialog gd = new GenericDialog(title+"...");
		gd.addNumericField("Radius (pixels):", radius, 0);
		gd.showDialog();
		if (gd.wasCanceled()) {
		 canceled = true;
		 return false;
		}
		radius = gd.getNextNumber();
		imp.startTiming();
		return true;
	}

	public void rank(ImageProcessor ip, double radius, int rankType) {
		int type = getType(ip);
		if (type==RGB) {
			rankRGB(ip, radius, rankType);
			return;
		}
		ip.setCalibrationTable(null);
		ImageProcessor ip2 = ip.convertToFloat();
		rankFloat(ip2, radius, rankType);
		convertBack(ip2, ip, type);
	}

	public void rankRGB(ImageProcessor ip, double radius, int rankType) {
		int width = ip.getWidth();
		int height = ip.getHeight();
		int size = width*height;
		if (slice==1) IJ.showStatus(title+" (red)");
		byte[] r = new byte[size];
		byte[] g = new byte[size];
		byte[] b = new byte[size];
		((ColorProcessor)ip).getRGB(r,g,b);
		ImageProcessor rip = new ByteProcessor(width, height, r, null);
		ImageProcessor gip = new ByteProcessor(width, height, g, null);
		ImageProcessor bip = new ByteProcessor(width, height, b, null);
		ImageProcessor ip2 = rip.convertToFloat();
		rankFloat(ip2, radius, rankType);
		if (canceled) return;
		ImageProcessor r2 = ip2.convertToByte(false);
		if (slice==1) IJ.showStatus(title+" (green)");
		ip2 = gip.convertToFloat();
		rankFloat(ip2, radius, rankType);
		if (canceled) return;
		ImageProcessor g2 = ip2.convertToByte(false);
		if (slice==1) IJ.showStatus(title+" (blue)");
		ip2 = bip.convertToFloat();
		rankFloat(ip2, radius, rankType);
		if (canceled) return;
		ImageProcessor b2 = ip2.convertToByte(false);
		((ColorProcessor)ip).setRGB((byte[])r2.getPixels(), (byte[])g2.getPixels(), (byte[])b2.getPixels());
	}

	public void rankFloat(ImageProcessor ip, double radius, int rankType) {
		int width = ip.getWidth();
		int height = ip.getHeight();
		int kw = (int)(radius*2) + 1;
		int kh = kw;
		if (IJ.altKeyDown()) {
			for (int r=1; r<10; r++) {
				int diameter = r*2+1;
				int[] mask = createCircularMask(diameter);
				float[] mask2 = new float[diameter*diameter];
				for (int i=0; i<diameter*diameter; i++)
					mask2[i] = mask[i];
				new ImagePlus("radius="+r, new FloatProcessor(diameter, diameter, mask2, null)).show();
			}
		}
		int[] mask = createCircularMask(kw);
		int maskSize = 0;
		for (int i=0; i<kw*kw; i++)
			if (mask[i]!=0)
				maskSize++;
		float values[] = new float[maskSize];
		int uc = kw/2;    
		int vc = kh/2;
		float[] pixels = (float[])ip.getPixels();
		float[] pixels2 = (float[])ip.getPixelsCopy();
		for (int i=0; i<width*height; i++)
			pixels[i] = 0f;
//if (medianFilter) {
//	float[] test = {0,1,2,3,4,5,6,7,8,9,10,0,1,2,3,4,5,6,7,8,9,10,0,1,2,3,4,5,6,7,8,9,10};
//	float median = findMedian(test);
//	return;
//}

 		int progress = Math.max(height/50,1);
		double sum;
		int offset, i, count;
		boolean edgePixel;  
		for(int y=0; y<height; y++) {
			if (y%progress ==0) {
				IJ.showProgress((double)y/height);
				canceled = win!=null && !win.running;
				if (canceled)
					break;
			}
			edgePixel = y<vc || y>=height+vc;
			for(int x=0; x<width; x++) {
				sum = 0.0;
				i = 0;
				count = 0;
				if (x<uc || x>=height+uc)
					edgePixel = true;
				for(int v=-vc; v <= vc; v++) {
					offset = x+(y+v)*width;
					for(int u = -uc; u <= uc; u++) {
						if (mask[i++]!=0) {
							if (edgePixel)
								values[count] = getPixel(x+u, y+v, pixels2, width, height);
							else
								values[count] = pixels2[offset+u];
							count++;
						}
					}
	    		}
				if (rankType==MEDIAN)
					pixels[x+y*width] = findMedian(values);
				else if (rankType==MEAN)
					pixels[x+y*width] = findMean(values);
				else if (rankType==MIN)
					pixels[x+y*width] = findMin(values);
				else
					pixels[x+y*width] = findMax(values);
			}
    	}
   		IJ.showProgress(1.0);
   		if (canceled) {
   			//ip.reset();
			ip.insert(new FloatProcessor(width,height,pixels2,null), 0, 0);
   			IJ.beep();
   		}
   	 }

	private float getPixel(int x, int y, float[] pixels, int width, int height) {
		if (x<=0) x = 0;
		if (x>=width) x = width-1;
		if (y<=0) y = 0;
		if (y>=height) y = height-1;
		return pixels[x+y*width];
	}
	
	int[] createCircularMask(int diameter) {
		int[] mask = new int[diameter*diameter];
		int r = diameter/2;
		int r2 = r*r+1;
		for (int x=-r; x<=r; x++)
			for (int y=-r; y<=r; y++)
				if ((x*x+y*y)<=r2)
					mask[r+x+(r+y)*diameter]=1;

		return mask;
	}

	private final float findMedian (float[] values) {
		int nValues = values.length;
		int mid = (nValues-1)/2;
		for (int i=0; i<mid; i++) {
			float max = 0f;
			int mj = 0;
			for (int j=0; j<nValues; j++)
				if (values[j] > max) {
					max = values[j];
					mj = j;
				}
			values[mj] = 0;
		}
		float max = 0f;
		for (int j=0; j<nValues; j++)
			if (values[j]>max)
				max = values[j];
		return max;
	}

	private final float findMin(float[] values) {
		float min = 255f;
		for (int i=0; i<values.length; i++)
			if (values[i]<min)
				min = values[i];
		return min;
	}

	private final float findMax(float[] values) {
		float max = 0f;
		for (int i=0; i<values.length; i++)
			if (values[i]>max)
				max = values[i];
		return max;
	}

	private final float findMean(float[] values) {
		float sum = 0f;
		for (int i=0; i<values.length; i++)
			sum += values[i];
		return (float)Math.round(sum/values.length);
	}
}


