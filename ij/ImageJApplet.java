package ij;
import java.applet.Applet;

/** This is a shell applet that runs ImageJ. */
public class ImageJApplet extends Applet {

	/** Starts ImageJ if it's not already running. */
    public void init() {
     	if (IJ.getInstance()==null)
			new ImageJ(this);
    }

}

