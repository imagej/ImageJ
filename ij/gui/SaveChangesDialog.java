package ij.gui;
import java.awt.*;
import java.awt.event.*;

/** A modal dialog box with a one line message and
	"Don't Save", "Cancel" and "Save" buttons. */
public class SaveChangesDialog extends Dialog implements ActionListener {
	static final String message = "";
    private Button dontSave, cancel, save;
    private boolean cancelPressed, savePressed;

    public SaveChangesDialog(Frame parent, String fileName) {
        super(parent, "Save?", true);
		GridBagLayout gridbag = new GridBagLayout();
		GridBagConstraints c = new GridBagConstraints();
		setLayout(gridbag);

		// The message
		c.gridx = 0; c.gridy = 0;
		c.fill = GridBagConstraints.BOTH;
		c.anchor = GridBagConstraints.CENTER;
		c.gridwidth = 3;
		c.insets = new Insets(20, 10, 10, 10);
		Label message;
		if (fileName.startsWith("Save "))
	    	message = new Label(fileName);
	    else
			message = new Label("Save changes to \"" + fileName + "\"?");
		gridbag.setConstraints(message, c);
		message.setFont(new Font("Dialog", Font.BOLD, 12));
        add(message);
        
		int yesx=0, nox=1, cancelx=2;
		if (ij.IJ.isMacintosh())
			{yesx=2; nox=0; cancelx=1;}
		// "Save" button. Add first so it's the highlighted button.
		c.gridx = yesx; c.gridy = 1;
		c.gridwidth = 1;
		c.fill = c.NONE;
		c.insets = new Insets(10, 10, 10, 10);
        save = new Button("  Save  ");
		save.addActionListener(this);
		gridbag.setConstraints(save, c);
        add(save);

		// "Cancel" button
		c.gridx = cancelx;
        cancel = new Button("  Cancel  ");
		cancel.addActionListener(this);
		gridbag.setConstraints(cancel, c);
        add(cancel);

		// "Don't Save" button
		c.gridx = nox;
        dontSave = new Button(" Don't Save ");
		dontSave.addActionListener(this);
		gridbag.setConstraints(dontSave, c);
        add(dontSave);

        setResizable(false);
        pack();
		GUI.center(this);
        show();
    }
    
	public void actionPerformed(ActionEvent e) {
		if (e.getSource()==cancel)
			cancelPressed = true;
		else if (e.getSource()==save)
			savePressed = true;
		setVisible(false);
		dispose();
	}
	
	/** Returns true if the user dismissed dialog by pressing "Cancel". */
	public boolean cancelPressed() {
		if (cancelPressed)
			ij.Macro.abort();
		return cancelPressed;
	}

	/** Returns true if the user dismissed dialog by pressing "Save". */
	public boolean savePressed() {
		return savePressed;
	}
}