package ij.process;
import ij.*;
import ij.plugin.FFT;
import ij.plugin.ContrastEnhancer;
import java.awt.image.ColorModel; 

/**
This class contains a Java implementation of the Fast Hartley
Transform. It is based on Pascal code in NIH Image contributed 
by Arlo Reeves (http://imagej.nih.gov/ij/docs/ImageFFT/).
The Fast Hartley Transform was restricted by U.S. Patent No. 4,646,256, 
but was placed in the public domain by Stanford University in 1995 
and is now freely available.
*/
public class FHT extends FloatProcessor {
	private boolean isFrequencyDomain;
	private int maxN;
	private float[] C;
	private float[] S;
	private int[] bitrev;
	private float[] tempArr;
	private boolean showProgress;

	
	/** Used by the FFT class. */
	public boolean quadrantSwapNeeded;
	/** Used by the FFT class. */
	public ColorProcessor rgb;
	/** Used by the FFT class. */
	public int originalWidth;
	/** Used by the FFT class. */
	public int originalHeight;
	/** Used by the FFT class. */
	public int originalBitDepth;
	/** Used by the FFT class. */
	public ColorModel originalColorModel;
	/** Used by the FFT class. */
	public double powerSpectrumMean;

	/** Constructs a FHT object from an ImageProcessor. Byte, short and RGB images 
		are converted to float. Float images are duplicated. */
	public FHT(ImageProcessor ip) {
		this(ip, false);
	}

	public FHT(ImageProcessor ip, boolean isFrequencyDomain) {
		super(ip.getWidth(), ip.getHeight(), (float[])((ip instanceof FloatProcessor)?ip.duplicate().getPixels():ip.convertToFloat().getPixels()), null);
		this.isFrequencyDomain = isFrequencyDomain;
		maxN = getWidth();
		resetRoi();
	}

	public FHT() {
		super(8,8); //create dummy FloatProcessor
	}

	/** Returns true of this FHT contains a square image with a width that is a power of two. */
	public boolean powerOf2Size() {
		int i=2;
		while(i<width) i *= 2;
		return i==width && width==height;
	}

	/** Performs a forward transform, converting this image into the frequency domain. 
		The image contained in this FHT must be square and its width must be a power of 2. */
	public void transform() {
		transform(false);
	}

	/** Performs an inverse transform, converting this image into the space domain. 
		The image contained in this FHT must be square and its width must be a power of 2. */
	public void inverseTransform() {
		transform(true);
	}
	
	public static int NO_WINDOW=0, HAMMING=1, HANN=2, FLATTOP=3; // fourier1D window function types

	/** Calculates the Fourier amplitudes of an array, based on a 1D Fast Hartley Transform.
	* With no Window function, if the array size is a power of 2, the input function
	* should be either periodic or the data at the beginning and end of the array should
	* approach the same value (the periodic continuation should be smooth).
	* With no Window function, if the array size is not a power of 2, the
	* data should decay towards 0 at the beginning and end of the array.
	* For data that do not fulfill these conditions, a window function can be used to
	* avoid artifacts from the edges. See http://en.wikipedia.org/wiki/Window_function.
	*
	* Supported window functions: Hamming, Hann ("raised cosine"), flat-top. Flat-top
	* refers to the HFT70 function in the report cited below, it is named for its
	* response in the frequency domain: a single-frequency sinewave becomes a peak with
	* a short plateau of 3 roughly equal Fourier amplitudes. It is optimized for
	* measuring amplitudes of signals with well-separated sharp frequencies.
	* All window functions will reduce the frequency resolution; this is especially
	* pronounced for the flat-top window.
	*
	* Normalization is done such that the peak height in the Fourier transform
	* (roughly) corresponds to the RMS amplitude of a sinewave (i.e., amplitude/sqrt(2)),
	* and the first Fourier amplitude corresponds to DC component (average value of
	* the data). If the sine frequency falls between two discrete frequencies of the
	* Fourier transform, peak heights can deviate from the true RMS amplitude by up to
	* approx. 36, 18, 15, and 0.1% for no window function, Hamming, Hann and flat-top
	* window functions, respectively.
	* When calculating the power spectrum from the square of the output, note that the
	* result is quantitative only if the input array size is a power of 2; then the
	* spectral density of the power spectrum must be divided by 1.3628 for the Hamming,
	* 1.5 for the Hann, and 3.4129 for the flat-top window.
	*
	* For more details about window functions, see:
	* G. Heinzel, A. Rdiger, and R. Schilling
	* Spectrum and spectral density estimation by the discrete Fourier transform (DFT),
	* including a comprehensive list of window functions and some new flat-top windows.
	* Technical Report, MPI f. Gravitationsphysik, Hannover, 2002; http://edoc.mpg.de/395068
	*
	* @param data Input array; its size need not be a power of 2. The input is not modified..
	* @param windowType may be NO_WINDOW, then the input array is used as it is.
	* Otherwise, it is multiplied by a window function, which can be HAMMING, HANN or
	* FLATTOP.
	* @return Array with the result, i.e., the RMS amplitudes for each frequency.
	* The output array size is half the size of the 2^n-sized array used for the FHT;
	* array element [0]corresponds to frequency zero (the "DC component"). The first
	* nonexisting array element, result[result.length] would correspond to a frequency
	* of 1 cycle per 2 input points, i.e., the Nyquist frequency. In other words, if
	* the spacing of the input data points is dx, results[i] corresponds to a frequency
	* of i/(2*results.length*dx).
	*/
	public float[] fourier1D(float[] data, int windowType) {
		int n = data.length;
		int size = 2;
		while (size<n) size *= 2; // find power of 2 where the data fit
		float[] y = new float[size]; // leave the original data untouched, work on a copy
		System.arraycopy(data, 0, y, 0, n); // pad to 2^n-size
		double sum = 0;
		if (windowType != NO_WINDOW) {
			for (int x=0; x<n; x++) { //calculate non-normalized window function
				double z = (x + 0.5) * (2 * Math.PI / n);
				double w = 0;
				if (windowType == HAMMING)
					w = 0.54 - 0.46 * Math.cos(z);
				else if (windowType == HANN)
					w = 1. - Math.cos(z);
				else if (windowType == FLATTOP)
					w = 1. - 1.90796 * Math.cos(z) + 1.07349 * Math.cos(2*z) - 0.18199 * Math.cos(3*z);
				else
					throw new IllegalArgumentException("Invalid Fourier Window Type");
				y[x] *= w;
				sum += w;
			}
		} else
			sum = n;
		for (int x=0; x<n; x++) //normalize
			y[x] *= (1./sum);
		transform1D(y); //transform
		float[] result = new float[size/2];
		result[0] = (float)Math.sqrt(y[0]*y[0]);
		for (int x=1; x<size/2; x++)
			result[x] = (float)Math.sqrt(y[x]*y[x]+y[size-x]*y[size-x]);
		return result;
	}

	/** Performs an optimized 1D Fast Hartley Transform (FHT) of an array.
	 *  Array size must be a power of 2.
	 *  Note that all amplitudes in the output 'x' are multiplied by the array length.
	 *  Therefore, to get the power spectrum, for 1 <=i < N/2, use
	 *  ps[i] = (x[i]*x[i]+x[maxN-i]*x[maxN-i])/(maxN*maxN), where maxN is the array length.
	 *  To get the real part of the complex FFT, for i=0 use x[0]/maxN,
	 *  and for i>0, use (x[i]+x[maxN-i])/(2*maxN).
	 *  The imaginary part of the complex FFT, with i>0, is given by (x[i]-x[maxN-i])/(2*maxN)
	 *  The coefficients of cosine and sine are like the real and imaginary values above,
	 *  but you have to divide by maxN instead of 2*maxN.
	 */
	public void transform1D(float[] x) {
		int n = x.length;
		if (S==null || n!=maxN) {
			if (!isPowerOf2(n))
				throw new IllegalArgumentException("Not power of 2 length: "+n);
			initializeTables(n);
		}
		dfht3(x, 0, false, n);
	}

    /** Performs an inverse 1D Fast Hartley Transform (FHT) of an array */
	public void inverseTransform1D(float[] fht) {
		int n = fht.length;
		if (S==null || n!=maxN) {
			if (!isPowerOf2(n))
				throw new IllegalArgumentException("Not power of 2 length: "+n);
			initializeTables(n);
		}
		dfht3(fht, 0, true, n);
	}

	void transform(boolean inverse) {
		if (!powerOf2Size())
			throw new  IllegalArgumentException("Image not power of 2 size or not square: "+width+"x"+height);
		setShowProgress(true);
		maxN = width;
		if (S==null)
			initializeTables(maxN);
		float[] fht = (float[])getPixels();
	 	rc2DFHT(fht, inverse, maxN);
		isFrequencyDomain = !inverse;
	}
	
	void initializeTables(int maxN) {
	    if (maxN>0x40000000)
	        throw new  IllegalArgumentException("Too large for FHT:  "+maxN+" >2^30");
		makeSinCosTables(maxN);
		makeBitReverseTable(maxN);
		tempArr = new float[maxN];
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

	/** Performs a 2D FHT (Fast Hartley Transform). */
	public void rc2DFHT(float[] x, boolean inverse, int maxN) {
		if (S==null) initializeTables(maxN);
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
		for (int row=0; row<=maxN/2; row++) { // Now calculate actual Hartley transform
			for (int col=0; col<=maxN/2; col++) {
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
		if (showProgress)
			IJ.showProgress(percent);
	}
	
	/** Performs an optimized 1D FHT of an array or part of an array.
	 *  @param x        Input array; will be overwritten by the output in the range given by base and maxN.
	 *  @param base     First index from where data of the input array should be read.
	 *  @param inverse  True for inverse transform.
	 *  @param maxN     Length of data that should be transformed; this must be always
	 *                  the same for a given FHT object.
	 *  Note that all amplitudes in the output 'x' are multiplied by maxN.
	 */
	public void dfht3(float[] x, int base, boolean inverse, int maxN) {
		int i, stage, gpNum, gpIndex, gpSize, numGps, Nlog2;
		int bfNum, numBfs;
		int Ad0, Ad1, Ad2, Ad3, Ad4, CSAd;
		float rt1, rt2, rt3, rt4;

		if (S==null) initializeTables(maxN);
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
		int count = 31;
		while (!btst(x, count))
			count--;
		return count;
	}

	
	private boolean btst (int  x, int bit) {
		return ((x & (1<<bit)) != 0);
	}

	void BitRevRArr (float[] x, int base, int bitlen, int maxN) {
		for (int i=0; i<maxN; i++)
			tempArr[i] = x[base+bitrev[i]];
		for (int i=0; i<maxN; i++)
			x[base+i] = tempArr[i];
	}

	private int bitRevX (int  x, int bitlen) {
		int  temp = 0;
		for (int i=0; i<=bitlen; i++)
			if ((x & (1<<i)) !=0)
				temp  |= (1<<(bitlen-i-1));
		return temp;
	}

	private int bset (int x, int bit) {
		x |= (1<<bit);
		return x;
	}

	/** Returns an 8-bit power spectrum, log-scaled to 1-254. The image in this
		FHT is assumed to be in the frequency domain. */
	public ImageProcessor getPowerSpectrum () {
		if (!isFrequencyDomain)
			throw new  IllegalArgumentException("Frequency domain image required");
		int base;
		float  r, scale;
		float min = Float.MAX_VALUE;
  		float max = Float.MIN_VALUE;
   		float[] fps = new float[maxN*maxN];
 		byte[] ps = new byte[maxN*maxN];
		float[] fht = (float[])getPixels();

  		for (int row=0; row<maxN; row++) {
			fht2ps(row, maxN, fht, fps);
			base = row * maxN;
			for (int col=0; col<maxN; col++) {
				r = fps[base+col];
				if (r<min)
					min = r;
				if (r>max)
					max = r;
			}
		}

		max = (float)Math.log(max);
		min = (float)Math.log(min);
		if (Float.isNaN(min) || max-min>50)
			min = max - 50; //display range not more than approx e^50
		scale = (float)(253.999/(max-min));
		
		//long t0 = System.currentTimeMillis();
		for (int row=0; row<maxN; row++) {
			base = row*maxN;
			for (int col=0; col<maxN; col++) {
				r = fps[base+col];
				r = ((float)Math.log(r)-min)*scale;
				if (Float.isNaN(r) || r<0)
					r = 0f;
				ps[base+col] = (byte)(r+1f); // 1 is min value
			}
		}
		//long t1 = System.currentTimeMillis();
		//IJ.log(""+(t1-t0));
		ImageProcessor ip = new ByteProcessor(maxN, maxN, ps);
		swapQuadrants(ip);
		return ip;
	}
	
	/** Returns the unscaled 32-bit power spectrum. */
	public FloatProcessor getRawPowerSpectrum() {
		if (!isFrequencyDomain)
			throw new  IllegalArgumentException("Frequency domain image required");
   		float[] fps = new float[maxN*maxN];
		float[] fht = (float[])getPixels();
  		for (int row=0; row<maxN; row++)
			fht2ps(row, maxN, fht, fps);
		return new FloatProcessor(maxN, maxN, fps);
	}

	/** Power Spectrum of one row from 2D Hartley Transform. */
 	private void fht2ps(int row, int maxN, float[] fht, float[] ps) {
 		int base = row*maxN;
		int l;
		for (int c=0; c<maxN; c++) {
			l = ((maxN-row)%maxN) * maxN + (maxN-c)%maxN;
			ps[base+c] = (sqr(fht[base+c]) + sqr(fht[l]))/2f;
 		}
	}

	/** Converts this FHT to a complex Fourier transform and returns it as a two slice stack.
	*	Author: Joachim Wesner
	*/
	public ImageStack getComplexTransform() {
		if (!isFrequencyDomain)
			throw new  IllegalArgumentException("Frequency domain image required");
		float[] fht = (float[])getPixels();
		float[] re = new float[maxN*maxN];
		float[] im = new float[maxN*maxN];
		for (int i=0; i<maxN; i++) {
			FHTreal(i, maxN, fht, re);
			FHTimag(i, maxN, fht, im);
		}
		swapQuadrants(new FloatProcessor(maxN, maxN, re));
		swapQuadrants(new FloatProcessor(maxN, maxN, im));
		ImageStack stack = new ImageStack(maxN, maxN);
		stack.addSlice("Real", re);
		stack.addSlice("Imaginary", im);
		return stack;
	}

	/**	 FFT real value of one row from 2D Hartley Transform.
	*	Author: Joachim Wesner
	*/
      void FHTreal(int row, int maxN, float[] fht, float[] real) {
            int base = row*maxN;
            int offs = ((maxN-row)%maxN) * maxN;
            for (int c=0; c<maxN; c++) {
                  real[base+c] = (fht[base+c] + fht[offs+((maxN-c)%maxN)])*0.5f;
            }
      }


	/** FFT imag value of one row from 2D Hartley Transform.
	*	Author: Joachim Wesner
	*/
      void FHTimag(int row, int maxN, float[] fht, float[] imag) {
            int base = row*maxN;
            int offs = ((maxN-row)%maxN) * maxN;
            for (int c=0; c<maxN; c++) {
                  imag[base+c] = (-fht[base+c] + fht[offs+((maxN-c)%maxN)])*0.5f;
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

	private float sqr(float x) {
		return x*x;
	}

	/**	Swap quadrants 1 and 3 and 2 and 4 of the specified ImageProcessor 
		so the power spectrum origin is at the center of the image.
		<pre>
		    2 1
		    3 4
		</pre>
	*/
 	public void swapQuadrants(ImageProcessor ip) {
 		FFT.swapQuadrants(ip);
	}

	/**	Swap quadrants 1 and 3 and 2 and 4 of the image
		contained in this FHT. */
 	public void swapQuadrants () {
 		swapQuadrants(this);
 	}
 	
	void changeValues(ImageProcessor ip, int v1, int v2, int v3) {
		byte[] pixels = (byte[])ip.getPixels();
		int v;
		for (int i=0; i<pixels.length; i++) {
			v = pixels[i]&255;
			if (v>=v1 && v<=v2)
				pixels[i] = (byte)v3;
		}
	}

	/** Returns the image resulting from the point by point Hartley multiplication
		of this image and the specified image. Both images are assumed to be in
		the frequency domain. Multiplication in the frequency domain is equivalent 
		to convolution in the space domain. */
	public FHT multiply(FHT fht) {
		return multiply(fht, false);
	}

	/** Returns the image resulting from the point by point Hartley conjugate 
		multiplication of this image and the specified image. Both images are 
		assumed to be in the frequency domain. Conjugate multiplication in
		the frequency domain is equivalent to correlation in the space domain. */
	public FHT conjugateMultiply(FHT fht) {
		return multiply(fht, true);
	}

	FHT multiply(FHT fht, boolean  conjugate) {
		int rowMod, cMod, colMod;
		double h2e, h2o;
		float[] h1 = (float[])getPixels();
		float[] h2 = (float[])fht.getPixels();
		float[] tmp = new float[maxN*maxN];
		for (int r =0; r<maxN; r++) {
			rowMod = (maxN - r) % maxN;
			for (int c=0; c<maxN; c++) {
				colMod = (maxN - c) % maxN;
				h2e = (h2[r * maxN + c] + h2[rowMod * maxN + colMod]) / 2;
				h2o = (h2[r * maxN + c] - h2[rowMod * maxN + colMod]) / 2;
				if (conjugate) 
					tmp[r * maxN + c] = (float)(h1[r * maxN + c] * h2e - h1[rowMod * maxN + colMod] * h2o);
				else
					tmp[r * maxN + c] = (float)(h1[r * maxN + c] * h2e + h1[rowMod * maxN + colMod] * h2o);
			}
		}
		FHT fht2 =  new FHT(new FloatProcessor(maxN, maxN, tmp, null));
		fht2.isFrequencyDomain = true;
		return fht2;
	}
		
	/** Returns the image resulting from the point by point Hartley division
		of this image by the specified image. Both images are assumed to be in
		the frequency domain. Division in the frequency domain is equivalent 
		to deconvolution in the space domain. */
	public FHT divide(FHT fht) {
		int rowMod, cMod, colMod;
		double mag, h2e, h2o;
		float[] h1 = (float[])getPixels();
		float[] h2 = (float[])fht.getPixels();
		float[] out = new float[maxN*maxN];
		for (int r=0; r<maxN; r++) {
			rowMod = (maxN - r) % maxN;
			for (int c=0; c<maxN; c++) {
				colMod = (maxN - c) % maxN;
				mag =h2[r*maxN+c] * h2[r*maxN+c] + h2[rowMod*maxN+colMod] * h2[rowMod*maxN+colMod];
				if (mag<1e-20)
					mag = 1e-20;
				h2e = (h2[r*maxN+c] + h2[rowMod*maxN+colMod]);
				h2o = (h2[r*maxN+c] - h2[rowMod*maxN+colMod]);
				double tmp = (h1[r*maxN+c] * h2e - h1[rowMod*maxN+colMod] * h2o);
				out[r*maxN+c] = (float)(tmp/mag);
			}
		}
		FHT fht2 = new FHT(new FloatProcessor(maxN, maxN, out, null));
		fht2.isFrequencyDomain = true;
		return fht2;
	}
			
	/** Enables/disables display of the progress bar during transforms. */
	public void setShowProgress(boolean showProgress) {
		this.showProgress = showProgress;
	}
	
	/** Returns a clone of this FHT. */
	public FHT getCopy() {
		ImageProcessor ip = super.duplicate();
		FHT fht = new FHT(ip);
		fht.isFrequencyDomain = isFrequencyDomain;
		fht.quadrantSwapNeeded = quadrantSwapNeeded;
		fht.rgb = rgb;
		fht.originalWidth = originalWidth;
		fht.originalHeight = originalHeight;
		fht.originalBitDepth = originalBitDepth;		
		fht.originalColorModel = originalColorModel;		
		fht.powerSpectrumMean = powerSpectrumMean;		
		return fht;
	}
		
	public static boolean isPowerOf2(int n) {
		int i=2;
		while(i<n) i *= 2;
		return i==n;
	}

	/** Returns a string containing information about this FHT. */
	public String toString() {
		return "FHT, " + getWidth() + "x"+getHeight() + ", fd=" + isFrequencyDomain;
	}
	
}
