package ij.text;

import java.awt.*;
import java.io.*;
import java.awt.event.*;
import ij.*;
import ij.io.*;

/** Uses a TextPanel to displays text in a window.
	@see TextPanel
*/
public class TextWindow extends Frame {

	private TextPanel textPanel;

	/**
	Opens a new single-column text window.
	@param title	the title of the window
	@param str		the text initially displayed in the window
	@param width	the width of the window in pixels
	@param height	the height of the window in pixels
	*/
	public TextWindow(String title, String data, int width, int height) {
		this(title, "", data, width, height);
	}

	/**
	Opens a new multi-column text window.
	@param title	the title of the window
	@param headings	the tab-delimited column headings
	@param data		the text initially displayed in the window
	@param width	the width of the window in pixels
	@param height	the height of the window in pixels
	*/
	public TextWindow(String title, String headings, String data, int width, int height) {
		super(title);
		enableEvents(AWTEvent.WINDOW_EVENT_MASK);
		textPanel = new TextPanel();
		textPanel.setTitle(title);
		add("Center", textPanel);
		textPanel.setColumnHeadings(headings);
		textPanel.append(data);
		setSize(width, height);
		setVisible(true);
	}

	/**
	Opens a new text window containing the contents
	of a text file.
	@param path		the path to the text file
	@param width	the width of the window in pixels
	@param height	the height of the window in pixels
	*/
	public TextWindow(String path, int width, int height) {
		super("");
		enableEvents(AWTEvent.WINDOW_EVENT_MASK);
		textPanel = new TextPanel();
		add("Center", textPanel);
		if (openFile(path)) {
			setSize(width, height);
			setVisible(true);
		} else
			dispose();
	}
	
	/**
	Adds one or lines of text to the window.
	@param text		The text to be appended. Multiple
					lines should be separated by \n.
	*/
	public void append(String text) {
		textPanel.append(text);
	}
	
	/** Set the font that will be used to display the text. */
	public void setFont(Font font) {
		textPanel.setFont(font);
	}
  
	boolean openFile(String path) {
		OpenDialog od = new OpenDialog("Open Text File...", path);
		String directory = od.getDirectory();
		String name = od.getFileName();
		if (name==null)
			return false;
		path = directory + name;
		
		IJ.showStatus("Opening: " + path);
		try {
			BufferedReader r = new BufferedReader(new FileReader(directory + name));
			load(r);
		}
		catch (Exception e) {
			IJ.error(e.getMessage());
			return true;
		}
		textPanel.setTitle(name);
		setTitle(name);
		IJ.showStatus("");
		return true;
	}
	
	/** Returns a reference to this TextWindow's TextPanel. */
	public TextPanel getTextPanel() {
		return textPanel;
	}

	/** Appends the text in the specified file to the end of this TextWindow. */
	public void load(BufferedReader in) throws IOException {
		int count=0;
		while (true) {
			String s=in.readLine();
			if (s==null) break;
			textPanel.appendLine(s);
		}
	}

	public void processWindowEvent(WindowEvent e) {
		super.processWindowEvent(e);
		if (e.getID()==WindowEvent.WINDOW_CLOSING) {	
			setVisible(false);
			dispose();
			textPanel.flush();
		}
	}
}