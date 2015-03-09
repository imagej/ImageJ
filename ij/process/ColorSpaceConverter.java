package ij.process;
import ij.*;
import ij.process.*;
import java.awt.Color;

/**
* ColorSpaceConverter
* @author dvs, hlp
* Created Jan 15, 2004
* Version 3 posted on ImageJ Mar 12, 2006 by Duane Schwartzwald
*	vonschwartzwalder at mac.com
* Version 4 created Feb. 27, 2007
*   by Harry Parker, harrylparker at yahoo dot com,
*   corrects RGB to XYZ (and LAB) conversion.
*/
public class ColorSpaceConverter {

	/**
	 * reference white in XYZ coordinates
	 */
	public double[] D50 = {96.4212, 100.0, 82.5188};
	public double[] D55 = {95.6797, 100.0, 92.1481};
	public double[] D65 = {95.0429, 100.0, 108.8900};
	public double[] D75 = {94.9722, 100.0, 122.6394};
	public double[] whitePoint = D65;

	/**
	 * reference white in xyY coordinates
	 */
	public double[] chromaD50 = {0.3457, 0.3585, 100.0};
	public double[] chromaD55 = {0.3324, 0.3474, 100.0};
	public double[] chromaD65 = {0.3127, 0.3290, 100.0};
	public double[] chromaD75 = {0.2990, 0.3149, 100.0};
	public double[] chromaWhitePoint = chromaD65;

	/**
	 * sRGB to XYZ conversion matrix
	 */
	public double[][] M = {{0.4124, 0.3576,  0.1805},
							 {0.2126, 0.7152,  0.0722},
							 {0.0193, 0.1192,  0.9505}};

	/**
	 * XYZ to sRGB conversion matrix
	 */
	public double[][] Mi = {{ 3.2406, -1.5372, -0.4986},
							 {-0.9689,	1.8758,	 0.0415},
							 { 0.0557, -0.2040,	 1.0570}};

	/**
	 * Default constructor; uses D65 for the white point
	 */
	public ColorSpaceConverter() {
	  whitePoint = D65;
	  chromaWhitePoint = chromaD65;
	}

	/**
	 * Constructor for setting a non-default white point
	 * @param white "d50", "d55", "d65" or "d75"
	 */
	public ColorSpaceConverter(String white) {
		whitePoint = D65;
		chromaWhitePoint = chromaD65;
		if (white.equalsIgnoreCase("d50")) {
			whitePoint = D50;
			chromaWhitePoint = chromaD50;
		} else if (white.equalsIgnoreCase("d55")) {
			whitePoint = D55;
			chromaWhitePoint = chromaD55;
		} else if (white.equalsIgnoreCase("d65")) {
			whitePoint = D65;
			chromaWhitePoint = chromaD65;
		} else if (white.equalsIgnoreCase("d75")) {
			whitePoint = D75;
			chromaWhitePoint = chromaD75;
		} else
			throw new IllegalArgumentException("Invalid white point");
	}

	/**
	 * @param H Hue angle/360 (0..1)
	 * @param S Saturation (0..1)
	 * @param B Value (0..1)
	 * @return RGB values
	 */
	public int[] HSBtoRGB(double H, double S, double B) {
	  int[] result = new int[3];
	  int rgb = Color.HSBtoRGB((float) H, (float) S, (float) B);
	  result[0] = (rgb >> 16) & 0xff;
	  result[1] = (rgb >> 8) & 0xff;
	  result[2] = (rgb >> 0) & 0xff;
	  return result;
	}

	public int[] HSBtoRGB(double[] HSB) {
	  return HSBtoRGB(HSB[0], HSB[1], HSB[2]);
	}

	/**
	 * Convert LAB to RGB.
	 * @param L
	 * @param a
	 * @param b
	 * @return RGB values
	 */
	public int[] LABtoRGB(double L, double a, double b) {
	  return XYZtoRGB(LABtoXYZ(L, a, b));
	}

	/**
	 * @param Lab
	 * @return RGB values
	 */
	public int[] LABtoRGB(double[] Lab) {
	  return XYZtoRGB(LABtoXYZ(Lab));
	}

	/**
	 * Convert LAB to XYZ.
	 * @param L
	 * @param a
	 * @param b
	 * @return XYZ values
	 */
	public double[] LABtoXYZ(double L, double a, double b) {
	  double[] result = new double[3];

	  double y = (L + 16.0) / 116.0;
	  double y3 = Math.pow(y, 3.0);
	  double x = (a / 500.0) + y;
	  double x3 = Math.pow(x, 3.0);
	  double z = y - (b / 200.0);
	  double z3 = Math.pow(z, 3.0);

	  if (y3 > 0.008856) {
		y = y3;
	  }
	  else {
		y = (y - (16.0 / 116.0)) / 7.787;
	  }
	  if (x3 > 0.008856) {
		x = x3;
	  }
	  else {
		x = (x - (16.0 / 116.0)) / 7.787;
	  }
	  if (z3 > 0.008856) {
		z = z3;
	  }
	  else {
		z = (z - (16.0 / 116.0)) / 7.787;
	  }

	  result[0] = x * whitePoint[0];
	  result[1] = y * whitePoint[1];
	  result[2] = z * whitePoint[2];

	  return result;
	}

	/**
	 * Convert LAB to XYZ.
	 * @param Lab
	 * @return XYZ values
	 */
	public double[] LABtoXYZ(double[] Lab) {
	  return LABtoXYZ(Lab[0], Lab[1], Lab[2]);
	}

	/**
	 * @param R Red in range 0..255
	 * @param G Green in range 0..255
	 * @param B Blue in range 0..255
	 * @return HSB values: H is 0..360 degrees / 360 (0..1), S is 0..1, B is 0..1
	 */
	public double[] RGBtoHSB(int R, int G, int B) {
	  double[] result = new double[3];
	  float[] hsb = new float[3];
	  Color.RGBtoHSB(R, G, B, hsb);
	  result[0] = hsb[0];
	  result[1] = hsb[1];
	  result[2] = hsb[2];
	  return result;
	}

	public double[] RGBtoHSB(int[] RGB) {
	  return RGBtoHSB(RGB[0], RGB[1], RGB[2]);
	}

	/**
	 * @param rgb RGB value
	 * @return Lab values
	 */
	public double[] RGBtoLAB(int rgb) {
		int r = (rgb&0xff0000)>>16;
		int g = (rgb&0xff00)>>8;
		int b = rgb&0xff;
		return XYZtoLAB(RGBtoXYZ(r,g,b));
	}

	/**
	 * @param RGB
	 * @return Lab values
	 */
	public double[] RGBtoLAB(int[] RGB) {
	  return XYZtoLAB(RGBtoXYZ(RGB));
	}

	/**
	 * Convert RGB to XYZ
	 * @param R
	 * @param G
	 * @param B
	 * @return XYZ in double array.
	 */
	public double[] RGBtoXYZ(int R, int G, int B) {
	  double[] result = new double[3];

	  // convert 0..255 into 0..1
	  double r = R / 255.0;
	  double g = G / 255.0;
	  double b = B / 255.0;

	  // assume sRGB
	  if (r <= 0.04045) {
		r = r / 12.92;
	  }
	  else {
		r = Math.pow(((r + 0.055) / 1.055), 2.4);
	  }
	  if (g <= 0.04045) {
		g = g / 12.92;
	  }
	  else {
		g = Math.pow(((g + 0.055) / 1.055), 2.4);
	  }
	  if (b <= 0.04045) {
		b = b / 12.92;
	  }
	  else {
		b = Math.pow(((b + 0.055) / 1.055), 2.4);
	  }

	  r *= 100.0;
	  g *= 100.0;
	  b *= 100.0;

	  // [X Y Z] = [r g b][M]
	  result[0] = (r * M[0][0]) + (g * M[0][1]) + (b * M[0][2]);
	  result[1] = (r * M[1][0]) + (g * M[1][1]) + (b * M[1][2]);
	  result[2] = (r * M[2][0]) + (g * M[2][1]) + (b * M[2][2]);

	  return result;
	}

	/**
	 * Convert RGB to XYZ
	 * @param RGB
	 * @return XYZ in double array.
	 */
	public double[] RGBtoXYZ(int[] RGB) {
	  return RGBtoXYZ(RGB[0], RGB[1], RGB[2]);
	}

	/**
	 * @param x
	 * @param y
	 * @param Y
	 * @return XYZ values
	 */
	public double[] xyYtoXYZ(double x, double y, double Y) {
	  double[] result = new double[3];
	  if (y == 0) {
		result[0] = 0;
		result[1] = 0;
		result[2] = 0;
	  }
	  else {
		result[0] = (x * Y) / y;
		result[1] = Y;
		result[2] = ((1 - x - y) * Y) / y;
	  }
	  return result;
	}

	/**
	 * @param xyY
	 * @return XYZ values
	 */
	public double[] xyYtoXYZ(double[] xyY) {
	  return xyYtoXYZ(xyY[0], xyY[1], xyY[2]);
	}

	/**
	 * Convert XYZ to LAB.
	 * @param X
	 * @param Y
	 * @param Z
	 * @return Lab values
	 */
	public double[] XYZtoLAB(double X, double Y, double Z) {

	  double x = X / whitePoint[0];
	  double y = Y / whitePoint[1];
	  double z = Z / whitePoint[2];

	  if (x > 0.008856) {
		x = Math.pow(x, 1.0 / 3.0);
	  }
	  else {
		x = (7.787 * x) + (16.0 / 116.0);
	  }
	  if (y > 0.008856) {
		y = Math.pow(y, 1.0 / 3.0);
	  }
	  else {
		y = (7.787 * y) + (16.0 / 116.0);
	  }
	  if (z > 0.008856) {
		z = Math.pow(z, 1.0 / 3.0);
	  }
	  else {
		z = (7.787 * z) + (16.0 / 116.0);
	  }

	  double[] result = new double[3];

	  result[0] = (116.0 * y) - 16.0;
	  result[1] = 500.0 * (x - y);
	  result[2] = 200.0 * (y - z);

	  return result;
	}

	/**
	 * Convert XYZ to LAB.
	 * @param XYZ
	 * @return Lab values
	 */
	public double[] XYZtoLAB(double[] XYZ) {
	  return XYZtoLAB(XYZ[0], XYZ[1], XYZ[2]);
	}

	/**
	 * Convert XYZ to RGB.
	 * @param X
	 * @param Y
	 * @param Z
	 * @return RGB in int array.
	 */
	public int[] XYZtoRGB(double X, double Y, double Z) {
	  int[] result = new int[3];

	  double x = X / 100.0;
	  double y = Y / 100.0;
	  double z = Z / 100.0;

	  // [r g b] = [X Y Z][Mi]
	  double r = (x * Mi[0][0]) + (y * Mi[0][1]) + (z * Mi[0][2]);
	  double g = (x * Mi[1][0]) + (y * Mi[1][1]) + (z * Mi[1][2]);
	  double b = (x * Mi[2][0]) + (y * Mi[2][1]) + (z * Mi[2][2]);

	  // assume sRGB
	  if (r > 0.0031308) {
		r = ((1.055 * Math.pow(r, 1.0 / 2.4)) - 0.055);
	  }
	  else {
		r = (r * 12.92);
	  }
	  if (g > 0.0031308) {
		g = ((1.055 * Math.pow(g, 1.0 / 2.4)) - 0.055);
	  }
	  else {
		g = (g * 12.92);
	  }
	  if (b > 0.0031308) {
		b = ((1.055 * Math.pow(b, 1.0 / 2.4)) - 0.055);
	  }
	  else {
		b = (b * 12.92);
	  }

	  r = (r < 0) ? 0 : r;
	  g = (g < 0) ? 0 : g;
	  b = (b < 0) ? 0 : b;

	  // convert 0..1 into 0..255
	  result[0] = (int) Math.round(r * 255);
	  result[1] = (int) Math.round(g * 255);
	  result[2] = (int) Math.round(b * 255);

	  return result;
	}

	/**
	 * Convert XYZ to RGB
	 * @param XYZ in a double array.
	 * @return RGB in int array.
	 */
	public int[] XYZtoRGB(double[] XYZ) {
	  return XYZtoRGB(XYZ[0], XYZ[1], XYZ[2]);
	}

	/**
	 * @param X
	 * @param Y
	 * @param Z
	 * @return xyY values
	 */
	public double[] XYZtoxyY(double X, double Y, double Z) {
	  double[] result = new double[3];
	  if ((X + Y + Z) == 0) {
		result[0] = chromaWhitePoint[0];
		result[1] = chromaWhitePoint[1];
		result[2] = chromaWhitePoint[2];
	  } else {
		result[0] = X / (X + Y + Z);
		result[1] = Y / (X + Y + Z);
		result[2] = Y;
	  }
	  return result;
	}

	/**
	 * @param XYZ
	 * @return xyY values
	 */
	public double[] XYZtoxyY(double[] XYZ) {
	  return XYZtoxyY(XYZ[0], XYZ[1], XYZ[2]);
	}
 	
	/** Converts an RGB image into a Lab stack. */
	public ImagePlus RGBToLab(ImagePlus img) {
		ColorProcessor cp = (ColorProcessor)img.getProcessor();
		ColorSpaceConverter converter = new ColorSpaceConverter();
		int[] pixels = (int[])cp.getPixels();
		int w = cp.getWidth();
		int h = cp.getHeight();
		ImageStack stack = new ImageStack(w,h);
		FloatProcessor L = new FloatProcessor(w,h);
		FloatProcessor a = new FloatProcessor(w,h);
		FloatProcessor b = new FloatProcessor(w,h);
		stack.addSlice("L*",L);
		stack.addSlice("a*",a);
		stack.addSlice("b*",b);
		for (int i=0; i<pixels.length; i++) {
			double[] values = converter.RGBtoLAB(pixels[i]);
			L.setf(i,(float)values[0]);
			a.setf(i,(float)values[1]);
			b.setf(i,(float)values[2]);
		}
		ImagePlus img2 = new ImagePlus("Lab_"+img.getTitle(),stack);
		CompositeImage img3 = new CompositeImage(img2,IJ.GRAYSCALE);
		img3.resetDisplayRanges();
		return img3;
	}

	/** Converts a Lab stack into an RGB image. */
	public ImagePlus LabToRGB(ImagePlus img) {
		int w = img.getWidth();
		int h = img.getHeight();
		ImageStack stack = img.getStack();
		FloatProcessor L = (FloatProcessor)stack.getProcessor(1);
		FloatProcessor a = (FloatProcessor)stack.getProcessor(2);
		FloatProcessor b = (FloatProcessor)stack.getProcessor(3);
		ColorProcessor cp = new ColorProcessor(w, h);
		ColorSpaceConverter converter = new ColorSpaceConverter();
		int[] values = new int[3];
		double[] dvalues = new double[3];
		for (int y=0; y<h; y++) {
			for (int x=0; x<w; x++) {
				dvalues[0] = L.getf(x,y);
				dvalues[1] = a.getf(x,y);
				dvalues[2] = b.getf(x,y);
				values = converter.LABtoRGB(dvalues);
				cp.putPixel(x,y,values);
			}
		}
		ImagePlus img2 = new ImagePlus(img.getTitle(), cp);
		return img2;
	}

}