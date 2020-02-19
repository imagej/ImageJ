package ij.plugin; 
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;
import java.awt.image.*;
import java.io.*;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.frame.Editor;
import ij.text.TextWindow;
import ij.util.Tools;
	
/**	Copies/pastes images to/from the system clipboard. */
public class Clipboard implements PlugIn, Transferable {
	static java.awt.datatransfer.Clipboard clipboard;
	
	public void run(String arg) {
		if (IJ.altKeyDown()) {
			if (arg.equals("copy"))
				arg = "scopy";
			else if (arg.equals("paste"))
				arg = "spaste";
		}
  		if (arg.equals("copy"))
			copy(false);
  		else if (arg.equals("paste"))
			paste();
  		else if (arg.equals("cut"))
			copy(true);
  		else if (arg.equals("scopy"))
			copyToSystem();
		else if (arg.equals("showsys"))
			showSystemClipboard();
		else if (arg.equals("show"))
			showInternalClipboard();
	}
	
	void copy(boolean cut) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null) {
	 		imp.copy(cut);
	 		if (cut)
	 			imp.changes = true;
	 	} else
	 		IJ.noImage();
	}
	
	private ImagePlus flatten(ImagePlus imp) {
		if (imp.getOverlay()!=null && !imp.getHideOverlay() && !imp.isHyperStack()) {
			ImagePlus imp2 = imp;
			Roi roi = imp.getRoi();
			if (imp.getStackSize()>1) {
				imp.deleteRoi();
				int slice = imp.getCurrentSlice();
				imp = new Duplicator().run(imp, slice, slice);
			}
			imp = imp.flatten();
			imp.setRoi(roi);
			imp2.setRoi(roi);
		}
		return imp;
	}
	
	void paste() {
		if (ImagePlus.getClipboard()==null)
			showSystemClipboard();
		else {
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp!=null)
				imp.paste();
			else
				showInternalClipboard	();
		}
	}

	void setup() {
		if (clipboard==null)
			clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
	}
	
	void copyToSystem() {
		setup();
		try {
			clipboard.setContents(this, null);
		} catch (Throwable t) {}
	}
	
	void showSystemClipboard() {
		setup();
		IJ.showStatus("Opening system clipboard...");
		try {
			Transferable transferable = clipboard.getContents(null);
			boolean imageSupported = transferable.isDataFlavorSupported(DataFlavor.imageFlavor);
			boolean textSupported = transferable.isDataFlavorSupported(DataFlavor.stringFlavor);
			if (imageSupported) {
				Image img = (Image)transferable.getTransferData(DataFlavor.imageFlavor);
				if (img==null) {
					IJ.error("Unable to convert image on system clipboard");
					IJ.showStatus("");
					return;
				}
				int width = img.getWidth(null);
				int height = img.getHeight(null);
				BufferedImage   bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
				Graphics g = bi.createGraphics();
				g.drawImage(img, 0, 0, null);
				g.dispose();
				WindowManager.checkForDuplicateName = true;
				new ImagePlus("Clipboard", bi).show();
			} else if (textSupported) {
				String text = (String)transferable.getTransferData(DataFlavor.stringFlavor);
				if (IJ.isMacintosh())
					text = Tools.fixNewLines(text);
				Editor ed = new Editor();
				ed.setSize(600, 300);
				ed.create("Clipboard", text);
				IJ.showStatus("");
			} else
				IJ.error("Unable to find an image on the system clipboard");
		} catch (Throwable e) {
			IJ.handleException(e);
		}
	}
	
	public DataFlavor[] getTransferDataFlavors() {
		return new DataFlavor[] { DataFlavor.imageFlavor };
	}

	public boolean isDataFlavorSupported(DataFlavor flavor) {
		return DataFlavor.imageFlavor.equals(flavor);
	}

	public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
		if (!isDataFlavorSupported(flavor))
			throw new UnsupportedFlavorException(flavor);
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null)
			return null;
		Roi roi = imp.getRoi();
		if (roi!=null && !roi.isLine())
			imp = imp.crop();
		boolean overlay = imp.getOverlay()!=null && !imp.getHideOverlay();
		if (overlay && !imp.tempOverlay())
			imp = imp.flatten(); 
		return imp.getImage();
	}
	
	void showInternalClipboard() {
		ImagePlus clipboard = ImagePlus.getClipboard();
		if (clipboard!=null) {
			ImageProcessor ip = clipboard.getProcessor();
			ImagePlus imp2 = new ImagePlus("Clipboard", ip.duplicate());
			Roi roi = clipboard.getRoi();
			imp2.deleteRoi();
			if (roi!=null && roi.isArea() && roi.getType()!=Roi.RECTANGLE) {
				roi = (Roi)roi.clone();
				roi.setLocation(0, 0);
				imp2.setRoi(roi);
				IJ.run(imp2, "Clear Outside", null);
				imp2.deleteRoi();
			}
			WindowManager.checkForDuplicateName = true;          
			imp2.show();
		} else
			IJ.error("The internal clipboard is empty.");
	}

}



