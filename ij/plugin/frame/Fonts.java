package ij.plugin.frame;
import java.awt.*;
import java.awt.event.*;

import ij.*;
import ij.plugin.*;
import ij.gui.*;

/** Displays a window that allows the user to set the font, size and style. */
public class Fonts extends PlugInFrame implements PlugIn, ItemListener {

	private Panel panel;
	private Choice font;
	private Choice size;
	private Choice style;

	public Fonts() {
		super("Fonts");
		setLayout(new FlowLayout(FlowLayout.CENTER, 10, 5));
		
		font = new Choice();
		String[] fonts = Toolkit.getDefaultToolkit().getFontList();
		for (int i=0; i<fonts.length; i++)
			font.addItem(fonts[i]);
		font.select(TextRoi.getFont());
		font.addItemListener(this);
		add(font);

		size = new Choice();
		size.addItem("8");
		size.addItem("9");
		size.addItem("10");
		size.addItem("12");
		size.addItem("14");
		size.addItem("18");
		size.addItem("24");
		size.addItem("28");
		size.addItem("36");
		size.addItem("48");
		size.addItem("60");
		size.addItem("72");
		size.select(""+TextRoi.getSize());
		size.addItemListener(this);
		add(size);
		
		style = new Choice();
		style.addItem("Plain");
		style.addItem("Bold");
		style.addItem("Italic");
		style.addItem("Bold+Italic");
		int i = TextRoi.getStyle();
		String s = "Plain";
		if (i==Font.BOLD)
			s = "Bold";
		else if (i==Font.ITALIC)
			s = "Italic";
		else if (i==(Font.BOLD+Font.ITALIC))
			s = "Bold+Italic";
		style.select(s);
		style.addItemListener(this);
		add(style);

		pack();
		GUI.center(this);
		setVisible(true);
	}
	
	public void itemStateChanged(ItemEvent e) {
		String fontName = font.getSelectedItem();
		int fontSize = Integer.parseInt(size.getSelectedItem());
		String styleName = style.getSelectedItem();
		int fontStyle = Font.PLAIN;
		if (styleName.equals("Bold"))
			fontStyle = Font.BOLD;
		else if (styleName.equals("Italic"))
			fontStyle = Font.ITALIC;
		else if (styleName.equals("Bold+Italic"))
			fontStyle = Font.BOLD+Font.ITALIC;
		TextRoi.setFont(fontName, fontSize, fontStyle);
		IJ.showStatus(fontSize+" point "+fontName + " " + styleName);
	}
	
}