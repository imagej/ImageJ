package ij.plugin.tool;
import ij.*;
import ij.plugin.frame.PlugInFrame;
import ij.process.*;
import ij.measure.*;
import ij.plugin.filter.Analyzer;
import ij.gui.*;
import ij.util.Tools;
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;
import java.awt.geom.*;

/**
 * This plugin continuously displays the pixel values of the cursor and
 * its surroundings. It is usefule for examining how a filter changes the 
 * data (also during preview).
 *
 * If the Pixel Inspector Window is in the foreground, "c" with any modifier
 * keys (CTRL-C etc) copies the current data into the clipboard (tab-delimited).
 * The arrow keys nudge the position.
 *
 * Preferences (Press the Prefs button at top left):
 *
 * Radius determines the size of the window, 3x3 for radius=1, etc.
 * The Pixel Inspector window must be closed and opened to get the new
 * size.
 * Readout for grayscale 8&16 bit images can be raw, calibrated or
 * hexadecimal.
 * Readout for RGB images can ge R,G,B triples, gray value or hexadecimal.
 * For copying the data to clipboard, it can be selected whether the position
 * (x,y) is not not written, written in the first line or in the same way
 * as the header lines of the Pixel Inspector panel.
 *
 * Limitations and known problems:
 *
 * x and y coordinates are always uncalibrated pixel numbers.
 *
 * Some image operations do not update the display.
 *
 * Michael Schmid
 * Version 2007-Dec-06 - bugs fixed:
 *		did not always follow cursor
 *		nudge could make the display hang
 *		pixel value calibration was sometimes ignored
 * Version 2007-Dec-14 - supports exponential format for large/small data values
 */
public class  PixelInspectionTool extends PlugInTool {
	PixelInspector pi;

	public void mousePressed(ImagePlus imp, MouseEvent e) {
		drawOutline(imp, e);
	}

	public void mouseDragged(ImagePlus imp, MouseEvent e) {
		drawOutline(imp, e);
	}

	public void showOptionsDialog() {
		if (pi!=null) pi.showDialog();		
	}

	void drawOutline(ImagePlus imp, MouseEvent e) {
		ImageCanvas ic = imp.getCanvas();
		int x = ic.offScreenX(e.getX());
		int y = ic.offScreenY(e.getY());
		int radius = PixelInspector.radius;
		int size = radius*2+1;
		Overlay overlay = imp.getOverlay();
		if (overlay==null)
			overlay = new Overlay();
		Roi roi = null;
		int index = PixelInspector.getIndex(overlay, PixelInspector.TITLE);
		if (index>=0) {
			roi = overlay.get(index);
			Rectangle r = roi.getBounds();
			if (r.width!=size || r.height!=size) {
				overlay.remove(index);
				roi = null;
			}
			if (roi!=null)
				roi.setLocation(x-radius, y-radius);
		}
		if (roi==null) {
			roi = new Roi(x-radius, y-radius, size, size);
			roi.setName(PixelInspector.TITLE);
			roi.setStrokeColor(Color.red);
			overlay.add(roi);
		}
		imp.setOverlay(overlay);
		if (pi==null) {
			if (PixelInspector.instance!=null)
				PixelInspector.instance.close();
			pi = new PixelInspector(imp, this);
		}
		pi.update(imp, PixelInspector.POSITION_UPDATE, x, y);
	}

	public String getToolName() {
		return "Pixel Inspection Tool";
	}

	public String getToolIcon() {
		return "Cb00T3b09PT8b09xC037L2e0cL0c02L0220L20d0Pd0f2fcde2e0BccP125665210";
	}

}


class PixelInspector extends PlugInFrame
		implements ImageListener, KeyListener, MouseListener, Runnable {
	//ImageListener: listens to changes of image data
	//KeyListener: for fix/unfix key
	//MouseListener: for "Prefs" label
	//Runnable: for background thread

	/* Preferences and related */
	static final String PREFS_KEY="pixelinspector."; //key in IJ_Prefs.txt
	static int radius = (int)Prefs.get(PREFS_KEY+"radius", 3);
	private static final String LOC_KEY = "inspector.loc";
	final static int MAX_RADIUS = 10;//the largest radius possible (ImageJ can hang if too large)
	int grayDisplayType = 0;		//how to display 8-bit&16-bit grayscale pixels
	final static String[] GRAY_DISPLAY_TYPES = {"Raw","Calibrated","Hex"};
	final static int GRAY_RAW = 0, GRAY_CAL = 1, GRAY_HEX = 2;
	int rgbDisplayType = 0;			//how to display rgb pixels
	final static String[] RGB_DISPLAY_TYPES = {"R,G,B","Gray Value","Hex"};
	final static int RGB_RGB = 0, RGB_GRAY = 1, RGB_HEX = 2;
	int copyType = 0;				//what to copy to the clipboard
	final static String[] COPY_TYPES = {"Data Only","x y and Data","Header and Data"};
	final static int COPY_DATA = 0, COPY_XY = 1, COPY_HEADER = 2;
	int colorNumber = 0;			//color of the position marker in fixed mode
	final static String[] COLOR_STRINGS = {"red","orange","yellow","green","cyan","blue","magenta",};
	final static Color[] COLORS = {Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA};
	int fixKey = '!';				//the key (keycode+0x10000 or char) for fixing/unfixing the position
	final static int KEYCODE_OFFSET = 0x10000;	//we add this to keycodes to separate them from key characters
	/* current status */
	private int x0,y0;				//the current position
	int nextUpdate;					//type of next update
	final static int POSITION_UPDATE = 1, FULL_UPDATE = 2;
	static final String TITLE = "Pixel Inspector";
	static PixelInspector instance;
	PixelInspectionTool tool;

	ImageJ ij;
	ImagePlus imp;					//the ImagePlus that we listen to
	int id;					        //the image ID
	int bitDepth;                 //the image bit depth
	int digits;						//decimal fraction digits to display
	boolean expMode;				//whether to display the data in exp format
	ImageCanvas canvas;				//the canvas of imp
	Thread bgThread;				//thread for output (in the background)
	Label[] labels;					//the display fields
	//Label prefsLabel = new Label("Prefs\u2026");
	Label prefsLabel = new Label("Prefs");
	

	/* Initialization, preparing the window (panel) **/
	public PixelInspector(ImagePlus imp, PixelInspectionTool tool) {
		super("Pixel Values");
		instance = this;
		this.imp = imp;
		this.tool = tool;
		ij = IJ.getInstance();
		if (ij == null) return;		//it won't work with the ImageJ applet
		if (imp==null) {
			IJ.noImage(); return;
		}
		id = imp.getID();
		bitDepth = imp.getBitDepth();
		//setTitle("Pixels of "+imp.getTitle());
		WindowManager.addWindow(this);
		//readPreferences();
		prefsLabel.addMouseListener(this);
		addKeyListener(this);
		init();
		Point loc = Prefs.getLocation(PREFS_KEY+"loc");
		if (loc!=null)
			setLocation(loc);
		else
			GUI.center(this);
		setResizable(false);
		show();
		toFront();
		addImageListeners();
											//thread for output in the background
		bgThread = new Thread(this, "Pixel Inspector");
		bgThread.start();
		bgThread.setPriority(Math.max(bgThread.getPriority()-3, Thread.MIN_PRIORITY));
		update(FULL_UPDATE);				//the first data display
	}

	private void init() {
		removeAll();
		int size = 2*radius+2;			   //number of columns and rows
		labels = new Label[size*size];
		for (int i=1; i<labels.length; i++) //make the labels (display fields)
			labels[i] = new Label();
		initializeLabels();					//fill the labels with spaceholders
		setLayout(new GridLayout(size, size, 0, 0));
		for (int row=0,p=0; row<size; row++) {
			for (int col=0; col<size; col++,p++) {
				if (row == 0 && col == 0)
					add(prefsLabel);
				else
					add(labels[p]);
			}
		}
		GUI.scale(this);
		pack();
	}

	public void close() {
		super.close();			   //also does WindowManager.removeWindow(this);
		Prefs.saveLocation(PREFS_KEY+"loc", getLocation());
		removeImageListeners();
		 synchronized(this) {				 //terminate the background thread
			bgThread.interrupt();
		}
		instance = null;
		tool.pi = null;
		removeOutline();
	}

	private void removeOutline() {
		Overlay overlay = imp.getOverlay();
		if (overlay==null) return;
		int index = getIndex(overlay, TITLE);
		if (index>=0) {
			overlay.remove(index);
			imp.setOverlay(overlay);
		}
	}

	private void addImageListeners() {
		imp.addImageListener(this);
		ImageWindow win = imp.getWindow();
		if (win == null) close();
		canvas = win.getCanvas();
		canvas.addKeyListener(this);
	}

	private void removeImageListeners() {
		imp.removeImageListener(this);
		canvas.removeKeyListener(this);
	}

	//ImageListener
	public void imageUpdated(ImagePlus imp) { update(FULL_UPDATE); }
	public void imageOpened(ImagePlus imp) {}
	public void imageClosed(ImagePlus imp) {}

	//KeyListener
	public void keyPressed(KeyEvent e) {
		boolean thisPanel = e.getSource() instanceof PixelInspector;
		if (thisPanel && e.getKeyCode()==KeyEvent.VK_C) { 
			copyToClipboard();
			return;
		}
		if (e.getKeyCode()==KeyEvent.VK_UP && y0 > 0) {
			y0--; update(FULL_UPDATE);
		} else if (e.getKeyCode()==KeyEvent.VK_DOWN && y0<imp.getHeight()-1) {
			y0++; update(FULL_UPDATE);
		} else if (e.getKeyCode()==KeyEvent.VK_LEFT && x0>0) {
			x0--; update(FULL_UPDATE);
		} else if (e.getKeyCode()==KeyEvent.VK_RIGHT && x0<imp.getWidth()-1) {
			x0++; update(FULL_UPDATE);
		} else if (e.getSource() instanceof Button)
			ij.keyPressed(e);  //forward other keys from the panel to ImageJ
		Overlay overlay = imp.getOverlay();
		if (overlay==null) return;
		int index = getIndex(overlay, TITLE);
		if (index>=0) {
			overlay.remove(index);
			Roi roi = new Roi(x0-radius, y0-radius, radius*2+1, radius*2+1);
			roi.setName(TITLE);
			roi.setStrokeColor(Color.red);
			overlay.add(roi);
			imp.setOverlay(overlay);	
	   }
	}

	public void mousePressed(MouseEvent e) {
		showDialog();
	}   
	public void mouseEntered(MouseEvent e) {}   
	public void mouseExited(MouseEvent e) {}   
	public void mouseClicked(MouseEvent e) {}   
	public void mouseReleased(MouseEvent e) {}   

	/** In the Overlay class in imageJ 1.46g and later. */
	static int getIndex(Overlay overlay, String name) {
		if (name==null) return -1;
		Roi[] rois = overlay.toArray();
		for (int i=rois.length-1; i>=0; i--) {
			if (name.equals(rois[i].getName()))
				return i;
		}
		return -1;
	}

	public void keyReleased(KeyEvent e) {}
	public void keyTyped(KeyEvent e) {}

	void update(ImagePlus imp, int whichUpdate, int x, int y) {
		if (imp!=this.imp) {
			removeImageListeners();
			removeOutline();
			this.imp = imp;
			addImageListeners();
			//setTitle("Pixels of "+imp.getTitle());
		}
		this.x0 = x;
		this.y0 = y;
		update(whichUpdate);
	}

	synchronized void update(int whichUpdate) {
		if (nextUpdate < whichUpdate)
			nextUpdate = whichUpdate;
		notify();		//wake up the background thread
	}

	// the background thread for updating the table
	public void run() {
		boolean doFullUpdate = false;
		while (true) {
			if (doFullUpdate) {
				setCalibration();
			}
			writeNumbers();
			IJ.wait(50);

			synchronized(this) {
				if (nextUpdate == 0) {
					try {wait();}				//notify wakes up the thread
					catch(InterruptedException e) { //interrupted tells the thread to exit
						return;
					}
				} else {
					doFullUpdate = nextUpdate == FULL_UPDATE;
					nextUpdate = 0;
				}
			}
		} //while (true)
	}

	/** get the surrounding pixels and display them */
	void writeNumbers() {
		if (imp.getID()!=id || imp.getBitDepth()!=bitDepth) {	//has the image changed?
			removeImageListeners();
			addImageListeners();
			initializeLabels();
			this.pack();
			id = imp.getID();
			bitDepth = imp.getBitDepth();
			nextUpdate = FULL_UPDATE;
			return;
		}
		ImageProcessor ip = imp.getProcessor();
		if (ip == null) return;
		int width = ip.getWidth();
		int height = ip.getHeight();
		int x0 = this.x0;		//class variables may change asynchronously, fixed values needed here
		int y0 = this.y0;
		int p = 1;	  //pointer in labels array
		for (int x = x0-radius; x <= x0+radius; x++,p++)
			labels[p].setText(x>=0&&x<width ? Integer.toString(x) : " ");
		for (int y = y0-radius; y <= y0+radius; y++) {
			boolean yInside = y>=0&&y<height;
			int yDisplay =	(Analyzer.getMeasurements() & Measurements.INVERT_Y)!=0 ? height-y-1 : y;
			labels[p].setText(yInside ? Integer.toString(yDisplay) : " ");
			p++;
			for (int x = x0-radius; x <= x0+radius; x++,p++) {
				if (x>=0&&x<width&&yInside) {
					if (ip instanceof ColorProcessor && rgbDisplayType == RGB_RGB) {
						int c = ip.getPixel(x,y);
						int r = (c&0xff0000)>>16;
						int g = (c&0xff00)>>8;
						int b = c&0xff;
						labels[p].setText(r+","+g+","+b);
					} else if (ip instanceof ColorProcessor && rgbDisplayType == RGB_HEX)
						labels[p].setText(int2hex(ip.getPixel(x,y),6));
					else if ((ip instanceof ByteProcessor || ip instanceof ShortProcessor) && grayDisplayType == GRAY_RAW)
						labels[p].setText(Integer.toString(ip.getPixel(x,y)));
					else if ((ip instanceof ByteProcessor || ip instanceof ShortProcessor) && grayDisplayType == GRAY_HEX)
						labels[p].setText(int2hex(ip.getPixel(x,y), ip instanceof ByteProcessor ? 2 : 4));
					else
						labels[p].setText(stringOf(ip.getPixelValue(x,y), digits, expMode));
				} else
					labels[p].setText(" ");
			}
		} //for y
	}

	/** initialize content of the labels to make sure we have enough space */
	void initializeLabels() {
		Color bgColor = new Color(0xcccccc);	//background for row/column header
		String placeHolder = "000000.00";		//how much space to reserve (enough for float, calibrated, rgb)
		ImageProcessor ip = imp.getProcessor();
		if (ip instanceof ByteProcessor && grayDisplayType==GRAY_RAW) {
			placeHolder = "000";
		} else if (ip instanceof ByteProcessor || ip instanceof ShortProcessor) {
			if (grayDisplayType == GRAY_RAW || grayDisplayType == GRAY_HEX)
				placeHolder = "00000";			//minimum space, needed for header (max 99k pixels)
		} else if (ip instanceof ColorProcessor) {
			if (rgbDisplayType == RGB_RGB)
				placeHolder = "000,000,000";
			if (rgbDisplayType == RGB_GRAY)
				placeHolder = "000.00";
			else if (rgbDisplayType == RGB_HEX)
				placeHolder = "CCCCCC";
		}
		if (placeHolder.length()<5 && (ip.getWidth()>9999 || ip.getHeight()>9999))
			placeHolder = "00000";
		if (placeHolder.length()<4 && (ip.getWidth()>999 || ip.getHeight()>999))
			placeHolder = "0000";
		int p = 0;								//pointer in labels array
		int size = 2*radius+1;
		for (int y = 0; y<size+1; y++) {		//header line and data lines
			if (y > 0)							//no label in top-left corner
				labels[p].setText(placeHolder);
			p++;
			for (int x = 0; x<size; x++,p++)
				labels[p].setText(placeHolder);
		}
		labels[radius+1].setForeground(Color.RED); //write current position in red
		labels[(2*radius+2)*(radius+1)].setForeground(Color.RED);
		labels[(2*radius+2)*(radius+1)+radius+1].setForeground(Color.RED);
		for (int i=0; i<size; i++) {			//header lines have a darker background
			labels[i+1].setBackground(bgColor);
			labels[(2*radius+2)*(i+1)].setBackground(bgColor);		
		}
		for (int i=1; i<labels.length; i++)
			labels[i].setAlignment(Label.RIGHT);
	}

	/* set the pixel value calibration of the ImageProcessor and the output format */
	void setCalibration() {
		Calibration cal = imp.getCalibration();
		float[] cTable = cal.getFunction()==Calibration.NONE ? null : cal.getCTable();
		ImageProcessor ip = imp.getProcessor();
		if (ip != null) ip.setCalibrationTable(cTable);
		if (ip instanceof FloatProcessor || cTable != null) {
			float[] data = (ip instanceof FloatProcessor) ? (float[])ip.getPixels() : cTable;
			double[] minmax = Tools.getMinMax(data);
			double maxDataValue = Math.max(Math.abs(minmax[0]), Math.abs(minmax[1]));
			digits = (int)(6-Math.log(maxDataValue)/Math.log(10));
			if (maxDataValue==0.0)
				digits = 6;
			expMode = digits<-1 || digits>7;
			if (Math.min(minmax[0], minmax[1]) < 0)
				digits--; //more space needed for minus sign
		} else {
			digits = 2;
			expMode = false;
		}
	}

	/** Converts a number to a string in decimal or exp format.
	 *	The number of digits is chosen to make the value fit into
	 *	a cell the size of "000000.00"
	 */
	String stringOf(float v, int digits, boolean expMode) {
		if (expMode) {
			int exp = (int)Math.floor(Math.log(Math.abs(v))/Math.log(10));
			double mant = v/Math.pow(10,exp);
			digits = (exp > 0 && exp < 10) ? 5 : 4;
			if (v<0) digits--;		//space needed for minus
			return IJ.d2s(mant,digits)+"e"+exp;
		} else
			return IJ.d2s(v, digits);
	}

	void copyToClipboard() {
		final char delim = '\t';
		int size = 2*radius+1;
		int p = 1;
		StringBuffer sb = new StringBuffer();
		if (copyType == COPY_XY) {
			sb.append(labels[radius+1].getText()); sb.append(delim);
			sb.append(labels[(2*radius+2)*(radius+1)].getText()); sb.append('\n');
		} else if (copyType == COPY_HEADER) {
			for (int x=0; x<size; x++,p++) {
				sb.append(delim);
				sb.append(labels[p].getText());
			}
			sb.append('\n');
		}
		p = size + 1;
		for (int y=0; y<size; y++) {
			if (copyType == COPY_HEADER) {
				sb.append(labels[p].getText()); sb.append(delim);
			}
			p++;
			for (int x=0; x<size; x++,p++) {
				if (x > 0)
					sb.append(delim);
				sb.append(labels[p].getText());
			}
			sb.append('\n');
		}
		String s = new String(sb);
		Clipboard clip = getToolkit().getSystemClipboard();
		if (clip==null) return;
		StringSelection contents = new StringSelection(s);
		clip.setContents(contents, contents);
		IJ.showStatus(size*size+" pixel values copied to clipboard");
	}

	/** Preferences dialog */
	void showDialog() {
		GenericDialog gd = new GenericDialog("Pixel Inspector Prefs...");
		gd.addNumericField("Radius:", radius, 0, 6, "(1-"+MAX_RADIUS+")");
		gd.addChoice("Grayscale readout:",GRAY_DISPLAY_TYPES,GRAY_DISPLAY_TYPES[grayDisplayType]);
		gd.addChoice("RGB readout:",RGB_DISPLAY_TYPES,RGB_DISPLAY_TYPES[rgbDisplayType]);
		gd.addChoice("Copy to clipboard:", COPY_TYPES, COPY_TYPES[copyType]);
		gd.addMessage("Use arrow keys to move red outline.\nPress 'c' to copy data to clipboard.", null, Color.darkGray);
		Point loc = Prefs.getLocation(LOC_KEY);
		if (loc!=null) {
			gd.centerDialog(false);
			gd.setLocation (loc);
		}
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		radius = (int)gd.getNextNumber();
		if (radius<1) radius=1;
		if (radius>MAX_RADIUS) radius=MAX_RADIUS;
		grayDisplayType = gd.getNextChoiceIndex();
		rgbDisplayType = gd.getNextChoiceIndex();
		copyType = gd.getNextChoiceIndex();
		boolean keyOK = false;
		init();
		update(POSITION_UPDATE);
		Prefs.set(PREFS_KEY+"radius", radius);
		Prefs.saveLocation(LOC_KEY, gd.getLocation());
	}

	static String int2hex(int i, int digits) {
		boolean addHexSign = digits<6;
		char[] buf = new char[addHexSign ? digits+1 : digits];
		for (int pos=buf.length-1; pos>=buf.length-digits; pos--) {
			buf[pos] = Tools.hexDigits[i&0xf];
			i >>>= 4;
			if (addHexSign) buf[0] = 'x';
		}
		return new String(buf);
	}
}
