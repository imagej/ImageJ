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

	/** Positions the specified window in the center of the primary screen. */
	public static void center(Window win) {
		if (win==null)
			return;
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		Rectangle bounds = ge.getMaximumWindowBounds();
		Dimension window= win.getSize();
		if (window.width==0)
			return;
		int left = bounds.x + (bounds.width-window.width)/2;
		if (left<bounds.x) left=bounds.x;
		int top = bounds.y + (bounds.height-window.height)/4;
		if (top<bounds.y) top=bounds.y;
		win.setLocation(left, top);
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
