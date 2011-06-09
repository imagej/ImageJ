package ij.plugin.frame;
import java.awt.*;
import java.awt.event.*;

import ij.*;
import ij.plugin.*;
import ij.gui.*;

/** Displays a window that allows the user to set the font, size and style. */
public class Fonts extends PlugInFrame implements PlugIn, ItemListener {

	public static final String LOC_KEY = "fonts.loc";
	private static String[] sizes = {"8","9","10","12","14","18","24","28","36","48","60","72","100","150","225","350"};
	private static int[] isizes = {8,9,10,12,14,18,24,28,36,48,60,72,100,150,225,350};
	private Panel panel;
	private Choice font;
	private Choice size;
	private Choice style;
	private Checkbox checkbox;
	private static Frame instance;

	public Fonts() {
		super("Fonts");
		if (instance!=null) {
			WindowManager.toFront(instance);
			return;
		}
		WindowManager.addWindow(this);
		instance = this;
		setLayout(new FlowLayout(FlowLayout.CENTER, 10, 5));
		
		font = new Choice();
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		String[] fonts = ge.getAvailableFontFamilyNames();
		font.add("SansSerif");
		font.add("Serif");
		font.add("Monospaced");
		for (int i=0; i<fonts.length; i++) {
			String f = fonts[i];
			if (!(f.equals("SansSerif")||f.equals("Serif")||f.equals("Monospaced")))
				font.add(f);
		}
		font.select(TextRoi.getFont());
		font.addItemListener(this);
		add(font);

		size = new Choice();
		for (int i=0; i<sizes.length; i++)
			size.add(sizes[i]);
		size.select(getSizeIndex());
		size.addItemListener(this);
		add(size);
		
		style = new Choice();
		style.add("Plain");
		style.add("Bold");
		style.add("Italic");
		style.add("Bold+Italic");
		style.add("Center");
		style.add("Right");
		style.add("Center+Bold");
		style.add("Right+Bold");
		int i = TextRoi.getStyle();
		int justificaton = TextRoi.getGlobalJustification();
		String s = "Plain";
		if (i==Font.BOLD) {
			if (justificaton==TextRoi.CENTER)
				s = "Center+Bold";
			else if (justificaton==TextRoi.RIGHT)
				s = "Right+Bold";
			else
				s = "Bold";
		} else if (i==Font.ITALIC)
			s = "Italic";
		else if (i==(Font.BOLD+Font.ITALIC))
			s = "Bold+Italic";
		else if (i==Font.PLAIN) {
			if (justificaton==TextRoi.CENTER)
				s = "Center";
			else if (justificaton==TextRoi.RIGHT)
				s = "Right";
		}
		style.select(s);
		style.addItemListener(this);
		add(style);
		
		checkbox = new Checkbox("Smooth", TextRoi.isAntialiased());
		add(checkbox);
		checkbox.addItemListener(this);

		pack();
		Point loc = Prefs.getLocation(LOC_KEY);
		if (loc!=null)
			setLocation(loc);
		else
			GUI.center(this);
		show();
		IJ.register(Fonts.class);
	}
	
	int getSizeIndex() {
		int size = TextRoi.getSize();
		int index=0;
		for (int i=0; i<isizes.length; i++) {
			if (size>=isizes[i])
				index = i;
		}
		return index;
	}
	
	public void itemStateChanged(ItemEvent e) {
		String fontName = font.getSelectedItem();
		int fontSize = Integer.parseInt(size.getSelectedItem());
		String styleName = style.getSelectedItem();
		int fontStyle = Font.PLAIN;
		int justification = TextRoi.LEFT;
		if (styleName.endsWith("Bold"))
			fontStyle = Font.BOLD;
		else if (styleName.equals("Italic"))
			fontStyle = Font.ITALIC;
		else if (styleName.equals("Bold+Italic"))
			fontStyle = Font.BOLD+Font.ITALIC;
		if (styleName.startsWith("Center"))
			justification = TextRoi.CENTER;
		else if (styleName.startsWith("Right"))
			justification = TextRoi.RIGHT;
		TextRoi.setFont(fontName, fontSize, fontStyle, checkbox.getState());
		TextRoi.setGlobalJustification(justification);
		IJ.showStatus(fontSize+" point "+fontName + " " + styleName);
	}
	
    public void close() {
	 	super.close();
		instance = null;
		Prefs.saveLocation(LOC_KEY, getLocation());
	}

}
