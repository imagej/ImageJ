package ij.plugin.filter;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;
import java.awt.*;
import java.util.*;


/** 
This class implements the commands in the Process/FFT submenu. It is based on Arlso Reeves'  
Pascal implementation of the Fast Hartley Transform from NIH Image 
(http://rsb.info.nih.gov/ij/docs/ImageFFT/). The Band-pass command is based 
on the "FFT Filter" plugin by Joachim Walter (http://rsb.info.nih.gov/ij/plugins/fft-filter.html). 
The Fast Hartley Transform was restricted by U.S. Patent No. 4,646,256, but was placed 
in the public domain by Stanford University in 1995 and is now freely available.
*/
public class FFT implements  PlugInFilter, Measurements {

	private ImagePlus imp;
	private String arg;
	private float[] C;
	private float[] S;
	private int[] bitrev;
	float[] tempArr;
	private boolean customFilter, bandpassFilter;
	private static int filterIndex = 1;
	private ImageProcessor fht;
	private int slice;
	private int stackSize = 1;	
	
	private static double filterLargeDia = 40.0;
	private static double  filterSmallDia = 3.0;
	private static int choiceIndex = 0;
	private static String[] choices = {"None","Horizontal","Vertical"};
	private static String choiceDia = choices[0];
	private static double toleranceDia = 5.0;
	private static boolean doScalingDia = true;
	private static boolean saturateDia = true;
	private static boolean displayFilter;
	private static boolean processStack;

	public int setup(String arg, ImagePlus imp) {
		this.arg = arg;
 		this.imp = imp;
 		if (imp==null)
 			{IJ.noImage(); return DONE;}
 		stackSize = imp.getStackSize();
		fht  = (ImageProcessor)imp.getProperty("FHT");
		if (arg.equals("bandpass"))
			bandpassFilter = true;
		else if (arg.equals("custom"))
			customFilter = true;
		int flags = DOES_8G+DOES_16+DOES_32;
		if (bandpassFilter) {
			if (fht!=null) {
				IJ.showMessage("FFT", "Spatial domain image required");
				return DONE;
			}
			if (!showBandpassDialog(imp))
				return DONE;
			return processStack?flags+DOES_STACKS:flags;
		} else if (arg.equals("redisplay"))
 			{redisplayPowerSpectrum(); return DONE;}
		return flags+NO_CHANGES+NO_UNDO;
	}

	public void run(ImageProcessor ip) {
		slice++;
		if (bandpassFilter) {
			filter(ip);
			return;
		}
		boolean inverse;
		if (fht==null) {
			if (arg.equals("inverse")||customFilter) {
				IJ.showMessage("FFT", "Frequency domain image required"); 
				return;
			}
			if (!powerOf2Size(ip)) {
				IJ.error("A square, power of two size image or selection\n(128x128, 256x256, etc.) is required.");
				return;
			}
		}
		//if (customFilter && fht==null) {
		//	doTransform(ip, false);
		//	fht  = (ImageProcessor)imp.getProperty("FHT");
		//}
		if (fht!=null) {
			ip = fht;
			imp.killRoi();
			Undo.setup(Undo.TRANSFORM, imp);
			inverse = true;
			if (customFilter) {
				if (!customFilter(fht))
					return;
			}
		} else {
			if (imp.getRoi()==null)
				Undo.setup(Undo.TRANSFORM, imp);
			inverse = false;
		}
		doTransform(ip, inverse);
	}
	
	void doTransform(ImageProcessor ip, boolean inverse) {
		IJ.showProgress(0.01);
		ip = ip.crop();
		IJ.showProgress(0.1);
		ip = ip.convertToFloat();
		IJ.showProgress(0.2);
		fft(ip, inverse);
		String title = imp.getTitle();
		if (title.startsWith("FFT of "))
			imp.setTitle(title.substring(6));
		if (!inverse && imp.getRoi()==null)
			imp.setTitle("FFT of "+imp.getTitle());
		IJ.showProgress(1.0);
	}
	
	// From FFT_Filter plugin by Joachim Walter
	void filter(ImageProcessor ip) {
		Rectangle roiRect = ip.getRoi();		
		int maxN = Math.max(roiRect.width, roiRect.height);
		// scale filterLarge and filterSmall to fractions of image size
		double filterLarge = filterLargeDia / maxN;
		double filterSmall = filterSmallDia / maxN;
		double sharpness = (100.0 - toleranceDia) / 100.0;
		boolean doScaling = doScalingDia;
		boolean saturate = saturateDia;
		
		IJ.showProgress(1,20);

		/* 	tile mirrored image to power of 2 size		
			first determine smallest power 2 >= 1.5 * image width/height
		  	factor of 1.5 to avoid wrap-around effects of Fourier Trafo */

		int i=2;
		while(i<1.5 * maxN) i *= 2;		
		
		// fit image into power of 2 size 
		Rectangle fitRect = new Rectangle();
		fitRect.x = (int) Math.round( (i - roiRect.width) / 2.0 );
		fitRect.y = (int) Math.round( (i - roiRect.height) / 2.0 );
		fitRect.width = roiRect.width;
		fitRect.height = roiRect.height;
		
		// put image (ROI) into power 2 size image
		// mirroring to avoid wrap around effects
		showStatus("Pad to "+i+"x"+i);
		ImageProcessor ip2 = tileMirror(ip, i, i, fitRect.x, fitRect.y);						
		ImagePlus imp2 = new ImagePlus(imp.getTitle()+"-filtered", ip2);
		
		// convert to float (if necessary) for FHT
		int imagetype = imp2.getType();
		if (imagetype != ImagePlus.GRAY32) {
			new ImageConverter(imp2).convertToGray32();
			ip2 = imp2.getProcessor();
			System.gc();
		}		
		IJ.showProgress(2,20);
		
		// transform forward
		showStatus("Forward transform");
		boolean inverse = false;
		fft2(ip2, inverse);
		System.gc();
		IJ.showProgress(9,20);
		//new ImagePlus("after fht",ip2.crop()).show();	

		// filter out large and small structures
		showStatus("Filter in frequency domain");
		filterLargeSmall(ip2, filterLarge, filterSmall, choiceIndex, sharpness);
		//new ImagePlus("filter",ip2.crop()).show();
		IJ.showProgress(11,20);

		// transform backward
		showStatus("Inverse transform");
		inverse = true;
		fft2(ip2, inverse);
		IJ.showProgress(19,20);
		//new ImagePlus("after inverse",ip2).show();	
		
		// crop to original size and do scaling if selected
		showStatus("Crop and convert to original type");
		ip2.setRoi(fitRect);
		ip2 = ip2.crop();
		imp2.setProcessor(null,ip2);
		if (doScaling)
			new ContrastEnhancer().stretchHistogram(imp2, ip2, saturate?1.0:0.0);

		// convert back to original data type
		if (!(imagetype == ImagePlus.GRAY32)) {
			//handle scaling of image when converting back (or not)
			boolean defaultscaling = ImageConverter.getDoScaling();
			ImageConverter.setDoScaling(doScaling);
			// convert back
			if (imagetype == ImagePlus.GRAY8)
				new ImageConverter(imp2).convertToGray8();
			else if (imagetype == ImagePlus.GRAY16)
				new ImageConverter(imp2).convertToGray16();
			// set scaling back to original value
			ImageConverter.setDoScaling(defaultscaling);
		}

		// copy filtered image back into original image and flush temp image
		ip.snapshot();
		ip.copyBits(imp2.getProcessor(),roiRect.x, roiRect.y, Blitter.COPY);
		imp2.flush();
		System.gc();		
		IJ.showProgress(20,20);
	}
	
	void showStatus(String msg) {
		if (slice>1)
			IJ.showStatus(slice+"/"+stackSize);
		else
			IJ.showStatus(msg);
	}

	public void fft2(ImageProcessor ip, boolean inverse) {
		int maxN = ip.getWidth();
		makeSinCosTables(maxN);
		makeBitReverseTable(maxN);
		tempArr = new float[maxN];
		float[] fht = (float[])ip.getPixels();
	 	rc2DFHT(fht, inverse, maxN);
	}
	
	/** Puts imageprocessor (ROI) into a new imageprocessor of size width x height y at position (x,y).
	The image is mirrored around its edges to avoid wrap around effects of the FFT. */
	ImageProcessor tileMirror(ImageProcessor ip, int width, int height, int x, int y) {
			
		if (x < 0 || x > (width -1) || y < 0 || y > (height -1)) {
			IJ.error("Image to be tiled is out of bounds.");
			return null;
		}
		
		ImageProcessor ipout = ip.createProcessor(width, height);
		
		ImageProcessor ip2 = ip.crop();
		int w2 = ip2.getWidth();
		int h2 = ip2.getHeight();
				
		//how many times does ip2 fit into ipout?
		int i1 = (int) Math.ceil(x / (double) w2);
		int i2 = (int) Math.ceil( (width - x) / (double) w2);
		int j1 = (int) Math.ceil(y / (double) h2);
		int j2 = (int) Math.ceil( (height - y) / (double) h2);		

		//tile		
		if ( (i1%2) > 0.5)
			ip2.flipHorizontal();
		if ( (j1%2) > 0.5)
			ip2.flipVertical();
					
		for (int i=-i1; i<i2; i += 2) {
			for (int j=-j1; j<j2; j += 2) {
				ipout.insert(ip2, x-i*w2, y-j*h2);
			}
		}
		
		ip2.flipHorizontal();
		for (int i=-i1+1; i<i2; i += 2) {
			for (int j=-j1; j<j2; j += 2) {
				ipout.insert(ip2, x-i*w2, y-j*h2);
			}
		}
		
		ip2.flipVertical();
		for (int i=-i1+1; i<i2; i += 2) {
			for (int j=-j1+1; j<j2; j += 2) {
				ipout.insert(ip2, x-i*w2, y-j*h2);
			}
		}
		
		ip2.flipHorizontal();
		for (int i=-i1; i<i2; i += 2) {
			for (int j=-j1+1; j<j2; j += 2) {
				ipout.insert(ip2, x-i*w2, y-j*h2);
			}
		}
		
		return ipout;
	}		
	

	/*
	filterLarge: down to which size are large structures suppressed?
	filterSmall: up to which size are small structures suppressed?
	filterLarge and filterSmall are given as fraction of the image size 
				in the original (untransformed) image.
	stripesHorVert: filter out: 0) nothing more  1) horizontal  2) vertical stripes
				(i.e. frequencies with x=0 / y=0)
	scaleStripes: width of the stripe filter, same unit as filterLarge
	*/
	void filterLargeSmall(ImageProcessor ip, double filterLarge, double filterSmall, int stripesHorVert, double scaleStripes) {
		
		int maxN = ip.getWidth();
			
		float[] fht = (float[])ip.getPixels();
		float[] filter = new float[maxN*maxN];
		for (int i=0; i<maxN*maxN; i++)
			filter[i]=1f;		

		int row;
		int backrow;
		float rowFactLarge;
		float rowFactSmall;
		
		int col;
		int backcol;
		float factor;
		float colFactLarge;
		float colFactSmall;
		
		float factStripes;
		
		// calculate factor in exponent of Gaussian from filterLarge / filterSmall

		double scaleLarge = filterLarge*filterLarge;
		double scaleSmall = filterSmall*filterSmall;
		scaleStripes = scaleStripes*scaleStripes;
		//float FactStripes;

		// loop over rows
		for (int j=1; j<maxN/2; j++) {
			row = j * maxN;
			backrow = (maxN-j)*maxN;
			rowFactLarge = (float) Math.exp(-(j*j) * scaleLarge);
			rowFactSmall = (float) Math.exp(-(j*j) * scaleSmall);
			

			// loop over columns
			for (col=1; col<maxN/2; col++){
				backcol = maxN-col;
				colFactLarge = (float) Math.exp(- (col*col) * scaleLarge);
				colFactSmall = (float) Math.exp(- (col*col) * scaleSmall);
				factor = (1 - rowFactLarge*colFactLarge) * rowFactSmall*colFactSmall;
				switch (stripesHorVert) {
					case 1: factor *= (1 - (float) Math.exp(- (col*col) * scaleStripes)); break;// hor stripes
					case 2: factor *= (1 - (float) Math.exp(- (j*j) * scaleStripes)); // vert stripes
				}
				
				fht[col+row] *= factor;
				fht[col+backrow] *= factor;
				fht[backcol+row] *= factor;
				fht[backcol+backrow] *= factor;
				filter[col+row] *= factor;
				filter[col+backrow] *= factor;
				filter[backcol+row] *= factor;
				filter[backcol+backrow] *= factor;
			}
		}

		//process meeting points (maxN/2,0) , (0,maxN/2), and (maxN/2,maxN/2)
		int rowmid = maxN * (maxN/2);
		rowFactLarge = (float) Math.exp(- (maxN/2)*(maxN/2) * scaleLarge);
		rowFactSmall = (float) Math.exp(- (maxN/2)*(maxN/2) * scaleSmall);	
		factStripes = (float) Math.exp(- (maxN/2)*(maxN/2) * scaleStripes);
		
		fht[maxN/2] *= (1 - rowFactLarge) * rowFactSmall; // (maxN/2,0)
		fht[rowmid] *= (1 - rowFactLarge) * rowFactSmall; // (0,maxN/2)
		fht[maxN/2 + rowmid] *= (1 - rowFactLarge*rowFactLarge) * rowFactSmall*rowFactSmall; // (maxN/2,maxN/2)
		filter[maxN/2] *= (1 - rowFactLarge) * rowFactSmall; // (maxN/2,0)
		filter[rowmid] *= (1 - rowFactLarge) * rowFactSmall; // (0,maxN/2)
		filter[maxN/2 + rowmid] *= (1 - rowFactLarge*rowFactLarge) * rowFactSmall*rowFactSmall; // (maxN/2,maxN/2)

		switch (stripesHorVert) {
			case 1: fht[maxN/2] *= (1 - factStripes);
					fht[rowmid] = 0;
					fht[maxN/2 + rowmid] *= (1 - factStripes);
					filter[maxN/2] *= (1 - factStripes);
					filter[rowmid] = 0;
					filter[maxN/2 + rowmid] *= (1 - factStripes);
					break; // hor stripes
			case 2: fht[maxN/2] = 0;
					fht[rowmid] *=  (1 - factStripes);
					fht[maxN/2 + rowmid] *= (1 - factStripes);
					filter[maxN/2] = 0;
					filter[rowmid] *=  (1 - factStripes);
					filter[maxN/2 + rowmid] *= (1 - factStripes);
					break; // vert stripes
		}		
		
		//loop along row 0 and maxN/2	
		rowFactLarge = (float) Math.exp(- (maxN/2)*(maxN/2) * scaleLarge);
		rowFactSmall = (float) Math.exp(- (maxN/2)*(maxN/2) * scaleSmall);			
		for (col=1; col<maxN/2; col++){
			backcol = maxN-col;
			colFactLarge = (float) Math.exp(- (col*col) * scaleLarge);
			colFactSmall = (float) Math.exp(- (col*col) * scaleSmall);
			
			switch (stripesHorVert) {
				case 0:
					fht[col] *= (1 - colFactLarge) * colFactSmall;
					fht[backcol] *= (1 - colFactLarge) * colFactSmall;
					fht[col+rowmid] *= (1 - colFactLarge*rowFactLarge) * colFactSmall*rowFactSmall;
					fht[backcol+rowmid] *= (1 - colFactLarge*rowFactLarge) * colFactSmall*rowFactSmall;
					filter[col] *= (1 - colFactLarge) * colFactSmall;
					filter[backcol] *= (1 - colFactLarge) * colFactSmall;
					filter[col+rowmid] *= (1 - colFactLarge*rowFactLarge) * colFactSmall*rowFactSmall;
					filter[backcol+rowmid] *= (1 - colFactLarge*rowFactLarge) * colFactSmall*rowFactSmall;	
					break;			
				case 1:
					factStripes = (float) Math.exp(- (col*col) * scaleStripes);
					fht[col] *= (1 - colFactLarge) * colFactSmall * (1 - factStripes);
					fht[backcol] *= (1 - colFactLarge) * colFactSmall * (1 - factStripes);
					fht[col+rowmid] *= (1 - colFactLarge*rowFactLarge) * colFactSmall*rowFactSmall * (1 - factStripes);
					fht[backcol+rowmid] *= (1 - colFactLarge*rowFactLarge) * colFactSmall*rowFactSmall * (1 - factStripes);
					filter[col] *= (1 - colFactLarge) * colFactSmall * (1 - factStripes);
					filter[backcol] *= (1 - colFactLarge) * colFactSmall * (1 - factStripes);
					filter[col+rowmid] *= (1 - colFactLarge*rowFactLarge) * colFactSmall*rowFactSmall * (1 - factStripes);
					filter[backcol+rowmid] *= (1 - colFactLarge*rowFactLarge) * colFactSmall*rowFactSmall * (1 - factStripes);
					break;
				case 2:
					factStripes = (float) Math.exp(- (maxN/2)*(maxN/2) * scaleStripes); 
					fht[col] = 0;
					fht[backcol] = 0;
					fht[col+rowmid] *= (1 - colFactLarge*rowFactLarge) * colFactSmall*rowFactSmall * (1 - factStripes);
					fht[backcol+rowmid] *= (1 - colFactLarge*rowFactLarge) * colFactSmall*rowFactSmall * (1 - factStripes);
					filter[col] = 0;
					filter[backcol] = 0;
					filter[col+rowmid] *= (1 - colFactLarge*rowFactLarge) * colFactSmall*rowFactSmall * (1 - factStripes);
					filter[backcol+rowmid] *= (1 - colFactLarge*rowFactLarge) * colFactSmall*rowFactSmall * (1 - factStripes);
			}
		}
		
		// loop along column 0 and maxN/2
		colFactLarge = (float) Math.exp(- (maxN/2)*(maxN/2) * scaleLarge);
		colFactSmall = (float) Math.exp(- (maxN/2)*(maxN/2) * scaleSmall);
		for (int j=1; j<maxN/2; j++) {
			row = j * maxN;
			backrow = (maxN-j)*maxN;
			rowFactLarge = (float) Math.exp(- (j*j) * scaleLarge);
			rowFactSmall = (float) Math.exp(- (j*j) * scaleSmall);

			switch (stripesHorVert) {
				case 0:
					fht[row] *= (1 - rowFactLarge) * rowFactSmall;
					fht[backrow] *= (1 - rowFactLarge) * rowFactSmall;
					fht[row+maxN/2] *= (1 - rowFactLarge*colFactLarge) * rowFactSmall*colFactSmall;
					fht[backrow+maxN/2] *= (1 - rowFactLarge*colFactLarge) * rowFactSmall*colFactSmall;
					filter[row] *= (1 - rowFactLarge) * rowFactSmall;
					filter[backrow] *= (1 - rowFactLarge) * rowFactSmall;
					filter[row+maxN/2] *= (1 - rowFactLarge*colFactLarge) * rowFactSmall*colFactSmall;
					filter[backrow+maxN/2] *= (1 - rowFactLarge*colFactLarge) * rowFactSmall*colFactSmall;
					break;
				case 1:
					factStripes = (float) Math.exp(- (maxN/2)*(maxN/2) * scaleStripes);
					fht[row] = 0;
					fht[backrow] = 0;
					fht[row+maxN/2] *= (1 - rowFactLarge*colFactLarge) * rowFactSmall*colFactSmall * (1 - factStripes);
					fht[backrow+maxN/2] *= (1 - rowFactLarge*colFactLarge) * rowFactSmall*colFactSmall * (1 - factStripes);
					filter[row] = 0;
					filter[backrow] = 0;
					filter[row+maxN/2] *= (1 - rowFactLarge*colFactLarge) * rowFactSmall*colFactSmall * (1 - factStripes);
					filter[backrow+maxN/2] *= (1 - rowFactLarge*colFactLarge) * rowFactSmall*colFactSmall * (1 - factStripes);
					break;
				case 2:
					factStripes = (float) Math.exp(- (j*j) * scaleStripes);
					fht[row] *= (1 - rowFactLarge) * rowFactSmall * (1 - factStripes);
					fht[backrow] *= (1 - rowFactLarge) * rowFactSmall * (1 - factStripes);
					fht[row+maxN/2] *= (1 - rowFactLarge*colFactLarge) * rowFactSmall*colFactSmall * (1 - factStripes);
					fht[backrow+maxN/2] *= (1 - rowFactLarge*colFactLarge) * rowFactSmall*colFactSmall * (1 - factStripes);
					filter[row] *= (1 - rowFactLarge) * rowFactSmall * (1 - factStripes);
					filter[backrow] *= (1 - rowFactLarge) * rowFactSmall * (1 - factStripes);
					filter[row+maxN/2] *= (1 - rowFactLarge*colFactLarge) * rowFactSmall*colFactSmall * (1 - factStripes);
					filter[backrow+maxN/2] *= (1 - rowFactLarge*colFactLarge) * rowFactSmall*colFactSmall * (1 - factStripes);	
			}
		}
		if (displayFilter && stackSize==1) {
			ImageProcessor filterIP = new FloatProcessor(maxN, maxN, filter, null);
			swapQuadrants(filterIP);
			new ImagePlus("Filter", filterIP).show();
		}
	}	

	boolean showBandpassDialog(ImagePlus imp) {
		GenericDialog gd = new GenericDialog("FFT Bandpass Filter");
		gd.addNumericField("Filter_Large Structures Down to", filterLargeDia, 0, 4, "pixels");
		gd.addNumericField("Filter_Small Structures Up to", filterSmallDia, 0, 4, "pixels");
		gd.addChoice("Suppress Stripes:", choices, choiceDia);
		gd.addNumericField("Tolerance of Direction:", toleranceDia, 0, 2, "%");
		gd.addCheckbox("Autoscale After Filtering", doScalingDia);
		gd.addCheckbox("Saturate Image when Autoscaling", saturateDia);
		gd.addCheckbox("Display Filter", displayFilter);
		if (stackSize>1)
			gd.addCheckbox("Process Entire Stack", processStack);	
		gd.showDialog();
		if(gd.wasCanceled())
			return false;
		if(gd.invalidNumber()) {
			IJ.showMessage("Error", "Invalid input number");
			return false;
		}				
		filterLargeDia = gd.getNextNumber();
		filterSmallDia = gd.getNextNumber();	
		choiceIndex = gd.getNextChoiceIndex();
		choiceDia = choices[choiceIndex];
		toleranceDia = gd.getNextNumber();
		doScalingDia = gd.getNextBoolean();
		saturateDia = gd.getNextBoolean();
		displayFilter = gd.getNextBoolean();
		if (stackSize>1)
			processStack = gd.getNextBoolean();
		return true;
	}

	public boolean powerOf2Size(ImageProcessor ip) {
		Rectangle r = ip.getRoi();
		return powerOf2(r.width) && r.width==r.height;
	}

	boolean powerOf2(int n) {		
		int i=2;
		while(i<n) i *= 2;
		return i==n;
	}

	public void fft(ImageProcessor ip, boolean inverse) {
		//IJ.write("fft: "+inverse);
		//new ImagePlus("Input", ip.crop()).show();
		int maxN = ip.getWidth();
		makeSinCosTables(maxN);
		makeBitReverseTable(maxN);
		tempArr = new float[maxN];
		float[] fht = (float[])ip.getPixels();
		if (inverse) doMasking(fht);
	 	rc2DFHT(fht, inverse, maxN);
		if (inverse) {
			ip.resetMinAndMax();
			if (!bandpassFilter)
				imp.setProcessor(null, ip);
			if (imp.getProperty("FHT")!=null)
				imp.getProperties().remove("FHT");
		} else {
			ImageProcessor ps = calculatePowerSpectrum(fht, maxN);
			ImagePlus imp2 = imp;
			if (imp.getRoi()!=null) {
				imp2 = new ImagePlus("FFT", ps);
				imp2.show();
			} else
				imp2.setProcessor(null, ps);
			imp2.setProperty("FHT", ip);
			if (IJ.altKeyDown()) {
				ImageProcessor amp = calculateAmplitude(fht, maxN);
				new ImagePlus("Amplitude", amp).show();
			}
		}
	}

	void makeSinCosTables(int maxN) {
		int n = maxN/4;
		C = new float[n];
		S = new float[n];
		double theta = 0.0;
		double dTheta = 2.0 * Math.PI/maxN;
		for (int i=0; i<n; i++) {
			C[i] = (float)Math.cos(theta);
			S[i] = (float)Math.sin(theta);
			theta += dTheta;
		}
	}
	
	void makeBitReverseTable(int maxN) {
		bitrev = new int[maxN];
		int nLog2 = log2(maxN);
		for (int i=0; i<maxN; i++)
			bitrev[i] = bitRevX(i, nLog2);
	}

	/** Row-column Fast Hartley Transform */
	void rc2DFHT(float[] x, boolean inverse, int maxN) {
		//IJ.write("FFT: rc2DFHT (row-column Fast Hartley Transform)");
		for (int row=0; row<maxN; row++)
			dfht3(x, row*maxN, inverse, maxN);		
		progress(0.4);
		transposeR(x, maxN);
		progress(0.5);
		for (int row=0; row<maxN; row++)		
			dfht3(x, row*maxN, inverse, maxN);
		progress(0.7);
		transposeR(x, maxN);
		progress(0.8);

		int mRow, mCol;
		float A,B,C,D,E;
		for (int row=0; row<maxN/2; row++) { // Now calculate actual Hartley transform
			for (int col=0; col<maxN/2; col++) {
				mRow = (maxN - row) % maxN;
				mCol = (maxN - col)  % maxN;
				A = x[row * maxN + col];	//  see Bracewell, 'Fast 2D Hartley Transf.' IEEE Procs. 9/86
				B = x[mRow * maxN + col];
				C = x[row * maxN + mCol];
				D = x[mRow * maxN + mCol];
				E = ((A + D) - (B + C)) / 2;
				x[row * maxN + col] = A - E;
				x[mRow * maxN + col] = B + E;
				x[row * maxN + mCol] = C + E;
				x[mRow * maxN + mCol] = D - E;
			}
		}
		progress(0.95);
	}
	
	void progress(double percent) {
		if (!bandpassFilter)
			IJ.showProgress(percent);
	}
	
	/* An optimized real FHT */
	void dfht3 (float[] x, int base, boolean inverse, int maxN) {
		int i, stage, gpNum, gpIndex, gpSize, numGps, Nlog2;
		int bfNum, numBfs;
		int Ad0, Ad1, Ad2, Ad3, Ad4, CSAd;
		float rt1, rt2, rt3, rt4;

		Nlog2 = log2(maxN);
		BitRevRArr(x, base, Nlog2, maxN);	//bitReverse the input array
		gpSize = 2;     //first & second stages - do radix 4 butterflies once thru
		numGps = maxN / 4;
		for (gpNum=0; gpNum<numGps; gpNum++)  {
			Ad1 = gpNum * 4;
			Ad2 = Ad1 + 1;
			Ad3 = Ad1 + gpSize;
			Ad4 = Ad2 + gpSize;
			rt1 = x[base+Ad1] + x[base+Ad2];   // a + b
			rt2 = x[base+Ad1] - x[base+Ad2];   // a - b
			rt3 = x[base+Ad3] + x[base+Ad4];   // c + d
			rt4 = x[base+Ad3] - x[base+Ad4];   // c - d
			x[base+Ad1] = rt1 + rt3;      // a + b + (c + d)
			x[base+Ad2] = rt2 + rt4;      // a - b + (c - d)
			x[base+Ad3] = rt1 - rt3;      // a + b - (c + d)
			x[base+Ad4] = rt2 - rt4;      // a - b - (c - d)
		 }

		if (Nlog2 > 2) {
			 // third + stages computed here
			gpSize = 4;
			numBfs = 2;
			numGps = numGps / 2;
			//IJ.write("FFT: dfht3 "+Nlog2+" "+numGps+" "+numBfs);
			for (stage=2; stage<Nlog2; stage++) {
				for (gpNum=0; gpNum<numGps; gpNum++) {
					Ad0 = gpNum * gpSize * 2;
					Ad1 = Ad0;     // 1st butterfly is different from others - no mults needed
					Ad2 = Ad1 + gpSize;
					Ad3 = Ad1 + gpSize / 2;
					Ad4 = Ad3 + gpSize;
					rt1 = x[base+Ad1];
					x[base+Ad1] = x[base+Ad1] + x[base+Ad2];
					x[base+Ad2] = rt1 - x[base+Ad2];
					rt1 = x[base+Ad3];
					x[base+Ad3] = x[base+Ad3] + x[base+Ad4];
					x[base+Ad4] = rt1 - x[base+Ad4];
					for (bfNum=1; bfNum<numBfs; bfNum++) {
					// subsequent BF's dealt with together
						Ad1 = bfNum + Ad0;
						Ad2 = Ad1 + gpSize;
						Ad3 = gpSize - bfNum + Ad0;
						Ad4 = Ad3 + gpSize;

						CSAd = bfNum * numGps;
						rt1 = x[base+Ad2] * C[CSAd] + x[base+Ad4] * S[CSAd];
						rt2 = x[base+Ad4] * C[CSAd] - x[base+Ad2] * S[CSAd];

						x[base+Ad2] = x[base+Ad1] - rt1;
						x[base+Ad1] = x[base+Ad1] + rt1;
						x[base+Ad4] = x[base+Ad3] + rt2;
						x[base+Ad3] = x[base+Ad3] - rt2;

					} /* end bfNum loop */
				} /* end gpNum loop */
				gpSize *= 2;
				numBfs *= 2;
				numGps = numGps / 2;
			} /* end for all stages */
		} /* end if Nlog2 > 2 */

		if (inverse)  {
			for (i=0; i<maxN; i++)
			x[base+i] = x[base+i] / maxN;
		}
	}

	void transposeR (float[] x, int maxN) {
		int   r, c;
		float  rTemp;

		for (r=0; r<maxN; r++)  {
			for (c=r; c<maxN; c++) {
				if (r != c)  {
					rTemp = x[r*maxN + c];
					x[r*maxN + c] = x[c*maxN + r];
					x[c*maxN + r] = rTemp;
				}
			}
		}
	}
	
	int log2 (int x) {
		int count = 15;
		while (!btst(x, count))
			count--;
		return count;
	}

	
	private boolean btst (int  x, int bit) {
		//int mask = 1;
		return ((x & (1<<bit)) != 0);
	}

	void BitRevRArr (float[] x, int base, int bitlen, int maxN) {
		for (int i=0; i<maxN; i++)
			tempArr[i] = x[base+bitrev[i]];
		for (int i=0; i<maxN; i++)
			x[base+i] = tempArr[i];
	}

	//private int BitRevX (int  x, int bitlen) {
	//	int  temp = 0;
	//	for (int i=0; i<=bitlen; i++)
	//		if (btst (x, i))
	//			temp = bset(temp, bitlen-i-1);
	//	return temp & 0x0000ffff;
	//}

	private int bitRevX (int  x, int bitlen) {
		int  temp = 0;
		for (int i=0; i<=bitlen; i++)
			if ((x & (1<<i)) !=0)
				temp  |= (1<<(bitlen-i-1));
		return temp & 0x0000ffff;
	}

	private int bset (int x, int bit) {
		x |= (1<<bit);
		return x;
	}

	ImageProcessor calculatePowerSpectrum (float[] fht, int maxN) {
		int base;
		float  r, scale;
		float min = Float.MAX_VALUE;
  		float max = Float.MIN_VALUE;
   		float[] fps = new float[maxN*maxN];
 		byte[] ps = new byte[maxN*maxN];

  		for (int row=0; row<maxN; row++) {
			FHTps(row, maxN, fht, fps);
			base = row * maxN;
			for (int col=0; col<maxN; col++) {
				r = fps[base+col];
				if (r<min) min = r;
				if (r>max) max = r;
			}
		}

		if (min<1.0)
			min = 0f;
		else
			min = (float)Math.log(min);
		max = (float)Math.log(max);
		scale = (float)(253.0/(max-min));

		for (int row=0; row<maxN; row++) {
			base = row*maxN;
			for (int col=0; col<maxN; col++) {
				r = fps[base+col];
				if (r<1f)
					r = 0f;
				else
					r = (float)Math.log(r);
				ps[base+col] = (byte)(((r-min)*scale+0.5)+1);
			}
		}
		ImageProcessor ip = new ByteProcessor(maxN, maxN, ps, null);
		swapQuadrants(ip);
		return ip;
	}

	/** Power Spectrum of one row from 2D Hartley Transform. */
 	void FHTps(int row, int maxN, float[] fht, float[] ps) {
 		int base = row*maxN;
		int l;
		for (int c=0; c<maxN; c++) {
			l = ((maxN-row)%maxN) * maxN + (maxN-c)%maxN;
			ps[base+c] = (sqr(fht[base+c]) + sqr(fht[l]))/2f;
 		}
	}

	ImageProcessor calculateAmplitude(float[] fht, int maxN) {
   		float[] amp = new float[maxN*maxN];
   		for (int row=0; row<maxN; row++) {
			amplitude(row, maxN, fht, amp);
		}
		ImageProcessor ip = new FloatProcessor(maxN, maxN, amp, null);
		swapQuadrants(ip);
		return ip;
	}

	/** Amplitude of one row from 2D Hartley Transform. */
 	void amplitude(int row, int maxN, float[] fht, float[] amplitude) {
 		int base = row*maxN;
		int l;
		for (int c=0; c<maxN; c++) {
			l = ((maxN-row)%maxN) * maxN + (maxN-c)%maxN;
			amplitude[base+c] = (float)Math.sqrt(sqr(fht[base+c]) + sqr(fht[l]));
 		}
	}

	float sqr(float x) {
		return x*x;
	}

	/**	Swap quadrants 1 and 3 and quadrants 2 and 4 so the power 
		spectrum origin is at the center of the image.
		2 1
		3 4
	*/
 	public void swapQuadrants (ImageProcessor ip) {
 		ImageProcessor t1, t2;
		int size = ip.getWidth()/2;
		ip.setRoi(size,0,size,size);
		t1 = ip.crop();
  		ip.setRoi(0,size,size,size);
		t2 = ip.crop();
		ip.insert(t1,0,size);
		ip.insert(t2,size,0);
		ip.setRoi(0,0,size,size);
		t1 = ip.crop();
  		ip.setRoi(size,size,size,size);
		t2 = ip.crop();
		ip.insert(t1,size,size);
		ip.insert(t2,0,0);
	}

	void doMasking(float[] fht) {
		ImageProcessor mask = imp.getProcessor();
		ImageStatistics stats = ImageStatistics.getStatistics(mask, MIN_MAX, null);
		if (stats.histogram[0]==0 && stats.histogram[255]==0)
			return;
		mask = mask.duplicate();
		boolean passMode = stats.histogram[255]!=0;
		if (passMode)
			changeValues(mask, 0, 254, 0);
		else
			changeValues(mask, 1, 255, 255);
		for (int i=0; i<3; i++)
			mask.smooth();
		imp.updateAndDraw();
		swapQuadrants(mask);
		//new ImagePlus("mask", mask.duplicate()).show();
		byte[] pixels = (byte[])mask.getPixels();
		for (int i=0; i<pixels.length; i++) {
			fht[i] = (float)(fht[i]*(pixels[i]&255)/255.0);
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
		if (imp==null)
			{IJ.noImage(); return;}
		ImageProcessor fht  = (ImageProcessor)imp.getProperty("FHT");
		if (fht==null)
			{IJ.showMessage("FFT", "Frequency domain image required"); return;}
		float[] pixels = (float[])fht.getPixels();
		ImageProcessor ps = calculatePowerSpectrum(pixels, fht.getWidth());
		imp.setProcessor(null, ps);
	}
	
	boolean customFilter(ImageProcessor fht) {
		int size = fht.getWidth();
		ImageProcessor filter = getFilter(size);
		if (filter==null)
			return false;
		swapQuadrants(filter);
		float[] fhtPixels = (float[])fht.getPixels();
		byte[] filterPixels = (byte[])filter.getPixels();
		for (int i=0; i<fhtPixels.length; i++)
			fhtPixels[i] = (float)(fhtPixels[i]*(filterPixels[i]&255)/255.0);
		swapQuadrants(filter);
		return true;
	}
	
	ImageProcessor getFilter(int size) {
		int[] wList = WindowManager.getIDList();
		if (wList==null || wList.length<2) {
			IJ.showMessage("FFT", "Filter (as an open image) required.");
			return null;
		}
		String[] titles = new String[wList.length];
		for (int i=0; i<wList.length; i++) {
			ImagePlus imp = WindowManager.getImage(wList[i]);
			titles[i] = imp!=null?imp.getTitle():"";
		}
		if (filterIndex<0 || filterIndex>=titles.length)
			filterIndex = 1;
		GenericDialog gd = new GenericDialog("FFT Filter");
		gd.addChoice("Frequency Domain Filter:", titles, titles[filterIndex]);
		gd.showDialog();
		if (gd.wasCanceled())
			return null;
		filterIndex = gd.getNextChoiceIndex();
		ImageProcessor filter = WindowManager.getImage(wList[filterIndex]).getProcessor();		
		if (filter.getWidth()!=size || filter.getHeight()!=size) {
			IJ.showMessage("FFT", "Filter must be a " + size + "x" + size);
			filter = null;
		}	
		return filter.convertToByte(true);
	}

}

