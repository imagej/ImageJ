package ij.io;
import java.io.*;
import java.net.*;
import ij.*;  //??

/** Reads raw 8-bit, 16-bit or 32-bit (float or RGB)
	images from a stream or URL. */
public class ImageReader {

    private FileInfo fi;
    private int width, height;
    private long skipCount;
    private int bytesPerPixel, bufferSize, byteCount, nPixels;
	private boolean showProgressBar=true;
	private int eofErrorCount;

	/**
	Constructs a new ImageReader using a FileInfo object to describe the file to be read.
	@see ij.io.FileInfo
	*/
	public ImageReader (FileInfo fi) {
		this.fi = fi;
	    width = fi.width;
	    height = fi.height;
	    skipCount = fi.longOffset>0?fi.longOffset:fi.offset;
	}
	
	void eofError() {
		eofErrorCount++;
	}

	byte[] read8bitImage(InputStream in) throws IOException {
		byte[] pixels;
		int totalRead = 0;
		pixels = new byte[nPixels];
		int count, actuallyRead;

		while (totalRead<byteCount) {
			if (totalRead+bufferSize>byteCount)
				count = byteCount-totalRead;
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
	
	/** Reads a 16-bit image. Signed pixels are converted to unsigned by adding 32768. */
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
				if (count==-1) {
					eofError();
					if (fi.fileType==FileInfo.GRAY16_SIGNED)
						for (int i=base; i<pixels.length; i++)
							pixels[i] = (short)32768;
					return pixels;
				}
				bufferCount += count;
			}
			totalRead += bufferSize;
			showProgress((double)totalRead/byteCount);
			pixelsRead = bufferSize/bytesPerPixel;
			if (fi.intelByteOrder) {
				if (fi.fileType==FileInfo.GRAY16_SIGNED)
					for (int i=base,j=0; i<(base+pixelsRead); i++,j+=2)
						pixels[i] = (short)((((buffer[j+1]&0xff)<<8) | (buffer[j]&0xff))+32768);
				else
					for (int i=base,j=0; i<(base+pixelsRead); i++,j+=2)
						pixels[i] = (short)(((buffer[j+1]&0xff)<<8) | (buffer[j]&0xff));
			} else {
				if (fi.fileType==FileInfo.GRAY16_SIGNED)
					for (int i=base,j=0; i<(base+pixelsRead); i++,j+=2)
						pixels[i] = (short)((((buffer[j]&0xff)<<8) | (buffer[j+1]&0xff))+32768);
				else
					for (int i=base,j=0; i<(base+pixelsRead); i++,j+=2)
						pixels[i] = (short)(((buffer[j]&0xff)<<8) | (buffer[j+1]&0xff));
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
					if (fi.fileType==FileInfo.GRAY32_FLOAT)
						pixels[i] = Float.intBitsToFloat(tmp);
					else if (fi.fileType==FileInfo.GRAY32_UNSIGNED)
						pixels[i] = (float)(tmp&0xffffffffL);
					else
						pixels[i] = tmp;
					j += 4;
				}
			else
				for (int i=base; i < (base+pixelsRead); i++) {
					tmp = (int)(((buffer[j]&0xff)<<24) | ((buffer[j+1]&0xff)<<16) | ((buffer[j+2]&0xff)<<8) | (buffer[j+3]&0xff));
					if (fi.fileType==FileInfo.GRAY32_FLOAT)
						pixels[i] = Float.intBitsToFloat(tmp);
					else if (fi.fileType==FileInfo.GRAY32_UNSIGNED)
						pixels[i] = (float)(tmp&0xffffffffL);
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
			boolean bgr = fi.fileType==FileInfo.BGR;
			int j = 0;
			for (int i=base; i<(base+pixelsRead); i++) {
				if (bytesPerPixel==4)
					j++; // ignore alfa byte
				r = buffer[j++]&0xff;
				g = buffer[j++]&0xff;
				b = buffer[j++]&0xff;
				if (bgr)
					pixels[i] = 0xff000000 | (b<<16) | (g<<8) | r;
				else
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

	Object readRGB48(InputStream in) throws IOException {
		int pixelsRead;
		bufferSize = 24*width;
		byte[] buffer = new byte[bufferSize];
		short[] red = new short[nPixels];
		short[] green = new short[nPixels];
		short[] blue = new short[nPixels];
		int totalRead = 0;
		int base = 0;
		int count, value;
		int bufferCount;
		
		Object[] stack = new Object[3];
		stack[0] = red;
		stack[1] = green;
		stack[2] = blue;
		while (totalRead<byteCount) {
			if ((totalRead+bufferSize)>byteCount)
				bufferSize = byteCount-totalRead;
			bufferCount = 0;
			while (bufferCount<bufferSize) { // fill the buffer
				count = in.read(buffer, bufferCount, bufferSize-bufferCount);
				if (count==-1) {eofError(); return stack;}
				bufferCount += count;
			}
			totalRead += bufferSize;
			showProgress((double)totalRead/byteCount);
			pixelsRead = bufferSize/bytesPerPixel;
			if (fi.intelByteOrder) {
				for (int i=base,j=0; i<(base+pixelsRead); i++) {
					red[i] = (short)(((buffer[j+1]&0xff)<<8) | (buffer[j]&0xff)); j+=2;
					green[i] = (short)(((buffer[j+1]&0xff)<<8) | (buffer[j]&0xff)); j+=2;
					blue[i] = (short)(((buffer[j+1]&0xff)<<8) | (buffer[j]&0xff)); j+=2;
				}
			} else {
				for (int i=base,j=0; i<(base+pixelsRead); i++) {
					red[i] = (short)(((buffer[j]&0xff)<<8) | (buffer[j+1]&0xff)); j+=2;
					green[i] = (short)(((buffer[j]&0xff)<<8) | (buffer[j+1]&0xff)); j+=2;
					blue[i] = (short)(((buffer[j]&0xff)<<8) | (buffer[j+1]&0xff)); j+=2;
				}
			}
			base += pixelsRead;
		}
		return stack;
	}

	short[] read12bitImage(InputStream in) throws IOException {
		int nBytes = (int)(nPixels*1.5);
		if ((nPixels&1)==1) nBytes++; // add 1 if odd
		byte[] buffer = new byte[nBytes];
		short[] pixels = new short[nPixels];
		int totalRead = 0;
		int count, actuallyRead;		
		DataInputStream dis = new DataInputStream(in);
		dis.readFully(buffer);
		int i = 0;
		int j = 0;
		for (int index=0; index<buffer.length/3; index++) {
			pixels[j++] = (short)(((buffer[i]&0xff)*16) + ((buffer[i+1]>>4)&0xf));
			pixels[j++] = (short)(((buffer[i+1]&0xf)*256) + (buffer[i+2]&0xff));
			i += 3;
		}
		return pixels;
	}

	void skip(InputStream in) throws IOException {
		if (skipCount>0) {
			long bytesRead = 0;
			int skipAttempts = 0;
			long count;
			while (bytesRead<skipCount) {
				count = in.skip(skipCount-bytesRead);
				skipAttempts++;
				if (count==-1 || skipAttempts>5) break;
				bytesRead += count;
				//IJ.log("skip: "+skipCount+" "+count+" "+bytesRead+" "+skipAttempts);
			}
		}
		byteCount = width*height*bytesPerPixel;
		if (fi.fileType==FileInfo.BITMAP) {
 			int scan=width/8, pad = width%8;
			if (pad>0) scan++;
			byteCount = scan*height;
		}
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
				case FileInfo.GRAY16_UNSIGNED:
					bytesPerPixel = 2;
					skip(in);
					return (Object)read16bitImage(in);
				case FileInfo.GRAY32_INT:
				case FileInfo.GRAY32_UNSIGNED:
				case FileInfo.GRAY32_FLOAT:
					bytesPerPixel = 4;
					skip(in);
					return (Object)read32bitImage(in);
				case FileInfo.RGB:
				case FileInfo.BGR:
				case FileInfo.ARGB:
					bytesPerPixel = fi.fileType==FileInfo.ARGB?4:3;
					skip(in);
					return (Object)readChunkyRGB(in);
				case FileInfo.RGB_PLANAR:
					bytesPerPixel = 3;
					skip(in);
					return (Object)readPlanarRGB(in);
				case FileInfo.BITMAP:
					bytesPerPixel = 1;
					skip(in);
					byte[] bitmap = read8bitImage(in);
					expandBitmap(bitmap);
					return (Object)bitmap;
				case FileInfo.RGB48:
					bytesPerPixel = 6;
					skip(in);
					return (Object)readRGB48(in);
				case FileInfo.GRAY12_UNSIGNED:
					skip(in);
					short[] data = read12bitImage(in);
					return (Object)data;
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
	public Object readPixels(InputStream in, long skipCount) {
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
	
 	void expandBitmap(byte[] pixels) {
 		int scan=width/8;
		int pad = width%8;
		if (pad>0) scan++;
		int len = scan*height;
		byte bitmap[] = new byte [len];
		System.arraycopy(pixels, 0, bitmap, 0, len);
		int value1,value2, offset, index;
		for (int y=0; y<height; y++) {
			offset = y*scan;
			index = y*width;
			for (int x=0; x<scan; x++) {
				value1 = bitmap[offset+x]&0xff;
				for (int i=7; i>=0; i--) {
					value2 = (value1&(1<<i))!=0?255:0;
					if (index<pixels.length)
						pixels[index++] = (byte)value2;
				}
			}
		}
	}
	
}

