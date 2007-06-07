import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;
import ij.io.*;

public class Macro_ implements PlugIn {

	public void run(String arg) {
		String description = (String)IJ.getImage().getProperty("description");
		IJ.log("description="+description);
		FileInfo fi = IJ.getImage().getOriginalFileInfo();
		IJ.log("info=" + fi.info);
		IJ.log("description=" + fi.description);
	}

}
