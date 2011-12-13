package ij.gui;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/** This is modal dialog box that displays HTML formated text. */
public class HTMLDialog extends JDialog implements ActionListener, KeyListener {
	private boolean escapePressed;
	
	public HTMLDialog(String title, String message) {
		super(ij.IJ.getInstance(), title, true);
		init(message);
	}

	public HTMLDialog(Dialog parent, String title, String message) {
		super(parent, title, true);
		init(message);
	}
	
	private void init(String message) {
		ij.util.Java2.setSystemLookAndFeel();
		Container container = getContentPane();
		container.setLayout(new BorderLayout());
		if (message==null) message = "";
		JLabel label = new JLabel(message);
		JPanel panel = new JPanel();
		panel.setLayout(new FlowLayout(FlowLayout.CENTER, 15, 15));
		panel.add(label);
		container.add(panel, "Center");
		JButton button = new JButton("OK");
		button.addActionListener(this);
		button.addKeyListener(this);
		panel = new JPanel();
		panel.add(button);
		container.add(panel, "South");
		setForeground(Color.black);
		pack();
		GUI.center(this);
		show();
	}

	public void actionPerformed(ActionEvent e) {
		//setVisible(false);
		dispose();
	}
	
	public void keyPressed(KeyEvent e) { 
		int keyCode = e.getKeyCode(); 
		ij.IJ.setKeyDown(keyCode);
		escapePressed = keyCode==KeyEvent.VK_ESCAPE;
		if (keyCode==KeyEvent.VK_ENTER || escapePressed)
			dispose();
	} 
	
	public void keyReleased(KeyEvent e) {
		int keyCode = e.getKeyCode(); 
		ij.IJ.setKeyUp(keyCode); 
	}
	
	public void keyTyped(KeyEvent e) {}
	
	public boolean escapePressed() {
		return escapePressed;
	}

}
