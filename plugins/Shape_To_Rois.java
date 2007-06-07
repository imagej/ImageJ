import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;

public class Shape_To_Rois implements PlugIn {

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		Roi roi = imp.getRoi();
		if (!(roi instanceof ShapeRoi)) return;
		ShapeRoi shape = (ShapeRoi)roi;
		Roi[] rois = shape.getRois();
		for (int i=0; i<rois.length; i++) {
			imp.setRoi(rois[i]);
			IJ.wait(2000);
		}
	}

}
