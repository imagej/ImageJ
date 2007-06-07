import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;

public class Macro_ implements PlugIn {

	public void run(String arg) {
		//((ImagePlus)IJ.runPlugIn("ij.plugin.DICOM", "/Users/wayne/Desktop/screen.tiff")).show();
		DICOM dcm = new DICOM();
		String path = "/Users/wayne/Desktop/Heart1.dcm";
		dcm.open(path);
		if (dcm.getWidth()==0)
			IJ.log("Error opening '"+path+"'");
		else
			dcm.show();
	}
}
