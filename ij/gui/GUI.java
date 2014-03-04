package ij.gui;
import java.awt.*;
import ij.*;

/** This class consists of static GUI utility methods. */
public class GUI {
	private static Color lightGray = new Color(240,240,240);
	private static boolean isWindows8;

	static {
		if (IJ.isWindows()) {
			String osname = System.getProperty("os.name");
			isWindows8 = osname.contains("unknown") || osname.contains("8");
		}
	}

	/** Positions the specified window in the center of the screen. */
	public static void center(Window win) {
		if (win==null)
			return;
		Rectangle bounds = getMaxWindowBounds();
		Dimension window= win.getSize();
		if (window.width==0)
			return;
		int left = bounds.x + (bounds.width-window.width)/2;
		if (left<bounds.x) left=bounds.x;
		int top = bounds.y + (bounds.height-window.height)/4;
		if (top<bounds.y) top=bounds.y;
		win.setLocation(left, top);
	}
	
	public static Rectangle getMaxWindowBounds() {
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		Rectangle bounds = ge.getMaximumWindowBounds();
		if (bounds.x>300)
			bounds = getPrimaryMonitor(ge, bounds);
		if (bounds.x<0 || bounds.x>300 || bounds.width<300) {
			Dimension screen = IJ.getScreenSize();
			bounds = new Rectangle(0, 0, screen.width, screen.height);
		}
		if (IJ.debugMode) IJ.log("GUI.getMaxWindowBounds: "+bounds);
		return bounds;
	}

	private static Rectangle getPrimaryMonitor(GraphicsEnvironment ge, Rectangle bounds) {
		GraphicsDevice[] gs = ge.getScreenDevices();
		Rectangle bounds2 = null;
		for (int j=0; j<gs.length; j++) {
			GraphicsDevice gd = gs[j];
			GraphicsConfiguration[] gc = gd.getConfigurations();
			for (int i=0; i<gc.length; i++) {
				bounds2 = gc[i].getBounds();
				if (bounds2!=null && bounds.x==0)
					break;
			}
		}
		if (IJ.debugMode) IJ.log("getPrimaryMonitor: "+bounds2);
		bounds = bounds2!=null?bounds2:bounds;
		return bounds;
	}

    static private Frame frame;
    
    /** Creates a white AWT Image image of the specified size. */
    public static Image createBlankImage(int width, int height) {
        if (width==0 || height==0)
            throw new IllegalArgumentException("");
		if (frame==null) {
			frame = new Frame();
			frame.pack();
			frame.setBackground(Color.white);
		}
        Image img = frame.createImage(width, height);
        return img;
    }
    
    /** Lightens overly dark scrollbar background on Windows 8. */
    public static void fix(Scrollbar sb) {
    	if (isWindows8) {
			sb.setBackground(lightGray);
		}
    }
    
}
