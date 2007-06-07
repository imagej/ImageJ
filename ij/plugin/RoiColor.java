package ij.plugin;
import java.awt.*;
import java.awt.event.*;
import ij.*;
import ij.gui.*;

/** Displays a window that allows the user to
	change the color of the ROI outline. */
public class RoiColor extends Frame implements PlugIn, ItemListener {

	private Choice color;
	private String[] colors = {"red","green","blue","magenta     ","cyan","yellow","orange","black","white"};

	public RoiColor() {
		super("ROI Color");
		setLayout(new FlowLayout(FlowLayout.CENTER, 10, 0));
		
		color = new Choice();
		for (int i=0; i<colors.length; i++)
			color.addItem(colors[i]);
		Color c = Roi.getColor();
		String name = "yellow";
		if (c==Color.red) name = colors[0];
		else if (c==Color.green) name = colors[1];
		else if (c==Color.blue) name = colors[2];
		else if (c==Color.magenta) name = colors[3];
		else if (c==Color.cyan) name = colors[4];
		else if (c==Color.yellow) name = colors[5];
		else if (c==Color.orange) name = colors[6];
		else if (c==Color.white) name = colors[7];
		else if (c==Color.black) name = colors[8];
		color.select(name);
		color.addItemListener(this);
		add(color);

		// Use anonymous inner class to handle window closing event
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e)
			 {setVisible(false); dispose();}
		});
		
		pack();
		Dimension size = getSize();
		if (size.width<180)
			setSize(180, size.height);
		GUI.center(this);
		show();
	}
	
	
	public void run(String arg) {
		// Initialization is done in the constructor
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
		Roi.setColor(c);
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null) imp.draw();
	}
	
}