import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;

public class Macro_ implements PlugIn {

	public void run(String arg) {
		IJ.runMacro("setKeyDown('alt'); run('RGB Split');");	}

}
