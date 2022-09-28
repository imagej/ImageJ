package ij.plugin.frame;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import ij.*;
import ij.plugin.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;
import ij.plugin.frame.Recorder;
import ij.util.Tools;

/** Adjusts the width of line selections.  */
public class LineWidthAdjuster extends PlugInFrame implements PlugIn,
	Runnable, AdjustmentListener, TextListener, ItemListener {

	public static final String LOC_KEY = "line.loc";
	int sliderRange = 300;
	Scrollbar slider;
	int value;
	boolean setText;
	static LineWidthAdjuster instance;
	Thread thread;
	boolean done;
	TextField tf;
	Checkbox checkbox;
	int lineWidth0 = (int)Line.getWidth();

	public LineWidthAdjuster() {
		super("Line Width");
		if (instance!=null) {
			WindowManager.toFront(instance);
			return;
		}
		WindowManager.addWindow(this);
		instance = this;
		slider = new Scrollbar(Scrollbar.HORIZONTAL, Line.getWidth(), 1, 1, sliderRange+1);
		GUI.fixScrollbar(slider);
		slider.setFocusable(false); // prevents blinking on Windows

		Panel panel = new Panel();
		int margin = IJ.isMacOSX()?5:0;
		GridBagLayout grid = new GridBagLayout();
		GridBagConstraints c  = new GridBagConstraints();
		panel.setLayout(grid);
		c.gridx = 0; c.gridy = 0;
		c.gridwidth = 1;
		c.ipadx = 100;
		c.insets = new Insets(margin, 15, margin, 5);
		c.anchor = GridBagConstraints.CENTER;
		grid.setConstraints(slider, c);
		panel.add(slider);
		c.ipadx = 0;  // reset
		c.gridx = 1;
		c.insets = new Insets(margin, 5, margin, 15);
		tf = new TextField(""+Line.getWidth(), 4);
		tf.addTextListener(this);
		grid.setConstraints(tf, c);
		panel.add(tf);

		c.gridx = 2;
		c.insets = new Insets(margin, 25, margin, 5);
		checkbox = new Checkbox("Spline fit", isSplineFit());
		checkbox.addItemListener(this);
		panel.add(checkbox);

		add(panel, BorderLayout.CENTER);
		slider.addAdjustmentListener(this);
		slider.setUnitIncrement(1);

		GUI.scale(this);
		pack();
		Point loc = Prefs.getLocation(LOC_KEY);
		if (loc!=null)
			setLocation(loc);
		else
			GUI.centerOnImageJScreen(this);
		setResizable(false);
		show();
		thread = new Thread(this, "LineWidthAdjuster");
		thread.start();
		setup();
		addKeyListener(IJ.getInstance());
	}

	public synchronized void adjustmentValueChanged(AdjustmentEvent e) {
		value = slider.getValue();
		setText = true;
		notify();
	}

    public  synchronized void textValueChanged(TextEvent e) {
        int width = (int)Tools.parseDouble(tf.getText(), -1);
		//IJ.log(""+width);
        if (width==-1) return;
        if (width<0) width=1;
        if (width!=Line.getWidth()) {
			slider.setValue(width);
        	value = width;
        	notify();
        }
    }
	void setup() {
	}

	// Separate thread that does the potentially time-consuming processing
	public void run() {
		while (!done) {
			synchronized(this) {
				try {wait();}
				catch(InterruptedException e) {}
				if (done) return;
			}
			if (setText) tf.setText(""+value);
			setText = false;
			Line.setWidth(value);
			updateRoi();
		}
	}

	private static void updateRoi() {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null) {
			Roi roi = imp.getRoi();
			if (roi!=null && roi.isLine()) {
				roi.updateWideLine(Line.getWidth());
				imp.draw();
				return;
			}
		}
		Roi previousRoi = Roi.getPreviousRoi();
		if (previousRoi==null) return;
		int id = previousRoi.getImageID();
		if (id>=0) return;
		imp = WindowManager.getImage(id);
		if (imp==null) return;
		Roi roi = imp.getRoi();
		if (roi!=null && roi.isLine()) {
			roi.updateWideLine(Line.getWidth());
			imp.draw();
		}
	}

	boolean isSplineFit() {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null) return false;
		Roi roi = imp.getRoi();
		if (roi==null) return false;
		if (!(roi instanceof PolygonRoi)) return false;
		return ((PolygonRoi)roi).isSplineFit();
	}

    /** Overrides close() in PlugInFrame. */
	public void close() {
		super.close();
		instance = null;
		done = true;
		Prefs.saveLocation(LOC_KEY, getLocation());
		int strokeWidth = -1;
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null) {
			Roi roi = imp.getRoi();
			if (roi!=null && roi.isLine())
				strokeWidth = (int)roi.getStrokeWidth();
		}
		if (Recorder.record && strokeWidth>=0 && strokeWidth!=lineWidth0) {
			if (Recorder.scriptMode()) {
				Recorder.recordCall("roi = imp.getRoi();");
				Recorder.recordCall("roi.setStrokeWidth("+strokeWidth+");");
				Recorder.recordCall("imp.draw();");
			} else {
				Recorder.record("Roi.setStrokeWidth", strokeWidth);
			}
			Recorder.disableCommandRecording();
		}
		synchronized(this) {notify();}
	}

    public void windowActivated(WindowEvent e) {
    	super.windowActivated(e);
    	checkbox.setState(isSplineFit());
	}

	public void itemStateChanged(ItemEvent e) {
		boolean selected = e.getStateChange()==ItemEvent.SELECTED;
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null)
			{checkbox.setState(false); return;};
		Roi roi = imp.getRoi();
		int type = roi!=null ? roi.getType() : -1;

		if (roi==null || !(roi instanceof PolygonRoi) || type==Roi.FREEROI || type==Roi.FREELINE || type==Roi.ANGLE) {
			checkbox.setState(false);
			return;
		};
		PolygonRoi poly = (PolygonRoi)roi;
		boolean splineFit = poly.isSplineFit();
		if (selected && !splineFit) {
			poly.fitSpline(); //this must not call roi.notifyListeners (live plot would trigger it continuously)
			Prefs.splineFitLines = true;
			imp.draw();
			roi.notifyListeners(RoiListener.MODIFIED);
		} else if (!selected && splineFit) {
			poly.removeSplineFit();
			Prefs.splineFitLines = false;
			imp.draw();
			roi.notifyListeners(RoiListener.MODIFIED);
		}
	}

	public static void update() {
		if (instance==null) return;
		instance.checkbox.setState(instance.isSplineFit());
		int sliderWidth = instance.slider.getValue();
		int lineWidth = Line.getWidth();
		if (lineWidth!=sliderWidth && lineWidth<=200) {
			instance.slider.setValue(lineWidth);
			instance.tf.setText(""+lineWidth);
		}
	}

}
