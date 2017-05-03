package plugins.wilbur;

import java.awt.Color;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Line;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

/**
 *
 * @author W. Burger
 */
public class GetLine_Test implements PlugIn {

    ImageProcessor ip = null;

    @Override
    public void run(String arg) {

        double x1 = 1.0;
        double y1 = 1.0;
        double x2 = 1.0;
        double y2 = 2.1;

        ip = new ByteProcessor(24, 24);
        makeCheckerBoard(ip);

        ip.setInterpolate(true);
        double[] values1 = ip.getLine(x1, y1, x2, y2);
        IJ.log("interpolated: values1.length: " + values1.length);

        ip.setInterpolate(false);
        double[] values2 = ip.getLine(x1, y1, x2, y2);
        IJ.log("discrete: values2.length: " + values2.length);

        ImagePlus im = new ImagePlus("TestImage", ip);
        Roi roi = new Line(x1 + 0.5, y1 + 0.5, x2 + 0.5, y2 + 0.5);
        roi.setStrokeColor(Color.green);

        im.setRoi(roi);
        im.show();
    }

    // -------------------------------------------------
    void makeCheckerBoard(ImageProcessor ip) {
        int w = ip.getWidth();
        int h = ip.getHeight();

        for (int u = 0; u < w; u++) {
            int val = u % 2;
            for (int v = 0; v < h; v++) {
                ip.putPixel(u, v, val * 255);
                val = (val + 1) % 2;
            }
        }
    }

}
