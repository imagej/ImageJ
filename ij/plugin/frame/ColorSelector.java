package ij.plugin.frame;
import java.awt.*;
import java.awt.event.*;
import ij.*;
import ij.gui.*;
import ij.plugin.frame.Recorder;

/** Displays a window that allows the user to change
	the foregound color or the color of ROI outline. */
public class ColorSelector extends PlugInFrame implements ItemListener {

	private static final int ROI=0, FOREGROUND=1, BACKGROUND=2;
	private Choice color;
	private String[] colors = {"red","green","blue","magenta     ","cyan","yellow","orange","black","white"};
	private int mode = ROI;
	
	public ColorSelector() {
		super("Selection Color");
	}
	
	public void run(String arg) {
		if (arg.equals("roi")) {
			setTitle("Selection Color");
			mode = ROI;
		} else if (arg.equals("back")) {
			setTitle("Background Color");
			mode = BACKGROUND;
		} else { // foregound
			setTitle("Foreground Color");
			mode = FOREGROUND;
		}
		
		setLayout(new FlowLayout(FlowLayout.CENTER, 35, 5));
		color = new Choice();
		for (int i=0; i<colors.length; i++)
			color.addItem(colors[i]);
		Color c;
		String name;
		switch (mode) {
			case ROI:
				c =Roi.getColor();
				name = "yellow";
				break;
			case BACKGROUND:
				c =Toolbar.getBackgroundColor();
				name = "white";
				break;
			default: // foregound
				c =Toolbar.getForegroundColor();
				name = "black";
				break;
		}
		if (c.equals(Color.red)) name = colors[0];
		else if (c.equals(Color.green)) name = colors[1];
		else if (c.equals(Color.blue)) name = colors[2];
		else if (c.equals(Color.magenta)) name = colors[3];
		else if (c.equals(Color.cyan)) name = colors[4];
		else if (c.equals(Color.yellow)) name = colors[5];
		else if (c.equals(Color.orange)) name = colors[6];
		else if (c.equals(Color.black)) name = colors[7];
		else if (c.equals(Color.white)) name = colors[8];
		color.select(name);
		color.addItemListener(this);
		add(color);

		pack();
		Dimension size = getSize();
		if (size.width<180)
			setSize(180, size.height);
		GUI.center(this);
		show();
	}

	public void itemStateChanged(ItemEvent e) {
		String name = color.getSelectedItem();
		Color c = Color.yellow;
		if (name.equals(colors[0])) c = Color.red;
		else if (name.equals(colors[1])) c = Color.green;
		else if (name.equals(colors[2])) c = Color.blue;
		else if (name.equals(colors[3])) c = Color.magenta;
		else if (name.equals(colors[4])) c = Color.cyan;
		else if (name.equals(colors[5])) c = Color.yellow;
		else if (name.equals(colors[6])) c = Color.orange;
		else if (name.equals(colors[7])) c = Color.black;
		else if (name.equals(colors[8])) c = Color.white;
		switch (mode) {
			case ROI:
				Roi.setColor(c);
				ImagePlus imp = WindowManager.getCurrentImage();
				if (imp!=null) imp.draw();
				break;
			case FOREGROUND:
				Toolbar.setForegroundColor(c);
				if (Recorder.record)
					Recorder.record("setForegroundColor", c.getRed(), c.getGreen(), c.getBlue());
				break;
			case BACKGROUND:
				Toolbar.setBackgroundColor(c);
				if (Recorder.record)
					Recorder.record("setBackgroundColor", c.getRed(), c.getGreen(), c.getBlue());
				break;
		}
	}
	
}