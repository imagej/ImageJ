package ij.gui;
import java.awt.*;
import java.awt.event.*;

/** A modal dialog box with a one line message and
	"Yes", "No" and "Cancel" buttons. */
public class YesNoCancelDialog extends Dialog implements ActionListener {
    private Button yesB, noB, cancelB;
    private boolean cancelPressed, yesPressed;

    public YesNoCancelDialog(Frame parent, String title, String msg) {
        super(parent, title, true);
		GridBagLayout gridbag = new GridBagLayout();
		GridBagConstraints c = new GridBagConstraints();
		setLayout(gridbag);

		// The message
		c.gridx = 0; c.gridy = 0;
		c.fill = GridBagConstraints.BOTH;
		c.anchor = GridBagConstraints.CENTER;
		c.gridwidth = 3;
		c.insets = new Insets(20, 10, 10, 10);
	    MultiLineLabel message = new MultiLineLabel(msg);
		gridbag.setConstraints(message, c);
		message.setFont(new Font("Dialog", Font.BOLD, 12));
        add(message);
        
		// "Yes" button. Add first so it's the highlighted button.
		c.gridx = 2; c.gridy = 1;
		c.gridwidth = 1;
		c.fill = c.NONE;
		c.insets = new Insets(10, 10, 10, 10);
        yesB = new Button("  Yes  ");
		yesB.addActionListener(this);
		gridbag.setConstraints(yesB, c);
        add(yesB);

		// "No" button
		c.gridx = 1;
        noB = new Button("  No  ");
		noB.addActionListener(this);
		gridbag.setConstraints(noB, c);
        add(noB);

		// "Cancel" button
		c.gridx = 0;
        cancelB = new Button(" Cancel ");
		cancelB.addActionListener(this);
		gridbag.setConstraints(cancelB, c);
        add(cancelB);

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