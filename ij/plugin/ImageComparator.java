package ij.plugin;
import ij.*;
import ij.gui.GenericDialog;
import ij.measure.ResultsTable;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

/** This plugin implements the Analyze>Tools>Compare Images command. It measures
	how similar two images of the same size are, using metrics that are widely
	used to evaluate denoising, compression, reconstruction and registration
	results but that are not otherwise built into ImageJ:
	<ul>
	<li>MSE  - mean squared error</li>
	<li>RMSE - root mean squared error</li>
	<li>MAE  - mean absolute error</li>
	<li>Max  - maximum absolute difference</li>
	<li>PSNR - peak signal-to-noise ratio, in decibels (higher is more similar)</li>
	<li>SSIM - mean structural similarity index, in the range -1..1 (1 is identical)</li>
	</ul>
	The two images must have the same width and height. 8-bit, 16-bit, 32-bit and
	RGB images are supported; RGB images are compared channel-by-channel and the
	per-channel metrics are averaged. When both images are stacks with the same
	number of slices the metrics are averaged over all slices.
	<pre>
	// test script
	imp1 = IJ.openImage("http://imagej.net/ij/images/boats.gif");
	imp2 = imp1.duplicate();
	IJ.run(imp2, "Add Specified Noise...", "standard=15");
	metrics = ImageComparator.compare(imp1.getProcessor(), imp2.getProcessor());
	IJ.log("PSNR="+metrics.psnr+" dB, SSIM="+metrics.ssim);
	</pre>
	@author Mustafa Merchant
*/
public class ImageComparator implements PlugIn {

	/** Holds the result of comparing two images. */
	public static class Metrics {
		/** Mean squared error. */
		public double mse;
		/** Root mean squared error. */
		public double rmse;
		/** Mean absolute error. */
		public double mae;
		/** Maximum absolute difference between corresponding pixels. */
		public double maxError;
		/** Peak signal-to-noise ratio in decibels; Double.POSITIVE_INFINITY for identical images. */
		public double psnr;
		/** Mean structural similarity index (Wang et al., 2004), in the range -1..1. */
		public double ssim;
	}

	// SSIM Gaussian window: radius 5, sigma 1.5 (the de facto standard 11x11 window)
	private static final int SSIM_RADIUS = 5;
	private static final double SSIM_SIGMA = 1.5;
	private static final double SSIM_K1 = 0.01;
	private static final double SSIM_K2 = 0.03;

	private static String title1 = "";
	private static String title2 = "";

	public void run(String arg) {
		int[] wList = WindowManager.getIDList();
		if (wList==null || wList.length<2) {
			IJ.error("Compare Images", "Two images of the same size are required.");
			return;
		}
		String[] titles = new String[wList.length];
		for (int i=0; i<wList.length; i++) {
			ImagePlus imp = WindowManager.getImage(wList[i]);
			titles[i] = imp!=null ? imp.getTitle() : "";
		}
		GenericDialog gd = new GenericDialog("Compare Images");
		String default1 = title1.equals("") ? titles[0] : title1;
		String default2 = title2.equals("") ? titles[titles.length>1?1:0] : title2;
		gd.addChoice("Image_1 (reference):", titles, default1);
		gd.addChoice("Image_2:", titles, default2);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		int index1 = gd.getNextChoiceIndex();
		int index2 = gd.getNextChoiceIndex();
		title1 = titles[index1];
		title2 = titles[index2];
		ImagePlus imp1 = WindowManager.getImage(wList[index1]);
		ImagePlus imp2 = WindowManager.getImage(wList[index2]);
		Metrics m = compare(imp1, imp2);
		if (m==null)
			return;
		ResultsTable rt = ResultsTable.getResultsTable("Image Comparison");
		if (rt==null)
			rt = new ResultsTable();
		rt.incrementCounter();
		rt.addValue("Image1", imp1.getTitle());
		rt.addValue("Image2", imp2.getTitle());
		rt.addValue("MSE", m.mse);
		rt.addValue("RMSE", m.rmse);
		rt.addValue("MAE", m.mae);
		rt.addValue("Max", m.maxError);
		rt.addValue("PSNR(dB)", m.psnr);
		rt.addValue("SSIM", m.ssim);
		rt.show("Image Comparison");
	}

	/** Compares two images, averaging the metrics over the slices when both are
		stacks of the same depth. Shows an error message and returns null if the
		images are incompatible. */
	public static Metrics compare(ImagePlus imp1, ImagePlus imp2) {
		if (imp1==null || imp2==null)
			return null;
		if (imp1.getWidth()!=imp2.getWidth() || imp1.getHeight()!=imp2.getHeight()) {
			IJ.error("Compare Images", "Images must have the same width and height.");
			return null;
		}
		int n1 = imp1.getStackSize();
		int n2 = imp2.getStackSize();
		if (n1>1 && n1==n2) {
			Metrics sum = new Metrics();
			double psnrSum = 0;
			boolean psnrInfinite = true;
			for (int s=1; s<=n1; s++) {
				Metrics m = compare(imp1.getStack().getProcessor(s), imp2.getStack().getProcessor(s));
				if (m==null)
					return null;
				sum.mse += m.mse;
				sum.mae += m.mae;
				sum.ssim += m.ssim;
				if (m.maxError>sum.maxError) sum.maxError = m.maxError;
				if (m.psnr!=Double.POSITIVE_INFINITY) {
					psnrSum += m.psnr;
					psnrInfinite = false;
				}
			}
			sum.mse /= n1;
			sum.mae /= n1;
			sum.ssim /= n1;
			sum.rmse = Math.sqrt(sum.mse);
			sum.psnr = psnrInfinite ? Double.POSITIVE_INFINITY : psnrSum/n1;
			return sum;
		}
		return compare(imp1.getProcessor(), imp2.getProcessor());
	}

	/** Compares two image processors using the data range implied by the type of
		the first (reference) processor: 255 for 8-bit and RGB, 65535 for 16-bit
		and the actual min-max range for 32-bit images. */
	public static Metrics compare(ImageProcessor ip1, ImageProcessor ip2) {
		return compare(ip1, ip2, dataRange(ip1));
	}

	/** Compares two image processors of equal size, using the supplied data range
		(maximum minus minimum possible value) for the PSNR and SSIM calculations.
		@throws IllegalArgumentException if the processors differ in size or channel count. */
	public static Metrics compare(ImageProcessor ip1, ImageProcessor ip2, double dataRange) {
		if (ip1==null || ip2==null)
			throw new IllegalArgumentException("Null processor");
		if (ip1.getWidth()!=ip2.getWidth() || ip1.getHeight()!=ip2.getHeight())
			throw new IllegalArgumentException("Images must have the same width and height");
		float[][] c1 = toChannels(ip1);
		float[][] c2 = toChannels(ip2);
		if (c1.length!=c2.length)
			throw new IllegalArgumentException("Images must have the same number of channels");
		int width = ip1.getWidth();
		int height = ip1.getHeight();
		int n = width*height;
		double sumSq = 0, sumAbs = 0, maxErr = 0, ssimSum = 0;
		for (int c=0; c<c1.length; c++) {
			float[] a = c1[c];
			float[] b = c2[c];
			for (int i=0; i<n; i++) {
				double d = a[i]-b[i];
				double ad = d<0 ? -d : d;
				sumSq += d*d;
				sumAbs += ad;
				if (ad>maxErr) maxErr = ad;
			}
			ssimSum += ssimChannel(a, b, width, height, dataRange);
		}
		long total = (long)n*c1.length;
		Metrics m = new Metrics();
		m.mse = sumSq/total;
		m.rmse = Math.sqrt(m.mse);
		m.mae = sumAbs/total;
		m.maxError = maxErr;
		m.psnr = m.mse==0 ? Double.POSITIVE_INFINITY : 10*Math.log10((dataRange*dataRange)/m.mse);
		m.ssim = ssimSum/c1.length;
		return m;
	}

	/** Returns one float[] per channel: a single grayscale channel, or R, G and B for RGB images. */
	private static float[][] toChannels(ImageProcessor ip) {
		int n = ip.getWidth()*ip.getHeight();
		if (ip instanceof ColorProcessor) {
			byte[] r = new byte[n], g = new byte[n], b = new byte[n];
			((ColorProcessor)ip).getRGB(r, g, b);
			return new float[][] {bytesToFloat(r), bytesToFloat(g), bytesToFloat(b)};
		}
		float[] a = new float[n];
		for (int i=0; i<n; i++)
			a[i] = ip.getf(i);
		return new float[][] {a};
	}

	private static float[] bytesToFloat(byte[] b) {
		float[] a = new float[b.length];
		for (int i=0; i<b.length; i++)
			a[i] = b[i]&0xff;
		return a;
	}

	/** Data range used for PSNR and SSIM: the full range for 8/16-bit and RGB
		images, and the actual min-max range of the reference image for 32-bit. */
	private static double dataRange(ImageProcessor ref) {
		switch (ref.getBitDepth()) {
			case 8: case 24: return 255;
			case 16: return 65535;
			default: // 32-bit float: use the min-max range of the reference image
				double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
				int n = ref.getWidth()*ref.getHeight();
				for (int i=0; i<n; i++) {
					float v = ref.getf(i);
					if (v<min) min = v;
					if (v>max) max = v;
				}
				double range = max-min;
				return range>0 ? range : 1;
		}
	}

	/** Mean structural similarity for a single channel, using an 11x11 Gaussian
		weighting window (sigma 1.5) as in Wang et al., 2004. */
	private static double ssimChannel(float[] x, float[] y, int w, int h, double L) {
		double[] kernel = gaussianKernel(SSIM_RADIUS, SSIM_SIGMA);
		float[] mux = blur(x, w, h, kernel);
		float[] muy = blur(y, w, h, kernel);
		float[] sxx = blur(mul(x, x), w, h, kernel);
		float[] syy = blur(mul(y, y), w, h, kernel);
		float[] sxy = blur(mul(x, y), w, h, kernel);
		double c1 = (SSIM_K1*L)*(SSIM_K1*L);
		double c2 = (SSIM_K2*L)*(SSIM_K2*L);
		double sum = 0;
		int n = w*h;
		for (int i=0; i<n; i++) {
			double ux = mux[i], uy = muy[i];
			double vx = sxx[i]-ux*ux;
			double vy = syy[i]-uy*uy;
			double vxy = sxy[i]-ux*uy;
			sum += ((2*ux*uy+c1)*(2*vxy+c2)) / ((ux*ux+uy*uy+c1)*(vx+vy+c2));
		}
		return sum/n;
	}

	private static float[] mul(float[] a, float[] b) {
		float[] r = new float[a.length];
		for (int i=0; i<a.length; i++)
			r[i] = a[i]*b[i];
		return r;
	}

	private static double[] gaussianKernel(int radius, double sigma) {
		double[] k = new double[2*radius+1];
		double sum = 0;
		for (int i=-radius; i<=radius; i++) {
			double v = Math.exp(-(i*i)/(2*sigma*sigma));
			k[i+radius] = v;
			sum += v;
		}
		for (int i=0; i<k.length; i++)
			k[i] /= sum;
		return k;
	}

	/** Separable Gaussian convolution with replicated (clamped) edges. */
	private static float[] blur(float[] in, int w, int h, double[] kernel) {
		int radius = kernel.length/2;
		float[] tmp = new float[w*h];
		for (int y=0; y<h; y++) {
			int row = y*w;
			for (int x=0; x<w; x++) {
				double s = 0;
				for (int t=-radius; t<=radius; t++) {
					int xx = x+t;
					if (xx<0) xx = 0; else if (xx>=w) xx = w-1;
					s += kernel[t+radius]*in[row+xx];
				}
				tmp[row+x] = (float)s;
			}
		}
		float[] out = new float[w*h];
		for (int y=0; y<h; y++) {
			for (int x=0; x<w; x++) {
				double s = 0;
				for (int t=-radius; t<=radius; t++) {
					int yy = y+t;
					if (yy<0) yy = 0; else if (yy>=h) yy = h-1;
					s += kernel[t+radius]*tmp[yy*w+x];
				}
				out[y*w+x] = (float)s;
			}
		}
		return out;
	}

}
