import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;

public class Version_Tester implements PlugIn {

	public void run(String arg) {
		IJ.log("getVersion: "+IJ.getVersion());
		IJ.log("VERSION: "+ImageJ.VERSION);
	}

}
