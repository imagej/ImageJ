package ij.gui;
import ij.IJ;
import java.awt.*;
import java.awt.event.*;

/** A modal dialog box with a one line message and
	"Yes", "No" and "Cancel" buttons. */
public class YesNoCancelDialog extends Dialog implements ActionListener, KeyListener {
    private Button yesB, noB, cancelB;
    private boolean cancelPressed, yesPressed;
	private boolean firstPaint = true;

    public YesNoCancelDialog(Frame parent, String title, String msg) {
        super(parent, title, true);
		setLayout(new BorderLayout());
		Panel panel = new Panel();
		panel.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 10));
	    MultiLineLabel message = new MultiLineLabel(msg);
		message.setFont(new Font("Dialog", Font.BOLD, 12));
		panel.add(message);
		add("North", panel);
		
		panel = new Panel();
		panel.setLayout(new FlowLayout(FlowLayout.RIGHT, 15, 8));
        yesB = new Button("  Yes  ");
		yesB.addActionListener(this);
		yesB.addKeyListener(this);
		panel.add(yesB);
        noB = new Button("  No  ");
		noB.addActionListener(this);
		noB.addKeyListener(this);
		panel.add(noB);
        cancelB = new Button(" Cancel ");
		cancelB.addActionListener(this);
		cancelB.addKeyListener(this);
		panel.add(cancelB);
		add("South", panel);
		if (ij.IJ.isMacintosh())
			setResizable(false);
		pack();
		GUI.center(this);
		show();
    }
    
	public void actionPerformed(ActionEvent e) {
		if (e.getSource()==cancelB)
			cancelPressed = true;
		else if (e.getSource()==yesB)
			yesPressed = true;
		closeDialog();
	}
	
	/** Returns true if the user dismissed dialog by pressing "Cancel". */
	public boolean cancelPressed() {
		return cancelPressed;
	}

	/** Returns true if the user dismissed dialog by pressing "Yes". */
	public boolean yesPressed() {
		return yesPressed;
	}
	
	void closeDialog() {
		setVisible(false);
		dispose();
	}

	public void keyPressed(KeyEvent e) { 
		int keyCode = e.getKeyCode(); 
		IJ.setKeyDown(keyCode); 
		if (keyCode==KeyEvent.VK_ENTER||keyCode==KeyEvent.VK_Y) {
			yesPressed = true;
			closeDialog(); 
		} else if (keyCode==KeyEvent.VK_N) {
			closeDialog(); 
		} else if (keyCode==KeyEvent.VK_ESCAPE||keyCode==KeyEvent.VK_C) { 
			cancelPressed = true; 
			closeDialog(); 
			IJ.resetEscape();
		} 
	} 

	public void keyReleased(KeyEvent e) {}
	public void keyTyped(KeyEvent e) {}

    public void paint(Graphics g) {
    	super.paint(g);
      	if (firstPaint) {
    		yesB.requestFocus();
    		firstPaint = false;
    	}
    }

}