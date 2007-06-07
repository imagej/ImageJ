package ij.plugin.quicktime;
import ij.*;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;

import quicktime.qd.*;
import quicktime.*;
import quicktime.util.*;


import quicktime.io.*;
import quicktime.std.StdQTConstants;
import quicktime.std.sg.*;

import quicktime.app.sg.SGDrawer;
import quicktime.std.movies.*;
import quicktime.app.display.QTCanvas;
import quicktime.app.image.QTImageProducer;
import quicktime.app.image.ImagePresenter;

public class QTCapture extends ImagePlus implements PlugIn {

	boolean error;

	public void run(String arg) {
		SGCapture pm = new SGCapture("QuickTime Capture", this);
		pm.show();
		pm.toFront();
	}

	void closing(Image img) {
		error = (img==null);
		if (!error) setImage(img);
	}

	void closed() {
		if (!error) show();
	}
}

class SGCapture extends Frame implements WindowListener, StdQTConstants, Errors {	

	static final int kWidth = 320;
	static final int kHeight = 240;
	
	private QTCanvas 		myQTCanvas;
	//private SGSoundChannel	mAudio;
	private SGDrawer		mDrawable;
	private SequenceGrabber mGrabber;
	private QTFile			mFile;
	private Movie			mMovie;
	private QTCapture		thePlugIn;

	SGCapture (String title, QTCapture thePlugIn) {
		super (title);
		this.thePlugIn = thePlugIn;
		try {		
			QTSession.open();		
			myQTCanvas = new QTCanvas(QTCanvas.kPerformanceResize, 0.5F, 0.5F);
			myQTCanvas.setMaximumSize (new Dimension (640, 480));

			setLayout (new BorderLayout());
			add ("Center", myQTCanvas);	
			addNotify();
			Insets insets = getInsets();
			setBounds (0, 0, (insets.left + insets.right + kWidth), (insets.top + insets.bottom + kHeight));
		
			addWindowListener(this);
		} catch (Exception ee) {
			ee.printStackTrace();
			QTSession.close();
		}
	}
	
	public void windowOpened (WindowEvent e) {
		try{
			
			mGrabber = new SequenceGrabber();
			SGVideoChannel mVideo = new SGVideoChannel(mGrabber);
			
			//mVideo.settingsDialog ();
			
			mVideo.setBounds (new QDRect(kWidth, kHeight));
			//mVideo.setUsage (seqGrabPreview | seqGrabRecord | seqGrabPlayDuringRecord); // seqGrabRecord
			mVideo.setUsage (seqGrabPreview); // seqGrabRecord
	
			mDrawable = new SGDrawer(mVideo);			
			myQTCanvas.setClient(mDrawable,true);			

			//mGrabber.setDataOutput (mFile, seqGrabPreview | seqGrabRecord | seqGrabPlayDuringRecord);
			mGrabber.prepare(true,true);
			mGrabber.startPreview();
		} catch (Exception ee) {
			ee.printStackTrace();
			QTSession.close();
		}
	}

	public void windowClosing (WindowEvent e) {
		ImageProducer producer = null;
		Pict pict = null;
		ImagePresenter ip = null;
		try {
			mGrabber.pause(seqGrabPause);
			pict = mGrabber.grabPict(new QDRect(kWidth, kHeight), 0, grabPictOffScreen);
			ip = ImagePresenter.fromPict(pict);
			QDRect rect = ip.getDisplayBounds();
			Dimension d = new Dimension(rect.getWidth(), rect.getHeight());
			producer = new QTImageProducer(ip, d);
			//producer = new QTImageProducer(mDrawable, new Dimension(kWidth, kHeight));
		} catch (Exception ee) {
			ee.printStackTrace();
			QTSession.close();
		}
		Image img = createImage(producer);
		thePlugIn.closing(img);
		myQTCanvas.removeClient();
		QTSession.close();
		dispose();
	}

	public void windowIconified (WindowEvent e) {
	}
	
	public void windowDeiconified (WindowEvent e) {
	}
	
	public void windowClosed (WindowEvent e) { 
		thePlugIn.closed();
		//System.exit(0);
	}
	
	public void windowActivated (WindowEvent e) {}
	public void windowDeactivated (WindowEvent e) {}
}
