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
 *	which allows the user to change the order of channels.
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
			IJ.error("Channel Arranger", "Image must have more than one channel");
			return;
		}
		if (nChannels>9) {
			IJ.error("Channel Arranger", "This command does not work with more than 9 channels.");
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
		gd.addStringField("New channel order:", allowedDigits);
		Vector v = gd.getStringFields();
		orderField = (TextField)v.elementAt(0);
		orderField.addTextListener(this);
		gd.addHelp(IJ.URL+"/docs/menus/image.html#arrange");
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		String newOrder = gd.getNextString();
		int nChannels2 = newOrder.length();
		if (nChannels2==0)
			return;
		for (int i=0; i<nChannels2; i++) {
			if (!Character.isDigit(newOrder.charAt(i))) {
				IJ.error("Channel Arranger", "Non-digit in new order string: \""+newOrder+"\"");
				return;
			}
		}
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
		int mode2 = CompositeImage.COLOR;
		if (imp.isComposite())
			mode2 = ((CompositeImage)imp).getMode();
		if (imp2.isComposite())
			((CompositeImage)imp2).setMode(mode2);
			
		int[] stackPos = thumbNails.getStackPos();
		String digit = ""+stackPos[0];
		int currentCh = newOrder.indexOf(digit)+1;
		int currentSlc = stackPos[1];
		int currentFrm = stackPos[2];
		imp2.setPosition(currentCh, currentSlc, currentFrm);//accepts currentCh out of range       

		Point location = imp.getWindow()!=null?imp.getWindow().getLocation():null;
		imp.changes = false;
		imp.close();
		imp2.copyAttributes(imp);
		if (location!=null)
			ImageWindow.setNextLocation(location);
		imp2.changes = true;
		imp2.show();
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

class ThumbnailsCanvas extends Canvas implements MouseListener, MouseMotionListener, ActionListener {

	protected static Cursor handCursor = new Cursor(Cursor.HAND_CURSOR);
	protected static Cursor defaultCursor = new Cursor(Cursor.DEFAULT_CURSOR);
	Image os;
	Graphics osg;
	CompositeImage cImp;
	int iconSize = 100;
	int iconWidth = iconSize, iconHeight = iconSize;
	int dx = 0, dy = 0;
	int separatorY = 6;
	int marginY = 10;
	int marginX = 44;
	int nChannels;
	int channelUnderCursor = 0;
	String seq = "1234567890";
	int currentChannel, currentSlice, currentFrame;

	public ThumbnailsCanvas(ImagePlus imp) {
		if (!imp.isComposite()) {
			return;
		}
		cImp = (CompositeImage) imp;
		addMouseListener(this);
		addMouseMotionListener(this);
		currentChannel = cImp.getChannel();
		currentSlice = cImp.getSlice();
		currentFrame = cImp.getFrame();
		channelUnderCursor = currentChannel;
		int ww = cImp.getWidth();
		int hh = cImp.getHeight();
		if (ww > hh) {
			iconHeight = iconWidth * hh / ww;
			dy = (iconWidth - iconHeight) / 2;
		}
		if (ww < hh) {
			iconWidth = iconHeight * ww / hh;
			dx = (iconHeight - iconWidth) / 2;
		}
		nChannels = cImp.getNChannels();
		seq = seq.substring(0, nChannels);
		setSize((nChannels + 1) * iconSize, 2 * iconSize + 30);
	}

	public void update(Graphics g) {
		paint(g);
	}

	public void setSequence(String seq) {
		this.seq = seq;
	}

	public int[] getStackPos() {
		return new int[]{currentChannel, currentSlice, currentFrame};
	}

	public void paint(Graphics g) {
		if (g == null) {
			return;
		}
		int savedMode = cImp.getMode();
		if (savedMode==CompositeImage.COMPOSITE)
			cImp.setMode(CompositeImage.COLOR);
		BufferedImage bImg;
		ImageProcessor ipSmall;

		os = createImage((nChannels + 1) * iconSize, 2 * iconSize + 30);
		osg = os.getGraphics();
		osg.setFont(ImageJ.SansSerif12);
		int y1;
		for (int chn = 1; chn <= nChannels; chn++) {
			cImp.setPositionWithoutUpdate(chn, currentSlice, currentFrame);
			cImp.updateImage();
			ipSmall = cImp.getProcessor().resize(iconWidth, iconHeight, true);
			bImg = ipSmall.getBufferedImage();
			int index = chn - 1;
			y1 = marginY;
			for (int row = 0; row < 2; row++) {
				if (index >= 0) {
					int xx = index * iconSize + marginX;
					osg.drawImage(bImg, xx + dx, y1 + dy, null);
					osg.setColor(Color.LIGHT_GRAY);
					osg.drawRect(xx, y1, iconSize, iconSize);
					osg.fillRoundRect(xx + iconSize / 2 - 4, y1 + iconSize - 22, 18, 18, 6, 6);
					osg.setColor(Color.BLACK);
					osg.drawRoundRect(xx + iconSize / 2 - 4, y1 + iconSize - 22, 18, 18, 6, 6);
					osg.drawString("" + chn, xx + 52, y1 + iconSize - 7);
					index = seq.indexOf("" + chn, 0);
					if (seq.indexOf("" + chn, index) == -1) {//char must not occur twice
						index = -1;
					}
				}
				y1 += (iconSize + separatorY);
			}
		}
		y1 = marginY + iconSize - 7;
		osg.drawString("Old:", 6, y1);
		y1 += (iconSize + separatorY);
		osg.drawString("New:", 6, y1);
		osg.dispose();
		if (os == null) {
			return;
		}
		g.drawImage(os, 0, 0, this);
		if (savedMode==CompositeImage.COMPOSITE)
			cImp.setMode(savedMode);
		cImp.setPosition(currentChannel, currentSlice, currentFrame);
		cImp.updateImage();
	}

	protected void handlePopupMenu(MouseEvent e) {
		int x = e.getX();
		int y = e.getY();
		PopupMenu popup = new PopupMenu();
		String[] colors = "Grays,-,Red,Green,Blue,Yellow,Magenta,Cyan,-,Fire,Ice,Spectrum,3-3-2 RGB,Red/Green".split(",");
		for (int jj = 0; jj < colors.length; jj++) {
			if (colors[jj].equals("-")) {
				popup.addSeparator();
			} else {
				MenuItem mi = new MenuItem(colors[jj]);
				popup.add(mi);
				mi.addActionListener(this);
			}
		}
		add(popup);
		if (IJ.isMacOSX()) {
			IJ.wait(10);
		}
		popup.show(this, x, y);
		setCursor(defaultCursor);
	}

	public void actionPerformed(ActionEvent e) {
		String cmd = e.getActionCommand();
		cImp.setPosition(currentChannel, currentSlice, currentFrame);
		CompositeImage cImp = (CompositeImage) this.cImp;

		IJ.run(cmd);

		repaint();
		setCursor(defaultCursor);
	}

	public void mouseMoved(MouseEvent e) {
		int x = e.getX() - marginX;
		int y = e.getY() - marginY;
		if (x < 0 || x > nChannels * iconSize || y < 0 || y > iconSize * 2 + separatorY) {
			setCursor(defaultCursor);
			channelUnderCursor = 0;
		} else {
			int chn = x / iconSize + 1;
			if (y > iconSize) {
				if (chn <= seq.length()) {
					String digit = seq.substring(chn - 1, chn);
					chn = "1234567890".indexOf(digit) + 1;
				} else {
					chn = 0;
				}
			}
			if (y > 2 * iconSize + separatorY) {
				chn = 0;
			}
			channelUnderCursor = chn;
		}

		if (channelUnderCursor > 0) {
			setCursor(handCursor);
		} else {
			setCursor(defaultCursor);
		}

	}

	public void mouseEntered(MouseEvent e) {
	}

	public void mousePressed(MouseEvent e) {
		if (channelUnderCursor > 0) {
			currentChannel = channelUnderCursor;
			handlePopupMenu(e);
			repaint();
		}
	}

	public void mouseReleased(MouseEvent e) {
		mouseMoved(e);
	}

	public void mouseExited(MouseEvent e) {
	}

	public void mouseDragged(MouseEvent e) {
	}

	public void mouseClicked(MouseEvent e) {
	}
}
