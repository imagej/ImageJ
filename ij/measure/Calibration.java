package ij.measure;
import ij.*;

/** Calibration objects contain an image's spatial and density calibration data. */
   
public class Calibration {

	public static final int STRAIGHT_LINE=0,POLY2=1,POLY3=2,POLY4=3,
		EXPONENTIAL=4,POWER=5,LOG=6,RODBARD=7,GAMMA_VARIATE=8;
	public static final int NONE=20, UNCALIBRATED_OD=21;

	/** Pixel width in 'unit's */
	public double pixelWidth = 1.0;
	
	/** Pixel height in 'unit's */
	public double pixelHeight = 1.0;
	
	/** Pixel depth in 'unit's */
	public double pixelDepth = 1.0;
	
	/** Frame interval in seconds */
	public double frameInterval;

	/** X origin in pixels. */
	public int xOrigin;

	/** Y origin in pixels. */
	public int yOrigin;

	/** Set true to display cartesian coordinates (not yet implemented). */
	public boolean invertY;

	/** Calibration function coefficients */
	private double[] coefficients;
		
	/* Distance unit (e.g. 'cm', 'inch') */
	private String unit = "pixel";
	
	/* Distance units (e.g. 'microns', 'inches') */
	private String units;

	/* Pixel value unit (e.g. 'gray level', 'OD') */
	private String valueUnit = "Gray Value";

	/* Calibration function ID */
	private int function = NONE;

	/* Calibration table */
	private float[] cTable;
	
	private boolean invertedLut;
	private int bitDepth = 8;

	/** Constructs a new Calibration object using the default values. */ 
	public Calibration(ImagePlus imp) {
		if (imp!=null) {
			if (imp.getType()==ImagePlus.GRAY16)
				bitDepth = 16;
			invertedLut = imp.isInvertedLut();
		}
	}
	
	/** Constructs a new Calibration object using the default values.
		For density calibration, the image is assumed to be 8-bits. */ 
	public Calibration() {
	}
	
	/** Returns true if this image is spatially calibrated. */
	public boolean scaled() {
		return pixelWidth!=1.0 || pixelHeight!=1.0;
	}
	
   	/** Sets the distance unit (e.g. "mm", "inch"). */
 	public void setUnit(String unit) {
 		if (unit==null || unit.equals(""))
 			this.unit = "pixel";
 		else
 			this.unit = unit;
 		units = null;
 	}
 	
 	/** Returns the distance unit (e.g. "micron", "inch"). */
 	public String getUnit() {
 		return unit;
 	}
 	
	/** Returns the plural form of the distance unit (e.g. "microns", "inches"). */
 	public String getUnits() {
 		if (units==null) {
  			if (unit.equals("pixel"))
 				units = "pixels";
 			else if (unit.equals("micron"))
 				units = "microns";
  			else if (unit.equals("inch"))
 				units = "inches";
			else
 				units = unit;
 		}
 		return units;
 	}
 	
 	/** Convertes an x-coodinate in pixels to physical units (e.g. mm). */
 	public double getX(int x) {
 		return (x-xOrigin)*pixelWidth;
 	}
 	
  	/** Convertes an x-coodinate in pixels to physical units (e.g. mm). */
 	public double getY(int y) {
 		return (y-yOrigin)*pixelHeight;
 	}
 	
  	/** Sets the calibration function,  coefficient table and unit (e.g. "OD"). */
 	public void setFunction(int function, double[] coefficients, String unit) {
 		if (function==NONE)
 			{disableDensityCalibration(); return;}
 		if (coefficients==null && function>=STRAIGHT_LINE && function<=GAMMA_VARIATE)
 			return;
 		this.function = function;
 		this.coefficients = coefficients;
 		if (unit!=null)
 			valueUnit = unit;
 		cTable = null;
 	}
 	
 	public void disableDensityCalibration() {
 		function = NONE;
  		coefficients = null;
  		cTable = null;
 		valueUnit = "Gray Value";
 	}
 	
	/** Returns the density unit. */
 	public String getValueUnit() {
 		return valueUnit;
 	}
 	
 	/** Returns the calibration function coefficients. */
 	public double[] getCoefficients() {
 		return coefficients;
 	}

 	/** Returns true if this image is density calibrated. */
	public boolean calibrated() {
		return function!=NONE;
	}
	
	/** Returns the calibration function ID. */
 	public int getFunction() {
 		return function;
 	}
 	
	/** Returns the calibration table. For 8-bit images,
		the table has a length of 256, for 16-bit images,
		the length is 65536. */
 	public float[] getCTable() {
 		if (cTable==null)
 			makeCTable();
 		return cTable;
 	}
 	
 	void makeCTable() {
 		if (bitDepth==16)
 			{make16BitCTable(); return;}
 		if (function==UNCALIBRATED_OD) {
 			cTable = new float[256];
			for (int i=0; i<256; i++)
				cTable[i] = (float)od(i);
		} else if (function>=STRAIGHT_LINE && function<=GAMMA_VARIATE && coefficients!=null) {
 			cTable = new float[256];
 			for (int i=0; i<256; i++)
				cTable[i] = (float)CurveFitter.f(function, coefficients, i);
		} else
 			cTable = null;
  	}

 	void make16BitCTable() {
		if (function>=STRAIGHT_LINE && function<=GAMMA_VARIATE && coefficients!=null) {
 			cTable = new float[65536];
 			for (int i=0; i<65536; i++)
				cTable[i] = (float)CurveFitter.f(function, coefficients, i);
		} else
 			cTable = null;
  	}

	double od(double v) {
		if (invertedLut) {
			if (v==255.0) v = 254.5;
			return 0.434294481*Math.log(255.0/(255.0-v));
		} else {
			if (v==0.0) v = 0.5;
			return 0.434294481*Math.log(255.0/v);
		}
	}
	
  	/** Converts a raw pixel value to a density calibrated value. */
 	public double getCValue(int value) {
		if (function==NONE)
			return value;
		if (function>=STRAIGHT_LINE && function<=GAMMA_VARIATE && coefficients!=null)
			return CurveFitter.f(function, coefficients, value);
		if (cTable==null)
			makeCTable();
 		if (cTable!=null && value>=0 && value<cTable.length)
 			return cTable[value];
 		else
 			return value;
 	}
 	 	
  	/** Converts a raw pixel value to a density calibrated value. */
 	public double getCValue(double value) {
		if (function==NONE)
			return value;
		else
			return getCValue((int)value);
 	}

  	/** Converts a density calibrated value into a raw pixel value. */
 	public double getRawValue(double value) {
		if (function==NONE)
			return value;
		if (cTable==null)
			makeCTable();
		float fvalue = (float)value;
		float smallestDiff = Float.MAX_VALUE;
		float diff;
		int index = 0;
		for (int i=0; i<cTable.length; i++) {
			diff = fvalue - cTable[i];
			if (diff<0f) diff = -diff;
			if (diff<smallestDiff) {
				smallestDiff = diff;
				index = i;
			}
		}
 		return index;
 	}
 	 	
	/** Returns a clone of this object. */
	public Calibration copy() {
		Calibration copy = new Calibration();
		copy.pixelWidth = pixelWidth;
		copy.pixelHeight = pixelHeight;
		copy.pixelDepth = pixelDepth;
		copy.frameInterval = frameInterval;
		copy.xOrigin = xOrigin;
		copy.yOrigin = yOrigin;
		copy.invertY = invertY;
		copy.unit = unit;
		copy.units = units;
		copy.valueUnit = valueUnit;
		copy.function = function;
		copy.coefficients = coefficients;
		copy.cTable = cTable;
		copy.invertedLut = invertedLut;
		copy.bitDepth = bitDepth;
		return copy;
	}
	
   public String toString() {
    	return
    		"w=" + pixelWidth
			+ ", h=" + pixelHeight
			+ ", d=" + pixelDepth
			+ ", unit=" + unit
			+ ", f=" + function
 			+ ", nc=" + (coefficients!=null?""+coefficients.length:"null")
 			+ ", table=" + (cTable!=null?""+cTable.length:"null")
			+ ", vunit=" + valueUnit;
   }
}

