package ij.process;

import java.awt.*;
import java.awt.image.*;
import ij.*;

/** Converts an RGB image to 8-bit index color using Heckbert's median-cut
    color quantization algorithm. Based on median.c by Anton Kruger from the
    September, 1994 issue of Dr. Dobbs Journal.

    2023-11-26 Modifictions by Michael Schmid:
    Calculates the average color values of the 8x8x8 bins, thus the color in the
    output is the exact average of the pixel colors (not the weighted average
    of the bin origins). In addition, bins are assigned to the closest available
    color, which is not necessarily that from the original median cut segmentation
    (refinement).
    This reduces the occurrence of large deviations from the orignal.
    In most cases, colors that occur very often are exactly perserved.
*/

public class MedianCut {

	static final int MAXCOLORS = 256;	// maximum # of output colors (max 256)
	static final int HSIZE = 32768;		// size of image histogram
	private int[] hist;					// RGB histogram and reverse color lookup table
	private int[] histPtr;				// points to colors in "hist"
	private byte[] inverseMap;			// color table entry for each histogram bin
	private long[] sumOffsetR, sumOffsetG, sumOffsetB; // if there is only one color in the bin, the color
	private Cube[] list;				// list of cubes
	private int[] pixels32;				// input pixels in 3x8-bit rgb format (alpha, if present, is ignored)
	private int width, height;
	private IndexColorModel cm;

	public MedianCut(int[] pixels, int width, int height) {
		int color16;

		pixels32 = pixels;
		this.width = width;
		this.height = height;
		
		//build 32x32x32 RGB histogram
		IJ.showProgress(0.3);
		IJ.showStatus("Building 32x32x32 RGB histogram");
		hist = new int[HSIZE];
		sumOffsetR = new long[HSIZE];
		sumOffsetG = new long[HSIZE];
		sumOffsetB = new long[HSIZE];
		for (int i=0; i<width*height; i++) {
			int c = pixels32[i];
			int r = (c&0xff0000)>>16;
			int g = (c&0x00ff00)>>8;
			int b = (c&0x0000ff);
			int bgr15 = bgr15(r, g, b);
			hist[bgr15]++;
			sumOffsetR[bgr15] += r - ((r>>3)<<3);		//sum differences between value and origin of 8x8x8 bin
			sumOffsetG[bgr15] += g - ((g>>3)<<3);
			sumOffsetB[bgr15] += b - ((b>>3)<<3);
		}
	}
	
	public MedianCut(ColorProcessor ip) {
		this((int[])ip.getPixels(), ip.getWidth(), ip.getHeight());
	}
	
	int getColorCount() {
		int count = 0;
		for (int i=0; i<HSIZE; i++)
			if (hist[i]>0) count++;
		return count;
	}

	Color getModalColor() {
		int max=0;
		int c = 0;
		for (int i=0; i<HSIZE; i++)
			if (hist[i]>max) {
				max = hist[i];
				c = i;
			}
		return new Color(red(c), green(c), blue(c));
	}

	/** Converts form 3*8-bit color to 15-bit color. Do NOT call with byte arguments,
	 *  these would be converted to negative ints for values >=128. */
	private final static int bgr15(int r, int g, int b) {
		int r5 = r>>3, g5 = g>>3, b5 = b>>3;	//5-bit color values
		return (b5<<10) | (g5<<5) | r5;			//15-bit color, index in histogram
	}

	/** Converts from 24-bit color to 15-bit color, which is used as index in histrogram */
	private final static int bgr15(int c) {
		int r = (c&0xf80000)>>19;
		int g = (c&0xf800)>>6;
		int b = (c&0xf8)<<7;
		return b | g | r;
	}

	// Get red component of a 15-bit color
	private final int red(int x) {
		return (x&31)<<3;
	}

	// Get green component of a 15-bit color
	private final int green(int x) {
		return (x>>2)&0xf8;
	}

	// Get blue component of a 15-bit color
	private final int blue(int x) {
		return (x>>7)&0xf8;
	}

	/** Uses Heckbert's median-cut algorithm to divide the color space defined by
	"hist" into "maxcubes" cubes. The centroids (average value) of each cube
	are are used to create a color table. "hist" is then updated to function
	as an inverse color map that is used to generate an 8-bit image. */
	public Image convert(int maxcubes) {
		ImageProcessor ip = convertToByte(maxcubes);
		return ip.createImage();
	}

	/** This is a version of convert that returns a ByteProcessor. */
	public ImageProcessor convertToByte(int maxcubes) {
		int lr, lg, lb;
		int i, median, color;
		int count;
		int k, level, ncubes, splitpos;
		int num, width;
		int longdim=0;	//longest dimension of cube
		Cube cube, cubeA, cubeB;
		
		// Create initial cube
		IJ.showStatus("Median cut");
		list = new Cube[MAXCOLORS];
		histPtr = new int[HSIZE];
		ncubes = 0;
		cube = new Cube();
		for (i=0,color=0; i<=HSIZE-1; i++) {
			if (hist[i] != 0) {
				histPtr[color++] = i;
				cube.count = cube.count + hist[i];
			}
		}
		cube.lower = 0; cube.upper = color-1;
		cube.level = 0;
		Shrink(cube);
		list[ncubes++] = cube;

		//Main loop
		while (ncubes < maxcubes) { 

			// Search the list of cubes for next cube to split, the lowest level cube
			level = 255; splitpos = -1; 
			for (k=0; k<=ncubes-1; k++) {
				if (list[k].lower == list[k].upper)  
					;	// single color; cannot be split
				else if (list[k].level < level) {
					level = list[k].level;
					splitpos = k;
				}
			}
			if (splitpos == -1)	// no more cubes to split
				break;

			// Find longest dimension of this cube
			cube = list[splitpos];
			lr = cube.rmax - cube.rmin;
			lg = cube.gmax - cube.gmin;
			lb = cube.bmax - cube.bmin;
			if (lr >= lg && lr >= lb) longdim = 0;
			if (lg >= lr && lg >= lb) longdim = 1;
			if (lb >= lr && lb >= lg) longdim = 2;
			
			// Sort along "longdim"
			reorderColors(histPtr, cube.lower, cube.upper, longdim);
			quickSort(histPtr, cube.lower, cube.upper);
			restoreColorOrder(histPtr, cube.lower, cube.upper, longdim);

			// Find median
			count = 0;
			for (i=cube.lower;i<=cube.upper-1;i++) {
				if (count >= cube.count/2) break;
				color = histPtr[i];
				count = count + hist[color];
			}
			median = i;

			// Now split "cube" at the median and add the two new
			// cubes to the list of cubes.
			cubeA = new Cube();
			cubeA.lower = cube.lower; 
			cubeA.upper = median-1;
			cubeA.count = count;
			cubeA.level = cube.level + 1;
			Shrink(cubeA);
			list[splitpos] = cubeA;				// add in old slot

			cubeB = new Cube();
			cubeB.lower = median; 
			cubeB.upper = cube.upper; 
			cubeB.count = cube.count - count;
			cubeB.level = cube.level + 1;
			Shrink(cubeB);
			list[ncubes++] = cubeB;				// add in new slot */
			if (ncubes%15==0)
				IJ.showProgress(0.3 + (0.6*ncubes)/maxcubes);
		}

		// We have enough cubes, or we have split all we can. Now
		// compute the color map, the inverse color map, and return
		// an 8-bit image.
		IJ.showProgress(0.9);
		makeInverseMap(ncubes);
		IJ.showProgress(0.95);
		return makeImage();
	}
	
	void Shrink(Cube cube) {
	// Encloses "cube" with a tight-fitting cube by updating the
	// (rmin,gmin,bmin) and (rmax,gmax,bmax) members of "cube".

		int r, g, b;
		int color;
		int rmin, rmax, gmin, gmax, bmin, bmax;

		rmin = 255; rmax = 0;
		gmin = 255; gmax = 0;
		bmin = 255; bmax = 0;
		for (int i=cube.lower; i<=cube.upper; i++) {
			color = histPtr[i];
			r = red(color);
			g = green(color);
			b = blue(color);
			if (r > rmax) rmax = r;
			if (r < rmin) rmin = r;
			if (g > gmax) gmax = g;
			if (g < gmin) gmin = g;
			if (b > bmax) bmax = b;
			if (b < bmin) bmin = b;
		}
		cube.rmin = rmin; cube.rmax = rmax;
		cube.gmin = gmin; cube.gmax = gmax;
		cube.bmin = bmin; cube.bmax = bmax;
	}

	void makeInverseMap(int ncubes) {
	// For each cube in the list of cubes, computes the centroid
	// (average value) of the colors enclosed by that cube, and
	// then loads the centroids in the color map. Next loads
	// "inverseMap" with indices into the color map
		byte[] rLUT = new byte[256];
		byte[] gLUT = new byte[256];
		byte[] bLUT = new byte[256];
		inverseMap = new byte[HSIZE];		//for each histogram bin, index of the LUT entry
		IJ.showStatus("Making inverse map");
		createInverseMap(ncubes, rLUT, gLUT, bLUT);
		for (int i=0; i<5; i++) {	//up to five attampts to refine the mapping
			boolean changes = improveInverseMap(ncubes, rLUT, gLUT, bLUT);
			if (!changes) break;
		}
		cm = new IndexColorModel(8, ncubes, rLUT, gLUT, bLUT);
	}

	/** Based on the median cut result, i.e., a segmentation of all histogram bins
	 *  in 'ncubes' Cube objects with different colors, assigns colors to the
	 *  bins (the 'inverseMap' and sets the colors according to the centroid
	 *  of all points in the 'Cube'. */
	void createInverseMap(int ncubes, byte[] rLUT, byte[] gLUT, byte[] bLUT) {
		for (int k=0; k<=ncubes-1; k++) {
			long rsum=0, gsum=0, bsum=0;
			Cube cube = list[k];
			int singleColor = -2;
			for (int i=cube.lower; i<=cube.upper; i++) {
				int color15 = histPtr[i];
				int r = red(color15);
				rsum += r*hist[color15] + sumOffsetR[color15];
				int g = green(color15);
				gsum += g*hist[color15] + sumOffsetG[color15];
				int b = blue(color15);
				bsum += b*hist[color15] + sumOffsetB[color15];
			}

			// Color is the centoid of all colors in the cube
			rLUT[k] = (byte)Math.round(rsum/(double)cube.count);
			gLUT[k] = (byte)Math.round(gsum/(double)cube.count);
			bLUT[k] = (byte)Math.round(bsum/(double)cube.count);
		}
		
		// For each histogram bin in each cube, write the corresponding LUT index
		// into the inverseMap.
		for (int k=0; k<=ncubes-1; k++) {
			Cube cube = list[k];
			for (int i=cube.lower; i<=cube.upper; i++) {
				int color15 = histPtr[i];
				inverseMap[color15] = (byte)k;
			}
		}
	}

	/** Modifies the inverse map in case there is a better match for a histogram bin
	 *  centroid than the one from the previous round. If so, returns true for 'changes done'.
	 *  If it turns out that at least one of the colors in the LUT is unused after
	 *  this refinement, replaces that color(s) with the color(s) of the bin centroid(s)
	 *  that are represented worst by the colors in the LUT.
	 *  Note that 'ncubes' is the number of entries in the color LUT */
	boolean improveInverseMap(int ncubes, byte[] rLUT, byte[] gLUT, byte[] bLUT) {
		boolean changes = false;
		boolean[] colorChanged = new boolean[ncubes];
		double[]  distanceSqr  = new double[HSIZE];			//distance (squared) to nearest color
		for (int i=0; i<hist.length; i++) {
			if (hist[i] != 0) {
				int nearestColor = -1;
				double minDistanceSqr = Double.MAX_VALUE;
				double rBin = red(i)  + sumOffsetR[i]/hist[i];
				double gBin = green(i)+ sumOffsetG[i]/hist[i];
				double bBin = blue(i) + sumOffsetB[i]/hist[i];
				for (int k=0; k<ncubes; k++) {
					if (i == bgr15(rLUT[k]&0xff, gLUT[k]&0xff, bLUT[k]&0xff)) { //this color bin is already in the table
						minDistanceSqr = 0;
						nearestColor = k;
						inverseMap[i] = (byte)k;			//usually the case, just to make sure
						break;
					}
					double dSqr = getDistanceSqr(rLUT[k]&0xff, gLUT[k]&0xff, bLUT[k]&0xff, rBin, gBin, bBin);
					if (dSqr < minDistanceSqr) {
						minDistanceSqr = dSqr;
						nearestColor = k;
					}
				}
				distanceSqr[i] = minDistanceSqr;
				if (nearestColor != (inverseMap[i]&0xff)) {	//there is a better color table entry for this bin
					changes = true;
					colorChanged[nearestColor] = true;		//remember to recalculate centroid for these colors
					colorChanged[inverseMap[i]&0xff] = true;
					inverseMap[i] = (byte)nearestColor;
				}
			}
		}
		if (changes) {
			boolean[] unused = new boolean[ncubes];
			long[] rsum = new long[ncubes];
			long[] gsum = new long[ncubes];
			long[] bsum = new long[ncubes];
			int[] nPxl =  new int[ncubes];
			for (int i=0; i<hist.length; i++) {
				int k = inverseMap[i]&0xff;			//LUT index
				if (colorChanged[k]) {
					rsum[k] += red(i)*hist[i]   + sumOffsetR[i];
					gsum[k] += green(i)*hist[i] + sumOffsetG[i];
					bsum[k] += blue(i)*hist[i]  + sumOffsetB[i];
					nPxl[k] += hist[i];
				}
			}
			for (int k=0; k<=ncubes-1; k++) {
				if (colorChanged[k]) {
					if (nPxl[k] > 0) {
						int r = (int)Math.round(rsum[k]/(double)nPxl[k]);
						int g = (int)Math.round(gsum[k]/(double)nPxl[k]);
						int b = (int)Math.round(bsum[k]/(double)nPxl[k]);
						rLUT[k] = (byte)r;
						gLUT[k] = (byte)g;
						bLUT[k] = (byte)b;
					} else
						unused[k] = true;
				}
			}
			for (int k=0; k<=ncubes-1; k++) {	//for unused LUT entries (usually one at most), find a new color
				if (unused[k]) {
					double worst = 0;			//worst distance^2 to existing color * number of pixels in bin
					int iOfWorst = -1;
					for (int i=0; i<hist.length; i++) {
						double badness = distanceSqr[i]*hist[i];
						if (badness > worst) {
							worst = badness;
							iOfWorst = i;
						}
					}
					setColorToBinCentroid(iOfWorst, rLUT, gLUT, bLUT, k);
					inverseMap[iOfWorst] = (byte)k;
				}
			}
		}
		return changes;
	}

	/** Transfers the color of the centroid of bin 'histIndex' to the lutIndex-th lut entry */
	void setColorToBinCentroid(int histIndex, byte[] rLUT, byte[] gLUT, byte[] bLUT, int lutIndex) {
		rLUT[lutIndex] = (byte)Math.round(red(histIndex)   + sumOffsetR[histIndex]*(1./hist[histIndex]));
		gLUT[lutIndex] = (byte)Math.round(green(histIndex) + sumOffsetG[histIndex]*(1./hist[histIndex]));
		bLUT[lutIndex] = (byte)Math.round(blue(histIndex)  + sumOffsetB[histIndex]*(1./hist[histIndex]));
	}

	/** Distance (squared) between two color values */
	static double getDistanceSqr(double r1, double g1, double b1, double r2, double g2, double b2) {
		return (r2-r1)*(r2-r1) + (g2-g1)*(g2-g1) + (b2-b1)*(b2-b1);
	}

	void reorderColors(int[] a, int lo, int hi, int longDim) {
	// Change the ordering of the 5-bit colors in each word of int[]
	// so we can sort on the 'longDim' color
	
		int c, r, g, b;
		switch (longDim) {
			case 0: //red
				for (int i=lo; i<=hi; i++) {
					c = a[i];
					r = c & 31;
					a[i] = (r<<10) | (c>>5);
					}
				break;
			case 1: //green
				for (int i=lo; i<=hi; i++) {
					c = a[i];
					r = c & 31;
					g = (c>>5) & 31;
					b = c>>10;
					a[i] = (g<<10) | (b<<5) | r;
					}
				break;
			case 2: //blue; already in the needed order
				break;
		}
	}
	

	void restoreColorOrder(int[] a, int lo, int hi, int longDim) {
	// Restore the 5-bit colors to the original order
	
		int c, r, g, b;
		switch (longDim){
			case 0: //red
				for (int i=lo; i<=hi; i++) {
					c = a[i];
					r = c >> 10;
					a[i] = ((c&1023)<<5) | r;
				}
				break;
			case 1: //green
				for (int i=lo; i<=hi; i++) {
					c = a[i];
					r = c & 31;
					g = c>>10;
					b = (c>>5) & 31;
					a[i] = (b<<10) | (g<<5) | r;
				}
				break;
			case 2: //blue
				break;
		}
	}
	
	
	void quickSort(int a[], int lo0, int hi0) {
   // Based on the QuickSort method by James Gosling from Sun's SortDemo applet
   
      int lo = lo0;
      int hi = hi0;
      int mid, t;

      if ( hi0 > lo0) {
         mid = a[ ( lo0 + hi0 ) / 2 ];
         while( lo <= hi ) {
            while( ( lo < hi0 ) && ( a[lo] < mid ) )
               ++lo;
            while( ( hi > lo0 ) && ( a[hi] > mid ) )
               --hi;
            if( lo <= hi ) {
		      t = a[lo]; 
		      a[lo] = a[hi];
		      a[hi] = t;
               ++lo;
               --hi;
            }
         }
         if( lo0 < hi )
            quickSort( a, lo0, hi );
         if( lo < hi0 )
            quickSort( a, lo, hi0 );
      }
   }

	/** Generates an 8-bit image with the indexColorModel created in makeInverseMap */
	ImageProcessor makeImage() {
	// Generate 8-bit image
	
		Image img8;
		byte[] pixels8;
		int color16;
		
		IJ.showStatus("Creating 8-bit image");
	    pixels8 = new byte[width*height];
	    for (int i=0; i<width*height; i++) {
	    	color16 = bgr15(pixels32[i]);
	    	pixels8[i] = (byte)inverseMap[color16];
	    }
	    ImageProcessor ip = new ByteProcessor(width, height, pixels8, cm);
        IJ.showProgress(1.0);
		return ip;
	}

} //class MedianCut


class Cube {			// structure for a cube in color space
	int  lower;			// one corner's index in histogram pointer
	int  upper;			// another corner's index in histogram pointer
	int  count;			// cube's histogram count
	int  level;			// cube's level
	int  rmin, rmax;
	int  gmin, gmax;
	int  bmin, bmax;
	
	Cube() {
		count = 0;
	}   

	public String toString() {
		String s = "lower=" + lower + " upper=" + upper;
		s = s + " count=" + count + " level=" + level;
		s = s + " rmin=" + rmin + " rmax=" + rmax;
		s = s + " gmin=" + gmin + " gmax=" + gmax;
		s = s + " bmin=" + bmin + " bmax=" + bmax;
		return s;
	}
	
}

