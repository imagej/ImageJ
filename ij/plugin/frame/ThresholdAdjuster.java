package ij.plugin.frame;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import ij.*;
import ij.plugin.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;

/** Adjusts the lower and upper threshold levels of the active image. This
	class is multi-threaded to provide a more responsive user interface. */
public class ThresholdAdjuster extends PlugInFrame implements PlugIn, Runnable, ActionListener, AdjustmentListener {

	static final double defaultMinThreshold = 85;
	static final double defaultMaxThreshold = 170;
	static boolean fill1 = true;
	static boolean fill2 = true;
	static boolean useBW = true; 
	
	ThresholdPlot plot = new ThresholdPlot();
	Thread thread;
	
	int minValue = -1;
	int maxValue = -1;
	int sliderRange = 256;
	boolean doAutoAdjust,doReset,doApplyLut,doStateChange,doSet;
	
	Panel panel;
	Button autoB, resetB, applyB, stateB, setB;
	int previousImageID;
	int previousImageType;
	double previousMin, previousMax;
	ImageJ ij;
	double minThreshold, maxThreshold;  // 0-255
	Scrollbar minSlider, maxSlider;
	Label label1, label2;
	boolean done;
	boolean invertedLut;
	boolean blackAndWhite;
	int lutColor = ImageProcessor.RED_LUT;

	public ThresholdAdjuster() {
		super("Threshold");
		ij = IJ.getInstance();
		Font font = new Font("SansSerif", Font.PLAIN, 10);
		GridBagLayout gridbag = new GridBagLayout();
		GridBagConstraints c = new GridBagConstraints();
		setLayout(gridbag);
		
		// plot
		int y = 0;
		c.gridx = 0;
		c.gridy = y++;
		c.gridwidth = 2;
		c.fill = c.BOTH;
		c.anchor = c.CENTER;
		c.insets = new Insets(10, 10, 0, 10);
		add(plot, c);
		
		// minThreshold slider
		minSlider = new Scrollbar(Scrollbar.HORIZONTAL, sliderRange/3, 1, 0, sliderRange);
		c.gridx = 0;
		c.gridy = y++;
		c.gridwidth = 1;
		c.weightx = IJ.isMacintosh()?90:100;
		c.fill = c.HORIZONTAL;
		c.insets = new Insets(5, 10, 0, 0);
		add(minSlider, c);
		minSlider.addAdjustmentListener(this);
		minSlider.setUnitIncrement(1);
		
		// minThreshold slider label
		c.gridx = 1;
		c.gridwidth = 1;
		c.weightx = IJ.isMacintosh()?10:0;
		c.insets = new Insets(5, 0, 0, 10);
		label1 = new Label("       ", Label.RIGHT);
    	label1.setFont(font);
		add(label1, c);
		
		// maxThreshold slider
		maxSlider = new Scrollbar(Scrollbar.HORIZONTAL, sliderRange*2/3, 1, 0, sliderRange);
		c.gridx = 0;
		c.gridy = y++;
		c.gridwidth = 1;
		c.weightx = 100;
		c.insets = new Insets(0, 10, 0, 0);
		add(maxSlider, c);
		maxSlider.addAdjustmentListener(this);
		maxSlider.setUnitIncrement(1);
		
		// maxThreshold slider label
		c.gridx = 1;
		c.gridwidth = 1;
		c.weightx = 0;
		c.insets = new Insets(0, 0, 0, 10);
		label2 = new Label("       ", Label.RIGHT);
    	label2.setFont(font);
		add(label2, c);
				
		// buttons
		panel = new Panel();
		//panel.setLayout(new GridLayout(2, 2, 0, 0));
		autoB = new Button("Auto");
		autoB.addActionListener(this);
		autoB.addKeyListener(ij);
		panel.add(autoB);
		applyB = new Button("Apply");
		applyB.addActionListener(this);
		applyB.addKeyListener(ij);
		panel.add(applyB);
		resetB = new Button("Reset");
		resetB.addActionListener(this);
		resetB.addKeyListener(ij);
		panel.add(resetB);
		stateB = new Button("B&W");
		stateB.addActionListener(this);
		stateB.addKeyListener(ij);
		panel.add(stateB);
		setB = new Button("Set");
		setB.addActionListener(this);
		setB.addKeyListener(ij);
		panel.add(setB);
		//panel.add(new Label("  "));
		//blackAndWhiteCB = new Checkbox("B&W", false);
		//blackAndWhiteCB.addItemListener(this);
		//blackAndWhiteCB.addKeyListener(ij);
		//panel.add(blackAndWhiteCB);
		c.gridx = 0;
		c.gridy = y++;
		c.gridwidth = 2;
		c.insets = new Insets(5, 5, 10, 5);
		add(panel, c);
		
 		addKeyListener(ij);  // ImageJ handles keyboard shortcuts
		pack();
		GUI.center(this);
		setVisible(true);

		thread = new Thread(this, "ThresholdAdjuster");
		//thread.setPriority(thread.getPriority()-1);
		thread.start();
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null)
			setup(imp);
	}
	
	public synchronized void adjustmentValueChanged(AdjustmentEvent e) {
		if (e.getSource()==minSlider)
			minValue = minSlider.getValue();
		else
			maxValue = maxSlider.getValue();
		notify();
	}

	public synchronized  void actionPerformed(ActionEvent e) {
		Button b = (Button)e.getSource();
		if (b==null) return;
		if (b==resetB)
			doReset = true;
		else if (b==autoB)
			doAutoAdjust = true;
		else if (b==applyB)
			doApplyLut = true;
		else if (b==stateB) {
			blackAndWhite = stateB.getLabel().equals("B&W");
			if (blackAndWhite) {
				stateB.setLabel("Red");
				lutColor = ImageProcessor.BLACK_AND_WHITE_LUT;
			} else {
				stateB.setLabel("B&W");
				lutColor = ImageProcessor.RED_LUT;
			}
			doStateChange = true;
		} else if (b==setB)
			doSet = true;
		notify();
	}
	
	ImageProcessor setup(ImagePlus imp) {
		ImageProcessor ip;
		int type = imp.getType();
		if (type==ImagePlus.COLOR_RGB)
			return null;
		ip = imp.getProcessor();
		boolean minMaxChange = false;
		if (type==ImagePlus.GRAY16 || type==ImagePlus.GRAY32) {
			if (ip.getMin()!=previousMin || ip.getMax()!=previousMax)
				minMaxChange = true;
	 		previousMin = ip.getMin();
	 		previousMax = ip.getMax();
		}
		int id = imp.getID();
		if (minMaxChange || id!=previousImageID || type!=previousImageType) {
			invertedLut = imp.isInvertedLut();
			minThreshold = ip.getMinThreshold();
			maxThreshold = ip.getMaxThreshold();
			if (minThreshold==ip.NO_THRESHOLD) {
				minThreshold = defaultMinThreshold;
				maxThreshold = defaultMaxThreshold;
			}
			plot.setHistogram(imp);
			scaleUpAndSet(ip, minThreshold, maxThreshold);
			updateLabels(imp, ip);
			updatePlot();
			updateScrollBars();
			imp.updateAndDraw();
		}
	 	previousImageID = id;
	 	previousImageType = type;
	 	return ip;
	}
	
	/** Scales threshold levels in the range 0-255 to the actual levels. */
	void scaleUpAndSet(ImageProcessor ip, double minThreshold, double maxThreshold) {
		if (!(ip instanceof ByteProcessor) && minThreshold!=ImageProcessor.NO_THRESHOLD) {
			double min = ip.getMin();
			double max = ip.getMax();
			if (max>min) {
				minThreshold = min + (minThreshold/255.0)*(max-min);
				maxThreshold = min + (maxThreshold/255.0)*(max-min);
			} else
				minThreshold = ImageProcessor.NO_THRESHOLD;
		}
		ip.setThreshold(minThreshold, maxThreshold, lutColor);
	}

	/** Scales a threshold level to the range 0-255. */
	double scaleDown(ImageProcessor ip, double threshold) {
		double min = ip.getMin();
		double max = ip.getMax();
		if (max>min)
			return ((threshold-min)/(max-min))*255.0;
		else
			return ImageProcessor.NO_THRESHOLD;
	}
	
	/** Scales a threshold level in the range 0-255 to the actual level. */
	double scaleUp(ImageProcessor ip, double threshold) {
		double min = ip.getMin();
		double max = ip.getMax();
		if (max>min)
			return min + (threshold/255.0)*(max-min);
		else
			return ImageProcessor.NO_THRESHOLD;
	}

	void updatePlot() {
		plot.minThreshold = minThreshold;
		plot.maxThreshold = maxThreshold;
		plot.blackAndWhite = blackAndWhite;
		plot.repaint();
	}
	
	void updateLabels(ImagePlus imp, ImageProcessor ip) {
		double min = ip.getMinThreshold();
		double max = ip.getMaxThreshold();
		if (min==ImageProcessor.NO_THRESHOLD) {
			label1.setText("");
			label2.setText("");
		} else {
			Calibration cal = imp.getCalibration();
			if (cal.calibrated()) {
				min = cal.getCValue((int)min);
				max = cal.getCValue((int)max);
			}
			if (((int)min==min && (int)max==max) || (ip instanceof ShortProcessor)) {
				label1.setText(""+(int)min);
				label2.setText(""+(int)max);
			} else {
				label1.setText(""+IJ.d2s(min,2));
				label2.setText(""+IJ.d2s(max,2));
			}
		}
	}

	void updateScrollBars() {
		minSlider.setValue((int)minThreshold);
		maxSlider.setValue((int)maxThreshold);
	}
	
	/** Restore image outside non-rectangular roi. */
  	void doMasking(ImagePlus imp, ImageProcessor ip) {
		int[] mask = imp.getMask();
		if (mask!=null)
			ip.reset(mask);
	}

	void adjustMinThreshold(ImagePlus imp, ImageProcessor ip, double value) {
		if (IJ.altKeyDown()) {
			double width = maxThreshold-minThreshold;
			if (width<1.0) width = 1.0;
			minThreshold = value;
			maxThreshold = minThreshold+width;
			if ((minThreshold+width)>255) {
				minThreshold = 255-width;
				maxThreshold = minThreshold+width;
				minSlider.setValue((int)minThreshold);
			}
			maxSlider.setValue((int)maxThreshold);
			scaleUpAndSet(ip, minThreshold, maxThreshold);
			return;
		}
		minThreshold = value;
		if (maxThreshold<minThreshold) {
			maxThreshold = minThreshold;
			maxSlider.setValue((int)maxThreshold);
		}
		scaleUpAndSet(ip, minThreshold, maxThreshold);
	}

	void adjustMaxThreshold(ImagePlus imp, ImageProcessor ip, int cvalue) {
		maxThreshold = cvalue;
		if (minThreshold>maxThreshold) {
			minThreshold = maxThreshold;
			minSlider.setValue((int)minThreshold);
		}
		scaleUpAndSet(ip, minThreshold, maxThreshold);
	}

	void reset(ImagePlus imp, ImageProcessor ip) {
		plot.setHistogram(imp);
		ip.setThreshold(ImageProcessor.NO_THRESHOLD,0,0);
		updateScrollBars();
	}

	void doSet(ImagePlus imp, ImageProcessor ip) {
		double level1 = ip.getMinThreshold();
		double level2 = ip.getMaxThreshold();
		if (level1==ImageProcessor.NO_THRESHOLD) {
			level1 = scaleUp(ip, defaultMinThreshold);
			level2 = scaleUp(ip, defaultMaxThreshold);
		}
		GenericDialog gd = new GenericDialog("Set Threshold Levels");
		gd.addNumericField("Lower Threshold Level: ", level1, 0);
		gd.addNumericField("Upper Threshold Level: ", level2, 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		level1 = gd.getNextNumber();
		level2 = gd.getNextNumber();
		
		if (level2<level1)
			level2 = level1;
		double minDisplay = ip.getMin();
		double maxDisplay = ip.getMax();
		ip.resetMinAndMax();
		double minValue = ip.getMin();
		double maxValue = ip.getMax();
		if (level1<minValue) level1 = minValue;
		if (level2>maxValue) level2 = maxValue;
		boolean outOfRange = level1<minDisplay || level2>maxDisplay;
		if (outOfRange)
			plot.setHistogram(imp);
		else
			ip.setMinAndMax(minDisplay, maxDisplay);
			
		minThreshold = scaleDown(ip,level1);
		maxThreshold = scaleDown(ip,level2);
		scaleUpAndSet(ip, minThreshold, maxThreshold);
		updateScrollBars();
	}

	void apply(ImagePlus imp, ImageProcessor ip) {
		boolean not8Bits = !(ip instanceof ByteProcessor);
		if (not8Bits) {
			double min = ip.getMin();
			double max = ip.getMax();
			ip.setMinAndMax(min, max);
			ip = new ByteProcessor(ip.createImage());
		}
			
		boolean useBlackAndWhite = (stateB.getLabel().equals("Red"));
		if (!useBlackAndWhite) {
			GenericDialog gd = new GenericDialog("Apply Lut", this);
			gd.addCheckbox("Set thresholded pixels to foreground color", fill1);
			gd.addCheckbox("Set remaining pixels to background color", fill2);
			gd.addMessage("");
			gd.addCheckbox("Black forground, white background", useBW);
			gd.showDialog();
			if (gd.wasCanceled())
				return;
			fill1 = gd.getNextBoolean();
			fill2 = gd.getNextBoolean();
			useBW = useBlackAndWhite = gd.getNextBoolean();
		} else {
			fill1 = true;
			fill2 = true;
		}
		
		Undo.setup(Undo.FILTER, imp);
		ip.snapshot();
		reset(imp, ip);
		int fcolor, bcolor;
		int savePixel = ip.getPixel(0,0);

		if (useBlackAndWhite)
 			ip.setColor(Color.black);
 		else
 			ip.setColor(Toolbar.getForegroundColor());
		ip.drawPixel(0,0);
		fcolor = ip.getPixel(0,0);
		if (useBlackAndWhite)
 			ip.setColor(Color.white);
 		else
 			ip.setColor(Toolbar.getBackgroundColor());
		ip.drawPixel(0,0);
		bcolor = ip.getPixel(0,0);
		ip.setColor(Toolbar.getForegroundColor());
		ip.putPixel(0,0,savePixel);

		int[] lut = new int[256];
		for (int i=0; i<256; i++) {
			if (i>=minThreshold && i<=maxThreshold)
				lut[i] = fill1?fcolor:(byte)i;
			else {
				lut[i] = fill2?bcolor:(byte)i;
			}
		}
		if (not8Bits) {
			ip.applyTable(lut);
			new ImagePlus(imp.getTitle(), ip).show();
		}
		if (imp.getStackSize()>1) {
			ImageStack stack = imp.getStack();
			YesNoCancelDialog d = new YesNoCancelDialog(this,
				"Entire Stack?", "Apply threshold to all "+stack.getSize()+" slices in the stack?");
			if (d.cancelPressed())
				return;
			if (d.yesPressed())
				new StackProcessor(stack, ip).applyTable(lut);
			else
				ip.applyTable(lut);
		} else
			ip.applyTable(lut);
		imp.changes = true;
		if (plot.histogram!=null)
			plot.setHistogram(imp);
	}

	void changeState(ImagePlus imp, ImageProcessor ip) {
		//if (blackAndWhite)
		//	minThreshold = 0;
		//IJ.write("blackAndWhite: "+blackAndWhite);
		scaleUpAndSet(ip, minThreshold, maxThreshold);
		updateScrollBars();
	}

	void autoThreshold(ImagePlus imp, ImageProcessor ip) {
		if (!(ip instanceof ByteProcessor))
			return;
		minThreshold = 0;
		maxThreshold = ((ByteProcessor)ip).getAutoThreshold();
		if (invertedLut) {
			minThreshold = maxThreshold;
			maxThreshold = 255;
		}
		scaleUpAndSet(ip, minThreshold, maxThreshold);
		updateScrollBars();
 	}
	
	static final int RESET=0, AUTO=1, HIST=2, APPLY=3, STATE_CHANGE=4, MIN_THRESHOLD=5, MAX_THRESHOLD=6, SET=7;

	// Separate thread that does the potentially time-consuming processing 
	public void run() {
		while (!done) {
			synchronized(this) {
				try {wait();}
				catch(InterruptedException e) {}
			}
			doUpdate();
		}
	}

	void doUpdate() {
		ImagePlus imp;
		ImageProcessor ip;
		int action;
		int min = minValue;
		int max = maxValue;
		if (doReset) action = RESET;
		else if (doAutoAdjust) action = AUTO;
		else if (doApplyLut) action = APPLY;
		else if (doStateChange) action = STATE_CHANGE;
		else if (doSet) action = SET;
		else if (minValue>=0) action = MIN_THRESHOLD;
		else if (maxValue>=0) action = MAX_THRESHOLD;
		else return;
		minValue = -1;
		maxValue = -1;
		doReset = false;
		doAutoAdjust = false;
		doApplyLut = false;
		doStateChange = false;
		doSet = false;
		imp = WindowManager.getCurrentImage();
		if (imp==null) {
			IJ.beep();
			IJ.showStatus("No image");
			return;
		}
		if (!imp.lock())
			{imp=null; return;}
		ip = setup(imp);
		if (ip==null) {
			imp.unlock();
			IJ.beep();
			IJ.showStatus("RGB images cannot be thresolded");
			return;
		}
		//IJ.write("setup: "+(imp==null?"null":imp.getTitle()));
		switch (action) {
			case RESET: reset(imp, ip); break;
			case AUTO: autoThreshold(imp, ip); break;
			case APPLY: apply(imp, ip); break;
			case STATE_CHANGE: changeState(imp, ip); break;
			case SET: doSet(imp, ip); break;
			case MIN_THRESHOLD: adjustMinThreshold(imp, ip, min); break;
			case MAX_THRESHOLD: adjustMaxThreshold(imp, ip, max); break;
		}
		updatePlot();
		updateLabels(imp, ip);
		ip.setLutAnimation(true);
		imp.updateAndDraw();
		imp.unlock();
	}

} // ContrastAdjuster class


class ThresholdPlot extends Canvas implements Measurements, MouseListener {
	
	static final int WIDTH = 256, HEIGHT=64;
	double minThreshold = 85;
	double maxThreshold = 170;
	int[] histogram;
	Color[] hColors;
	int hmax;
	Image os;
	Graphics osg;
	boolean blackAndWhite;
	
	public ThresholdPlot() {
		addMouseListener(this);
		setSize(WIDTH+1, HEIGHT+1);
	}

	void setHistogram(ImagePlus imp) {
		ImageProcessor ip = imp.getProcessor();
		if (!(ip instanceof ByteProcessor)) {
			double min = ip.getMin();
			double max = ip.getMax();
			ip.setMinAndMax(min, max);
			ip = new ByteProcessor(ip.createImage());
		}
		ip.setMask(imp.getMask());
		ImageStatistics stats = ImageStatistics.getStatistics(ip, AREA+MODE, null);
		int maxCount2 = 0;
		histogram = stats.histogram;
		for (int i = 0; i < stats.nBins; i++)
		if ((histogram[i] > maxCount2) && (i != stats.mode))
			maxCount2 = histogram[i];
		hmax = stats.maxCount;
		if ((hmax>(maxCount2 * 2)) && (maxCount2 != 0)) {
			hmax = (int)(maxCount2 * 1.5);
			histogram[stats.mode] = hmax;
        	}
		os = null;

   		ColorModel cm = ip.getColorModel();
		if (!(cm instanceof IndexColorModel))
			return;
		IndexColorModel icm = (IndexColorModel)cm;
		int mapSize = icm.getMapSize();
		if (mapSize!=256)
			return;
		byte[] r = new byte[256];
		byte[] g = new byte[256];
		byte[] b = new byte[256];
		icm.getReds(r); 
		icm.getGreens(g); 
		icm.getBlues(b);
		hColors = new Color[256];
		for (int i=0; i<256; i++)
			hColors[i] = new Color(r[i]&255, g[i]&255, b[i]&255);
	}

	public void update(Graphics g) {
		paint(g);
	}

	public void paint(Graphics g) {
		if (histogram!=null) {
			if (os==null) {
				os = createImage(WIDTH,HEIGHT);
				osg = os.getGraphics();
				osg.setColor(Color.white);
				osg.fillRect(0, 0, WIDTH, HEIGHT);
				osg.setColor(Color.gray);
				for (int i = 0; i < WIDTH; i++) {
					if (hColors!=null) osg.setColor(hColors[i]);
					osg.drawLine(i, HEIGHT, i, HEIGHT - ((int)(HEIGHT * histogram[i])/hmax));
				}
				osg.dispose();
			}
			g.drawImage(os, 0, 0, this);
		} else {
			g.setColor(Color.white);
			g.fillRect(0, 0, WIDTH, HEIGHT);
		}
		g.setColor(Color.black);
 		g.drawRect(0, 0, WIDTH, HEIGHT);
 		if (!blackAndWhite)
			g.setColor(Color.red);
 		g.drawRect((int)minThreshold, 1, (int)(maxThreshold-minThreshold), HEIGHT);
     }

	public void mousePressed(MouseEvent e) {}
	public void mouseReleased(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void mouseClicked(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}

} // ThresholdPlot class
