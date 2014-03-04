package ij.gui;
import ij.*;
import java.awt.event.*;

/** This is an extension of GenericDialog that is non-model.
 *	@author Johannes Schindelin
 */
public class NonBlockingGenericDialog extends GenericDialog {
	public NonBlockingGenericDialog(String title) {
		super(title, null);
		setModal(false);
	}

	public synchronized void showDialog() {
		super.showDialog();
		WindowManager.addWindow(this);
		try {
			wait();
		} catch (InterruptedException e) { }
	}

	public synchronized void actionPerformed(ActionEvent e) {
		super.actionPerformed(e);
		if (!isVisible()) {
			WindowManager.removeWindow(this);
			notify();
		}
	}
	
	public synchronized void keyPressed(KeyEvent e) {
		super.keyPressed(e);
		if (wasOKed() || wasCanceled()) {
			WindowManager.removeWindow(this);
			notify();
		}
	}

    public synchronized void windowClosing(WindowEvent e) {
		super.windowClosing(e);
		WindowManager.removeWindow(this);
		if (wasOKed() || wasCanceled())
			notify();
    }

}
