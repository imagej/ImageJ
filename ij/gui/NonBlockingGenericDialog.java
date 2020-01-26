package ij.gui;
import ij.*;
import java.awt.event.*;
import java.awt.EventQueue;

/** This is an extension of GenericDialog that is non-modal.
 *	@author Johannes Schindelin
 */
public class NonBlockingGenericDialog extends GenericDialog {

	ImagePlus imp;                  //when non-null, this dialog gets closed when the image is closed
	WindowListener windowListener;  //checking for whether the associated window gets closed

	public NonBlockingGenericDialog(String title) {
		super(title, getParentFrame());
		setModal(false);
		IJ.protectStatusBar(false);
	}

	public synchronized void showDialog() {
		super.showDialog();
		if (isMacro())
			return;
		if (!IJ.macroRunning()) {   // add to Window menu on event dispatch thread
			final NonBlockingGenericDialog thisDialog = this;
			EventQueue.invokeLater(new Runnable() {
				public void run() {
					WindowManager.addWindow(thisDialog);
				}
			});
		}
		if (imp != null) {
			ImageWindow win = imp.getWindow();
			if (win != null) {      //when the associated image closes, also close the dialog
				final NonBlockingGenericDialog gd = this;
				windowListener = new WindowAdapter() {
					public void windowClosed(WindowEvent e) {
						cancelDialogAndClose();	
					}
				};
				win.addWindowListener(windowListener);
			}
		}
		try {
			wait();
		} catch (InterruptedException e) { }
		finalizeRecording();
		resetCounters();
	}

	/** Gets called if the associated image window is closed */
	private void cancelDialogAndClose() {
		super.windowClosing(null);	// sets wasCanceled=true and does dispose()
	}

	public synchronized void actionPerformed(ActionEvent e) {
		super.actionPerformed(e);
		if (!isVisible())
			notify();
	}
	
	public synchronized void keyPressed(KeyEvent e) {
		super.keyPressed(e);
		if (wasOKed() || wasCanceled())
			notify();
	}

    public synchronized void windowClosing(WindowEvent e) {
		super.windowClosing(e);
		if (wasOKed() || wasCanceled())
			notify();
    }
    
	public void dispose() {
		super.dispose();
		WindowManager.removeWindow(this);
		if (imp != null) {
			ImageWindow win = imp.getWindow();
			if (win != null && windowListener != null)
				win.removeWindowListener(windowListener);
		}			
	}
	
	/** Returns a new NonBlockingGenericDialog with given title, unless
	 *  java is running in headless mode; then a GenericDialog will be
	 *  returned (headless mode does not support the NonBlockingGenericDialog).
	 *  @param title Dialog title
	 *  @param imp   The image associated with this dialog
	*/
	public static GenericDialog newDialog(String title, ImagePlus imp) {
		if (Prefs.nonBlockingFilterDialogs && imp!=null && imp.getWindow()!=null) {
			NonBlockingGenericDialog gd = new NonBlockingGenericDialog(title);
			gd.imp = imp;
			return gd;
		} else
			return new GenericDialog(title);
	}

	/** Put the dialog into the foreground when the image we work on gets into the foreground */
	@Override
	public void windowActivated(WindowEvent e) {
		if ((e.getWindow() instanceof ImageWindow) && e.getOppositeWindow()!=this)
			toFront();
		WindowManager.setWindow(this);
	}
	
}
