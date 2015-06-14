package ij.gui;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.awt.datatransfer.*;
import java.util.*;
import ij.*;
import ij.process.*;
import ij.util.*;
import ij.text.TextWindow;
import ij.plugin.filter.Analyzer;
import ij.measure.*;
import ij.io.SaveDialog;

/** This class implements the Analyze/Plot Profile command.
* @author Michael Schmid
* @author Wayne Rasband
*/
public class PlotWindow extends ImageWindow implements ActionListener,	ItemListener,
	ClipboardOwner, ImageListener, RoiListener, Runnable {

	/** Display points using a circle 5 pixels in diameter. */
	public static final int CIRCLE = Plot.CIRCLE;
	/** Display points using an X-shaped mark. */
	public static final int X = Plot.X;
	/** Display points using an box-shaped mark. */
	public static final int BOX = Plot.BOX;
	/** Display points using an tiangular mark. */
	public static final int TRIANGLE = Plot.TRIANGLE;
	/** Display points using an cross-shaped mark. */
	public static final int CROSS = Plot.CROSS;
	/** Connect points with solid lines. */
	public static final int LINE = Plot.LINE;
	/** Save x-values only. To set, use Edit/Options/
		Profile Plot Options. */
	public static boolean saveXValues;
	/** Automatically close window after saving values. To
		set, use Edit/Options/Profile Plot Options. */
	public static boolean autoClose;
	/** Display the XY coordinates in a separate window. To
		set, use Edit/Options/Profile Plot Options. */
	public static boolean listValues;
	/** Interpolate line profiles. To
		set, use Edit/Options/Profile Plot Options. */
	public static boolean interpolate;
	// default values for new installations; values will be then saved in prefs
	private static final int WIDTH = 450;
	private static final int HEIGHT = 200;
	private static final int FONT_SIZE = 12;
	/** The width of the plot (without frame) in pixels. */
	public static int plotWidth = WIDTH;
	/** The height of the plot in pixels. */
	public static int plotHeight = HEIGHT;
	/** The plot text size, can be overridden by Plot.setFont, Plot.setFontSize, Plot.setXLabelFont etc. */
	public static int fontSize = FONT_SIZE;
	/** Have axes with no grid lines. If both noGridLines and noTicks are true,
	 *	only min&max value of the axes are given */
	public static boolean noGridLines;
	/** Have axes with no ticks. If both noGridLines and noTicks are true,
	 *	only min&max value of the axes are given */
	public static boolean noTicks;

	
	private static final String PREFS_WIDTH = "pp.width";
	private static final String PREFS_HEIGHT = "pp.height";
	private static final String PREFS_FONT_SIZE = "pp.fontsize";
	private static final String OPTIONS = "pp.options";
	private static final int SAVE_X_VALUES = 1;
	private static final int AUTO_CLOSE = 2;
	private static final int LIST_VALUES = 4;
	private static final int INTERPOLATE = 8;
	private static final int NO_GRID_LINES = 16;
	private static final int NO_TICKS = 32;
	private static String moreButtonLabel = "More "+'\u00bb';

	private Button list, save, more, live;
	private PopupMenu popupMenu;
	private MenuItem[] menuItems;
	private Label coordinates;
	private static String defaultDirectory = null;
	private static int options;
	private int defaultDigits = -1;
	private int markSize = 5;
	private static Plot staticPlot;
	private Plot plot;
	boolean layoutDone;				// becomes true after the layout has been done, used by PlotCanvas
	private String blankLabel = "                       ";

	private PlotMaker plotMaker;
	private ImagePlus srcImp;		// the source image for live plotting
	private Thread bgThread;		// thread for plotting (in the background)
	private boolean doUpdate;		// tells the background thread to update

	private Roi[] rangeArrowRois;	// these constitute the arrow overlays for changing the range
	private boolean rangeArrowsVisible;
	private int activeRangeArrow = -1;
	

	// static initializer
	static {
		options = Prefs.getInt(OPTIONS, SAVE_X_VALUES);
		saveXValues = (options&SAVE_X_VALUES)!=0;
		autoClose = (options&AUTO_CLOSE)!=0;
		listValues = (options&LIST_VALUES)!=0;
		plotWidth = Prefs.getInt(PREFS_WIDTH, WIDTH);
		plotHeight = Prefs.getInt(PREFS_HEIGHT, HEIGHT);
		fontSize = Prefs.getInt(PREFS_FONT_SIZE, FONT_SIZE);
		interpolate = (options&INTERPOLATE)==0; // 0=true, 1=false
		noGridLines = (options&NO_GRID_LINES)!=0; 
		noTicks = (options&NO_TICKS)!=0; 
	}

	/**
	* @deprecated
	* replaced by the Plot class.
	*/
	public PlotWindow(String title, String xLabel, String yLabel, float[] xValues, float[] yValues) {
		super(createImage(title, xLabel, yLabel, xValues, yValues));
		plot = staticPlot;
		((PlotCanvas)getCanvas()).setPlot(plot);
	}

	/**
	* @deprecated
	* replaced by the Plot class.
	*/
	public PlotWindow(String title, String xLabel, String yLabel, double[] xValues, double[] yValues) {
		this(title, xLabel, yLabel, Tools.toFloat(xValues), Tools.toFloat(yValues));
	}
	
	/** Creates a PlotWindow from a Plot object. */
	PlotWindow(Plot plot) {
		super(plot.getImagePlus());
		((PlotCanvas)getCanvas()).setPlot(plot);
		this.plot = plot;
		draw();
	}

	/** Called by the constructor to generate the image the plot will be drawn on.
		This is a static method because constructors cannot call instance methods. */
	static ImagePlus createImage(String title, String xLabel, String yLabel, float[] xValues, float[] yValues) {
		staticPlot = new Plot(title, xLabel, yLabel, xValues, yValues);
		return new ImagePlus(title, staticPlot.getBlankProcessor());
	}
	
	/** Sets the x-axis and y-axis range.
		Must be called before the plot is displayed. */
	public void setLimits(double xMin, double xMax, double yMin, double yMax) {
		plot.setLimits(xMin, xMax, yMin, yMax);
	}

	/** Adds a set of points to the plot or adds a curve if shape is set to LINE.
		Must be called before the plot is displayed.
	 *	Note that there are more options available by using the methods of the Plot class instead.
	 * @param x			the x-coodinates
	 * @param y			the y-coodinates
	 * @param shape		Plot.CIRCLE, X, BOX, TRIANGLE, CROSS, LINE etc. */

	public void addPoints(float[] x, float[] y, int shape) {
		plot.addPoints(x, y, shape);
	}

	/** Adds a set of points to the plot using double arrays.
		Must be called before the plot is displayed.
		Note that there are more options available by using the methods of the Plot class instead. */
	public void addPoints(double[] x, double[] y, int shape) {
		addPoints(Tools.toFloat(x), Tools.toFloat(y), shape);
	}
	
	/** Adds vertical error bars to the plot.
		Must be called before the plot is displayed.
		Note that there are more options available by using the methods of the Plot class instead. */
	public void addErrorBars(float[] errorBars) {
		plot.addErrorBars(errorBars);
	}

	/** Draws a label.
		Must be called before the plot is displayed.
		Note that there are more options available by using the methods of the Plot class instead. */
	public void addLabel(double x, double y, String label) {
		plot.addLabel(x, y, label);
	}
	
	/** Changes the drawing color. The frame and labels are
		always drawn in black.
		Must be called before the plot is displayed.
		Note that there are more options available by using the methods of the Plot class instead. */
	public void setColor(Color c) {
		plot.setColor(c);
	}

	/** Changes the line width.
		Must be called before the plot is displayed.
		Note that there are more options available by using the methods of the Plot class instead. */
	public void setLineWidth(int lineWidth) {
		plot.setLineWidth(lineWidth);
	}

	/** Changes the font.
		Must be called before the plot is displayed.
		Note that there are more options available by using the methods of the Plot class instead. */
	public void changeFont(Font font) {
		plot.changeFont(font);
	}

	/** Displays the plot. */
	public void draw() {
		Panel bottomPanel = new Panel();
		int hgap = IJ.isMacOSX()?1:5;

		list = new Button(" List ");
		list.addActionListener(this);
		bottomPanel.add(list);
		bottomPanel.setLayout(new FlowLayout(FlowLayout.RIGHT,hgap,0));
		save = new Button("Save...");
		save.addActionListener(this);
		bottomPanel.add(save);
		more = new Button(moreButtonLabel);
		more.addActionListener(this);
		bottomPanel.add(more);
		if (plot!=null && plot.getPlotMaker()!=null) {
			live = new Button("Live");
			live.addActionListener(this);
			bottomPanel.add(live);
		}
		coordinates = new Label(blankLabel);
		coordinates.setFont(new Font("Monospaced", Font.PLAIN, 12));
		coordinates.setBackground(new Color(220, 220, 220));
		bottomPanel.add(coordinates);
		add(bottomPanel);
		more.add(getPopupMenu());
		plot.draw();
		LayoutManager lm = getLayout();
		if (lm instanceof ImageLayout)
			((ImageLayout)lm).ignoreNonImageWidths(true);  //don't expand size to make the panel fit
		pack();

		ImageProcessor ip = plot.getProcessor();
		if ((ip instanceof ColorProcessor) && (imp.getProcessor() instanceof ByteProcessor))
			imp.setProcessor(null, ip);
		else
			imp.updateAndDraw();
		layoutDone = true;
		if (listValues)
			showList();
		else
			ic.requestFocus();	//have focus on the canvas, not the button, so that pressing the space bar allows panning
	}

	/** Releases the resources used by this PlotWindow */
	public void dispose() {
		plot.dispose();
		plot = null;
		plotMaker = null;
		srcImp = null;
		super.dispose();
	}

	/** Called when the canvas is resized */
	void updateMinimumSize() {
		if (plot == null) return;
		Dimension d1 = getExtraSize();
		Dimension d2 = plot.getMinimumSize();
		setMinimumSize(new Dimension(d1.width + d2.width, d1.height + d2.height));
	}

	//names for popupMenu items
	private static int COPY=0, COPY_ALL=1, SET_RANGE=2, PREV_RANGE=3, RESET_RANGE=4, FIT_RANGE=5,
			ZOOM_SELECTION=6, AXIS_OPTIONS=7, LEGEND=8, RESET_PLOT=9, FREEZE=10, HI_RESOLUTION=11,
			PROFILE_PLOT_OPTIONS=12;
	//the following commands are disabled when the plot is frozen
	private static int[] DISABLED_WHEN_FROZEN = new int[]{SET_RANGE, PREV_RANGE, RESET_RANGE,
			FIT_RANGE, ZOOM_SELECTION, AXIS_OPTIONS, LEGEND, RESET_PLOT};
	/** Prepares and returns the popupMenu of the More>> button*/
	PopupMenu getPopupMenu() {
		popupMenu = new PopupMenu();
		menuItems = new MenuItem[13];
		menuItems[COPY] = addPopupItem(popupMenu, "Copy 1st Data Set");
		menuItems[COPY_ALL] = addPopupItem(popupMenu, "Copy All Data");
		popupMenu.addSeparator();
		menuItems[SET_RANGE] = addPopupItem(popupMenu, "Set Range...");
		menuItems[PREV_RANGE] = addPopupItem(popupMenu, "Previous Range");
		menuItems[RESET_RANGE] = addPopupItem(popupMenu, "Reset Range");
		menuItems[FIT_RANGE] = addPopupItem(popupMenu, "Set Range to Fit All");
		menuItems[ZOOM_SELECTION] = addPopupItem(popupMenu, "Zoom to Selection");
		popupMenu.addSeparator();
		menuItems[AXIS_OPTIONS] = addPopupItem(popupMenu, "Axis Options...");
		menuItems[LEGEND] = addPopupItem(popupMenu, "Legend...");
		menuItems[RESET_PLOT] = addPopupItem(popupMenu, "Reset Format");
		menuItems[FREEZE] = addPopupItem(popupMenu, "Freeze Plot", true);
		menuItems[HI_RESOLUTION] = addPopupItem(popupMenu, "High-Resolution Plot...");
		popupMenu.addSeparator();
		menuItems[PROFILE_PLOT_OPTIONS] = addPopupItem(popupMenu, "Profile & Plot Options...");
		return popupMenu;
	}

	MenuItem addPopupItem(PopupMenu popupMenu, String s) {
		return addPopupItem(popupMenu, s, false);
	}

	MenuItem addPopupItem(PopupMenu popupMenu, String s, boolean isCheckboxItem) {
		MenuItem mi = null;
		if (isCheckboxItem) {
			mi = new CheckboxMenuItem(s);
			((CheckboxMenuItem)mi).addItemListener(this);
		} else {
			mi = new MenuItem(s);
			mi.addActionListener(this);
		}
		popupMenu.add(mi);
		return mi;
	}

	/** Called if user has activated a button or popup menu item */
	public void actionPerformed(ActionEvent e) {
		Object b = e.getSource();
		if (b==live)
			toggleLiveProfiling();
		else if (b==list)
			showList();
		else if (b==save)
			saveAsText();
		else if (b==more) {
			boolean frozen = plot.isFrozen();	//prepare menu according to 'frozen' state of plot
			((CheckboxMenuItem)menuItems[FREEZE]).setState(frozen);
			for (int i : DISABLED_WHEN_FROZEN)
				menuItems[i].setEnabled(!frozen);
			popupMenu.show((Component)b, 1, 1);
		}
		else if (b==menuItems[COPY])
			copyToClipboard(false);
		else if (b==menuItems[COPY_ALL])
			copyToClipboard(true);
		else if (b==menuItems[ZOOM_SELECTION]) {
			if (imp!=null && imp.getRoi()!=null && imp.getRoi().isArea())
				plot.zoomToRect(imp.getRoi().getBounds());
		} else if (b==menuItems[SET_RANGE])
			new PlotDialog(plot, PlotDialog.SET_RANGE).showDialog(this);
		else if (b==menuItems[PREV_RANGE])
			plot.setPreviousMinMax();
		else if (b==menuItems[RESET_RANGE])
			plot.setLimitsToDefaults(true);
		else if (b==menuItems[FIT_RANGE])
			plot.setLimitsToFit(true);
		else if (b==menuItems[AXIS_OPTIONS])
			new PlotDialog(plot, PlotDialog.AXIS_OPTIONS).showDialog(this);
		else if (b==menuItems[LEGEND])
			new PlotDialog(plot, PlotDialog.LEGEND).showDialog(this);
		else if (b==menuItems[RESET_PLOT]) {
			plot.setFont(Font.PLAIN, Prefs.getInt(PREFS_FONT_SIZE, FONT_SIZE));
			plot.setAxisLabelFont(Font.PLAIN, Prefs.getInt(PREFS_FONT_SIZE, FONT_SIZE));
			plot.setFormatFlags(Plot.getDefaultFlags());
			plot.setFrameSize(plotWidth, plotHeight); //updates the image
		} else if (b==menuItems[HI_RESOLUTION])
			new PlotDialog(plot, PlotDialog.HI_RESOLUTION).showDialog(this);
		else if (b==menuItems[PROFILE_PLOT_OPTIONS])
			IJ.doCommand("Profile Plot Options...");
		ic.requestFocus();	//have focus on the canvas, not the button, so that pressing the space bar allows panning
	}

	/** Called if the user activates/deactivates a CheckboxMenuItem */
	public void itemStateChanged(ItemEvent e) {
		if (e.getSource()==menuItems[FREEZE]) {
			boolean frozen = ((CheckboxMenuItem)menuItems[FREEZE]).getState();
			plot.setFrozen(frozen);
		}
	}

	/** Updates the X and Y values when the mouse is moved and, if appropriate, shows/hides
	 *	the overlay with the triangular buttons for changing the axis range limits
	 *	Overrides mouseMoved() in ImageWindow. 
	 *	@see ij.gui.ImageWindow#mouseMoved
	 */
	public void mouseMoved(int x, int y) {
		super.mouseMoved(x, y);
		if (plot==null) return;
		if (coordinates!=null) {	//coordinate readout
			String coords = plot.getCoordinates(x,y) + blankLabel;
			coordinates.setText(coords.substring(0, blankLabel.length()));
		}

		//arrows for modifying the plot range
		if (x<plot.leftMargin || y>plot.topMargin+plot.frameHeight) {
			if (!rangeArrowsVisible && !plot.isFrozen())
				showRangeArrows();
			if (activeRangeArrow >= 0 && !rangeArrowRois[activeRangeArrow].contains(x,y)) {
				rangeArrowRois[activeRangeArrow].setFillColor(Color.GRAY);
				ic.repaint();			//de-highlight arrow where cursor has moved out
				activeRangeArrow = -1;
			}
			if (activeRangeArrow < 0) { //highlight arrow below cursor (if any)
				int i = getRangeArrowIndex(x,y);
				if (i >= 0) {			//we have an arrow at cursor position
					rangeArrowRois[i].setFillColor(Color.RED);
					activeRangeArrow = i;
					ic.repaint();
				}
			}
		} else if (rangeArrowsVisible)
			hideRangeArrows();
	}

	/** Called by PlotCanvas */
	void mouseExited(MouseEvent e) {
		if (rangeArrowsVisible)
			hideRangeArrows();
	}

	/** Mouse wheel: zooms when shift or ctrl is pressed, scrolls in x if space bar down, in y otherwise. */
	public synchronized void mouseWheelMoved(MouseWheelEvent e) {
		if (plot.isFrozen() || !(ic instanceof PlotCanvas)) {	   //frozen plots are like normal images
			super.mouseWheelMoved(e);
			return;
		}
		int rotation = e.getWheelRotation();
		int amount = e.getScrollAmount();
		boolean ctrl = (e.getModifiers()&Event.CTRL_MASK)!=0;
		if (amount<1) amount=1;
		if (rotation==0)
			return;
		if (ctrl||IJ.shiftKeyDown()) {
			double zoomFactor = rotation<0 ? Math.pow(2, 0.2) : Math.pow(0.5, 0.2);
			Point loc = ic.getCursorLoc();
			int x = ic.screenX(loc.x);
			int y = ic.screenY(loc.y);
			((PlotCanvas)ic).zoom(x, y, zoomFactor);
		} else if (IJ.spaceBarDown())
			plot.scroll(rotation*amount*Math.max(ic.imageWidth/50, 1), 0);
		else
			plot.scroll(0, rotation*amount*Math.max(ic.imageHeight/50, 1));
	}

	/** Creates an overlay with triangular buttons for changing the axis range limits and shows it */
	void showRangeArrows() {
		if (imp == null) return;
		hideRangeArrows(); //in case we have old arrows from a different plot size or so
		rangeArrowRois = new Roi[4*2]; //4 arrows per axis
		int i=0;
		int height = imp.getHeight();
		int arrowH = plot.topMargin < 14 ? 6 : 8; //height of arrows and distance between them; base is twice that value
		float[] yP = new float[]{height-arrowH/2, height-3*arrowH/2, height-5*arrowH/2-0.1f};
		for (float x : new float[]{plot.leftMargin, plot.leftMargin+plot.frameWidth}) { //create arrows for x axis
			float[] x0 = new float[]{x-arrowH/2, x-3*arrowH/2-0.1f, x-arrowH/2};
			rangeArrowRois[i++] = new PolygonRoi(x0, yP, 3, Roi.POLYGON);
			float[] x1 = new float[]{x+arrowH/2, x+3*arrowH/2+0.1f, x+arrowH/2};
			rangeArrowRois[i++] = new PolygonRoi(x1, yP, 3, Roi.POLYGON);
		}
		float[] xP = new float[]{arrowH/2-0.1f, 3*arrowH/2, 5*arrowH/2+0.1f};
		for (float y : new float[]{plot.topMargin+plot.frameHeight, plot.topMargin}) { //create arrows for y axis
			float[] y0 = new float[]{y+arrowH/2, y+3*arrowH/2+0.1f, y+arrowH/2};
			rangeArrowRois[i++] = new PolygonRoi(xP, y0, 3, Roi.POLYGON);
			float[] y1 = new float[]{y-arrowH/2, y-3*arrowH/2-0.1f, y-arrowH/2};
			rangeArrowRois[i++] = new PolygonRoi(xP, y1, 3, Roi.POLYGON);
		}
		Overlay ovly = imp.getOverlay();
		if (ovly == null)
			ovly = new Overlay();
		for (Roi roi : rangeArrowRois) {
			roi.setFillColor(Color.GRAY);
			ovly.add(roi);
		}
		imp.setOverlay(ovly);
		ic.repaint();
		rangeArrowsVisible = true;
	}

	void hideRangeArrows() {
		if (imp == null || rangeArrowRois==null) return;
		Overlay ovly = imp.getOverlay();
		if (ovly == null) return;
		for (Roi roi : rangeArrowRois)
			ovly.remove(roi);
		ic.repaint();
		rangeArrowsVisible = false;
		activeRangeArrow = -1;
	}

	/** Returns the index of the range arrow at cursor position x,y, or -1 of none.
	 *	Index numbers start with 0 at the 'down' arrow of the lower side of the x axis
	 *	and end with the up arrow at the upper side of the y axis. */
	int getRangeArrowIndex(int x, int y) {
		if (!rangeArrowsVisible) return -1;
		for (int i=0; i<rangeArrowRois.length; i++)
			if (rangeArrowRois[i].getBounds().contains(x,y))
				return i;
		return -1;
	}


	/** Shows the data of the backing plot in a Textwindow with columns */
	void showList(){
		ResultsTable rt = plot.getResultsTable(saveXValues);
		rt.show("Plot Values");
		if (autoClose) {
			imp.changes=false;
			close();
		}
	}
	
	/** Returns the plot values as a ResultsTable. */
	public ResultsTable getResultsTable() {
		return plot.getResultsTable(saveXValues);
	}

	/** creates the data that fills the showList() function values */
	private String getValuesAsString(){
		ResultsTable rt = getResultsTable();
		StringBuffer sb = new StringBuffer();
		for (int i=0; i<rt.size(); i++) {
			sb.append(rt.getRowAsString(i));
			sb.append("\n");
		}
		return sb.toString();
	}

	/** Saves the data of the plot in a text file */
	void saveAsText() {
		if (plot.getXValues() == null) {
			IJ.error("Plot has no data");
			return;
		}
		SaveDialog sd = new SaveDialog("Save as Text", "Values", Prefs.defaultResultsExtension());
		String name = sd.getFileName();
		if (name==null) return;
		String directory = sd.getDirectory();
		IJ.wait(250);  // give system time to redraw ImageJ window
		IJ.showStatus("Saving plot values...");
		ResultsTable rt = getResultsTable();
		try {
			rt.saveAs(directory+name);
		} catch (IOException e) {
			IJ.error("" + e);
			return;
		}
		if (autoClose)
			{imp.changes=false; close();}
	}

	/** Copy the first dataset or all values to the clipboard */
	void copyToClipboard(boolean writeAllColumns) {
		float[] xValues = plot.getXValues();
		float[] yValues = plot.getYValues();
		if (xValues == null) return;
		Clipboard systemClipboard = null;
		try {systemClipboard = getToolkit().getSystemClipboard();}
		catch (Exception e) {systemClipboard = null; }
		if (systemClipboard==null)
			{IJ.error("Unable to copy to Clipboard."); return;}
		IJ.showStatus("Copying plot values...");
		CharArrayWriter aw = new CharArrayWriter(10*xValues.length);
		PrintWriter pw = new PrintWriter(aw); //uses platform's line termination characters

		if (writeAllColumns) {
			ResultsTable rt = plot.getResultsTable(true);
			if (!Prefs.dontSaveHeaders) {
				String headings = rt.getColumnHeadings();
				pw.println(headings);
			}
			for (int i=0; i<rt.size(); i++)
				pw.println(rt.getRowAsString(i));
		} else {
			int xdigits = 0;
			if (saveXValues)
				xdigits = plot.getPrecision(xValues);
			int ydigits = plot.getPrecision(yValues);
			for (int i=0; i<Math.min(xValues.length, yValues.length); i++) {
				if (saveXValues)
					pw.println(IJ.d2s(xValues[i],xdigits)+"\t"+IJ.d2s(yValues[i],ydigits));
				else
					pw.println(IJ.d2s(yValues[i],ydigits));
			}
		}
		String text = aw.toString();
		pw.close();
		StringSelection contents = new StringSelection(text);
		systemClipboard.setContents(contents, this);
		IJ.showStatus(text.length() + " characters copied to Clipboard");
		if (autoClose)
			{imp.changes=false; close();}
	}
		
	public void lostOwnership(Clipboard clipboard, Transferable contents) {}

	public float[] getXValues() {
		return plot.getXValues();
	}

	public float[] getYValues() {
		return plot.getYValues();
	}

	/** Draws a new plot in this window. */
	public void drawPlot(Plot plot) {
		this.plot = plot;
		if (imp!=null) {
			if (ic instanceof PlotCanvas)
				((PlotCanvas)ic).setPlot(plot);
			imp.setProcessor(null, plot.getProcessor());
			plot.setImagePlus(imp); //also adjusts the calibration of imp
		}
	}

	/** Called once when ImageJ quits. */
	public static void savePreferences(Properties prefs) {
		double min = ProfilePlot.getFixedMin();
		double max = ProfilePlot.getFixedMax();
		if (plotWidth!=WIDTH || plotHeight!=HEIGHT) {
			prefs.put(PREFS_WIDTH, Integer.toString(plotWidth));
			prefs.put(PREFS_HEIGHT, Integer.toString(plotHeight));
			prefs.put(PREFS_FONT_SIZE, Integer.toString(fontSize));
		}
		int options = 0;
		if (saveXValues) options |= SAVE_X_VALUES;
		if (autoClose && !listValues) options |= AUTO_CLOSE;
		if (listValues) options |= LIST_VALUES;
		if (!interpolate) options |= INTERPOLATE; // true=0, false=1
		if (noGridLines) options |= NO_GRID_LINES; 
		if (noTicks) options |= NO_TICKS; 
		prefs.put(OPTIONS, Integer.toString(options));
	}

	private void toggleLiveProfiling() {
		boolean liveMode = live.getForeground()==Color.red;
		if (liveMode)
			removeListeners();
		else
			enableLiveProfiling();
	}

	private void enableLiveProfiling() {
		if (plotMaker==null)
			plotMaker = plot!=null?plot.getPlotMaker():null;
		if (plotMaker!=null && bgThread==null) {
			srcImp = plotMaker.getSourceImage();
			if (srcImp==null)
				return;
			bgThread = new Thread(this, "Live Profiler");
			bgThread.setPriority(Math.max(bgThread.getPriority()-3, Thread.MIN_PRIORITY));
			bgThread.start();
			imageUpdated(srcImp);
		}
		createListeners();
		if (srcImp!=null)
			imageUpdated(srcImp);
	}

	/** For live plots, update the plot if the ROI of the source image is changed */
	public synchronized void roiModified(ImagePlus img, int id) {
		if (IJ.debugMode) IJ.log("PlotWindow.roiModified: "+img+"  "+id);
		if (img==srcImp) {
			doUpdate=true;
			notify();
		}
	}
	
	// Unused
	public void imageOpened(ImagePlus imp) {
	}

	/** For live plots, this method is called if the source image content is changed */
	public synchronized void imageUpdated(ImagePlus imp) {
		if (imp==srcImp) { 
			doUpdate = true;
			notify();
		}
	}
	
	// For live plots, if either the source image or this image are closed, exit
	public void imageClosed(ImagePlus imp) {
		if (imp==srcImp || imp==this.imp) {
			if (bgThread!=null)
				bgThread.interrupt();
			bgThread = null;
			removeListeners();
			srcImp = null;
			plotMaker = null;
		}
	}
	
	// the background thread for live plotting.
	public void run() {
		while (true) {
			IJ.wait(50);	//delay to make sure the roi has been updated
			Plot plot = plotMaker.getPlot();
			if (doUpdate && plot!=null) {
				plot.useTemplate(this.plot, this.plot.templateFlags);
				plot.setPlotMaker(plotMaker);
				this.plot = plot;
				((PlotCanvas)ic).setPlot(plot);
				ImageProcessor ip = plot.getProcessor();
				if (ip!=null && imp!=null) {
					imp.setProcessor(null, ip);
					plot.setImagePlus(imp);
				}
			}
			synchronized(this) {
				if (doUpdate) {
					doUpdate = false;		//and loop again
				} else {
					try {wait();}	//notify wakes up the thread
					catch(InterruptedException e) { //interrupted tells the thread to exit
						return;
					}
				}
			}
		}
	}
		
	private void createListeners() {
		if (IJ.debugMode) IJ.log("PlotWindow.createListeners");
		if (srcImp==null)
			return;
		ImagePlus.addImageListener(this);
		Roi.addRoiListener(this);
		Font font = live.getFont();
		live.setFont(new Font(font.getName(), Font.BOLD, font.getSize()));
		live.setForeground(Color.red);
	}
	
	private void removeListeners() {
		if (IJ.debugMode) IJ.log("PlotWindow.removeListeners");
		if (srcImp==null)
			return;
		ImagePlus.removeImageListener(this);
		Roi.removeRoiListener(this);
		Font font = live.getFont();
		live.setFont(new Font(font.getName(), Font.PLAIN, font.getSize()));
		live.setForeground(Color.black);
	}
	
	/** Returns the Plot associated with this PlotWindow. */
	public Plot getPlot() {
		return plot;
	}
	
}


