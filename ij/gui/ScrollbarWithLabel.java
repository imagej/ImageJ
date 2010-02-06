package ij.gui;
import ij.IJ;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;


/** This class, taken from Joachim Walter's Image5D package,
	 adds "c", "z" or "t" labels to the hyperstack dimension sliders.
 * @author Joachim Walter
 */
public class ScrollbarWithLabel extends Panel implements Adjustable, AdjustmentListener {
	private Scrollbar bar;
	private Label label;
	private PlayPauseIcon playPauseIcon;
	private StackWindow stackWindow;
	transient AdjustmentListener adjustmentListener;
	
	public ScrollbarWithLabel() {
	}

	public ScrollbarWithLabel(StackWindow stackWindow, int value, int visible, int minimum, int maximum, String label) {
		super(new BorderLayout(2, 0));
		this.stackWindow = stackWindow;
		bar = new Scrollbar(Scrollbar.HORIZONTAL, value, visible, minimum, maximum);
		if (label != null) {
			if (label.equals("t")) {
				label = null;
				playPauseIcon = new PlayPauseIcon();
				add(playPauseIcon, BorderLayout.WEST);
			} else {
				this.label = new Label(label);
				add(this.label, BorderLayout.WEST);
			}
		}
		add(bar, BorderLayout.CENTER);
		bar.addAdjustmentListener(this);
	}
	
	/* (non-Javadoc)
	 * @see java.awt.Component#getPreferredSize()
	 */
	public Dimension getPreferredSize() {
		Dimension dim = new Dimension(0,0);
		int width = bar.getPreferredSize().width+(label!=null?label.getPreferredSize().width:0);
		Dimension minSize = getMinimumSize();
		if (width<minSize.width) width = minSize.width;		
		int height = bar.getPreferredSize().height;
		dim = new Dimension(width, height);
		return dim;
	}
	
	public Dimension getMinimumSize() {
		return new Dimension(80, 15);
	}
	
	/* Adds KeyListener also to all sub-components.
	 */
	public synchronized void addKeyListener(KeyListener l) {
		super.addKeyListener(l);
		bar.addKeyListener(l);
		if (label!=null) label.addKeyListener(l);
	}

	/* Removes KeyListener also from all sub-components.
	 */
	public synchronized void removeKeyListener(KeyListener l) {
		super.removeKeyListener(l);
		bar.removeKeyListener(l);
		if (label!=null) label.removeKeyListener(l);
	}

	/* 
	 * Methods of the Adjustable interface
	 */
	public synchronized void addAdjustmentListener(AdjustmentListener l) {
		if (l == null) {
			return;
		}
		adjustmentListener = AWTEventMulticaster.add(adjustmentListener, l);
	}
	public int getBlockIncrement() {
		return bar.getBlockIncrement();
	}
	public int getMaximum() {
		return bar.getMaximum();
	}
	public int getMinimum() {
		return bar.getMinimum();
	}
	public int getOrientation() {
		return bar.getOrientation();
	}
	public int getUnitIncrement() {
		return bar.getUnitIncrement();
	}
	public int getValue() {
		return bar.getValue();
	}
	public int getVisibleAmount() {
		return bar.getVisibleAmount();
	}
	public synchronized void removeAdjustmentListener(AdjustmentListener l) {
		if (l == null) {
			return;
		}
		adjustmentListener = AWTEventMulticaster.remove(adjustmentListener, l);
	}
	public void setBlockIncrement(int b) {
		bar.setBlockIncrement(b);		 
	}
	public void setMaximum(int max) {
		bar.setMaximum(max);		
	}
	public void setMinimum(int min) {
		bar.setMinimum(min);		
	}
	public void setUnitIncrement(int u) {
		bar.setUnitIncrement(u);		
	}
	public void setValue(int v) {
		bar.setValue(v);		
	}
	public void setVisibleAmount(int v) {
		bar.setVisibleAmount(v);		
	}

	public void setFocusable(boolean focusable) {
		super.setFocusable(focusable);
		bar.setFocusable(focusable);
		if (label!=null) label.setFocusable(focusable);
	}
		
	/*
	 * Method of the AdjustmenListener interface.
	 */
	public void adjustmentValueChanged(AdjustmentEvent e) {
		if (bar != null && e.getSource() == bar) {
			AdjustmentEvent myE = new AdjustmentEvent(this, e.getID(), e.getAdjustmentType(), 
					e.getValue(), e.getValueIsAdjusting());
			AdjustmentListener listener = adjustmentListener;
			if (listener != null) {
				listener.adjustmentValueChanged(myE);
			}
		}
	}
		
	public void updatePlayPauseIcon() {
		playPauseIcon.repaint();
	}
	
	
	class PlayPauseIcon extends Canvas implements MouseListener {
		private static final int WIDTH = 12, HEIGHT=14;
		private BasicStroke stroke = new BasicStroke(2f);

		public PlayPauseIcon() {
			addMouseListener(this);
			setSize(WIDTH, HEIGHT);
		}
		
		/** Overrides Component getPreferredSize(). */
		public Dimension getPreferredSize() {
			return new Dimension(WIDTH, HEIGHT);
		}
				
		public void update(Graphics g) {
			paint(g);
		}
		
		public void paint(Graphics g) {
			if (g==null) return;
			g.setColor(Color.white);
			g.fillRect(0, 0, WIDTH, HEIGHT);
			Graphics2D g2d = (Graphics2D)g;
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			if (stackWindow.getAnimate()) {
				g.setColor(Color.black);
				g2d.setStroke(stroke);
				g.drawLine(3, 3, 3, 11);
				g.drawLine(8, 3, 8, 11);
			} else {
				g.setColor(Color.darkGray);
				GeneralPath path = new GeneralPath();
				path.moveTo(4f, 2f);
				path.lineTo(10f, 7f);
				path.lineTo(4f, 12f);
				path.lineTo(4f, 2f);
				g2d.fill(path);
			}
		}
		
		public void mousePressed(MouseEvent e) {
			IJ.doCommand("Start Animation [\\]");
		}
		
		public void mouseReleased(MouseEvent e) {}
		public void mouseExited(MouseEvent e) {}
		public void mouseClicked(MouseEvent e) {}
		public void mouseEntered(MouseEvent e) {}
	
	} // StartStopIcon class

	
}
