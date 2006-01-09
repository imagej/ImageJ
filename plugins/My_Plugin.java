import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;

public class My_Plugin implements PlugIn {

	public void run(String arg) {
		ImagePlus imp = IJ.openImage("/Users/wayne/Pictures/E815/12-21-05_1024.jpg");
		IJ.log(""+imp);
		imp.show();
	}

}
