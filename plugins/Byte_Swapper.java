import ij.*;
import ij.process.*;
import ij.plugin.filter.*;

public class Byte_Swapper implements PlugInFilter {
	ImagePlus imp;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_16+DOES_32+DOES_STACKS;
	}

	public void run(ImageProcessor ip) {
		if (ip instanceof ShortProcessor)
			swapShortBytes(ip);
		else
			swapFloatBytes(ip);
	}
	
	void swapShortBytes(ImageProcessor ip) {
		short[] pixels = (short[])ip.getPixels();
		int value, byte1, byte2;
		for (int i=0; i<pixels.length; i++) {
			value = pixels[i];
			byte1 = value&0xff;
			byte2 = (value>>8)&0xff;
			pixels[i] = (short)(byte1<<8 | byte2);
		}
	}

	void swapFloatBytes(ImageProcessor ip) {
		float[] pixels = (float[])ip.getPixels();
		int value, byte1, byte2, byte3, byte4;
		for (int i=0; i<pixels.length; i++) {
			value = Float.floatToIntBits(pixels[i]);
			byte1 = value&0xff;
			byte2 = (value>>8)&0xff;
			byte3 = (value>>16)&0xff;
			byte4 = (value>>24)&0xff;
			value = byte1<<24 | byte2<<16 | byte3<<8 | byte4;
			pixels[i] = Float.intBitsToFloat(value);
		}
	}
	
}

