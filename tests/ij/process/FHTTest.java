package ij.process;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * Unit tests for {@link FHT#fourier1D}.
 * Covers the fix for https://github.com/imagej/ImageJ/issues/262:
 * result[0] was incorrectly scaled by 1/sqrt(2) compared to result[k>0].
 */
public class FHTTest {

	private static final float DELTA = 1e-5f;

	/** For a unit impulse, the amplitude spectrum must be flat: all result[k] equal. */
	@Test
	public void testImpulseFlatSpectrum() {
		// Unit impulse of length 4 (power-of-2): spectrum should be flat
		float[] impulse = {1f, 0f, 0f, 0f};
		float[] result = new FHT().fourier1D(impulse, FHT.NO_WINDOW);
		// All output values must be equal (flat spectrum)
		for (int k = 1; k < result.length; k++)
			assertEquals("result[0] and result[" + k + "] should be equal for unit impulse",
					result[0], result[k], DELTA);
	}

	/** For a unit impulse of non-power-of-2 length, the spectrum should still be flat. */
	@Test
	public void testImpulseFlatSpectrumNonPow2() {
		float[] impulse = new float[5]; // size=8 after zero-padding
		impulse[0] = 1f;
		float[] result = new FHT().fourier1D(impulse, FHT.NO_WINDOW);
		for (int k = 1; k < result.length; k++)
			assertEquals("result[0] and result[" + k + "] should be equal for unit impulse",
					result[0], result[k], DELTA);
	}

	/** DC signal: result[0] should be sqrt(2)*mean, result[k>0] should be zero. */
	@Test
	public void testDCSignal() {
		int n = 8;
		float dc = 3f;
		float[] data = new float[n];
		for (int i = 0; i < n; i++) data[i] = dc;
		float[] result = new FHT().fourier1D(data, FHT.NO_WINDOW);
		assertEquals("DC result[0] should be sqrt(2)*mean", (float)(dc * Math.sqrt(2)), result[0], DELTA);
		for (int k = 1; k < result.length; k++)
			assertEquals("AC components should be zero for constant signal", 0f, result[k], DELTA);
	}

	/**
	 * Cosine signal: result at the cosine frequency should be amplitude/sqrt(2) (RMS).
	 * The same formula used for result[0] and result[k>0] ensures consistency.
	 */
	@Test
	public void testCosineRMSAmplitude() {
		int n = 8;
		double amplitude = 2.0;
		float[] data = new float[n];
		// Pure cosine with 1 cycle over n samples, zero DC is not usable (sum=0),
		// so add DC offset >= amplitude to keep sum > 0 for normalization
		double dc = 4.0;
		for (int i = 0; i < n; i++)
			data[i] = (float)(dc + amplitude * Math.cos(2 * Math.PI * i / n));
		float[] result = new FHT().fourier1D(data, FHT.NO_WINDOW);
		// result[1] should equal amplitude/sqrt(2) (RMS of cosine), normalized by n/n=1
		float expectedRMS = (float)(amplitude / Math.sqrt(2));
		assertEquals("Cosine RMS amplitude", expectedRMS, result[1], 1e-4f);
	}

	/** Verify result[0] is exactly sqrt(2) times larger than the old (buggy) value. */
	@Test
	public void testResult0ScaledBySquareRootOf2VsOldBehavior() {
		// Use the issue's test array (first few values are enough to check scaling)
		float[] data = {
			1.044f, 1.516f, 2.197f, 2.752f, 3.363f, 3.947f, 4.53f, 5.183f,
			5.725f, 6.35f, 7.308f, 8.405f, 9.863f, 11.433f, 13.447f, 15.433f
		};
		float[] result = new FHT().fourier1D(data, FHT.NO_WINDOW);
		// result[0] should use the same sqrt(y[0]^2 + y[0]^2) formula as k>0
		// Compute mean manually
		double sum = 0;
		for (float v : data) sum += v;
		double mean = sum / data.length;
		// After normalization by n and FHT, y[0] = mean; result[0] = sqrt(2)*mean
		float expected = (float)(Math.sqrt(2) * mean);
		assertEquals("result[0] should be sqrt(2)*mean", expected, result[0], 1e-4f);
	}
}
