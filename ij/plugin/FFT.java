package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;
import ij.plugin.ContrastEnhancer;
import ij.measure.Calibration;
import java.awt.*;
import java.util.*;


/** 
This class implements the FFT, Inverse FFT and Redisplay Power Spectrum commands 
in the Process/FFT submenu. It is based on Arlo Reeves'  
Pascal implementation of the Fast Hartley Transform from NIH Image 
(http://rsb.info.nih.gov/ij/docs/ImageFFT/). 
The Fast Hartley Transform was restricted by U.S. Patent No. 4,646,256, but was placed 
in the public domain by Stanford University in 1995 and is now freely available.
*/
public class FFT implements  PlugIn, Measurements {

	public static boolean displayRawPS;
	public static boolean displayFHT;
	public static String fileName;
	
	private ImagePlus imp;
	private String arg;
	private FHT transform;
	private ImageProcessor filter;
	private static boolean processStack;
	private boolean padded;
	private	int originalWidth;
	private int originalHeight;
	private int stackSize = 1;
	private int slice = 1;

	public void run(String arg) {
		if (arg.equals("options"))
 			{showDialog(); return;}
		imp = IJ.getImage();
		if (arg.equals("redisplay"))
 			{redisplayPowerSpectrum(); return;}
 		ImageProcessor ip = imp.getProcessor();
		Object obj = imp.getProperty("FHT");
		FHT fht = (obj instanceof FHT)?(FHT)obj:null;
		stackSize = imp.getStackSize();
		boolean inverse;
		if (fht==null && arg.equals("inverse")) {
			IJ.error("FFT", "Frequency domain image required");
			return;
		}
		if (fht!=null) {
			inverse = true;
			imp.killRoi();
		} else {
			if (imp.getRoi()!=null)
				ip = ip.crop();
			fht = newFHT(ip);
			inverse = false;
		}
		if (inverse)
			doInverseTransform(fht, ip);
		else {
			if (displayRawPS || displayFHT)
				fileName = imp.getTitle();
			doForewardTransform(fht, ip);	
		}	 
		IJ.showProgress(1.0);
	}
	
	void doInverseTransform(FHT fht, ImageProcessor ip) {
		fht = fht.getCopy();
		doMasking(fht);
		showStatus("Inverse transform");
		fht.inverseTransform();
		if (fht.quadrantSwapNeeded)
			fht.swapQuadrants();
		fht.resetMinAndMax();
		ImageProcessor ip2 = fht;
		if (fht.originalWidth>0) {
			fht.setRoi(0, 0, fht.originalWidth, fht.originalHeight);
			ip2 = fht.crop();
		}
		int bitDepth = fht.originalBitDepth>0?fht.originalBitDepth:imp.getBitDepth();
		switch (bitDepth) {
			case 8: ip2 = ip2.convertToByte(false); break;
			case 16: ip2 = ip2.convertToShort(false); break;
			case 24:
				showStatus("Setting brightness");
				if (fht.rgb==null || ip2==null) {
					IJ.error("FFT", "Unable to set brightness");
					return;
				}
				ColorProcessor rgb = (ColorProcessor)fht.rgb.duplicate();
				rgb.setBrightness((FloatProcessor)ip2);
				ip2 = rgb; 
				fht.rgb = null;
				break;
			case 32: break;
		}
		if (bitDepth!=24 && fht.originalColorModel!=null)
			ip2.setColorModel(fht.originalColorModel);
		String title = imp.getTitle();
		if (title.startsWith("FFT of "))
			title = title.substring(7, title.length());
		ImagePlus imp2 = new ImagePlus("Inverse FFT of "+title, ip2);
		if (imp2.getWidth()==imp.getWidth())
			imp2.setCalibration(imp.getCalibration());
		imp2.show();
	}

	public void doForewardTransform(FHT fht, ImageProcessor ip) {
		showStatus("Foreward transform");
		fht.transform();
		showStatus("Calculating power spectrum");
		ImageProcessor ps = fht.getPowerSpectrum();
		ImagePlus imp2 = new ImagePlus("FFT of "+imp.getTitle(), ps);
		imp2.show();
		imp2.setProperty("FHT", fht);
		imp2.setCalibration(imp.getCalibration());
	}
	
	FHT newFHT(ImageProcessor ip) {
		FHT fht;
		if (ip instanceof ColorProcessor) {
			showStatus("Extracting brightness");
			ImageProcessor ip2 = ((ColorProcessor)ip).getBrightness();
			fht = new FHT(pad(ip2));
			fht.rgb = (ColorProcessor)ip.duplicate(); // save so we can later update the brightness
		} else
			fht = new FHT(pad(ip));
		if (padded) {
			fht.originalWidth = originalWidth;
			fht.originalHeight = originalHeight;
		}
		fht.originalBitDepth = imp.getBitDepth();
		fht.originalColorModel = ip.getColorModel();
		return fht;
	}
	
	ImageProcessor pad(ImageProcessor ip) {
		originalWidth = ip.getWidth();
		originalHeight = ip.getHeight();
		int maxN = Math.max(originalWidth, originalHeight);
		int i = 2;
		while(i<maxN) i *= 2;
		if (i==maxN && originalWidth==originalHeight) {
			padded = false;
			return ip;
		}
		maxN = i;
		showStatus("Padding to "+ maxN + "x" + maxN);
		ImageStatistics stats = ImageStatistics.getStatistics(ip, MEAN, null);
		ImageProcessor ip2 = ip.createProcessor(maxN, maxN);
		ip2.setValue(stats.mean);
		ip2.fill();
		ip2.insert(ip, 0, 0);
		padded = true;
		Undo.reset();
		//new ImagePlus("padded", ip2.duplicate()).show();
		return ip2;
	}
	
	void showStatus(String msg) {
		if (stackSize>1)
			IJ.showStatus("FFT: " + slice+"/"+stackSize);
		else
			IJ.showStatus(msg);
	}
	
	void doMasking(FHT ip) {
		if (stackSize>1)
			return;
		float[] fht = (float[])ip.getPixels();
		ImageProcessor mask = imp.getProcessor();
		mask = mask.convertToByte(false);
		ImageStatistics stats = ImageStatistics.getStatistics(mask, MIN_MAX, null);
		if (stats.histogram[0]==0 && stats.histogram[255]==0)
			return;
		boolean passMode = stats.histogram[255]!=0;
		IJ.showStatus("Masking: "+(passMode?"pass":"filter"));
		mask = mask.duplicate();
		if (passMode)
			changeValues(mask, 0, 254, 0);
		else
			changeValues(mask, 1, 255, 255);
		for (int i=0; i<3; i++)
			mask.smooth();
		//imp.updateAndDraw();
		ip.swapQuadrants(mask);
		//new ImagePlus("mask", mask.duplicate()).show();
		byte[] maskPixels = (byte[])mask.getPixels();
		for (int i=0; i<fht.length; i++) {
			fht[i] = (float)(fht[i]*(maskPixels[i]&255)/255.0);
		}
		//FloatProcessor fht2 = new FloatProcessor(mask.getWidth(),mask.getHeight(),fht,null);
		//new ImagePlus("fht", fht2.duplicate()).show();
	}

	void changeValues(ImageProcessor ip, int v1, int v2, int v3) {
		byte[] pixels = (byte[])ip.getPixels();
		int v;
		//IJ.log(v1+" "+v2+" "+v3+" "+pixels.length);
		for (int i=0; i<pixels.length; i++) {
			v = pixels[i]&255;
			if (v>=v1 && v<=v2)
				pixels[i] = (byte)v3;
		}
	}
	
	public void redisplayPowerSpectrum() {
		FHT fht = (FHT)imp.getProperty("FHT");
		if (fht==null)
			{IJ.error("FFT", "Frequency domain image required"); return;}
		ImageProcessor ps = fht.getPowerSpectrum();
		imp.setProcessor(null, ps);
	}
	
	void showDialog() {
		GenericDialog gd = new GenericDialog("FFT Options");
		gd.addMessage("Display:");
		gd.addCheckbox("Raw 32-bit Power Spectrum", displayRawPS);
		gd.addCheckbox("Fast Hartley Transform", displayFHT);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		displayRawPS = gd.getNextBoolean();
		displayFHT = gd.getNextBoolean();
	}
	
}

