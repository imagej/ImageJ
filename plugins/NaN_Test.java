import ij.*;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import java.awt.*;

public class NaN_Test implements PlugInFilter {

    public int setup(String arg, ImagePlus imp) {
         return DOES_32+SUPPORTS_MASKING ;
    }

    public void run(ImageProcessor ip) {
       float[] pixels = (float[])ip.getPixels();
       Rectangle r = ip.getRoi();
       int width = ip.getWidth();
       int offset1, offset2, value1, value2;
        for (int y=r.y; y<r.y+r.height; y++) {
            for (int x=r.x; x<r.x+r.width; x++) {
                  pixels[y*width+x] = Float.NaN;
            }
        }

    }

}


