package ij.plugin.frame;
import java.awt.*;
import java.awt.event.*;
import ij.*;
import ij.plugin.*;

/**  This is a non-modal dialog that plugins can extend. */
public class PlugInDialog extends Dialog implements PlugIn, WindowListener, FocusListener {

	String title;
	
	public PlugInDialog(String title) {
		super(IJ.isMacOSX()&&IJ.getInstance()!=null?IJ.getInstance():IJ.isJava16()?null:new Frame(), title);
		enableEvents(AWTEvent.WINDOW_EVENT_MASK);
		this.title = title;
		ImageJ ij = IJ.getInstance();
		addWindowListener(this);
 		addFocusListener(this);
		if (IJ.isLinux()) setBackground(ImageJ.backgroundColor);
		if (ij!=null && !IJ.isMacOSX() && IJ.isJava16()) {
			Image img = ij.getIconImage();
			if (img!=null)
				try {setIconImage(img);} catch (Exception e) {}
		}
	}
	
	public void run(String arg) {
	}
	
    public void windowClosing(WindowEvent e) {
    	if (e.getSource()==this) {
    		close();
    		if (Recorder.record)
    			Recorder.record("run", "Close");
    	}
    }
    
    /** Closes this window. */
    public void close() {
		//setVisible(false);
		dispose();
		WindowManager.removeWindow(this);
    }

    public void windowActivated(WindowEvent e) {
    	ImageJ ij = IJ.getInstance();
		if (IJ.isMacOSX() && ij!=null) {
			IJ.wait(10); // may be needed for Java 1.4 on OS X
			ij.setMenuBar(Menus.getMenuBar());
		}
		WindowManager.setWindow(this);
	}

	public void focusGained(FocusEvent e) {
		//IJ.log("PlugInFrame: focusGained");
		WindowManager.setWindow(this);
	}

    public void windowOpened(WindowEvent e) {}
    public void windowClosed(WindowEvent e) {}
    public void windowIconified(WindowEvent e) {}
    public void windowDeiconified(WindowEvent e) {}
    public void windowDeactivated(WindowEvent e) {}
	public void focusLost(FocusEvent e) {}
}
