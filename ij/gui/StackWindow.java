package ij.gui;
import ij.*;
import ij.measure.Calibration;
import ij.plugin.frame.SyncWindows;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;

/** This class is an extended ImageWindow used to display image stacks. */
public class StackWindow extends ImageWindow implements Runnable, AdjustmentListener, ActionListener, MouseWheelListener {

	protected Scrollbar sliceSelector; // for backward compatibity with Image5D
	protected ScrollbarWithLabel cSelector, zSelector, tSelector;
	protected Thread thread;
	protected volatile boolean done;
	protected volatile int slice;
	private ScrollbarWithLabel animationSelector;
	boolean hyperStack;
	int nChannels=1, nSlices=1, nFrames=1;
	int c=1, z=1, t=1;
	

	public StackWindow(ImagePlus imp) {
		this(imp, null);
	}
	
    public StackWindow(ImagePlus imp, ImageCanvas ic) {
		super(imp, ic);
		addScrollbars(imp);
		addMouseWheelListener(this);
		if (sliceSelector==null && this.getClass().getName().indexOf("Image5D")!=-1)
			sliceSelector = new Scrollbar(); // prevents Image5D from crashing
		//IJ.log(nChannels+" "+nSlices+" "+nFrames);
		pack();
		ic = imp.getCanvas();
		if (ic!=null) ic.setMaxBounds();
		show();
		int previousSlice = imp.getCurrentSlice();
		if (previousSlice>1 && previousSlice<=imp.getStackSize())
			imp.setSlice(previousSlice);
		else
			imp.setSlice(1);
		thread = new Thread(this, "zSelector");
		thread.start();
	}
	
	void addScrollbars(ImagePlus imp) {
		ImageStack s = imp.getStack();
		int stackSize = s.getSize();
		int sliderHeight = 0;
		nSlices = stackSize;
		hyperStack = imp.getOpenAsHyperStack();
		//imp.setOpenAsHyperStack(false);
		int[] dim = imp.getDimensions();
		int nDimensions = 2+(dim[2]>1?1:0)+(dim[3]>1?1:0)+(dim[4]>1?1:0);
		if (nDimensions<=3 && dim[2]!=nSlices)
			hyperStack = false;
		if (hyperStack) {
			nChannels = dim[2];
			nSlices = dim[3];
			nFrames = dim[4];
		}
		if (nSlices==stackSize) hyperStack = false;
		if (nChannels*nSlices*nFrames!=stackSize) hyperStack = false;
		if (cSelector!=null||zSelector!=null||tSelector!=null)
			removeScrollbars();
		ImageJ ij = IJ.getInstance();
		//IJ.log("StackWindow: "+hyperStack+" "+nChannels+" "+nSlices+" "+nFrames+" "+imp);
		if (nChannels>1) {
			cSelector = new ScrollbarWithLabel(this, 1, 1, 1, nChannels+1, 'c');
			add(cSelector);
			sliderHeight += cSelector.getPreferredSize().height + ImageWindow.VGAP;
			if (ij!=null) cSelector.addKeyListener(ij);
			cSelector.addAdjustmentListener(this);
			cSelector.setFocusable(false); // prevents scroll bar from blinking on Windows
			cSelector.setUnitIncrement(1);
			cSelector.setBlockIncrement(1);
		}
		if (nSlices>1) {
			char label = nChannels>1||nFrames>1?'z':'t';
			if (stackSize==dim[2] && imp.isComposite()) label = 'c';
			zSelector = new ScrollbarWithLabel(this, 1, 1, 1, nSlices+1, label);
			if (label=='t') animationSelector = zSelector;
			add(zSelector);
			sliderHeight += zSelector.getPreferredSize().height + ImageWindow.VGAP;
			if (ij!=null) zSelector.addKeyListener(ij);
			zSelector.addAdjustmentListener(this);
			zSelector.setFocusable(false);
			int blockIncrement = nSlices/10;
			if (blockIncrement<1) blockIncrement = 1;
			zSelector.setUnitIncrement(1);
			zSelector.setBlockIncrement(blockIncrement);
			sliceSelector = zSelector.bar;
		}
		if (nFrames>1) {
			animationSelector = tSelector = new ScrollbarWithLabel(this, 1, 1, 1, nFrames+1, 't');
			add(tSelector);
			sliderHeight += tSelector.getPreferredSize().height + ImageWindow.VGAP;
			if (ij!=null) tSelector.addKeyListener(ij);
			tSelector.addAdjustmentListener(this);
			tSelector.setFocusable(false);
			int blockIncrement = nFrames/10;
			if (blockIncrement<1) blockIncrement = 1;
			tSelector.setUnitIncrement(1);
			tSelector.setBlockIncrement(blockIncrement);
		}
		ImageWindow win = imp.getWindow();
		if (win!=null)
			win.setSliderHeight(sliderHeight);
	}

	public synchronized void adjustmentValueChanged(AdjustmentEvent e) {
		if (!running2 || imp.isHyperStack()) {
			if (e.getSource()==cSelector) {
				c = cSelector.getValue();
				if (c==imp.getChannel()&&e.getAdjustmentType()==AdjustmentEvent.TRACK) return;
			} else if (e.getSource()==zSelector) {
				z = zSelector.getValue();
				int slice = hyperStack?imp.getSlice():imp.getCurrentSlice();
				if (z==slice&&e.getAdjustmentType()==AdjustmentEvent.TRACK) return;
			} else if (e.getSource()==tSelector) {
				t = tSelector.getValue();
				if (t==imp.getFrame()&&e.getAdjustmentType()==AdjustmentEvent.TRACK) return;
			}
			updatePosition();
			notify();
		}
		if (!running)
			syncWindows(e.getSource());
	}
	
	private void syncWindows(Object source) {
		if (SyncWindows.getInstance()==null)
			return;
		if (source==cSelector)
			SyncWindows.setC(this, cSelector.getValue());
		else if (source==zSelector) {
			int stackSize = imp.getStackSize();
			if (imp.getNChannels()==stackSize)
				SyncWindows.setC(this, zSelector.getValue());
			else if (imp.getNFrames()==stackSize)
				SyncWindows.setT(this, zSelector.getValue());
			else
				SyncWindows.setZ(this, zSelector.getValue());
		} else if (source==tSelector)
			SyncWindows.setT(this, tSelector.getValue());
		else
			throw new RuntimeException("Unknownsource:"+source);
	}

	
	void updatePosition() {
		slice = (t-1)*nChannels*nSlices + (z-1)*nChannels + c;
		imp.updatePosition(c, z, t);
	}

	public void actionPerformed(ActionEvent e) {
	}

	public void mouseWheelMoved(MouseWheelEvent e) {
		synchronized(this) {
			int rotation = e.getWheelRotation();
			boolean ctrl = (e.getModifiers()&Event.CTRL_MASK)!=0;
			if ((ctrl||IJ.shiftKeyDown()) && ic!=null) {
				int ox = ic.offScreenX(e.getX());
				int oy = ic.offScreenY(e.getX());
				if (rotation<0)
					ic.zoomIn(ox,oy);
				else
					ic.zoomOut(ox,oy);
				return;
			}
			if (hyperStack) {
				if (rotation>0)
					IJ.run(imp, "Next Slice [>]", "");
				else if (rotation<0)
					IJ.run(imp, "Previous Slice [<]", "");
			} else {
				int slice = imp.getCurrentSlice() + rotation;
				if (slice<1)
					slice = 1;
				else if (slice>imp.getStack().getSize())
					slice = imp.getStack().getSize();
				imp.setSlice(slice);
				imp.updateStatusbarValue();
				SyncWindows.setZ(this, slice);
			}
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
		if (imp!=null && index>=1 && index<=imp.getStackSize()) {
			imp.setSlice(index);
			SyncWindows.setZ(this, index);
		}
	}
	
	/** Updates the stack scrollbar. */
	public void updateSliceSelector() {
		if (hyperStack || zSelector==null) return;
		int stackSize = imp.getStackSize();
		int max = zSelector.getMaximum();
		if (max!=(stackSize+1))
			zSelector.setMaximum(stackSize+1);
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				zSelector.setValue(imp.getCurrentSlice());
			}
		});
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
		String subtitle = super.createSubtitle();
		if (!hyperStack) return subtitle;
    	String s="";
    	int[] dim = imp.getDimensions(false);
    	int channels=dim[2], slices=dim[3], frames=dim[4];
		if (channels>1) {
			s += "c:"+imp.getChannel()+"/"+channels;
			if (slices>1||frames>1) s += " ";
		}
		if (slices>1) {
			s += "z:"+imp.getSlice()+"/"+slices;
			if (frames>1) s += " ";
		}
		if (frames>1)
			s += "t:"+imp.getFrame()+"/"+frames;
		if (running2) return s;
		int index = subtitle.indexOf(";");
		if (index!=-1) {
			int index2 = subtitle.indexOf("(");
			if (index2>=0 && index2<index && subtitle.length()>index2+4 && !subtitle.substring(index2+1, index2+4).equals("ch:")) {
				index = index2;
				s = s + " ";
			}
			subtitle = subtitle.substring(index, subtitle.length());
		} else
			subtitle = "";
    	return s + subtitle;
    }
    
    public boolean isHyperStack() {
    	return hyperStack;
    }
    
    public void setPosition(int channel, int slice, int frame) {
    	if (cSelector!=null && channel!=c) {
    		c = channel;
			cSelector.setValue(channel);
			SyncWindows.setC(this, channel);
		}
    	if (zSelector!=null && slice!=z) {
    		z = slice;
			zSelector.setValue(slice);
			SyncWindows.setZ(this, slice);
		}
    	if (tSelector!=null && frame!=t) {
    		t = frame;
			tSelector.setValue(frame);
			SyncWindows.setT(this, frame);
		}
    	updatePosition();
		if (this.slice>0) {
			int s = this.slice;
			this.slice = 0;
			if (s!=imp.getCurrentSlice())
				imp.setSlice(s);
		}
    }
    
	public boolean validDimensions() {
		int c = imp.getNChannels();
		int z = imp.getNSlices();
		int t = imp.getNFrames();
		//IJ.log(c+" "+z+" "+t+" "+nChannels+" "+nSlices+" "+nFrames+" "+imp.getStackSize());
		int size = imp.getStackSize();
		if (c==size && c*z*t==size && nSlices==size && nChannels*nSlices*nFrames==size)
			return true;
		if (c!=nChannels||z!=nSlices||t!=nFrames||c*z*t!=size)
			return false;
		else
			return true;
	}
    
    public void setAnimate(boolean b) {
    	if (running2!=b && animationSelector!=null)
    		animationSelector.updatePlayPauseIcon();
		running2 = b;
    }
    
    public boolean getAnimate() {
    	return running2;
    }
    
    public int getNScrollbars() {
    	int n = 0;
    	if (cSelector!=null) n++;
    	if (zSelector!=null) n++;
    	if (tSelector!=null) n++;
    	return n;
    }
    
    void removeScrollbars() {
    	if (cSelector!=null) {
    		remove(cSelector);
			cSelector.removeAdjustmentListener(this);
    		cSelector = null;
    	}
    	if (zSelector!=null) {
    		remove(zSelector);
			zSelector.removeAdjustmentListener(this);
    		zSelector = null;
    	}
    	if (tSelector!=null) {
    		remove(tSelector);
			tSelector.removeAdjustmentListener(this);
    		tSelector = null;
    	}
    }

}
