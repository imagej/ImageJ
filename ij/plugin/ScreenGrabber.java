package ij.plugin;
import ij.*;
import java.awt.*;

/** Implements the Plugins/Utilities/Capture Screen command. */
public class ScreenGrabber implements PlugIn {
    
    public void run(String arg) {
        if (!IJ.isJava2()) {
            IJ.error("Screen Grabber", "Java 1.3 or later required");
            return;
        }
        try {
            Robot robot = new Robot();
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            Dimension dimension = toolkit.getScreenSize();
            Rectangle r = new Rectangle(dimension);
            Image img = robot.createScreenCapture(r);
            if (img!=null)
                    new ImagePlus("Screen", img).show();
        } catch(Exception e) {
        }
    }

}

