package ij.io;
import java.io.*;
import java.net.*;
import ij.*;  //??

/** Reads raw 8-bit, 16-bit or 32-bit (float or RGB)
	images from a stream or URL. */
public class ImageReader {

    private FileInfo fi;
    private int width, height, skipCount;
    private int bytesPerPixel, bufferSize, byteCount, nPixels;
	private boolean showProgressBar=true;
	private int eofErrorCount;

	/**
	Constructs a new ImageReader using a FileInfo object to describe the file to be read.
	@see ij.io.FileInfo
	@see ij.plugin.ImageReaderDemo
	*/
	public ImageReader (FileInfo fi) {
		this.fi = fi;
	    width = fi.width;
	    height = fi.height;
	    skipCount = fi.offset;
	}
	
	void eofError() {
		if (eofErrorCount++==1)
			IJ.write("<<End of file exceeded>>");
	}

	byte[] read8bitImage(InputStream in) throws IOException {
		byte[] pixels;
		int totalRead = 0;
		pixels = new byte[nPixels];
		int count, actuallyRead;

		while (totalRead<byteCount) {
			if (totalRead+bufferSize>nPixels)
				count = nPixels-totalRead;
			else
				count = bufferSize;
			actuallyRead = in.read(pixels, totalRead, count);
			if (actuallyRead==-1) {eofError(); break;}
			totalRead += actuallyRead;
			showProgress((double)totalRead/byteCount);
		}
		return pixels;
	}
	
	private void showProgress(double progress) {
		if (showProgressBar)
			IJ.showProgress(progress);
	}
	
	short[] read16bitImage(InputStream in) throws IOException {
		int pixelsRead;
		byte[] buffer = new byte[bufferSize];
		short[] pixels = new short[nPixels];
		int totalRead = 0;
		int base = 0;
		int count, value;
		int bufferCount;
		
		while (totalRead<byteCount) {
			if ((totalRead+bufferSize)>byteCount)
				bufferSize = byteCount-totalRead;
			bufferCount = 0;
			while (bufferCount<bufferSize) { // fill the buffer
				count = in.read(buffer, bufferCount, bufferSize-bufferCount);
				if (count==-1) {eofError(); return pixels;}
				bufferCount += count;
			}
			totalRead += bufferSize;
			showProgress((double)totalRead/byteCount);
			pixelsRead = bufferSize/bytesPerPixel;
			int j = 0;
			if (fi.intelByteOrder)
				for (int i=base; i < (base+pixelsRead); i++) {
					value = ((buffer[j+1]&0xff)<<8) | (buffer[j]&0xff);
					pixels[i] = (short)value;
					j += 2;
				}
			else
				for (int i=base; i < (base+pixelsRead); i++) {
					value = ((buffer[j]&0xff)<<8) | (buffer[j+1]&0xff);
					pixels[i] = (short)value;
					j += 2;
				}
			base += pixelsRead;
		}
		return pixels;
	}
	
	float[] read32bitImage(InputStream in) throws IOException {
		int pixelsRead;
		byte[] buffer = new byte[bufferSize];
		float[] pixels = new float[nPixels];
		int totalRead = 0;
		int base = 0;
		int count, value;
		int bufferCount;
		int tmp;
		
		while (totalRead<byteCount) {
			if ((totalRead+bufferSize)>byteCount)
				bufferSize = byteCount-totalRead;
			bufferCount = 0;
			while (bufferCount<bufferSize) { // fill the buffer
				count = in.read(buffer, bufferCount, bufferSize-bufferCount);
				if (count==-1) {eofError(); return pixels;}
				bufferCount += count;
			}
			totalRead += bufferSize;
			showProgress((double)totalRead/byteCount);
			pixelsRead = bufferSize/bytesPerPixel;
			int j = 0;
			if (fi.intelByteOrder)
				for (int i=base; i < (base+pixelsRead); i++) {
					tmp = (int)(((buffer[j+3]&0xff)<<24) | ((buffer[j+2]&0xff)<<16) | ((buffer[j+1]&0xff)<<8) | (buffer[j]&0xff));
					if (fi.fileType==FileInfo.GRAY32_FLOAT) {
						pixels[i] = Float.intBitsToFloat(tmp);
					}
					else
						pixels[i] = tmp;
					j += 4;
				}
			else
				for (int i=base; i < (base+pixelsRead); i++) {
					tmp = (int)(((buffer[j]&0xff)<<24) | ((buffer[j+1]&0xff)<<16) | ((buffer[j+2]&0xff)<<8) | (buffer[j+3]&0xff));
					if (fi.fileType==FileInfo.GRAY32_FLOAT)
						pixels[i] = Float.intBitsToFloat(tmp);
					else
						pixels[i] = tmp;
					j += 4;
				}
			base += pixelsRead;
		}
		return pixels;
	}
	
	int[] readChunkyRGB(InputStream in) throws IOException {
		int pixelsRead;
		bufferSize = 24*width;
		byte[] buffer = new byte[bufferSize];
		int[] pixels = new int[nPixels];
		int totalRead = 0;
		int base = 0;
		int count, value;
		int bufferCount;
		int r, g, b;
		
		while (totalRead<byteCount) {
			if ((totalRead+bufferSize)>byteCount)
				bufferSize = byteCount-totalRead;
			bufferCount = 0;
			while (bufferCount<bufferSize) { // fill the buffer
				count = in.read(buffer, bufferCount, bufferSize-bufferCount);
				if (count==-1) {eofError(); return pixels;}
				bufferCount += count;
			}
			totalRead += bufferSize;
			showProgress((double)totalRead/byteCount);
			pixelsRead = bufferSize/bytesPerPixel;
			int j = 0;
			for (int i=base; i<(base+pixelsRead); i++) {
				r = buffer[j++]&0xff;
				g = buffer[j++]&0xff;
				b = buffer[j++]&0xff;
				pixels[i] = 0xff000000 | (r<<16) | (g<<8) | b;
			}
			base += pixelsRead;
		}
		return pixels;
	}

	int[] readPlanarRGB(InputStream in) throws IOException {
		int planeSize = nPixels; // 1/3 image size
		byte[] buffer = new byte[planeSize];
		int[] pixels = new int[nPixels];
		int r, g, b;

		int bytesRead;
		int totalRead = 0;
		showProgress(0.12);
		bytesRead = in.read(buffer, 0, planeSize);
		if (bytesRead==-1) {eofError(); return pixels;}
		totalRead += bytesRead;
		for (int i=0; i < planeSize; i++) {
			r = buffer[i]&0xff;
			pixels[i] = 0xff000000 | (r<<16);
		}
		
		showProgress(0.37);
		bytesRead = in.read(buffer, 0, planeSize);
		if (bytesRead==-1) {eofError(); return pixels;}
		totalRead += bytesRead;
		for (int i=0; i < planeSize; i++) {
			g = buffer[i]&0xff;
			pixels[i] |= g<<8;
		}

		showProgress(0.62);
		bytesRead = in.read(buffer, 0, planeSize);
		if (bytesRead==-1) {eofError(); return pixels;}
		totalRead += bytesRead;
		for (int i=0; i < planeSize; i++) {
			b = buffer[i]&0xff;
			pixels[i] |= b;
		}

		showProgress(0.87);
		return pixels;
	}

	void skip(InputStream in) throws IOException {
		if (skipCount>0) {
			int bytesRead = 0;
			int skipAttempts = 0;
			long count;
			while (bytesRead<skipCount) {
				count = in.skip((long)(skipCount-bytesRead));
				skipAttempts++;
				if (count==-1 || skipAttempts>5) break;
				bytesRead += count;
			}
		}
		byteCount = width*height*bytesPerPixel;
		nPixels = width*height;
		bufferSize = byteCount/25;
		if (bufferSize<8192)
			bufferSize = 8192;
		else
			bufferSize = (bufferSize/8192)*8192;
	}
	
	/** 
	Reads the image from the InputStream and returns the pixel
	array (byte, short, int or float). Returns null if there
	was an IO exception. Does not close the InputStream.
	*/
	public Object readPixels(InputStream in) {
		try {
			switch (fi.fileType) {
				case FileInfo.GRAY8:
				case FileInfo.COLOR8:
					bytesPerPixel = 1;
					skip(in);
					return (Object)read8bitImage(in);
				case FileInfo.GRAY16_SIGNED:
					bytesPerPixel = 2;
					skip(in);
					short[] pixels = read16bitImage(in);
					convertToUnsigned(pixels);
					return (Object)pixels;
				case FileInfo.GRAY16_UNSIGNED:
					bytesPerPixel = 2;
					skip(in);
					return (Object)read16bitImage(in);
				case FileInfo.GRAY32_INT:
				case FileInfo.GRAY32_FLOAT:
					bytesPerPixel = 4;
					skip(in);
					return (Object)read32bitImage(in);
				case FileInfo.RGB:
					bytesPerPixel = 3;
					skip(in);
					return (Object)readChunkyRGB(in);
				case FileInfo.RGB_PLANAR:
					bytesPerPixel = 3;
					skip(in);
					return (Object)readPlanarRGB(in);
				default:
					return null;
			}
		}
		catch (IOException e) {
			IJ.write("" + e);
			return null;
		}
	}
	
	/** 
	Skips the specified number of bytes, then reads an image and 
	returns the pixel array (byte, short, int or float). Returns
	null if there was an IO exception. Does not close the InputStream.
	*/
	public Object readPixels(InputStream in, int skipCount) {
		this.skipCount = skipCount;
		showProgressBar = false;
		Object pixels = readPixels(in);
		if (eofErrorCount>0)
			return null;
		else
			return pixels;
	}
	
	/** 
	Reads the image from a URL and returns the pixel array (byte, 
	short, int or float). Returns null if there was an IO exception.
	*/
	public Object readPixels(String url) {
		java.net.URL theURL;
		InputStream is;
		try {theURL = new URL(url);}
		catch (MalformedURLException e) {IJ.write(""+e); return null;}
		try {is = theURL.openStream();}
		catch (IOException e) {IJ.write(""+e); return null;}
		return readPixels(is);
	}
	
	/** If the pixel array contains any value less than zero than
		32768 is added to all values. The file type is changed to
		GRAY16_UNSIGNED if 32768 is not added to the pixel values. */
	void convertToUnsigned(short[] pixels) {
		int min = Integer.MAX_VALUE;
		int value;
		for (int i=0; i<pixels.length; i++) {
			value = pixels[i];
			if (value<min)
				min = value;
		}
		if (min<0 || fi.fileFormat==FileInfo.FITS) {
			for (int i=0; i<pixels.length; i++)
				pixels[i] = (short)(pixels[i]+32768);
		} else
			fi.fileType = FileInfo.GRAY16_UNSIGNED;			
	}

}

