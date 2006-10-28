import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;

public class get_Directory implements PlugIn {

	public void run(String arg) {
		String dir = IJ.getDirectory("Choose a directory");
		IJ.log(dir);
	}

}
