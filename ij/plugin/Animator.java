package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;

/** This plugin animates stacks. */
public class Animator implements PlugIn {

	private static double animationSpeed = 7.0; // frames per second
	private static boolean oscillate;
	private ImagePlus imp;
	private StackWindow swin;
	private int slice;
	private int nSlices;	
	/** Set 'arg' to "set" to display a dialog that allows the user to specify the
		animation speed. Set it to "start" to start animating the current stack.
		Set it to "stop" to stop animation. Set it to "next" or "previous"
		to stop any animation and display the next or previous frame. 
	*/
	public void run(String arg) {
		imp = WindowManager.getCurrentImage();
		if (imp==null)
			{IJ.noImage(); return;}
    	nSlices = imp.getStackSize();
		if (nSlices<2)
			{IJ.error("Stack required."); return;}
		ImageWindow win = imp.getWindow();
		if (!(win instanceof StackWindow))
			return;
		swin = (StackWindow)win;
		ImageStack stack = imp.getStack();
		slice = imp.getCurrentSlice();
		IJ.register(Animator.class);
		
		if (arg.equals("options"))
			{doOptions(); return;}
			
		if (arg.equals("start"))
			{startAnimation(); return;}

		if (swin.running) // "stop", "next" and "previous" all stop animation
			stopAnimation();

		if (arg.equals("stop")) {
			return;
		}
			
		if (arg.equals("next")) {
			if (!imp.lock()) return;
			if (IJ.altKeyDown())
				slice += 10;
			else
				slice++;
			if (slice>nSlices)
				slice = nSlices;
			swin.showSlice(slice);
			imp.unlock();
			return;
		}
		
		if (arg.equals("previous")) {
			if (!imp.lock()) return;
			if (IJ.altKeyDown())
				slice -= 10;
			else
				slice--;
			if (slice<1)
				slice = 1;
			swin.showSlice(slice);
			imp.unlock();
			return;
		}
	}

	void stopAnimation() {
		swin.running = false;
		IJ.wait(500+(int)(1000.0/animationSpeed));
		imp.unlock(); 
	}

	void startAnimation() {
		if (swin.running)
			{stopAnimation(); return;}
		imp.unlock(); // so users can adjust brightness/contrast/threshold
		swin.running = true;
		long time, nextTime=System.currentTimeMillis();
		Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
		int sliceIncrement = 1;
		while (swin.running) {
			time = System.currentTimeMillis();
			if (time<nextTime)
				IJ.wait((int)(nextTime-time));
			else
				Thread.yield();
			nextTime += (long)(1000.0/animationSpeed);
			slice += sliceIncrement;
			if (slice<1) {
				slice = 2;
				sliceIncrement = 1;
			}
			if (slice>nSlices) {
				if (oscillate) {
					slice = nSlices-1;
					sliceIncrement = -1;
				} else {
					slice = 1;
					sliceIncrement = 1;
				}
			}
			swin.showSlice(slice);
		}
	}

	void doOptions() {
		boolean start = !swin.running;
		boolean saveOscillate = oscillate;
		GenericDialog gd = new GenericDialog("Animation Options");
		gd.addNumericField("Speed (1-100fps):", animationSpeed, 0);
		gd.addCheckbox("Loop Back and Forth", oscillate);
		gd.addCheckbox("Start Animation", start);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		double speed = gd.getNextNumber();
		oscillate = gd.getNextBoolean();
		start = gd.getNextBoolean();
		if (speed>100.0) speed = 100.0;
		if (speed<1.0) speed = 1.0;
		animationSpeed = speed;
		if (start && !swin.running)
			startAnimation();
	}
	
	/** Returns the current animation speed in frames per second. */
	public static double getFrameRate() {
		return animationSpeed;
	}

}

