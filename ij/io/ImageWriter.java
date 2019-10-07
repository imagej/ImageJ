package ij.io;
import java.io.*;
import ij.*;  //??
import ij.process.ImageProcessor;

/** Writes a raw image described by a FileInfo object to an OutputStream. */
public class ImageWriter {
	private FileInfo fi;
	private boolean showProgressBar=true;
	private boolean savingStack;
	
	public ImageWriter (FileInfo fi) {
		this.fi = fi;
	}
	
	private void showProgress(double progress) {
		if (showProgressBar)
			IJ.showProgress(progress);
	}
	
	void write8BitImage(OutputStream out, byte[] pixels)  throws IOException {
		int bytesWritten = 0;
		int size = fi.width*fi.height;
		int count = getCount(size);
		while (bytesWritten<size) {
			if ((bytesWritten + count)>size)
				count = size - bytesWritten;
			//System.out.println(bytesWritten + " " + count + " " + size);
			out.write(pixels, bytesWritten, count);
			bytesWritten += count;
			showProgress((double)bytesWritten/size);
		}
	}
	
	void write8BitStack(OutputStream out, Object[] stack)  throws IOException {
		showProgressBar = false;
		savingStack = true;
		for (int i=0; i<fi.nImages; i++) {
			IJ.showStatus("Writing: " + (i+1) + "/" + fi.nImages);
			write8BitImage(out, (byte[])stack[i]);
			IJ.showProgress((double)(i+1)/fi.nImages);
		}
	}

	void write8BitVirtualStack(OutputStream out, VirtualStack virtualStack)  throws IOException {
		showProgressBar = false;
		boolean flip = "FlipTheseImages".equals(fi.fileName);
		for (int i=1; i<=fi.nImages; i++) {
			IJ.showStatus("Writing: " + i + "/" + fi.nImages);
			ImageProcessor ip = virtualStack.getProcessor(i);
			if (flip) ip.flipVertical();
			byte[] pixels = (byte[])ip.getPixels();
			write8BitImage(out, pixels);
			IJ.showProgress((double)i/fi.nImages);
		}
	}

	void write16BitImage(OutputStream out, short[] pixels)  throws IOException {
		long bytesWritten = 0L;
		long size = 2L*fi.width*fi.height;
		int count = getCount(size);
		byte[] buffer = new byte[count];

		while (bytesWritten<size) {
			if ((bytesWritten + count)>size)
				count = (int)(size-bytesWritten);
			int j = (int)(bytesWritten/2L);
			int value;
			if (fi.intelByteOrder)
				for (int i=0; i < count; i+=2) {
					value = pixels[j];
					buffer[i] = (byte)value;
					buffer[i+1] = (byte)(value>>>8);
					j++;
				}
			else
				for (int i=0; i < count; i+=2) {
					value = pixels[j];
					buffer[i] = (byte)(value>>>8);
					buffer[i+1] = (byte)value;
					j++;
				}
			out.write(buffer, 0, count);
			bytesWritten += count;
			showProgress((double)bytesWritten/size);
		}
	}
	
	void write16BitStack(OutputStream out, Object[] stack)  throws IOException {
		showProgressBar = false;
		for (int i=0; i<fi.nImages; i++) {
			IJ.showStatus("Writing: " + (i+1) + "/" + fi.nImages);
			write16BitImage(out, (short[])stack[i]);
			IJ.showProgress((double)(i+1)/fi.nImages);
		}
	}

	void write16BitVirtualStack(OutputStream out, VirtualStack virtualStack)  throws IOException {
		showProgressBar = false;
		boolean flip = "FlipTheseImages".equals(fi.fileName);
		for (int i=1; i<=fi.nImages; i++) {
			IJ.showStatus("Writing: " + i + "/" + fi.nImages);
			ImageProcessor ip = virtualStack.getProcessor(i);
			if (flip) ip.flipVertical();
			short[] pixels = (short[])ip.getPixels();
			write16BitImage(out, pixels);
			IJ.showProgress((double)i/fi.nImages);
		}
	}

	void writeRGB48Image(OutputStream out, Object[] stack)  throws IOException {
		short[] r = (short[])stack[0];
		short[] g = (short[])stack[1];
		short[] b = (short[])stack[2];
		int size = fi.width*fi.height;
		int count = fi.width*6;
		byte[] buffer = new byte[count];
		for (int line=0; line<fi.height; line++) {
			int index2 = 0;
			int index1 = line*fi.width;
			int value;
			if (fi.intelByteOrder) {
				for (int i=0; i<fi.width; i++) {
					value = r[index1];
					buffer[index2++] = (byte)value;
					buffer[index2++] = (byte)(value>>>8);
					value = g[index1];
					buffer[index2++] = (byte)value;
					buffer[index2++] = (byte)(value>>>8);
					value = b[index1];
					buffer[index2++] = (byte)value;
					buffer[index2++] = (byte)(value>>>8);
					index1++;
				}
			} else {
				for (int i=0; i<fi.width; i++) {
					value = r[index1];
					buffer[index2++] = (byte)(value>>>8);
					buffer[index2++] = (byte)value;
					value = g[index1];
					buffer[index2++] = (byte)(value>>>8);
					buffer[index2++] = (byte)value;
					value = b[index1];
					buffer[index2++] = (byte)(value>>>8);
					buffer[index2++] = (byte)value;
					index1++;
				}
			}
			out.write(buffer, 0, count);
		}
	}

	void writeFloatImage(OutputStream out, float[] pixels)  throws IOException {
		long bytesWritten = 0L;
		long size = 4L*fi.width*fi.height;
		int count = getCount(size);
		byte[] buffer = new byte[count];
		int tmp;

		while (bytesWritten<size) {
			if ((bytesWritten + count)>size)
				count = (int)(size-bytesWritten);
			int j = (int)(bytesWritten/4L);
			if (fi.intelByteOrder)
				for (int i=0; i < count; i+=4) {
					tmp = Float.floatToRawIntBits(pixels[j]);
					buffer[i]   = (byte)tmp;
					buffer[i+1] = (byte)(tmp>>8);
					buffer[i+2] = (byte)(tmp>>16);
					buffer[i+3] = (byte)(tmp>>24);
					j++;
				}
			else
				for (int i=0; i < count; i+=4) {
					tmp = Float.floatToRawIntBits(pixels[j]);
					buffer[i]   = (byte)(tmp>>24);
					buffer[i+1] = (byte)(tmp>>16);
					buffer[i+2] = (byte)(tmp>>8);
					buffer[i+3] = (byte)tmp;
					j++;
				}
			out.write(buffer, 0, count);
			bytesWritten += count;
			showProgress((double)bytesWritten/size);
		}
	}
	
	private int getCount(long imageSize) {
		if (savingStack)
			return (int)imageSize;
		int count = (int)(imageSize/50L);
		if (count<65536)
			count = 65536;
		if (count>imageSize)
			count = (int)imageSize;
		count = (count/4)*4;
		if (IJ.debugMode) IJ.log("ImageWriter: "+imageSize+" "+count+" "+imageSize/50);	
		return count;	
	}
	
	void writeFloatStack(OutputStream out, Object[] stack)  throws IOException {
		showProgressBar = false;
		for (int i=0; i<fi.nImages; i++) {
			IJ.showStatus("Writing: " + (i+1) + "/" + fi.nImages);
			writeFloatImage(out, (float[])stack[i]);
			IJ.showProgress((double)(i+1)/fi.nImages);
		}
	}

	void writeFloatVirtualStack(OutputStream out, VirtualStack virtualStack)  throws IOException {
		showProgressBar = false;
		boolean flip = "FlipTheseImages".equals(fi.fileName);
		for (int i=1; i<=fi.nImages; i++) {
			IJ.showStatus("Writing: " + i + "/" + fi.nImages);
			ImageProcessor ip = virtualStack.getProcessor(i);
			if (flip) ip.flipVertical();
			float[] pixels = (float[])ip.getPixels();
			writeFloatImage(out, pixels);
			IJ.showProgress((double)i/fi.nImages);
		}
	}

	void writeRGBImage(OutputStream out, int[] pixels)  throws IOException {
		long bytesWritten = 0L;
		long size = 3L*fi.width*fi.height;
		int count = fi.width*24;
		byte[] buffer = new byte[count];
		while (bytesWritten<size) {
			if ((bytesWritten+count)>size)
				count = (int)(size-bytesWritten);
			int j = (int)(bytesWritten/3L);
			for (int i=0; i<count; i+=3) {
				buffer[i]   = (byte)(pixels[j]>>16);	//red
				buffer[i+1] = (byte)(pixels[j]>>8);	//green
				buffer[i+2] = (byte)pixels[j];		//blue
				j++;
			}
			out.write(buffer, 0, count);
			bytesWritten += count;
			showProgress((double)bytesWritten/size);
		}
	}
	
	void writeRGBStack(OutputStream out, Object[] stack)  throws IOException {
		showProgressBar = false;
		for (int i=0; i<fi.nImages; i++) {
			IJ.showStatus("Writing: " + (i+1) + "/" + fi.nImages);
			writeRGBImage(out, (int[])stack[i]);
			IJ.showProgress((double)(i+1)/fi.nImages);
		}
	}

	void writeRGBVirtualStack(OutputStream out, VirtualStack virtualStack)  throws IOException {
		showProgressBar = false;
		boolean flip = "FlipTheseImages".equals(fi.fileName);
		for (int i=1; i<=fi.nImages; i++) {
			IJ.showStatus("Writing: " + i + "/" + fi.nImages);
			ImageProcessor ip = virtualStack.getProcessor(i);
			if (flip) ip.flipVertical();
			int[] pixels = (int[])ip.getPixels();
			writeRGBImage(out, pixels);
			IJ.showProgress((double)i/fi.nImages);
		}
	}

	/** Writes the image to the specified OutputStream.
		The OutputStream is not closed. The fi.pixels field
		must contain the image data. If fi.nImages>1
		then fi.pixels must be a 2D array, for example an
 		array of images returned by ImageStack.getImageArray()).
 		The fi.offset field is ignored. */
	public void write(OutputStream out) throws IOException {
		if (fi.pixels==null && fi.virtualStack==null)
				throw new IOException("ImageWriter: fi.pixels==null");
		if (fi.nImages>1 && fi.virtualStack==null && !(fi.pixels instanceof Object[]))
				throw new IOException("ImageWriter: fi.pixels not a stack");
		if (fi.width*fi.height*fi.getBytesPerPixel()<26214400)
			showProgressBar = false; // don't show progress bar if image<25MB
		switch (fi.fileType) {
			case FileInfo.GRAY8:
			case FileInfo.COLOR8:
				if (fi.nImages>1 && fi.virtualStack!=null)
					write8BitVirtualStack(out, fi.virtualStack);
				else if (fi.nImages>1)
					write8BitStack(out, (Object[])fi.pixels);
				else
					write8BitImage(out, (byte[])fi.pixels);
				break;
			case FileInfo.GRAY16_SIGNED:
			case FileInfo.GRAY16_UNSIGNED:
				if (fi.nImages>1 && fi.virtualStack!=null)
					write16BitVirtualStack(out, fi.virtualStack);
				else if (fi.nImages>1)
					write16BitStack(out, (Object[])fi.pixels);
				else
					write16BitImage(out, (short[])fi.pixels);
				break;
			case FileInfo.RGB48:
				writeRGB48Image(out, (Object[])fi.pixels);
				break;
			case FileInfo.GRAY32_FLOAT:
				if (fi.nImages>1 && fi.virtualStack!=null)
					writeFloatVirtualStack(out, fi.virtualStack);
				else if (fi.nImages>1)
					writeFloatStack(out, (Object[])fi.pixels);
				else
					writeFloatImage(out, (float[])fi.pixels);
				break;
			case FileInfo.RGB:
				if (fi.nImages>1 && fi.virtualStack!=null)
					writeRGBVirtualStack(out, fi.virtualStack);
				else if (fi.nImages>1)
					writeRGBStack(out, (Object[])fi.pixels);
				else
					writeRGBImage(out, (int[])fi.pixels);
				break;
			default:
		}
		savingStack = false;
	}
	
}

