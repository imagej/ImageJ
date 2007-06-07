import ij.*;
import ij.plugin.PlugIn;
import ij.gui.*;

public class Try_Toolbar implements PlugIn {

	public void run(String arg) {
		Toolbar tb = Toolbar.getInstance();
		int tool = tb.addTool("Ellipse");
IJ.log(""+tool);
		tb.setTool(tool);
		//tb.setTool(15);

	}
}
