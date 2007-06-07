import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;

public class ShapeRoi_Test implements PlugIn {

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		Roi roi = imp.getRoi();
		float[] array = ((ShapeRoi)roi).getShapeAsArray();
		for (int i=0; i<array.length; i++)
			IJ.log(i+" "+array[i]);
		imp.killRoi();
		IJ.wait(1000);
		imp.setRoi(new ShapeRoi(array));
	}

}
