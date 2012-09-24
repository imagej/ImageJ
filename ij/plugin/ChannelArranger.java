package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.ChannelSplitter;
import java.awt.image.BufferedImage;
import java.awt.event.*;
import java.util.Vector;

/**
 * This plugin implements the Image/Colors/Arrange Channels command,
 *  which allows the user to change the order of channels.
 *
 * @author Norbert Vischer <vischer@science.uva.nl> 23-sep-2012
 */
public class ChannelArranger implements PlugIn, TextListener {
	private ThumbnailsCanvas thumbNails;
	private String patternString;
	private String allowedDigits;
	private TextField orderField;
	private int nChannels;

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		nChannels = imp.getNChannels();
		if (nChannels==1) {
			IJ.error("Image must have more than one channel");
			return;
		}
		patternString = "1234567890".substring(0, nChannels);
		allowedDigits = patternString;
		GenericDialog gd = new GenericDialog("Arrange Channels");
		thumbNails = new ThumbnailsCanvas(imp);
		Panel panel = new Panel();
		panel.add(thumbNails);
		gd.addPanel(panel);
		//gd.setInsets(20, 20, 5);
		gd.addStringField("New Channel Order:", allowedDigits);
		Vector v = gd.getStringFields();
		orderField = (TextField)v.elementAt(0);
		orderField.addTextListener(this);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		String newOrder = gd.getNextString();
		int nChannels2 = newOrder.length();
		if (nChannels2==0)
			return;
		if (nChannels2<nChannels) {
			String msg = "The number of channels will be reduced from "+nChannels+" to "+nChannels2+".";
			if (!IJ.showMessageWithCancel("Reduce Number of Channels?", msg))
				return;
		}
		ImagePlus[] channels = ChannelSplitter.split(imp);
		ImagePlus[] channels2 = new ImagePlus[nChannels2];
		for (int i=0; i<nChannels2; i++)
			channels2[i] = channels[newOrder.charAt(i)-48-1];
		ImagePlus imp2 = null;
		if (nChannels2==1)
			imp2 = channels2[0];
		else
			imp2 = RGBStackMerge.mergeChannels(channels2, false);
		imp2.setTitle(imp.getTitle());
		int mode2 = CompositeImage.COLOR;
		if (imp.isComposite())
			mode2 = ((CompositeImage)imp).getMode();
		if (imp2.isComposite())
			((CompositeImage)imp2).setMode(mode2);
		Point location = imp.getWindow()!=null?imp.getWindow().getLocation():null;
		imp.changes = false;
		imp.close();
		if (location!=null)
			ImageWindow.setNextLocation(location);
		imp2.show();
		//imp.setImage(imp2);
	}

	public void textValueChanged(TextEvent e) {
		TextField tf = (TextField) e.getSource();
		String typed = tf.getText();
		if (typed.length()>nChannels) {
			orderField.setText(patternString);
			return;
		}
		for (int jj=0; jj<typed.length(); jj++) {
			String digit = typed.substring(jj, jj + 1);
			int found = typed.indexOf(digit, jj + 1);
			if (found != -1) {
				orderField.setText(patternString);
				return;
			}
			if (allowedDigits.indexOf(digit)==-1) {
				orderField.setText(patternString);
				return;
			}
		}
		patternString = typed;
		thumbNails.setSequence(patternString);
		thumbNails.repaint();
		//orderField.setText(patternString);
	}

}


class ThumbnailsCanvas extends Canvas {
	Image os;
	Graphics osg;
	ImagePlus imp;
	int iconSize = 100;
	int iconWidth = iconSize, iconHeight = iconSize;
	int dx = 0, dy = 0;
	int k6 = 6;
	int nChannels;
	String seq = "1234567890";

	public ThumbnailsCanvas(ImagePlus imp) {
		this.imp = imp;
		int ww = imp.getWidth();
		int hh = imp.getHeight();
		if (ww > hh) {
			iconHeight = iconWidth * hh / ww;
			dy = (iconWidth - iconHeight) / 2;
		}
		if (ww < hh) {
			iconWidth = iconHeight * ww / hh;
			dx = (iconHeight - iconWidth) / 2;
		}
		nChannels = imp.getNChannels();
		seq = seq.substring(0, nChannels);
		setSize((nChannels + 1) * iconSize, 2 * iconSize + 30);
	}

	public void update(Graphics g) {
		paint(g);
	}

	public void setSequence(String seq) {
		this.seq = seq;
	}

	public void paint(Graphics g) {
		if (g==null) {
			return;
		}
		int mode = 0;
		CompositeImage ci = null;
		if (imp.isComposite()) {
			ci = (CompositeImage) imp;
			mode = ci.getMode();
			ci.setMode(CompositeImage.COLOR);
		}

		BufferedImage bImg;
		ImageProcessor ipSmall;

		os = createImage((nChannels+1) * iconSize, 2 * iconSize + 30);
		osg = os.getGraphics();
		osg.setFont(new Font("SansSerif", Font.PLAIN, 12));
		int channel = imp.getChannel();
		int y1;
		for (int chn=1; chn<=nChannels; chn++) {
			imp.setPosition(chn, imp.getSlice(), imp.getFrame());
			imp.updateImage();
			ipSmall = imp.getProcessor().resize(iconWidth, iconHeight, true);
			bImg = ipSmall.getBufferedImage();
			int index = chn - 1;
			y1 = 10;
			for (int row=0; row<2; row++) {
				if (index >= 0) {
					int xx = index * iconSize + 44;
					osg.drawImage(bImg, xx + dx, y1 + dy, null);
					osg.setColor(Color.LIGHT_GRAY);
					osg.drawRect(xx, y1, iconSize, iconSize);
					osg.fillRoundRect(xx + iconSize / 2 - 4, y1 + iconSize - 22, 18, 18, 6, 6);
					osg.setColor(Color.BLACK);
					osg.drawRoundRect(xx + iconSize / 2 - 4, y1 + iconSize - 22, 18, 18, 6, 6);
					osg.drawString("" + chn, xx + 52, y1 + iconSize - 7);
					index = seq.indexOf("" + chn, 0);
					if (seq.indexOf("" + chn, index) == -1) //char must not occur twice
						index = -1;
				}
				y1 += (iconSize + k6);
			}
		}
		imp.setPosition(channel, imp.getSlice(), imp.getFrame());
		y1 = iconSize - 4;
		osg.drawString("Old:", 6, y1);
		y1 += (iconSize + k6);
		osg.drawString("New:", 6, y1);
		osg.dispose();
		if (os==null)
			return;
		g.drawImage(os, 0, 0, this);
		if (ci!=null)
			ci.setMode(mode);
		imp.updateImage();
	}

}
