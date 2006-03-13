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
	Runnable, AdjustmentListener, TextListener {

	int sliderRange = 100;
	Scrollbar slider;
	int value;
	boolean setText;
	static Frame instance; 
	Thread thread;
	boolean done;
	TextField tf;

	public LineWidthAdjuster() {
		super("Line Width");
		if (instance!=null) {
			instance.toFront();
			return;
		}		
		WindowManager.addWindow(this);
		instance = this;
		slider = new Scrollbar(Scrollbar.HORIZONTAL, 1, 1, 1, sliderRange+1);
		
		Panel panel = new Panel();
		GridBagLayout grid = new GridBagLayout();
		GridBagConstraints c  = new GridBagConstraints();
		panel.setLayout(grid);
		c.gridx = 0; c.gridy = 0;
		c.gridwidth = 1;
		c.ipadx = 75;
		c.insets = new Insets(5, 15, 5, 5);
		c.anchor = GridBagConstraints.WEST;
		grid.setConstraints(slider, c);
		panel.add(slider);
		c.ipadx = 0;  // reset
		c.gridx = 1;
		c.insets = new Insets(5, 5, 5, 15);
		c.anchor = GridBagConstraints.EAST;
		tf = new TextField(""+Line.getWidth(), 3);
		tf.addTextListener(this);
		grid.setConstraints(tf, c);
    	panel.add(tf);
		
		add(panel, BorderLayout.CENTER);
		slider.addAdjustmentListener(this);
		slider.setUnitIncrement(1);
		pack();
		setResizable(false);
		GUI.center(this);
		show();
		thread = new Thread(this, "LineWidthAdjuster");
		thread.start();
		setup();
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
				Line.setWidth(value);
				if (setText) tf.setText(""+value);
				setText = false;
				ImagePlus imp = WindowManager.getCurrentImage();
				if (imp!=null) {
					Roi roi = imp.getRoi();
					if (roi!=null) imp.draw();
				}
			}
		}
	}

    public void windowClosing(WindowEvent e) {
    	close();
	}

    /** Overrides close() in PlugInFrame. */
    public void close() {
    	super.close();
		instance = null;
		done = true;
		synchronized(this) {notify();}
	}

    public void windowActivated(WindowEvent e) {
    	super.windowActivated(e);
	}

} 

