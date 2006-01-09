import ij.ImagePlus;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;

/* W. Burger, M. J. Burge: "Digitale Bildverarbeitung" 
 * © Springer-Verlag, 2005
 * www.imagingbook.com
*/

public class My_Inverter implements PlugInFilter {

	public int setup(String arg, ImagePlus img) {
		return DOES_8G;	// this plugin accepts 8-bit grayscale images
	}

	public void run(ImageProcessor ip) {
		int w = ip.getWidth();
		int h = ip.getHeight();
		int size = w*h;

		ByteProcessor ip2 = (ByteProcessor)ip;
		for (int i = 0; i < size; i++) {
			int p = ip2.get(i);
			ip2.set(i,255-p);
		}
	}
			
}
