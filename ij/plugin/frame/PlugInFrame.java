package ij.plugin.frame;
import java.awt.*;
import java.awt.event.*;
import ij.*;
import ij.plugin.*;

/**  This is a closeable window that plugins can extend. */
public class PlugInFrame extends Frame implements PlugIn, WindowListener {

	String title;
	
	public PlugInFrame(String title) {
		super(title);
		enableEvents(AWTEvent.WINDOW_EVENT_MASK);
		this.title = title;
		ImageJ ij = IJ.getInstance();
		addWindowListener(this);
		if (ij!=null) {
			Image img = ij.getIconImage();
			if (img!=null) setIconImage(img);
		}
		if (IJ.debugMode) IJ.write("opening "+title);
	}
	
	public void run(String arg) {
	}
	
    public void windowClosing(WindowEvent e) {
		setVisible(false);
		dispose();
		if (IJ.debugMode) IJ.write("closing "+title);
    }

    public void windowActivated(WindowEvent e) {
		if (IJ.isMacintosh() && IJ.getInstance()!=null)
			setMenuBar(Menus.getMenuBar());
	}

    public void windowOpened(WindowEvent e) {}
    public void windowClosed(WindowEvent e) {}
    public void windowIconified(WindowEvent e) {}
    public void windowDeiconified(WindowEvent e) {}
    public void windowDeactivated(WindowEvent e) {}
}