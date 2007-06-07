package ij.plugin.frame;
import java.awt.*;
import java.awt.event.*;
import ij.*;
import ij.plugin.*;

/**  This is a closeable window that plugins can extend. */
public class PlugInFrame extends Frame implements PlugIn {

	String title;
	
	public PlugInFrame(String title) {
		super(title);
		enableEvents(AWTEvent.WINDOW_EVENT_MASK);
		this.title = title;
		ImageJ ij = IJ.getInstance();
		if (ij!=null) {
			Image img = ij.getIconImage();
			if (img!=null) setIconImage(img);
		}
		if (IJ.debugMode) IJ.write("opening "+title);
	}
	
	public void run(String arg) {
	}
	
	public void processWindowEvent(WindowEvent e) {
		super.processWindowEvent(e);
		if (e.getID()==WindowEvent.WINDOW_CLOSING) {	
			setVisible(false);
			dispose();
			if (IJ.debugMode) IJ.write("closing "+title);
		}
	}
}