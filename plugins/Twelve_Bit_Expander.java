import ij.*;
import ij.process.*;
import ij.plugin.filter.*;

/** Unpacks a 16-bit image containing 12 bit data with 2 pixels packed in 3 bytes. */
public class Twelve_Bit_Expander implements PlugInFilter {

	public int setup(String arg, ImagePlus imp) {
		return DOES_16+DOES_STACKS;
	}

	public void run(ImageProcessor ip) {
		int width = ip.getWidth();
		int height = ip.getHeight();
		int nwords = (int)(width*height*.75);
		byte[] bytes = new byte[nwords*2];
		short[] pixels = (short[])ip.getPixels();
		int j = 0;
		for (int i=0; i<nwords; i++) {
			bytes[j++] = (byte)pixels[i];
			bytes[j++] = (byte)(pixels[i]>>>8);
		}
		int i = 0;
		j = 0;
		for (int index=0; index<bytes.length/3; index++) {
			pixels[j++] = (short)((bytes[i]&0xff) + ((bytes[i+1]&0xf))*256);
			pixels[j++] = (short)((bytes[i+1]>>4) + (bytes[i+2]&0xff)*16);
			i += 3;
		}
		ip.resetMinAndMax();
	}

}
