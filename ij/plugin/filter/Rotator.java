package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import java.awt.*;
import java.awt.geom.*;


/** This plugin implements the Image/Rotate/Arbitrarily command. */
public class Rotator implements ExtendedPlugInFilter, DialogListener {
	public static final String GRID = "|GRID|";
	private int flags = DOES_ALL|SUPPORTS_MASKING;
	private static double angle = 15.0;
	private static boolean fillWithBackground;
	private static boolean enlarge;
	private static int gridLines = 1;
	private ImagePlus imp;
	private int bitDepth;
	private boolean canEnlarge;
	private boolean isEnlarged;
	private GenericDialog gd;
	private PlugInFilterRunner pfr;
	private String[] methods = ImageProcessor.getInterpolationMethods();
	private static int interpolationMethod = ImageProcessor.BILINEAR;
	private Overlay overlay;
	private boolean done;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		if (imp!=null) {
			bitDepth = imp.getBitDepth();
			Roi roi = imp.getRoi();
			if (roi!=null && roi.isLine())
				roi = null;
			overlay = imp.getOverlay();
			if (roi!=null && overlay!=null && Macro.getOptions()==null) {
				String msg = "This image has an overlay so the\nselection will be removed.";
				if (!IJ.showMessageWithCancel("Rotator", msg))
					return DONE;
				imp.deleteRoi();
			}
			Rectangle r = roi!=null?roi.getBounds():null;
			canEnlarge = r==null || (r.x==0&&r.y==0&&r.width==imp.getWidth()&&r.height==imp.getHeight());
			if (imp.getDisplayMode()==IJ.COMPOSITE) { // setup Undo for composite color stacks
				Undo.setup(Undo.TRANSFORM, imp);
				flags = flags | NO_UNDO_RESET;
			}
			Undo.saveOverlay(imp);
			if (overlay==null)
				overlay = new Overlay();
		}
		return flags;
	}

	public void run(ImageProcessor ip) {
		if (enlarge && gd.wasOKed()) synchronized(this) {
			if (!isEnlarged) {
				enlargeCanvas();
				isEnlarged=true;
			}
		}
		if (isEnlarged) {	//enlarging may have made the ImageProcessor invalid, also for the parallel threads
			int slice = pfr.getSliceNumber();
			if (imp.getStackSize()==1)
				ip = imp.getProcessor();
			else
				ip = imp.getStack().getProcessor(slice);
		}
		ip.setInterpolationMethod(interpolationMethod);
		if (fillWithBackground)
			ip.setBackgroundColor(Toolbar.getBackgroundColor());
		else
			ip.setBackgroundValue(0);
		ip.rotate(angle);
		if (!gd.wasOKed())
			drawGridLines(gridLines);
		if (overlay!=null && !imp.getHideOverlay()) {
			Overlay overlay2 = overlay.rotate(angle, ip.getWidth()/2, ip.getHeight()/2);
			if (overlay2!=null && overlay2.size()>0)
				imp.setOverlay(overlay2);
		}
		if (isEnlarged && imp.getStackSize()==1) {
			imp.changes = true;
			imp.updateAndDraw();
			Undo.setup(Undo.COMPOUND_FILTER_DONE, imp);
		}
		if (done) { // remove grid
			Overlay ovly = imp.getOverlay();
			if (ovly!=null) {
				ovly.remove(GRID);
				if (ovly.size()==0) imp.setOverlay(null);			
			}
		}
	}

	void enlargeCanvas() {
		imp.unlock();
		IJ.run(imp, "Select All", "");
		IJ.run(imp, "Rotate...", "angle="+angle);
		Roi roi = imp.getRoi();
		imp.deleteRoi();
		Rectangle2D.Double fb = roi.getFloatBounds();
		Rectangle r = new Rectangle((int)Math.round(fb.x),(int)Math.round(fb.y),(int)Math.round(fb.width),(int)Math.round(fb.height));
		if (r.width<imp.getWidth()) r.width = imp.getWidth();
		if (r.height<imp.getHeight()) r.height = imp.getHeight();
		IJ.showStatus("Rotate: Enlarging...");
		if (imp.getStackSize()==1)
			Undo.setup(Undo.COMPOUND_FILTER, imp);
		IJ.run(imp, "Canvas Size...", "width="+r.width+" height="+r.height+" position=Center "+(fillWithBackground?"":"zero"));
		IJ.showStatus("Rotating...");
	}

	void drawGridLines(int lines) {
		if (overlay==null)
			return;
		overlay.remove(GRID);
		if (lines==0)
			return;
		GeneralPath path = new GeneralPath();
		float width = imp.getWidth();
		float height = imp.getHeight();
		float xinc = width/lines;
		float yinc = height/lines;
		float xstart = xinc/2f;
		float ystart = yinc/2f;
		for (int i=0; i<lines; i++) {
			path.moveTo(xstart+xinc*i, 0f);
			path.lineTo(xstart+xinc*i, height);
			path.moveTo(0f, ystart+yinc*i);
			path.lineTo(width, ystart+yinc*i);
		}
		Roi roi = new ShapeRoi(path);
		roi.setName(GRID);
		roi.setStrokeWidth(0);
		overlay.add(roi);
	}

	public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
		this.pfr = pfr;
		String macroOptions = Macro.getOptions();
		if (macroOptions!=null) {
			if (macroOptions.indexOf(" interpolate")!=-1)
				macroOptions = macroOptions.replaceAll(" interpolate", " interpolation=Bilinear");
			else if (macroOptions.indexOf(" interpolation=")==-1)
				macroOptions = macroOptions+" interpolation=None";
			Macro.setOptions(macroOptions);
		}
		gd = new GenericDialog("Rotate");
		gd.addSlider("Angle:", -90, 90, angle, 0.1);
		gd.addNumericField("Grid lines:", gridLines, 0);
		gd.addChoice("Interpolation:", methods, methods[interpolationMethod]);
		gd.addCheckbox("Fill with background color", fillWithBackground);
		if (canEnlarge)
			gd.addCheckbox("Enlarge image", enlarge);
		else
			enlarge = false;
		gd.addPreviewCheckbox(pfr);
		gd.addDialogListener(this);
		gd.showDialog();
		if (gd.wasCanceled()) {
			if (overlay.size()>0) {
				overlay.remove(GRID);
				imp.setOverlay(overlay);
			}
			return DONE;
		}
		Overlay ovly = imp.getOverlay();
		if (ovly!=null) {
			ovly.remove(GRID);
			if (ovly.size()==0) imp.setOverlay(null);		
		}
		if (enlarge)
			flags |= NO_CHANGES;			// undoable as a "compound filter"
		else if (imp.getStackSize()==1)			
			flags |= KEEP_PREVIEW;		// standard filter without enlarge
		done = true;
		return IJ.setupDialog(imp, flags);
	}

	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		angle = gd.getNextNumber();
		//only check for invalid input to "angle", don't care about gridLines
		if (gd.invalidNumber()) {
			if (gd.wasOKed()) IJ.error("Angle is invalid.");
			return false;
		}
		gridLines = (int)gd.getNextNumber();
		interpolationMethod = gd.getNextChoiceIndex();
		fillWithBackground = gd.getNextBoolean();
		if (canEnlarge)
			enlarge = gd.getNextBoolean();
		return true;
	}

	/** Returns the current angle. */
	public static double getAngle() {
		return angle;
	}

	public void setNPasses(int nPasses) {
	}

}

