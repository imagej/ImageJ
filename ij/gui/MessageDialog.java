package ij.gui;
import ij.*;
import java.awt.*;
import java.awt.event.*;

/** A modal dialog box that displays information. Based on the
	InfoDialogclass from "Java in a Nutshell" by David Flanagan. */
public class MessageDialog extends Dialog implements ActionListener, KeyListener, WindowListener {
	protected Button button;
	protected MultiLineLabel label;
	private boolean escapePressed;
	
	public MessageDialog(Frame parent, String title, String message) {
		super(parent, title, true);
		setLayout(new BorderLayout());
		if (message==null) message = "";
		Font font = null;
		double scale = Prefs.getGuiScale();
		if (scale>1.0) {
			font = getFont();
			if (font!=null)
				font = font.deriveFont((float)(font.getSize()*scale));
			else
				font = new Font("SansSerif", Font.PLAIN, (int)(12*scale));
			setFont(font);
		}
		label = new MultiLineLabel(message);
		if (font!=null)
			label.setFont(font);
		else if (!IJ.isLinux())
			label.setFont(new Font("SansSerif", Font.PLAIN, 14));
		Panel panel = new Panel();
		panel.setLayout(new FlowLayout(FlowLayout.CENTER, 15, 15));
		panel.add(label);
		add("Center", panel);
		button = new Button("  OK  ");
		button.addActionListener(this);
		button.addKeyListener(this);
		panel = new Panel();
		panel.setLayout(new FlowLayout());
		panel.add(button);
		add("South", panel);
		if (ij.IJ.isMacintosh())
			setResizable(false);
		pack();
		GUI.center(this);
		addWindowListener(this);
		show();
	}
	
	public void actionPerformed(ActionEvent e) {
		dispose();
	}
	
	public void keyPressed(KeyEvent e) { 
		int keyCode = e.getKeyCode(); 
		IJ.setKeyDown(keyCode);
		escapePressed = keyCode==KeyEvent.VK_ESCAPE;
		if (keyCode==KeyEvent.VK_ENTER || escapePressed)
			dispose();
	} 
	
	public void keyReleased(KeyEvent e) {
		int keyCode = e.getKeyCode(); 
		IJ.setKeyUp(keyCode); 
	}
	
	public void keyTyped(KeyEvent e) {}

	public void windowClosing(WindowEvent e) {
		dispose();
	}
	
	public boolean escapePressed() {
		return escapePressed;
	}

	public void windowActivated(WindowEvent e) {}
	public void windowOpened(WindowEvent e) {}
	public void windowClosed(WindowEvent e) {}
	public void windowIconified(WindowEvent e) {}
	public void windowDeiconified(WindowEvent e) {}
	public void windowDeactivated(WindowEvent e) {}

}
