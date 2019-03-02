package ij.gui;
import ij.*;
import java.awt.*;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.UIManager;

/** This class consists of static GUI utility methods. */
public class GUI {
	private static final Font DEFAULT_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
	private static Color lightGray = new Color(240,240,240);
	private static boolean isWindows8;
	private static Rectangle maxBounds;
	private static Rectangle zeroBasedMaxBounds;
	private static Rectangle unionOfBounds;

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
		if (GraphicsEnvironment.isHeadless())
			return new Rectangle(0,0,0,0);
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		Rectangle bounds = ge.getMaximumWindowBounds();
		if (IJ.isLinux() && unionOfBounds==null)
			unionOfBounds = getUnionOfBounds(ge);
		zeroBasedMaxBounds = null;
		if (bounds.x>300 || bounds.equals(unionOfBounds))
			bounds = getZeroBasedMonitor(ge, bounds);
		if (bounds.x<0 || bounds.x>300 || bounds.width<300) {
			Dimension screen = getScreenSize();
			bounds = new Rectangle(0, 0, screen.width, screen.height);
		}
		if (IJ.debugMode) IJ.log("GUI.getMaxWindowBounds: "+bounds);
		maxBounds = bounds;
		return bounds;
	}

	public static Rectangle getZeroBasedMaxBounds() {
		if (maxBounds==null)
			getMaxWindowBounds();
		//if (IJ.debugMode) IJ.log("GUI.getZeroBasedMaxBounds: "+zeroBasedMaxBounds);
		return zeroBasedMaxBounds;
	}
	
	private static Dimension getScreenSize() {
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] gd = ge.getScreenDevices();
		GraphicsConfiguration[] gc = gd[0].getConfigurations();
		Rectangle bounds = gc[0].getBounds();
		if ((bounds.x==0&&bounds.y==0) || (IJ.isLinux()&&gc.length>1))
			return new Dimension(bounds.width, bounds.height);
		else
			return Toolkit.getDefaultToolkit().getScreenSize();
	}
	
	public static Rectangle getUnionOfBounds() {
		if (unionOfBounds==null)
			getMaxWindowBounds();
		return unionOfBounds;
	}

	private static Rectangle getUnionOfBounds(GraphicsEnvironment ge) {
		Rectangle virtualBounds = new Rectangle();
		GraphicsDevice[] gs = ge.getScreenDevices();
		Rectangle bounds2 = null;
		int nMonitors = 0;
		for (int j = 0; j < gs.length; j++) {
			GraphicsDevice gd = gs[j];
			GraphicsConfiguration[] gc = gd.getConfigurations();
			for (int i=0; i < gc.length; i++) {
				Rectangle bounds = gc[i].getBounds();
				if (bounds!=null && !bounds.equals(bounds2)) {
					virtualBounds = virtualBounds.union(bounds);
					nMonitors++;
				}
				bounds2 = bounds;
			}
		}
		if (nMonitors<2)
			virtualBounds = new Rectangle(0,0,1,1);
		if (IJ.debugMode) IJ.log("GUI.getUnionOfBounds: "+nMonitors+" "+virtualBounds);
		return virtualBounds;
	} 

	private static Rectangle getZeroBasedMonitor(GraphicsEnvironment ge, Rectangle bounds) {
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
		//if (IJ.debugMode) IJ.log("GUI.getZeroBasedMonitor: "+bounds2);
		if (bounds2!=null) {
			bounds = bounds2;
			zeroBasedMaxBounds = bounds2;
		}
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
    }
    
    public static boolean showCompositeAdvisory(ImagePlus imp, String title) {
    	if (imp==null || imp.getCompositeMode()!=IJ.COMPOSITE || imp.getNChannels()==1 || IJ.macroRunning())
    		return true;
    	String msg = "Channel "+imp.getC()+" of this color composite image will be processed.";
		GenericDialog gd = new GenericDialog(title);
		gd.addMessage(msg);
		gd.showDialog();
		return !gd.wasCanceled();
	}
	
	/**
	 * Scales an AWT component according to {@link Prefs#getGuiScale()}.
	 * @param component the AWT component to be scaled. If a container, scaling is applied to all its child components
	 */
	public static void scale(final Component component) {
		final float scale = (float)Prefs.getGuiScale();
		if (scale==1f)
			return;
		if (component instanceof Container)
			scaleComponents((Container)component, scale);
		else
			scaleComponent(component, scale);
	}

	private static void scaleComponents(final Container container, final float scale) {
		for (final Component child : container.getComponents()) {
			if (child instanceof Container)
				scaleComponents((Container) child, scale);
			else
				scaleComponent(child, scale);
		}
	}

	private static void scaleComponent(final Component component, final float scale) {
		Font font = component.getFont();
		if (font == null)
			font = DEFAULT_FONT;
		font = font.deriveFont(scale*font.getSize());
		component.setFont(font);
	}

	public static void scalePopupMenu(final PopupMenu popup) {
		final float scale = (float) Prefs.getGuiScale();
		if (scale==1f)
			return;
		Font font = popup.getFont();
		if (font == null)
			font = DEFAULT_FONT;
		font = font.deriveFont(scale*font.getSize());
		popup.setFont(font);
	}
	
	/**
	 * Tries to detect if a Swing component is unscaled and scales it it according
	 * to {@link #getGuiScale()}.
	 * <p>
	 * This is mainly relevant to linux: Swing components scale automatically on
	 * most platforms, specially since Java 8. However there are still exceptions to
	 * this on linux: e.g., In Ubuntu, Swing components do scale, but only under the
	 * GTK L&F. (On the other hand AWT components do not scale <i>at all</i> on
	 * hiDPI screens on linux).
	 * </p>
	 * <p>
	 * This method tries to avoid exaggerated font sizes by detecting if a component
	 * has been already scaled by the UIManager, applying only
	 * {@link #getGuiScale()} to the component's font if not.
	 * </p>
	 *
	 * @param component the component to be scaled
	 * @return true, if component's font was resized
	 */
	public static boolean scale(final JComponent component) {
		final double guiScale = Prefs.getGuiScale();
		if (guiScale == 1d)
			return false;
		Font font = component.getFont();
		if (font == null && component instanceof JList)
			font = UIManager.getFont("List.font");
		else if (font == null && component instanceof JTable)
			font = UIManager.getFont("Table.font");
		else if (font == null)
			font = UIManager.getFont("Label.font");
		if (font.getSize() > DEFAULT_FONT.getSize())
			return false;
		if (component instanceof JTable)
			((JTable) component).setRowHeight((int) (((JTable) component).getRowHeight() * guiScale * 0.9));
		else if (component instanceof JList)
			((JList<?>) component).setFixedCellHeight((int) (((JList<?>) component).getFixedCellHeight() * guiScale * 0.9));
		component.setFont(font.deriveFont((float) guiScale * font.getSize()));
		return true;
	}

}
