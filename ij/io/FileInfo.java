package ij.io;
import java.io.*;
import java.util.Properties;

/** This class consists of public fields that describe an image file. */
public class FileInfo {

	/* 8-bit unsigned integer (0-255). */
	public static final int GRAY8 = 0;
	
	/*	16-bit signed integer (-32768-32767). Imported signed images
		are converted to unsigned by adding 32768. */
	public static final int GRAY16_SIGNED = 1;
	
	/* 16-bit unsigned integer (0-65535). */
	public static final int GRAY16_UNSIGNED = 2;
	
	/*	32-bit signed integer. Imported 32-bit integer images are
		converted to floating-point. */
	public static final int GRAY32_INT = 3;
	
	/* 32-bit floating-point. */
	public static final int GRAY32_FLOAT = 4;
	
	/* 8-bit unsigned integer with color lookup table. */
	public static final int COLOR8 = 5;
	
	/* 24-bit interleaved RGB. Import/export only. */
	public static final int RGB = 6;	
	
	/* 24-bit planer RGB. Import only. */
	public static final int RGB_PLANAR = 7;
	
	// File formats
	public static final int UNKNOWN = 0;
	public static final int RAW = 1;
	public static final int TIFF = 2;
	public static final int GIF_OR_JPG = 3;
	public static final int FITS = 4;
	
	public int fileFormat;
	public int fileType;
	public String fileName;
	public String directory;
	public String url;
    public int width;
    public int height;
    public int offset=0;
    public int nImages;
    public int gapBetweenImages;
    public boolean whiteIsZero;
    public boolean intelByteOrder;
	public int lutSize;
	public byte[] reds;
	public byte[] greens;
	public byte[] blues;
	public Object pixels;	
	public String info;
	InputStream inputStream;
	
	public double pixelWidth=1.0;
	public double pixelHeight=1.0;
	public double pixelDepth=1.0;
	public String unit;
	public int calibrationFunction;
	public double[] coefficients;
	public String valueUnit;
    
	/** Creates a FileInfo object with all of its fields set to their default value. */
     public FileInfo() {
    	// assign default values
    	fileFormat = UNKNOWN;
    	fileType = GRAY8;
    	fileName = "Untitled";
    	directory = "";
    	url = "";
	    nImages = 1;
    }
    
	/** Returns the number of bytes used per pixel. */
	public int getBytesPerPixel() {
		switch (fileType) {
			case GRAY8: case COLOR8: return 1;
			case GRAY16_SIGNED: case GRAY16_UNSIGNED: return 2;
			case GRAY32_INT: case GRAY32_FLOAT: return 4;
			case RGB: case RGB_PLANAR: return 3;
			default: return 0;
		}
	}

    public String toString() {
    	return
    		"name=" + fileName
			+ ", dir=" + directory
			+ ", url=" + url
			+ ", width=" + width
			+ ", height=" + height
			+ ", nImages=" + nImages
			+ ", type=" + fileType
			+ ", offset=" + offset
			+ ", whiteZero=" + (whiteIsZero?"t":"f")
			+ ", Intel=" + (intelByteOrder?"t":"f")
			+ ", lutSize=" + lutSize;
    }
    
}