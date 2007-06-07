import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;

public class Histogram_Test implements PlugIn {

    public void run(String arg) {
        double[] x = new double[1000];
        for (int i=0; i<1000; i++)
            x[i] = -3.0 + i * 6.0/1000.0;
        new ImagePlus("test", new FloatProcessor(100, 10, x)).show();
    }

}
  
