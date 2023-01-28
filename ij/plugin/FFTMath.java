package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.text.*;
import ij.measure.Calibration;
import java.awt.*;
import java.io.*;

/** The class implements the Process/FFT/Math command. */
public class FFTMath implements PlugIn {

    private static final int CONJUGATE_MULTIPLY=0, MULTIPLY=1, DIVIDE=2;
    private static String[] ops = {"Correlate", "Convolve", "Deconvolve"};
    private static int index1;
    private static int index2;
    private static int operation = CONJUGATE_MULTIPLY;
    private static boolean doInverse = true;
    private static String title = "Result";
    private ImagePlus imp1, imp2;
            
    public void run(String arg) {
        if (showDialog())
            doMath(imp1, imp2);
    }
    
    public boolean showDialog() {
        int[] wList = WindowManager.getIDList();
        if (wList==null) {
            IJ.noImage();
            return false;
        }
        int nGoodImages = 0;
        for (int i=0; i<wList.length; i++) {
            ImagePlus imp = WindowManager.getImage(wList[i]);
            if (imp == null || imp.getWidth() != imp.getHeight() || !FHT.isPowerOf2(imp.getWidth()))
                wList[i] = 0;               //mark images that are not a power of 2
            else
                nGoodImages++;
        }
        if (nGoodImages == 0) {
        	IJ.error("FFT Math", "Images must be a power of 2 size (256x256, 512x512, etc.)");
        	return false;
        }
        int[] wList2 = new int[nGoodImages];
        String[] titles = new String[nGoodImages];
        for (int i=0, i2=0; i<wList.length; i++) {
            if (wList[i] == 0) continue;    //ignore this image, not power of 2
            wList2[i2] = wList[i];
            ImagePlus imp = WindowManager.getImage(wList2[i2]);
            if (imp!=null)
                titles[i2] = imp.getTitle();
            else
                titles[i2] = "";
            i2++;
        }
        if (index1>=wList2.length) index1 = 0;
        if (index2>=wList2.length) index2 = 0;
        if (WindowManager.getImage(title)!=null)
            title = WindowManager.getUniqueName(title);
        GenericDialog gd = new GenericDialog("FFT Math");
        gd.addChoice("Image1: ", titles, titles[index1]);
        gd.addChoice("Operation:", ops, ops[operation]);
        gd.addChoice("Image2: ", titles, titles[index2]);
        gd.addStringField("Result:", title);
        gd.addCheckbox("Do inverse transform", doInverse);
        gd.addHelp(IJ.URL2+"/docs/menus/process.html#fft-math");
        gd.showDialog();
        if (gd.wasCanceled())
            return false;
        index1 = gd.getNextChoiceIndex();
        operation = gd.getNextChoiceIndex();
        index2 = gd.getNextChoiceIndex();
        title = gd.getNextString();
        doInverse = gd.getNextBoolean();
        imp1 = WindowManager.getImage(wList2[index1]);
        imp2 = WindowManager.getImage(wList2[index2]);
        return true;
   }
    
    public void doMath(ImagePlus imp1, ImagePlus imp2) {
    	FHT h1, h2=null;
    	ImageProcessor fht1, fht2;
		fht1  = (ImageProcessor)imp1.getProperty("FHT");
		if (fht1!=null)
			h1 = new FHT(fht1);
		else {
			IJ.showStatus("Converting to float");
       		ImageProcessor ip1 = imp1.getProcessor();
       	 	h1 = new FHT(ip1);
       	}
		fht2  = (ImageProcessor)imp2.getProperty("FHT");
		if (fht2!=null)
			h2 = new FHT(fht2);
		else {
        	ImageProcessor ip2 = imp2.getProcessor();
        	if (imp2!=imp1)
       	 		h2 = new FHT(ip2);
       	}
        if (!h1.powerOf2Size()) {
        	IJ.error("FFT Math", "Images must be a power of 2 size (256x256, 512x512, etc.)");
        	return;
        }
        if (imp1.getWidth()!=imp2.getWidth()) {
        	IJ.error("FFT Math", "Images must be the same size");
        	return;
        }
		if (fht1==null) {
			IJ.showStatus("Transform image1");
			h1.transform();
		}
		if (fht2==null) {
			if (h2==null)
				h2 = new FHT(h1.duplicate());
				else {
					IJ.showStatus("Transform image2");
					h2.transform();
				}
		}
		FHT result=null;
		switch (operation) {
			case CONJUGATE_MULTIPLY: 
				IJ.showStatus("Complex conjugate multiply");
				result = h1.conjugateMultiply(h2); 
				break;
			case MULTIPLY: 
				IJ.showStatus("Fourier domain multiply");
				result = h1.multiply(h2); 
				break;
			case DIVIDE: 
				IJ.showStatus("Fourier domain divide");
				result = h1.divide(h2); 
				break;
		}
		ImagePlus imp3 = null;
		if (doInverse) {
			IJ.showStatus("Inverse transform");
			result.inverseTransform();
			IJ.showStatus("Swap quadrants");
			result.swapQuadrants();
			IJ.showStatus("Display image");
			result.resetMinAndMax();
			imp3 = new ImagePlus(title, result);
		} else {
			IJ.showStatus("Power spectrum");
			ImageProcessor ps = result.getPowerSpectrum();
			imp3 = new ImagePlus(title, ps.convertToFloat());
			result.quadrantSwapNeeded = true;
			imp3.setProperty("FHT", result);
		}
		Calibration cal1 = imp1.getCalibration();
		Calibration cal2 = imp2.getCalibration();
		Calibration cal3 = cal1.scaled() ? cal1 : cal2;
		if (cal1.scaled() && cal2.scaled() && !cal1.equals(cal2))
			cal3 = null;                //can't decide between different calibrations
		imp3.setCalibration(cal3);
		cal3 = imp3.getCalibration();   //imp3 has a copy, which we may modify
		cal3.disableDensityCalibration();
		imp3.show();
		IJ.showProgress(1.0);
    }

}
