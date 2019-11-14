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
	private static Color scrollbarBackground = new Color(245,245,245);

	static {
		if (IJ.isWindows()) {
			String osname = System.getProperty("os.name");
			isWindows8 = osname.contains("unknown") || osname.contains("8");
		}
	}

	/** Positions the specified window in the center of the screen that contains target. */
	public static void center(Window win, Component target) {
		if (win == null)
			return;
		Rectangle bounds = getMaxWindowBounds(target);
		Dimension window = win.getSize();
		if (window.width == 0)
			return;
		int left = bounds.x + Math.max(0, (bounds.width - window.width) / 2);
		int top = bounds.y + Math.max(0, (bounds.height - window.height) / 4);
		win.setLocation(left, top);
	}
	
	/** Positions the specified window in the center of the
		 screen containing the "ImageJ" window. */
	public static void centerOnImageJScreen(Window win) {
		center(win, IJ.getInstance());
	}

	public static void center(Window win) {
		center(win, win);
	}
	
	private static java.util.List<GraphicsConfiguration> getScreenConfigs() {
		java.util.ArrayList<GraphicsConfiguration> configs = new java.util.ArrayList<GraphicsConfiguration>();
		for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
			configs.add(device.getDefaultConfiguration());
		}
		return configs;
	}
	
	/**
	 * Get maximum bounds for the screen that contains a given point.
	 * @param point Coordinates of point.
	 * @param accountForInsets Deduct the space taken up by menu and status bars, etc. (after point is found to be inside bonds)
	 * @return Rectangle of bounds or <code>null</code> if point not inside of any screen.
	 */
	public static Rectangle getScreenBounds(Point point, boolean accountForInsets) {
		if (GraphicsEnvironment.isHeadless())
			return new Rectangle(0,0,0,0);
		for (GraphicsConfiguration config : getScreenConfigs()) {
			Rectangle bounds = config.getBounds();
			if (bounds != null && bounds.contains(point)) {
				Insets insets = accountForInsets ? Toolkit.getDefaultToolkit().getScreenInsets(config) : null;
				return shrinkByInsets(bounds, insets);
			}
		}
		return null;		
	}
	
	/**
	 * Get maximum bounds for the screen that contains a given component.
	 * @param component An AWT component located on the desired screen.
	 * If <code>null</code> is provided, the default screen is used.
	 * @param accountForInsets Deduct the space taken up by menu and status bars, etc.
	 * @return Rectangle of bounds.
	 */	
	public static Rectangle getScreenBounds(Component component, boolean accountForInsets) {
		if (GraphicsEnvironment.isHeadless())
			return new Rectangle(0,0,0,0);
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();		
		GraphicsConfiguration gc = component == null ? ge.getDefaultScreenDevice().getDefaultConfiguration() :
													   component.getGraphicsConfiguration();   
		Insets insets = accountForInsets ? Toolkit.getDefaultToolkit().getScreenInsets(gc) : null;
		return shrinkByInsets(gc.getBounds(), insets);
	}

	public static Rectangle getScreenBounds(Point point) {
		return getScreenBounds(point, false);
	}		

	public static Rectangle getScreenBounds(Component component) {
		return getScreenBounds(component, false);
	}			

	public static Rectangle getScreenBounds() {
		return getScreenBounds((Component)null);
	}			

	public static Rectangle getMaxWindowBounds(Point point) {
		return getScreenBounds(point, true);
	}

	public static Rectangle getMaxWindowBounds(Component component) {
		return getScreenBounds(component, true);
	}
	
	public static Rectangle getMaxWindowBounds() {
		return getMaxWindowBounds((Component)null);
	}
	
	private static Rectangle shrinkByInsets(Rectangle bounds, Insets insets) {
		Rectangle shrunk = new Rectangle(bounds);
		if (insets == null) return shrunk; 
		shrunk.x += insets.left;
		shrunk.y += insets.top;
		shrunk.width -= insets.left + insets.right;
		shrunk.height -= insets.top + insets.bottom;
		return shrunk;
	}
	
	public static Rectangle getZeroBasedMaxBounds() {
		for (GraphicsConfiguration config : getScreenConfigs()) {
			Rectangle bounds = config.getBounds();
			if (bounds != null && bounds.x == 0 && bounds.y == 0)
				return bounds;
		}
		return null;
	}
	
	public static Rectangle getUnionOfBounds() {
		Rectangle unionOfBounds = new Rectangle();
		for (GraphicsConfiguration config : getScreenConfigs()) {
			unionOfBounds = unionOfBounds.union(config.getBounds());
		}
		return unionOfBounds;
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
	
		/** Works around an OpenJDK bug on Windows that
		 * causes the scrollbar thumb color and background
		 * color to be almost identical.
		*/
		public static final void fixScrollbar(Scrollbar sb) {
		if (IJ.isWindows())
			sb.setBackground(scrollbarBackground);
	}	

}
