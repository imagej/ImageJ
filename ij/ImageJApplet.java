package ij;
import java.applet.Applet;

	/** Runs ImageJ as an applet and optionally opens images 
		using URLs that are passed as a parameters. */
public class ImageJApplet extends Applet {

	/** Starts ImageJ if it's not already running. */
    public void init() {
    	ImageJ ij = IJ.getInstance();
     	if (ij==null || (ij!=null && !ij.isShowing()))
			new ImageJ(this);
		for (int i=1; i<=9; i++) {
			String url = getParameter("url"+i);
			if (url==null) break;
			ImagePlus imp = new ImagePlus(url);
			if (imp!=null) imp.show();
		}
    }

}

