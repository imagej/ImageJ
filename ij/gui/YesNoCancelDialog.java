package ij.gui;
import java.awt.*;
import java.awt.event.*;

/** A modal dialog box with a one line message and
	"Yes", "No" and "Cancel" buttons. */
public class YesNoCancelDialog extends Dialog implements ActionListener {
    private Button yesB, noB, cancelB;
    //private Checkbox hide;
    private boolean cancelPressed, yesPressed;

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
		panel.add(yesB);
        noB = new Button("  No  ");
		noB.addActionListener(this);
		panel.add(noB);
        cancelB = new Button(" Cancel ");
		cancelB.addActionListener(this);
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
		setVisible(false);
		dispose();
	}
	
	/** Returns true if the user dismissed dialog by pressing "Cancel". */
	public boolean cancelPressed() {
		return cancelPressed;
	}

	/** Returns true if the user dismissed dialog by pressing "Yes". */
	public boolean yesPressed() {
		return yesPressed;
	}
}