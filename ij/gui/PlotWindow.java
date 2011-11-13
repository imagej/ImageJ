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

/** This class implements the Analyze>Plot Profile command.
* @authors Michael Schmid and Wayne Rasband
*/
public class PlotWindow extends ImageWindow implements ActionListener, ClipboardOwner,
	MouseListener, MouseMotionListener, KeyListener, ImageListener, Runnable {

	/** Display points using a circle 5 pixels in diameter. */
	public static final int CIRCLE = 0;
	/** Display points using an X-shaped mark. */
	public static final int X = 1;
	/** Display points using an box-shaped mark. */
	public static final int BOX = 3;
	/** Display points using an tiangular mark. */
	public static final int TRIANGLE = 4;
	/** Display points using an cross-shaped mark. */
	public static final int CROSS = 5;
	/** Connect points with solid lines. */
	public static final int LINE = 2;

	private static final int WIDTH = 450;
	private static final int HEIGHT = 200;
	
	private static final String MIN = "pp.min";
	private static final String MAX = "pp.max";
	private static final String PLOT_WIDTH = "pp.width";
	private static final String PLOT_HEIGHT = "pp.height";
	private static final String OPTIONS = "pp.options";
	private static final int SAVE_X_VALUES = 1;
	private static final int AUTO_CLOSE = 2;
	private static final int LIST_VALUES = 4;
	private static final int INTERPOLATE = 8;
	private static final int NO_GRID_LINES = 16;

	private Button list, save, copy, live;
	private Label coordinates;
	private static String defaultDirectory = null;
	private static int options;
	private int defaultDigits = -1;
	private int markSize = 5;
	private static Plot staticPlot;
	private Plot plot;
	private String blankLabel = "                      ";
	
	private ImagePlus srcImp;		// the source image for live plotting
	private Thread bgThread;		// thread for plotting (in the background)
	private boolean doUpdate;	// tells the background thread to update

	
	/** Save x-values only. To set, use Edit/Options/
		Profile Plot Options. */
	public static boolean saveXValues;
	
	/** Automatically close window after saving values. To
		set, use Edit/Options/Profile Plot Options. */
	public static boolean autoClose;
	
	/** The width of the plot in pixels. */
	public static int plotWidth = WIDTH;

	/** The height of the plot in pixels. */
	public static int plotHeight = HEIGHT;

	/** Display the XY coordinates in a separate window. To
		set, use Edit/Options/Profile Plot Options. */
	public static boolean listValues;

	/** Interpolate line profiles. To
		set, use Edit/Options/Profile Plot Options. */
	public static boolean interpolate;

	/** Add grid lines to plots */
	public static boolean noGridLines;

	// static initializer
	static {
		options = Prefs.getInt(OPTIONS, SAVE_X_VALUES);
		saveXValues = (options&SAVE_X_VALUES)!=0;
		autoClose = (options&AUTO_CLOSE)!=0;
		listValues = (options&LIST_VALUES)!=0;
		plotWidth = Prefs.getInt(PLOT_WIDTH, WIDTH);
		plotHeight = Prefs.getInt(PLOT_HEIGHT, HEIGHT);
		interpolate = (options&INTERPOLATE)==0; // 0=true, 1=false
		noGridLines = (options&NO_GRID_LINES)!=0; 
   }

 	/**
	* @deprecated
	* replaced by the Plot class.
	*/
	public PlotWindow(String title, String xLabel, String yLabel, float[] xValues, float[] yValues) {
		super(createImage(title, xLabel, yLabel, xValues, yValues));
		plot = staticPlot;
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
		this.plot = plot;
		draw();
		//addComponentListener(this);
	}

	/** Called by the constructor to generate the image the plot will be drawn on.
		This is a static method because constructors cannot call instance methods. */
	static ImagePlus createImage(String title, String xLabel, String yLabel, float[] xValues, float[] yValues) {
		staticPlot = new Plot(title, xLabel, yLabel, xValues, yValues);
		return new ImagePlus(title, staticPlot.getBlankProcessor());
	}
	
	/** Sets the x-axis and y-axis range. */
	public void setLimits(double xMin, double xMax, double yMin, double yMax) {
		plot.setLimits(xMin, xMax, yMin, yMax);
	}

	/** Adds a set of points to the plot or adds a curve if shape is set to LINE.
	* @param x			the x-coodinates
	* @param y			the y-coodinates
	* @param shape		CIRCLE, X, BOX, TRIANGLE, CROSS or LINE
	*/
	public void addPoints(float[] x, float[] y, int shape) {
		plot.addPoints(x, y, shape);
	}

	/** Adds a set of points to the plot using double arrays.
		Must be called before the plot is displayed. */
	public void addPoints(double[] x, double[] y, int shape) {
		addPoints(Tools.toFloat(x), Tools.toFloat(y), shape);
	}
	
	/** Adds error bars to the plot. */
	public void addErrorBars(float[] errorBars) {
		plot.addErrorBars(errorBars);
	}

	/** Draws a label. */
	public void addLabel(double x, double y, String label) {
		plot.addLabel(x, y, label);
	}
	
	/** Changes the drawing color. The frame and labels are
		always drawn in black. */
	public void setColor(Color c) {
		plot.setColor(c);
	}

	/** Changes the line width. */
	public void setLineWidth(int lineWidth) {
		plot.setLineWidth(lineWidth);
	}

	/** Changes the font. */
	public void changeFont(Font font) {
		plot.changeFont(font);
	}

	/** Displays the plot. */
	public void draw() {
		Panel buttons = new Panel();
		int hgap = IJ.isMacOSX()?1:5;
		buttons.setLayout(new FlowLayout(FlowLayout.RIGHT,hgap,0));
		list = new Button(" List ");
		list.addActionListener(this);
		buttons.add(list);
		save = new Button("Save...");
		save.addActionListener(this);
		buttons.add(save);
		copy = new Button("Copy...");
		copy.addActionListener(this);
		buttons.add(copy);
		live = new Button("Live");
		live.addActionListener(this);
		buttons.add(live);
		coordinates = new Label("X=12345678, Y=12345678"); 
		coordinates.setFont(new Font("Monospaced", Font.PLAIN, 12));
		coordinates.setBackground(new Color(220, 220, 220));
		buttons.add(coordinates);
		add(buttons);
		plot.draw();
		pack();
		coordinates.setText(blankLabel);
		ImageProcessor ip = plot.getProcessor();
		if ((ip instanceof ColorProcessor) && (imp.getProcessor() instanceof ByteProcessor))
			imp.setProcessor(null, ip);
		else
			imp.updateAndDraw();
		if (listValues)
			showList();
	}

	int getDigits(double n1, double n2) {
		if (Math.round(n1)==n1 && Math.round(n2)==n2)
			return 0;
		else {
			n1 = Math.abs(n1);
			n2 = Math.abs(n2);
			double n = n1<n2&&n1>0.0?n1:n2;
			double diff = Math.abs(n2-n1);
			if (diff>0.0 && diff<n) n = diff;			
			int digits = 1;
			if (n<10.0) digits = 2;
			if (n<0.01) digits = 3;
			if (n<0.001) digits = 4;
			if (n<0.0001) digits = 5;
			return digits;
		}
	}

	/** Updates the graph X and Y values when the mouse is moved.
		Overrides mouseMoved() in ImageWindow. 
		@see ij.gui.ImageWindow#mouseMoved
	*/
	public void mouseMoved(int x, int y) {
		super.mouseMoved(x, y);
		if (plot!=null && plot.frame!=null && coordinates!=null) {
			String coords = plot.getCoordinates(x,y) + blankLabel;
			coordinates.setText(coords.substring(0, blankLabel.length()));
		}
	}
			
	/** shows the data of the backing plot in a Textwindow with columns */
	void showList(){
		String headings = createHeading();
		String data = createData();
		TextWindow tw = new TextWindow("Plot Values", headings, data, 230, 400);
		if (autoClose)
			{imp.changes=false; close();}
	}
	
	/** creates the headings corresponding to the showlist funcion*/
	private String createHeading(){
		String head = "";
		int sets = plot.storedData.size()/2;
		if (saveXValues || sets>1)
			head += sets==1?"X\tY\t":"X0\tY0\t";
		else
			head += sets==1?"Y0\t":"Y0\t";
		if (plot.errorBars!=null)
			head += "ERR\t";
		for (int j = 1; j<sets; j++){
			if (saveXValues || sets>1)
				head += "X" + j + "\tY" + j + "\t";
			else
				head += "Y" + j + "\t";
		}
		return head;
	}
	
	/** creates the data that fills the showList() function values */
	private String createData(){
		int max = 0;
		
		/** find the longest x-value data set */
		float[] column;
		for(int i = 0; i<plot.storedData.size(); i+=2){
			column = (float[])plot.storedData.get(i);
			int s = column.length;
			max = s>max?s:max;
		}
		
		/** stores the values that will be displayed*/
		ArrayList displayed = new ArrayList(plot.storedData);
		boolean eb_test = false;
		
		/** includes error bars.*/
		if (plot.errorBars !=null)
			displayed.add(2, plot.errorBars);
					
		StringBuffer sb = new StringBuffer();
		String v;
		int n = displayed.size();
		for (int i = 0; i<max; i++) {
			eb_test = plot.errorBars != null;
			for (int j = 0; j<n;) {
				int xdigits = 0;
				if (saveXValues || n>2) {
					column = (float[])displayed.get(j);
					xdigits = getPrecision(column);
					v = i<column.length?IJ.d2s(column[i],xdigits):"";
					sb.append(v);
					sb.append("\t");
				}
				j++;
				column = (float[])displayed.get(j);
				int ydigits = xdigits;
				if (ydigits==0)
					ydigits = getPrecision(column);
				v = i<column.length?IJ.d2s(column[i],ydigits):"";
				sb.append(v);
				sb.append("\t");
				j++;
				if (eb_test){
					column = (float[])displayed.get(j);
					v = i<column.length?IJ.d2s(column[i],ydigits):"";
					sb.append(v);
					sb.append("\t");
					j++;
					eb_test=false;
				}
			}
			sb.append("\n");
		}
		return sb.toString();
	}
	
	void saveAsText() {
		SaveDialog sd = new SaveDialog("Save as Text", "Values", ".txt");
		String name = sd.getFileName();
		if (name==null) return;
		String directory = sd.getDirectory();
		PrintWriter pw = null;
		try {
			FileOutputStream fos = new FileOutputStream(directory+name);
			BufferedOutputStream bos = new BufferedOutputStream(fos);
			pw = new PrintWriter(bos);
		}
		catch (IOException e) {
			IJ.error("" + e);
			return;
		}
		IJ.wait(250);  // give system time to redraw ImageJ window
		IJ.showStatus("Saving plot values...");
		pw.print(createData());
		pw.close();
		if (autoClose)
			{imp.changes=false; close();}
	}
		
	void copyToClipboard() {
		Clipboard systemClipboard = null;
		try {systemClipboard = getToolkit().getSystemClipboard();}
		catch (Exception e) {systemClipboard = null; }
		if (systemClipboard==null)
			{IJ.error("Unable to copy to Clipboard."); return;}
		IJ.showStatus("Copying plot values...");
		int xdigits = 0;
		if (saveXValues)
			xdigits = getPrecision(plot.xValues);
		int ydigits = xdigits;
		if (ydigits==0)
			ydigits = getPrecision(plot.yValues);
		CharArrayWriter aw = new CharArrayWriter(plot.nPoints*4);
		PrintWriter pw = new PrintWriter(aw);
		for (int i=0; i<plot.nPoints; i++) {
			if (saveXValues)
				pw.print(IJ.d2s(plot.xValues[i],xdigits)+"\t"+IJ.d2s(plot.yValues[i],ydigits)+"\n");
			else
				pw.print(IJ.d2s(plot.yValues[i],ydigits)+"\n");
		}
		String text = aw.toString();
		pw.close();
		StringSelection contents = new StringSelection(text);
		systemClipboard.setContents(contents, this);
		IJ.showStatus(text.length() + " characters copied to Clipboard");
		if (autoClose)
			{imp.changes=false; close();}
	}
	
	int getPrecision(float[] values) {
		int setDigits = Analyzer.getPrecision();
		int measurements = Analyzer.getMeasurements();
		boolean scientificNotation = (measurements&Measurements.SCIENTIFIC_NOTATION)!=0;
		int minDecimalPlaces = 4;
		if (scientificNotation) {
			if (setDigits<minDecimalPlaces)
				setDigits = minDecimalPlaces;
			return -setDigits;
		}
		int digits = minDecimalPlaces;
		if (setDigits>digits)
			digits = setDigits;
		boolean realValues = false;
		for (int i=0; i<values.length; i++) {
			if ((int)values[i]!=values[i]) {
				realValues = true;
				break;
			}
		}
		if (!realValues)
			digits = 0;
		return digits;
	}
		
	public void lostOwnership(Clipboard clipboard, Transferable contents) {}
	
	public void actionPerformed(ActionEvent e) {
		Object b = e.getSource();
		if (b==live)
			toggleLiveProfiling();
		else if (b==list)
			showList();
		else if (b==save)
			saveAsText();
		else
			copyToClipboard();
	}
	
	public float[] getXValues() {
		return plot.xValues;
	}

	public float[] getYValues() {
		return plot.yValues;
	}
	
	/** Returns the X and Y plot values as a ResultsTable. */
	public ResultsTable getResultsTable() {
		int sets = plot.storedData.size()/2;
		int max = 0;
		for(int i = 0; i<plot.storedData.size(); i+=2) {
			float[] column = (float[])plot.storedData.get(i);
			int s = column.length;
			if (column.length>max) max=column.length;
		}
		ResultsTable rt = new ResultsTable();
		for (int row=0; row<max; row++) {
			rt.incrementCounter();
			for (int i=0; i<sets; i++) {
				float[] x = (float[])plot.storedData.get(i*2);
				float[] y = (float[])plot.storedData.get(i*2+1);
				if (row<x.length) rt.addValue("x"+i, x[row]);
				if (row<y.length) rt.addValue("y"+i, y[row]);
			}
		}
		return rt;
	}
	
	/** Draws a new plot in this window. */
	public void drawPlot(Plot plot) {
		this.plot = plot;
		imp.setProcessor(null, plot.getProcessor());	
	}
	
	/** Called once when ImageJ quits. */
	public static void savePreferences(Properties prefs) {
		double min = ProfilePlot.getFixedMin();
		double max = ProfilePlot.getFixedMax();
		if (!(min==0.0&&max==0.0) && min<max) {
			prefs.put(MIN, Double.toString(min));
			prefs.put(MAX, Double.toString(max));
		}
		if (plotWidth!=WIDTH || plotHeight!=HEIGHT) {
			prefs.put(PLOT_WIDTH, Integer.toString(plotWidth));
			prefs.put(PLOT_HEIGHT, Integer.toString(plotHeight));
		}
		int options = 0;
		if (saveXValues) options |= SAVE_X_VALUES;
		if (autoClose && !listValues) options |= AUTO_CLOSE;
		if (listValues) options |= LIST_VALUES;
		if (!interpolate) options |= INTERPOLATE; // true=0, false=1
		if (noGridLines) options |= NO_GRID_LINES; 
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
		if (plot!=null && bgThread==null) {
			int id = plot.getSourceImageID();
			srcImp = WindowManager.getImage(id);
			if (srcImp==null) return;
			bgThread = new Thread(this, "Live Profiler");
			bgThread.setPriority(Math.max(bgThread.getPriority()-3, Thread.MIN_PRIORITY));
			bgThread.start();
			imageUpdated(srcImp);
		}
		createListeners();
		if (srcImp!=null)
			imageUpdated(srcImp);
	}
	
	// these listeners are activated if the selection is changed in the source ImagePlus
	public synchronized void mousePressed(MouseEvent e) { doUpdate = true; notify(); }   
	public synchronized void mouseDragged(MouseEvent e) { doUpdate = true; notify(); }
	public synchronized void mouseClicked(MouseEvent e) { doUpdate = true; notify(); }
	public synchronized void keyPressed(KeyEvent e) { doUpdate = true; notify(); }
	
	// unused listeners
	public void mouseReleased(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
	public void mouseMoved(MouseEvent e) {}
	public void keyTyped(KeyEvent e) {}
	public void keyReleased(KeyEvent e) {}
	public void imageOpened(ImagePlus imp) {}
	
	// This listener is called if the source image content is changed
	public synchronized void imageUpdated(ImagePlus imp) {
		if (imp==srcImp) { 
			if (!isSelection())
				IJ.run(imp, "Restore Selection", "");
			doUpdate = true;
			notify();
		}
	}
	
	// If either the source image or this image are closed, exit
	public void imageClosed(ImagePlus imp) {
		if (imp==srcImp || imp==this.imp) {
			if (bgThread!=null)
				bgThread.interrupt();
			bgThread = null;
			removeListeners();
			srcImp = null;
		}
	}
	
	// the background thread for live plotting.
	public void run() {
		while (true) {
			IJ.wait(50);	//delay to make sure the roi has been updated
			Plot plot = getProfilePlot();
			if (doUpdate && plot!=null) {
				this.plot = plot;
				ImageProcessor ip = plot.getProcessor();
				if (ip!=null)
					imp.setProcessor(null, ip);
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
		//IJ.log("createListeners");
		if (srcImp==null) return;
		ImageCanvas ic = srcImp.getCanvas();
		if (ic==null) return;
		ic.addMouseListener(this);
		ic.addMouseMotionListener(this);
		ic.addKeyListener(this);
		srcImp.addImageListener(this);
		Font font = live.getFont();
		live.setFont(new Font(font.getName(), Font.BOLD, font.getSize()));
		live.setForeground(Color.red);
	}
	
	private void removeListeners() {
		//IJ.log("removeListeners");
		if (srcImp==null) return;
		ImageCanvas ic = srcImp.getCanvas();
		ic.removeMouseListener(this);
		ic.removeMouseMotionListener(this);
		ic.removeKeyListener(this);
		srcImp.removeImageListener(this);
		Font font = live.getFont();
		live.setFont(new Font(font.getName(), Font.PLAIN, font.getSize()));
		live.setForeground(Color.black);
	}
	
	/** Returns true if there is a straight line selection or rectangular selection */
	private boolean isSelection() {
		if (srcImp==null)
			return false;
		Roi roi = srcImp.getRoi();
		if (roi==null)
			return false;
		int type = roi.getType();
		return type==Roi.LINE || type==Roi.POLYLINE || type==Roi.RECTANGLE;
	}
	
	/** Get a source image profile plot. */
	private Plot getProfilePlot() {
		if (srcImp==null || !isSelection())
			return null;
		Roi roi = srcImp.getRoi();
		if (roi == null)
			return null;
		if (!(roi.isLine() || roi.getType()==Roi.RECTANGLE))
			return null;
		boolean averageHorizontally = Prefs.verticalProfile || IJ.altKeyDown();
		ProfilePlot pp = new ProfilePlot(srcImp, averageHorizontally);
		return pp.getPlot();
	}
	
}


