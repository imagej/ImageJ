package ij.io;
import java.io.*;
import ij.*;
import ij.process.*;

/** Saves an image described by an ImageProcessor object as a tab-delimited text file. */
public class TextEncoder {

	ImageProcessor ip;

	/** Constructs a TextEncoder from an ImageProcessor. */
	public TextEncoder (ImageProcessor ip) {
		this.ip = ip;
	}
	
	/** Saves the image as a tab-delimited text file. */
	public void write(DataOutputStream out) throws IOException {
		PrintWriter pw = new PrintWriter(out);
		boolean intData = (ip instanceof ByteProcessor) || (ip instanceof ShortProcessor);
		int width = ip.getWidth();
		int height = ip.getHeight();
		int inc = height/20;
		if (inc<1) inc = 1;
		IJ.showStatus("Exporting as text...");
		double value;
		for (int y=0; y<height; y++) {
			for (int x=0; x<width; x++) {
				value = ip.getPixelValue(x,y);
				if (intData)
					pw.print((int)value);
				else
					pw.print(IJ.d2s(value));
				if (x!=(width-1))
					pw.print("\t");
			}
			pw.println();
			if (y%inc==0) IJ.showProgress((double)y/height);
		}
		pw.close();
		IJ.showProgress(1.0);
		IJ.showStatus("");
	}
	
}
