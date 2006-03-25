package ij.gui;

import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import ij.*;


/** This class is an extended ImageWindow used to display image stacks. */
public class StackWindow extends ImageWindow implements Runnable, AdjustmentListener, ActionListener {

	protected Scrollbar sliceSelector;
	protected Thread thread;
	protected boolean done;
	protected int slice;

	public StackWindow(ImagePlus imp) {
		this(imp, new ImageCanvas(imp));
	}
    
    public StackWindow(ImagePlus imp, ImageCanvas ic) {
		super(imp, ic);
		// add slice selection slider
		ImageStack s = imp.getStack();
		int stackSize = s.getSize();
		sliceSelector = new Scrollbar(Scrollbar.HORIZONTAL, 1, 1, 1, stackSize+1);
		add(sliceSelector);
		ImageJ ij = IJ.getInstance();
		if (ij!=null) sliceSelector.addKeyListener(ij);
		sliceSelector.addAdjustmentListener(this);
		int blockIncrement = stackSize/10;
		if (blockIncrement<1) blockIncrement = 1;
		sliceSelector.setUnitIncrement(1);
		sliceSelector.setBlockIncrement(blockIncrement);
		pack();
		show();
		int previousSlice = imp.getCurrentSlice();
		imp.setSlice(1);
		if (previousSlice>1 && previousSlice<=stackSize)
			imp.setSlice(previousSlice);
		thread = new Thread(this, "SliceSelector");
		thread.start();
	}

	public synchronized void adjustmentValueChanged(AdjustmentEvent e) {
		if (!running2){
			slice = sliceSelector.getValue();
			notify();
		}
	}

	public void actionPerformed(ActionEvent e) {
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

}
