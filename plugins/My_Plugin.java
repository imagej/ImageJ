import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;

public class My_Plugin implements PlugIn {

	public void run(String arg) {
		IJ.getImage().setRoi(new PointRoi(100, 100));
	}

}
