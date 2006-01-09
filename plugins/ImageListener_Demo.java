import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;

public class ImageListener_Demo implements PlugIn, ImageListener {

	public void run(String arg) {
		ImagePlus.addImageListener(this);
	}

	public void imageOpened(ImagePlus imp) {
		IJ.log(imp.getTitle() + " opened");
	}

	public void imageClosed(ImagePlus imp) {
		IJ.log(imp.getTitle() + " closed");
	}

	public void imageUpdated(ImagePlus imp) {
		IJ.log(imp.getTitle() + " updated");
	}
	
}
