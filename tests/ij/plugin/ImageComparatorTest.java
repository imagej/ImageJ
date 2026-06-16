package ij.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import ij.plugin.ImageComparator.Metrics;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;

/**
 * Unit tests for {@link ImageComparator}.
 *
 * @author Mustafa Merchant
 */
public class ImageComparatorTest {

	private static final double EPS = 1e-6;

	/** Builds a deterministic, spatially varying 8-bit image. */
	private static ByteProcessor pattern(int w, int h) {
		byte[] pixels = new byte[w*h];
		for (int y=0; y<h; y++)
			for (int x=0; x<w; x++)
				pixels[y*w+x] = (byte)((x*31 + y*17) & 0xff);
		return new ByteProcessor(w, h, pixels);
	}

	@Test
	public void testIdenticalImages() {
		ByteProcessor a = pattern(32, 24);
		ByteProcessor b = pattern(32, 24);
		Metrics m = ImageComparator.compare(a, b);
		assertEquals(0.0, m.mse, EPS);
		assertEquals(0.0, m.rmse, EPS);
		assertEquals(0.0, m.mae, EPS);
		assertEquals(0.0, m.maxError, EPS);
		assertEquals(Double.POSITIVE_INFINITY, m.psnr, 0.0);
		assertEquals(1.0, m.ssim, 1e-4);
	}

	@Test
	public void testKnownErrors() {
		// 2x2 images, differences: 0, 1, 2, 3
		ByteProcessor a = new ByteProcessor(2, 2, new byte[] {10, 20, 30, 40});
		ByteProcessor b = new ByteProcessor(2, 2, new byte[] {10, 21, 32, 43});
		Metrics m = ImageComparator.compare(a, b);
		// MSE = (0 + 1 + 4 + 9)/4 = 3.5
		assertEquals(3.5, m.mse, EPS);
		assertEquals(Math.sqrt(3.5), m.rmse, EPS);
		// MAE = (0 + 1 + 2 + 3)/4 = 1.5
		assertEquals(1.5, m.mae, EPS);
		assertEquals(3.0, m.maxError, EPS);
		assertEquals(10*Math.log10((255.0*255.0)/3.5), m.psnr, EPS);
	}

	@Test
	public void testConstantOffsetPsnr() {
		int w = 16, h = 16;
		byte[] pa = new byte[w*h];
		byte[] pb = new byte[w*h];
		for (int i=0; i<pa.length; i++) {
			pa[i] = (byte)100;
			pb[i] = (byte)101;
		}
		Metrics m = ImageComparator.compare(new ByteProcessor(w, h, pa), new ByteProcessor(w, h, pb));
		assertEquals(1.0, m.mse, EPS);
		assertEquals(1.0, m.maxError, EPS);
		assertEquals(10*Math.log10(255.0*255.0), m.psnr, EPS); // ~48.13 dB
	}

	@Test
	public void testSymmetry() {
		ByteProcessor a = pattern(20, 20);
		ByteProcessor b = pattern(20, 20);
		// Perturb b deterministically.
		for (int i=0; i<20*20; i++)
			b.set(i, (a.get(i) + ((i*13) % 7)) & 0xff);
		Metrics ab = ImageComparator.compare(a, b);
		Metrics ba = ImageComparator.compare(b, a);
		assertEquals(ab.mse, ba.mse, EPS);
		assertEquals(ab.mae, ba.mae, EPS);
		assertEquals(ab.ssim, ba.ssim, EPS);
	}

	@Test
	public void testSsimInRange() {
		ByteProcessor a = pattern(40, 30);
		ByteProcessor b = new ByteProcessor(40, 30);
		for (int i=0; i<40*30; i++)
			b.set(i, (255 - a.get(i)) & 0xff); // strongly dissimilar (inverted)
		double ssim = ImageComparator.compare(a, b).ssim;
		assertTrue("SSIM should be <= 1", ssim <= 1.0 + EPS);
		assertTrue("SSIM should be >= -1", ssim >= -1.0 - EPS);
	}

	@Test
	public void testFloatWithExplicitRange() {
		int w = 8, h = 8;
		float[] fa = new float[w*h];
		float[] fb = new float[w*h];
		for (int i=0; i<fa.length; i++) {
			fa[i] = i*0.5f;
			fb[i] = i*0.5f + 0.5f; // constant offset of 0.5
		}
		Metrics m = ImageComparator.compare(new FloatProcessor(w, h, fa),
				new FloatProcessor(w, h, fb), 10.0);
		assertEquals(0.25, m.mse, EPS);          // 0.5^2
		assertEquals(0.5, m.mae, EPS);
		assertEquals(10*Math.log10((10.0*10.0)/0.25), m.psnr, EPS);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSizeMismatchThrows() {
		ImageComparator.compare(new ByteProcessor(4, 4), new ByteProcessor(4, 5));
	}
}
