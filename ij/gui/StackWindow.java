package ij.gui;
import ij.*;
import ij.measure.Calibration;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;

/** This class is an extended ImageWindow used to display image stacks. */
public class StackWindow extends ImageWindow implements Runnable, AdjustmentListener, ActionListener, MouseWheelListener {

	protected Scrollbar channelSelector, sliceSelector, frameSelector;
	protected Thread thread;
	protected volatile boolean done;
	protected volatile int slice;
	boolean hyperStack;
	int nChannels=1, nSlices=1, nFrames=1;
	int c=1, z=1, t=1;
	

	public StackWindow(ImagePlus imp) {
		this(imp, null);
	}
    
    public StackWindow(ImagePlus imp, ImageCanvas ic) {
		super(imp, ic);
		// add slice selection slider
		ImageStack s = imp.getStack();
		int stackSize = s.getSize();
		nSlices = stackSize;
		hyperStack = imp.getOpenAsHyperStack();
		imp.setOpenAsHyperStack(false);
		int[] dim = imp.getDimensions();
		int nDimensions = 2+(dim[2]>1?1:0)+(dim[3]>1?1:0)+(dim[4]>1?1:0);
		if (nDimensions<=3) hyperStack = false;
		if (hyperStack) {
			nChannels = dim[2];
			nSlices = dim[3];
			nFrames = dim[4];
		}
		//IJ.log("StackWindow: "+hyperStack+" "+nChannels+" "+nSlices+" "+nFrames);
		if (nSlices==stackSize) hyperStack = false;
		addMouseWheelListener(this);
		ImageJ ij = IJ.getInstance();
		if (nChannels>1) {
			channelSelector = new Scrollbar(Scrollbar.HORIZONTAL, 1, 1, 1, nChannels+1);
			Panel panel = new Panel(new BorderLayout(2, 0));
			//panel.add(new Label("c"), BorderLayout.WEST);
			//panel.add(channelSelector, BorderLayout.CENTER);
			add(channelSelector);
			if (ij!=null) channelSelector.addKeyListener(ij);
			channelSelector.addAdjustmentListener(this);
			channelSelector.setFocusable(false); // prevents scroll bar from blinking on Windows
			channelSelector.setUnitIncrement(1);
			channelSelector.setBlockIncrement(1);
		}
		if (nSlices>1) {
			sliceSelector = new Scrollbar(Scrollbar.HORIZONTAL, 1, 1, 1, nSlices+1);
			add(sliceSelector);
			if (ij!=null) sliceSelector.addKeyListener(ij);
			sliceSelector.addAdjustmentListener(this);
			sliceSelector.setFocusable(false);
			int blockIncrement = nSlices/10;
			if (blockIncrement<1) blockIncrement = 1;
			sliceSelector.setUnitIncrement(1);
			sliceSelector.setBlockIncrement(blockIncrement);
		}
		if (nFrames>1) {
			frameSelector = new Scrollbar(Scrollbar.HORIZONTAL, 1, 1, 1, nFrames+1);
			add(frameSelector);
			if (ij!=null) frameSelector.addKeyListener(ij);
			frameSelector.addAdjustmentListener(this);
			frameSelector.setFocusable(false);
			int blockIncrement = nFrames/10;
			if (blockIncrement<1) blockIncrement = 1;
			frameSelector.setUnitIncrement(1);
			frameSelector.setBlockIncrement(blockIncrement);
		}
		if (sliceSelector==null && this.getClass().getName().indexOf("Image5D")!=-1)
			sliceSelector = new Scrollbar(); // prevents Image5D from crashing
		//IJ.log(nChannels+" "+nSlices+" "+nFrames);
		pack();
		ic = imp.getCanvas();
		if (ic!=null) ic.setMaxBounds();
		show();
		int previousSlice = imp.getCurrentSlice();
		imp.setSlice(1);
		if (previousSlice>1 && previousSlice<=stackSize)
			imp.setSlice(previousSlice);
		thread = new Thread(this, "SliceSelector");
		thread.start();
	}

	public synchronized void adjustmentValueChanged(AdjustmentEvent e) {
		if (!running2) {
			//slice = sliceSelector.getValue();
			if (e.getSource()==channelSelector)
				c = channelSelector.getValue();
			else if (e.getSource()==sliceSelector)
				z = sliceSelector.getValue();
			else if (e.getSource()==frameSelector)
				t = frameSelector.getValue();
			updatePosition();
			notify();
		}
	}
	
	void updatePosition() {
		slice = (t-1)*nChannels*nSlices + (z-1)*nChannels + c;
	}

	public void actionPerformed(ActionEvent e) {
	}

	public void mouseWheelMoved(MouseWheelEvent event) {
		if (hyperStack) return;
		synchronized(this) {
			int slice = imp.getCurrentSlice() + event.getWheelRotation();
			if (slice<1)
				slice = 1;
			else if (slice>imp.getStack().getSize())
				slice = imp.getStack().getSize();
			imp.setSlice(slice);
		}
	}

	public boolean close() {
		if (!super.close())
			return false;
		synchronized(this) {
			done = true;
			notify();
		}
        return true;
	}

	/** Displays the specified slice and updates the stack scrollbar. */
	public void showSlice(int index) {
		if (index>=1 && index<=imp.getStackSize())
			imp.setSlice(index);
	}
	
	/** Updates the stack scrollbar. */
	public void updateSliceSelector() {
		if (hyperStack) return;
		int stackSize = imp.getStackSize();
		int max = sliceSelector.getMaximum();
		if (max!=(stackSize+1))
			sliceSelector.setMaximum(stackSize+1);
		sliceSelector.setValue(imp.getCurrentSlice());
	}
	
	public void run() {
		while (!done) {
			synchronized(this) {
				try {wait();}
				catch(InterruptedException e) {}
			}
			if (done) return;
			if (slice>0) {
				int s = slice;
				slice = 0;
				if (s!=imp.getCurrentSlice())
					imp.setSlice(s);
			}
		}
	}
	
	public String createSubtitle() {
		String s = super.createSubtitle();
		if (!hyperStack) return s;
    	s="";
		if (nChannels>1) {
			s += "c:"+c+"/"+nChannels;
			if (nSlices==1&&nFrames==1)
				s += "; ";
			else
				s += " ";
		}
		if (nSlices>1) {
			s += "z:"+z+"/"+nSlices;
			if (nFrames==1)
				s += "; ";
			else
				s += " ";
		}
		if (nFrames>1) {
			s += "t:"+t+"/"+nFrames;
			s += "; ";
		}
		if (running2) return s;
    	int type = imp.getType();
    	Calibration cal = imp.getCalibration();
    	if (cal.scaled())
    		s += IJ.d2s(imp.getWidth()*cal.pixelWidth,2) + "x" + IJ.d2s(imp.getHeight()*cal.pixelHeight,2)
 			+ " " + cal.getUnits() + " (" + imp.getWidth() + "x" + imp.getHeight() + "); ";
    	else
    		s += imp.getWidth() + "x" + imp.getHeight() + " pixels; ";
		int size = (imp.getWidth()*imp.getHeight()*imp.getStackSize())/1024;
    	switch (type) {
	    	case ImagePlus.GRAY8:
	    	case ImagePlus.COLOR_256:
	    		s += "8-bit";
	    		break;
	    	case ImagePlus.GRAY16:
	    		s += "16-bit";
				size *= 2;
	    		break;
	    	case ImagePlus.GRAY32:
	    		s += "32-bit";
				size *= 4;
	    		break;
	    	case ImagePlus.COLOR_RGB:
	    		s += "RGB";
				size *= 4;
	    		break;
    	}
    	if (imp.isInvertedLut())
    		s += " (inverting LUT)";
    	if (size>=10000)    	
    		s += "; " + (int)Math.round(size/1024.0) + "MB";
    	else if (size>=1024) {
    		double size2 = size/1024.0;
    		s += "; " + IJ.d2s(size2,(int)size2==size2?0:1) + "MB";
    	} else
    		s += "; " + size + "K";
    	return s;
    }
    
    public boolean isHyperStack() {
    	return hyperStack;
    }
    
    public int getHSChannel() {
    	return c;
    }

    public int getHSSlice() {
    	return z;
    }

    public int getHSFrame() {
    	return t;
    }

    public void setPosition(int channel, int slice, int frame) {
    	if (channel<1) channel = 1;
    	if (channel>nChannels) channel = nChannels;
    	if (slice<1) slice = 1;
    	if (slice>nSlices) slice = nSlices;
    	if (frame<1) frame = 1;
    	if (frame>nFrames) frame = nFrames;
    	if (channelSelector!=null && channel!=c) {
    		c = channel;
			channelSelector.setValue(channel);
		}
    	if (sliceSelector!=null && slice!=z) {
    		z = slice;
			sliceSelector.setValue(slice);
		}
    	if (frameSelector!=null && frame!=t) {
    		t = frame;
			frameSelector.setValue(frame);
		}
    	updatePosition();
		if (this.slice>0) {
			int s = this.slice;
			this.slice = 0;
			if (s!=imp.getCurrentSlice())
				imp.setSlice(s);
		}
    }
    
}
