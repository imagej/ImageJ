package ij.gui;
import java.awt.*;
import ij.*;

/** This class consists of static GUI utility methods. */
public class GUI {

	/** Positions the specified frame in the center of the screen. */
	public static void center(Window w) {
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		Dimension window = w.getSize();
		if (window.width==0)
			return;
		int left = screen.width/2-window.width/2;
		int top = (screen.height-window.height)/4;
		if (top<0) top = 0;
		w.setLocation(left, top);
		//ij.IJ.write("screen: "+screen);
		//ij.IJ.write("window: "+window);
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
    
}