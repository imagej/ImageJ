import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;

public class Macro_ implements PlugIn {

	public void run(String arg) {
		IJ.getImage().	setProperty("Info", "This is some text for the info property\\ Another line.");

	}

}
