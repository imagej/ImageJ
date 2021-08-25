package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.Calibration;
import ij.plugin.frame.Recorder;
import java.awt.Point;

/** This plugin animates stacks. */
public class Animator implements PlugIn {

	private static double animationRate = Prefs.getDouble(Prefs.FPS, 7.0);
	private static int firstFrame=0, lastFrame=0;
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
		imp = IJ.getImage();
		nSlices = imp.getStackSize();
		if (nSlices<2)
			{IJ.error("Stack required."); return;}
		if (imp.isLocked())
			{IJ.beep(); IJ.showStatus("Image is locked: \""+imp.getTitle()+"\""); return;}
		ImageWindow win = imp.getWindow();
		if ((win==null || !(win instanceof StackWindow)) && !arg.equals("options")) {
			if (arg.equals("next"))
				imp.setSlice(imp.getCurrentSlice()+1);
			else if (arg.equals("previous"))
				imp.setSlice(imp.getCurrentSlice()-1);
			if (win!=null) imp.updateStatusbarValue();
			return;
		}
		swin = (StackWindow)win;
		ImageStack stack = imp.getStack();
		slice = imp.getCurrentSlice();
		IJ.register(Animator.class);
		
		if (arg.equals("options")) {
			doOptions();
			return;
		}
			
		if (arg.equals("start")) {
			startAnimation();
			return;
		}

		//if (swin.getAnimate()) // "stop", "next" and "previous" all stop animation
		//	stopAnimation();

		if (arg.equals("stop")) {
			stopAnimation();
			return;
		}
			
		if (arg.equals("next")) {
			if (Prefs.reverseNextPreviousOrder)
				changeSlice(1);
			else
				nextSlice();
			return;
		}
		
		if (arg.equals("previous")) {
			if (Prefs.reverseNextPreviousOrder)
				changeSlice(-1);
			else
				previousSlice();
			return;
		}
		
		if (arg.equals("set")) {
			setSlice();
			return;
		}
	}

	void stopAnimation() {
		swin.setAnimate(false);
		IJ.wait(500+(int)(1000.0/animationRate));
	}

	void startAnimation() {
		int first=firstFrame, last=lastFrame;
		if (first<1 || first>nSlices || last<1 || last>nSlices)
			{first=1; last=nSlices;}
		if (swin.getAnimate())
			{stopAnimation(); return;}
		swin.setAnimate(true);
		long time, nextTime=System.currentTimeMillis();
		Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
		int sliceIncrement = 1;
		Calibration cal = imp.getCalibration();
		if (cal.fps!=0.0)
			animationRate = cal.fps;
		if (animationRate<0.1) {
			animationRate = 1.0;
			cal.fps = animationRate;
		}
		int frames = imp.getNFrames();
		int slices = imp.getNSlices();
		
		if (imp.isDisplayedHyperStack() && frames>1) {
			int frame = imp.getFrame();
			first = 1;
			last = frames;
			while (swin.getAnimate()) {
				time = System.currentTimeMillis();
				if (time<nextTime)
					IJ.wait((int)(nextTime-time));
				else
					Thread.yield();
				nextTime += (long)(1000.0/animationRate);
				frame += sliceIncrement;
				if (frame<first) {
					frame = first+1;
					sliceIncrement = 1;
				}
				if (frame>last) {
					if (cal.loop) {
						frame = last-1;
						sliceIncrement = -1;
					} else {
						frame = first;
						sliceIncrement = 1;
					}
				}
				if (imp.isLocked()) return;
				imp.setPosition(imp.getChannel(), imp.getSlice(), frame);
				imp.updateStatusbarValue();
			}
			return;
		}

		if (imp.isDisplayedHyperStack() && slices>1) {
			slice = imp.getSlice();
			first = 1;
			last = slices;
			while (swin.getAnimate()) {
				time = System.currentTimeMillis();
				if (time<nextTime)
					IJ.wait((int)(nextTime-time));
				else
					Thread.yield();
				nextTime += (long)(1000.0/animationRate);
				slice += sliceIncrement;
				if (slice<first) {
					slice = first+1;
					sliceIncrement = 1;
				}
				if (slice>last) {
					if (cal.loop) {
						slice = last-1;
						sliceIncrement = -1;
					} else {
						slice = first;
						sliceIncrement = 1;
					}
				}
				if (imp.isLocked()) return;
				imp.setPosition(imp.getChannel(), slice, imp.getFrame());
				imp.updateStatusbarValue();
			}
			return;
		}
		
		long startTime=System.currentTimeMillis();
		int count = 0;
		double fps = 0.0;
		while (swin.getAnimate()) {
			time = System.currentTimeMillis();
			count++;
			if (time>startTime+1000L) {
				startTime=System.currentTimeMillis();
				fps=count;
				count=0;
			}
			ImageCanvas ic = imp.getCanvas();
			boolean showFrameRate = !(ic!=null?ic.cursorOverImage():false);
			if (showFrameRate)
				IJ.showStatus((int)(fps+0.5) + " fps");
			if (time<nextTime)
				IJ.wait((int)(nextTime-time));
			else
				Thread.yield();
			nextTime += (long)Math.round(1000.0/animationRate);
			slice += sliceIncrement;
			if (slice<first) {
				slice = first+1;
				sliceIncrement = 1;
			}
			if (slice>last) {
				if (cal.loop) {
					slice = last-1;
					sliceIncrement = -1;
				} else {
					slice = first;
					sliceIncrement = 1;
				}
			}
			if (imp.isLocked()) return;
			swin.showSlice(slice);
			if (!showFrameRate)
				imp.updateStatusbarValue();
		}
	}

	void doOptions() {
		if (firstFrame<1 || firstFrame>nSlices || lastFrame<1 || lastFrame>nSlices)
			{firstFrame=1; lastFrame=nSlices;}
		if (imp.isDisplayedHyperStack()) {
			int frames = imp.getNFrames();
			int slices = imp.getNSlices();
			firstFrame = 1;
			if (frames>1)
				lastFrame = frames;
			else if (slices>1)
				lastFrame=slices;
		}
		boolean start = swin!=null && !swin.getAnimate();
		Calibration cal = imp.getCalibration();
		if (cal.fps!=0.0)
			animationRate = cal.fps;
		else if (cal.frameInterval!=0.0 && cal.getTimeUnit().equals("sec"))
			animationRate = 1.0/cal.frameInterval;
		int decimalPlaces = (int)animationRate==animationRate?0:3;
		GenericDialog gd = new GenericDialog("Animation Options");
		gd.addNumericField("Speed (0.1-1000 fps):", animationRate, decimalPlaces);
		if (!imp.isDisplayedHyperStack()) {
			gd.addNumericField("First Frame:", firstFrame, 0);
			gd.addNumericField("Last Frame:", lastFrame, 0);
		}
		gd.addCheckbox("Loop Back and Forth", cal.loop);
		gd.addCheckbox("Start Animation", start);
		gd.showDialog();
		if (gd.wasCanceled()) {
			if (firstFrame==1 && lastFrame==nSlices)
				{firstFrame=0; lastFrame=0;}
			return;
		}
		double speed = gd.getNextNumber();
		if (!imp.isDisplayedHyperStack()) {
			firstFrame = (int)gd.getNextNumber();
			lastFrame = (int)gd.getNextNumber();
		}
		if (firstFrame==1 && lastFrame==nSlices)
			{firstFrame=0; lastFrame=0;}
		cal.loop = gd.getNextBoolean();
		Calibration.setLoopBackAndForth(cal.loop);
		start = gd.getNextBoolean();
		if (speed>1000.0) speed = 1000.0;
		//if (speed<0.1) speed = 0.1;
		animationRate = speed;
		if (animationRate!=0.0)
			cal.fps = animationRate;
		if (start && swin!=null && !swin.getAnimate())
			startAnimation();
	}
	
	void nextSlice() {
		boolean hyperstack = imp.isDisplayedHyperStack();
		int channels = imp.getNChannels();
		int slices = imp.getNSlices();
		int frames = imp.getNFrames();
		if (hyperstack && channels>1 && !((slices>1||frames>1)&&(IJ.controlKeyDown()||IJ.spaceBarDown()||IJ.altKeyDown()))) {
			int c = imp.getChannel() + 1;
			if (c>channels) c = channels;
			swin.setPosition(c, imp.getSlice(), imp.getFrame());
		} else if (hyperstack && slices>1 && !(frames>1&&IJ.altKeyDown())) {
			int z = imp.getSlice() + 1;
			if (z>slices) z = slices;
			swin.setPosition(imp.getChannel(), z, imp.getFrame());
		} else if (hyperstack && frames>1) {
			int t = imp.getFrame() + 1;
			if (t>frames) t = frames;
			swin.setPosition(imp.getChannel(), imp.getSlice(), t);
		} else {
			if (IJ.altKeyDown())
				slice += 10;
			else
				slice++;
			if (slice>nSlices)
				slice = nSlices;
			swin.showSlice(slice);
		}
		imp.updateStatusbarValue();
	}	
	
	void previousSlice() {
		boolean hyperstack = imp.isDisplayedHyperStack();
		int channels = imp.getNChannels();
		int slices = imp.getNSlices();
		int frames = imp.getNFrames();
		if (hyperstack && channels>1 && !((slices>1||frames>1)&&(IJ.controlKeyDown()||IJ.spaceBarDown()||IJ.altKeyDown()))) {
			int c = imp.getChannel() - 1;
			if (c<1) c = 1;
			swin.setPosition(c, imp.getSlice(), imp.getFrame());
		} else if (hyperstack && slices>1 && !(frames>1&&IJ.altKeyDown())) {
			int z = imp.getSlice() - 1;
			if (z<1) z = 1;
			swin.setPosition(imp.getChannel(), z, imp.getFrame());
		} else if (hyperstack && frames>1) {
			int t = imp.getFrame() - 1;
			if (t<1) t = 1;
			swin.setPosition(imp.getChannel(), imp.getSlice(), t);
		} else {
			if (IJ.altKeyDown())
				slice -= 10;
			else
				slice--;
			if (slice<1)
				slice = 1;
			swin.showSlice(slice);
		}
		imp.updateStatusbarValue();
	}

	void changeSlice(int pn) {
		boolean hyperstack = imp.isDisplayedHyperStack();
		int channels = imp.getNChannels();
		int slices = imp.getNSlices();
		int frames = imp.getNFrames();
		if (swin.getAnimate() && (channels*slices*frames==Math.max(channels,Math.max(slices,frames))) )
			{stopAnimation(); return;} //if only one dimension, stop animating
		if(hyperstack){
			int c=imp.getChannel(); int z=imp.getSlice(); int t=imp.getFrame();
			if (frames>1 && !((slices>1||channels>1)&&(IJ.controlKeyDown()||IJ.spaceBarDown()||IJ.altKeyDown()) || swin.getAnimate())){
				t += pn;
				if (t>frames) t = frames;
				if (t<1) t = 1;
			} else if (slices>1 && !(channels>1&& (IJ.altKeyDown() || IJ.spaceBarDown()) || ((swin.getAnimate()|| IJ.controlKeyDown()) && frames==1)) ) {
				z += pn;
				if (z>slices) z = slices;
				if (z<1) z = 1;
			} else if (channels>1) {
				c += pn;
				if (c>channels) c = channels;
				if (c<1) c = 1;
			}
			swin.setPosition(c, z, t);
		} else {
			if (IJ.altKeyDown())
				slice+=(pn*10);
			else
				slice+=pn;
			if (slice>nSlices)
				slice = nSlices;
			if (slice<1)
				slice = 1;
			swin.showSlice(slice);
		}
		imp.updateStatusbarValue();
	}
	
	void setSlice() {
		if (imp.isDisplayedHyperStack()) {
			GenericDialog gd = new GenericDialog("Set Position");
			int c = imp.getChannel();
			int z = imp.getSlice();
			int t = imp.getFrame();			
			gd.addNumericField("Channel:", c);
			gd.addNumericField("Slice:", z);
			gd.addNumericField("Frame:", t);
			gd.showDialog();
			if (!gd.wasCanceled()) {
				c = (int) gd.getNextNumber();
				z = (int) gd.getNextNumber();
				t = (int) gd.getNextNumber();
				imp.setPosition(c, z, t);
			}
			if (Recorder.record) {
				String method = Recorder.scriptMode()?"imp":"Stack";
				Recorder.recordString(method+".setPosition("+c+","+z+","+t+");\n");
				Recorder.disableCommandRecording();
			}
		} else {
			GenericDialog gd = new GenericDialog("Set Slice");
			gd.addNumericField("Slice (1-"+nSlices+"):", slice, 0);
			gd.showDialog();
			if (!gd.wasCanceled()) {
				int slice = (int)gd.getNextNumber();
				imp.setSlice(slice);
			}
		}
	}

	/** Returns the current animation speed in frames per second. */
	public static double getFrameRate() {
		return animationRate;
	}

}
