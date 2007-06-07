import ij.*;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import ij.gui.*;
import ij.measure.Calibration;
import java.awt.*;
import java.util.*;

/** Fourier Filter
To remove low- and high- frequency noise and horizontal or vertical stripes.
Written by Joachim Walter.
*/

/*
Uses the FHT from the plugin filter "FFT" by Wayne Rasband
and the method "autoAdjust()" from the ContrastAdjuster

There is a patent on the FHT. Read the note below!


Version 1.1	2001-10-29
contains removal of horizontal/vertical stripes (frequencies with
x=0/y=0). And a progress bar, which at least indicates that the
plugin is still running.


Version 1.2 2003-01-31
corrected a bug, which made the FFT_Filter unusable in plugins and macros 
(the input fields for large and small structures had the same values when 
the plugin was called from another plugin or a macro)

Can be called on each image in a stack.

The original image or ROI is now overwritten.

*/

/*
This file contains a Java language implementation of the 
Fast Hartley Transform algorithm which is covered under
United States Patent Number 4,646,256.

This code may therefore be freely used and distributed only
under the following conditions:

	1)  This header is included in every copy of the code; and
	2)  The code is used for noncommercial research purposes only.

 Firms using this code for commercial purposes may be infringing a United
 States patent and should contact the

	Office of Technology Licensing
	Stanford University
	857 Serra Street, 2nd Floor
	Stanford, CA   94305-6225
	(415) 723 0651

This implementation is based on Pascal
code contibuted by Arlo Reeves.
*/

/* Fourier Filter. */
public class FFT_Filter implements  PlugInFilter {

	private ImagePlus imp;
	private float[] C;
	private float[] S;
	//private float[] fht;

	static double filterLargeDia = 40.0;
	static double  filterSmallDia = 3.0;
	static int choiceIndex = 0;
	static String[] choices = {"none","horizontal","vertical"};
	static String choiceDia = choices[0];
	static double toleranceDia = 5.0;
	static boolean doScalingDia = true;
	static boolean saturateDia = true;
	
	static boolean processStack = false;
	

	public int setup(String arg, ImagePlus imp) {
		IJ.register(FFT_Filter.class);
 		this.imp = imp;
 		
 		int stackSize = imp.getStackSize();
 		
	/* 	dialog for input values		
		structure sizes are to be given as percentages of 
		the length of the longer edge of the ROI */
		GenericDialog dia = new GenericDialog("Fourier Filter 1.2", IJ.getInstance());
		dia.addNumericField("Filter_large structures down to x pixels", filterLargeDia, 1);
		dia.addNumericField("Filter_small structures up to x pixels", filterSmallDia, 1);
		dia.addChoice("Suppress stripes in one direction", choices, choiceDia);
		dia.addNumericField("Tolerance of direction (%)", toleranceDia, 1);
		dia.addCheckbox("Autoscale after filtering", doScalingDia);
		dia.addCheckbox("Saturate image when autoscaling", saturateDia);
		if (stackSize > 1) {
			dia.addCheckbox("Process entire Stack", processStack);	
		}
		dia.showDialog();
		
		if(dia.wasCanceled())
			return DONE;
		if(dia.invalidNumber()) {
			IJ.showMessage("Error", "Invalid input Number");
			return DONE;
		}		
		
		filterLargeDia = dia.getNextNumber();
		filterSmallDia = dia.getNextNumber();	
		choiceIndex = dia.getNextChoiceIndex();
		choiceDia = choices[choiceIndex];
		toleranceDia = dia.getNextNumber();
		doScalingDia = dia.getNextBoolean();
		saturateDia = dia.getNextBoolean();
		if (stackSize > 1) {
			processStack = dia.getNextBoolean();
		}
 		
 		int returnValue = DOES_8G | DOES_16 | DOES_32;
 		
 		if (stackSize > 1 && processStack) 
 			returnValue = returnValue | DOES_STACKS;
 			
		return returnValue;
	}


	public void run(ImageProcessor ip) {
		boolean inverse;
		int imagetype;
		
		Rectangle roiRect = ip.getRoi();		
		int maxN = Math.max(roiRect.width, roiRect.height);
		// scale filterLarge and filterSmall to fractions of image size
		double filterLarge = filterLargeDia / maxN;
		double filterSmall = filterSmallDia / maxN;
		double sharpness = (100.0 - toleranceDia) / 100.0;
		boolean doScaling = doScalingDia;
		boolean saturate = saturateDia;
		

		IJ.wait(31); // help the progress bar
		IJ.showProgress(0.05);

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
		ImageProcessor ip2 = tileMirror(ip, i, i, fitRect.x, fitRect.y);
						
		ImagePlus imp2 = new ImagePlus(imp.getTitle()+"-filtered", ip2);
		
		// convert to float (if necessary) for FHT
		imagetype = imp2.getType();
		if (imagetype != ImagePlus.GRAY32) {
			new ImageConverter(imp2).convertToGray32();
			ip2 = imp2.getProcessor();
			System.gc();
		}
		
		IJ.showProgress(0.1);
		
		// transform forward
		inverse = false;
		fft(ip2, inverse);
		System.gc();
//new ImagePlus("after fht",ip2.crop()).show();	

		IJ.showProgress(0.45);

		// filter out large and small structures
		filterLargeSmall(ip2, filterLarge, filterSmall, choiceIndex, sharpness);
new ImagePlus("filter",ip2.crop()).show();

		IJ.showProgress(0.55);
		
		// transform backward
		inverse = true;
		fft(ip2, inverse);
new ImagePlus("after inverse",ip2.crop()).show();		
		IJ.showProgress(0.95);
		
		// crop to original size and do scaling if selected
		ip2.setRoi(fitRect);
		ip2 = ip2.crop();
		imp2.setProcessor(null,ip2);
		if (doScaling)
			autoAdjust(imp2, ip2, saturate);

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
		ip.copyBits(imp2.getProcessor(),roiRect.x, roiRect.y, Blitter.COPY);
		imp2.flush();		
		System.gc();
		
		IJ.showProgress(1.1);
	}
	
	
	void autoAdjust(ImagePlus imp, ImageProcessor ip, boolean saturate) {
		/* Taken and adapted from Wayne Rasbands plugin "ContrastAdjuster"
			This is careful not to saturate (hmin-1 ; hmax+1)
			and does not handle rgb input in any way. */
		
		Calibration cal = imp.getCalibration();
		imp.setCalibration(null);
		ImageStatistics stats = imp.getStatistics(); // get uncalibrated stats
		imp.setCalibration(cal);
		int hmin, hmax;
		int threshold;
		int[] histogram = stats.histogram;		
		if (saturate)
			threshold = stats.pixelCount/5000;
		else
			threshold = 0;
		int i = -1;
		boolean found = false;
		do {
			i++;
			found = histogram[i] > threshold;
		} while (!found && i<255);
		if (i > 0)
			hmin = i-1;
		else
			hmin = 0;
				
		i = 256;
		do {
			i--;
			found = histogram[i] > threshold;
		} while (!found && i>0);
		if (i < 255)
			hmax = i+1;
		else
			hmax = 255;
				
		if (hmax>hmin) {
			imp.killRoi();
			double min = stats.histMin+hmin*stats.binSize;
			double max = stats.histMin+hmax*stats.binSize;
			ip.setMinAndMax(min, max);
		}
	}
	
	
	

/** Puts imageprocessor (ROI) into a new imageprocessor of size width x height y 
	at position (x,y).
	The image is mirrored around its edges to avoid wrap around effects of the
	FFT. */
	public ImageProcessor tileMirror(ImageProcessor ip, int width, int height, int x, int y) {

		

		
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
		
		
		
	
	
	void filterLargeSmall(ImageProcessor ip, double filterLarge, double filterSmall, int stripesHorVert, double scaleStripes) {
		/*
		filterLarge: down to which size are large structures suppressed?
		filterSmall: up to which size are small structures suppressed?
		filterLarge and filterSmall are given as fraction of the image size 
					in the original (untransformed) image.
		stripesHorVert: filter out: 0) nothing more  1) horizontal  2) vertical stripes
					(i.e. frequencies with x=0 / y=0)
		scaleStripes: width of the stripe filter, same unit as filterLarge
		*/
		
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
					break; // hor stripes
			case 2: fht[maxN/2] = 0;
					fht[rowmid] *=  (1 - factStripes);
					fht[maxN/2 + rowmid] *= (1 - factStripes);
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
					break;			
				case 1:
					factStripes = (float) Math.exp(- (col*col) * scaleStripes);
					fht[col] *= (1 - colFactLarge) * colFactSmall * (1 - factStripes);
					fht[backcol] *= (1 - colFactLarge) * colFactSmall * (1 - factStripes);
					fht[col+rowmid] *= (1 - colFactLarge*rowFactLarge) * colFactSmall*rowFactSmall * (1 - factStripes);
					fht[backcol+rowmid] *= (1 - colFactLarge*rowFactLarge) * colFactSmall*rowFactSmall * (1 - factStripes);
					break;
				case 2:
					factStripes = (float) Math.exp(- (maxN/2)*(maxN/2) * scaleStripes); 
					fht[col] = 0;
					fht[backcol] = 0;
					fht[col+rowmid] *= (1 - colFactLarge*rowFactLarge) * colFactSmall*rowFactSmall * (1 - factStripes);
					fht[backcol+rowmid] *= (1 - colFactLarge*rowFactLarge) * colFactSmall*rowFactSmall * (1 - factStripes);
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
					break;
				case 1:
					factStripes = (float) Math.exp(- (maxN/2)*(maxN/2) * scaleStripes);
					fht[row] = 0;
					fht[backrow] = 0;
					fht[row+maxN/2] *= (1 - rowFactLarge*colFactLarge) * rowFactSmall*colFactSmall * (1 - factStripes);
					fht[backrow+maxN/2] *= (1 - rowFactLarge*colFactLarge) * rowFactSmall*colFactSmall * (1 - factStripes);
					break;
				case 2:
					factStripes = (float) Math.exp(- (j*j) * scaleStripes);
					fht[row] *= (1 - rowFactLarge) * rowFactSmall * (1 - factStripes);
					fht[backrow] *= (1 - rowFactLarge) * rowFactSmall * (1 - factStripes);
					fht[row+maxN/2] *= (1 - rowFactLarge*colFactLarge) * rowFactSmall*colFactSmall * (1 - factStripes);
					fht[backrow+maxN/2] *= (1 - rowFactLarge*colFactLarge) * rowFactSmall*colFactSmall * (1 - factStripes);	
			}
		}
		ImageProcessor filterIP = new FloatProcessor(maxN, maxN, filter, null);
		swapQuadrants(filterIP);
		//new ImagePlus("Filter", filterIP).show();

	}	



/**	fft Performs an FHT (Fast Hartley Transform) despite its name.
	This file is based on a Java language implementation of the 
	Fast Hartley Transform algorithm which is covered under
	United States Patent Number 4,646,256. */
	public void fft(ImageProcessor ip, boolean inverse) {
		int maxN = ip.getWidth();
		makeSinCosTables(maxN);
		float[] fht = (float[])ip.getPixels();
	 	rc2DFHT(fht, inverse, maxN);

//	took out part about powerspectrum and amplitude
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
	
	/** Row-column Fast Hartley Transform */
	void rc2DFHT(float[] x, boolean inverse, int maxN) {
		//IJ.write("FFT: rc2DFHT (row-column Fast Hartley Transform)");
		for (int row=0; row<maxN; row++)
			dfht3(x, row*maxN, inverse, maxN);
		transposeR(x, maxN);
		for (int row=0; row<maxN; row++)		
			dfht3(x, row*maxN, inverse, maxN);
		transposeR(x, maxN);

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
		int    l;
		float[] tempArr = new float[maxN];
		for (int i=0; i<maxN; i++)  {
			l = BitRevX (i, bitlen);  //i=1, l=32767, bitlen=15
			tempArr[i] = x[base+l];
		}
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

	private int BitRevX (int  x, int bitlen) {
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
}

