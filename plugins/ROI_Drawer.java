import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;

public class ROI_Drawer implements PlugIn {

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		Polygon p = imp.getRoi().getPolygon();
		ImageProcessor ip = imp.getProcessor();
		ip.setColor(Color.white);
		ip.fillPolygon(p);
		ip.setColor(Color.black);
		ip.drawPolygon(p);
		imp.updateAndDraw();
	}

}
