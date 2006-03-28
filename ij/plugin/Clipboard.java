package ij.plugin;
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;
import java.awt.image.*;
import ij.*;
import ij.process.*;
import ij.gui.*;
	
/**	Copies and pastes images to the clipboard. Java 1.4 or later is 
	required to copy to or paste from the system clipboard. */
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
		if (imp!=null)
	 		imp.copy(cut);
	 	else
	 		IJ.noImage();
	}
	
	void paste() {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null)
			imp.paste();
		else
			IJ.noImage();
	}

	boolean setup() {
		if (!IJ.isJava14()) {
			IJ.error("Clipboard", "Java 1.4 or later required");
			return false;
		}
		if (clipboard==null)
			clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		return true;
	}
	
	void copyToSystem() {
		if (!setup()) return;
		try {
			clipboard.setContents(this, null);
		} catch (Throwable t) {}
	}
	
	void showSystemClipboard() {
		if (!setup()) return;
		try {
			Transferable transferable = clipboard.getContents(null);
			if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
				Image img = (Image)transferable.getTransferData(DataFlavor.imageFlavor);
				int width = img.getWidth(null);
				int height = img.getHeight(null);
				BufferedImage   bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
				Graphics g = bi.createGraphics();
				g.drawImage(img, 0, 0, null);
				g.dispose();
				WindowManager.checkForDuplicateName = true;          
				new ImagePlus("Clipboard", bi).show();
			} else
				IJ.error("Clipboard", "No image found on system clipboard.");
		} catch (Throwable t) {}
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
		if ( imp != null) {
			ImageProcessor ip = imp.getProcessor();
			ip = ip.crop();
			int w = ip.getWidth();
			int h = ip.getHeight();
			IJ.showStatus(w+"x"+h+ " image copied to system clipboard");
			Image img = IJ.getInstance().createImage(w, h);
			Graphics g = img.getGraphics();
			g.drawImage(ip.createImage(), 0, 0, null);
			g.dispose();
			return img;
		} else {
			//IJ.noImage();
			return null;
		}
	}
	
	void showInternalClipboard() {
		ImagePlus clipboard = ImagePlus.getClipboard();
		if (clipboard!=null) {
			ImageProcessor ip = clipboard.getProcessor();
			ImagePlus imp2 = new ImagePlus("Clipboard", ip.duplicate());
			Roi roi = clipboard.getRoi();
			imp2.killRoi();
			if (roi!=null && roi.isArea() && roi.getType()!=Roi.RECTANGLE) {
				roi = (Roi)roi.clone();
				roi.setLocation(0, 0);
				imp2.setRoi(roi);
				WindowManager.setTempCurrentImage(imp2);
				IJ.run("Clear Outside");
				imp2.killRoi();
			}
			WindowManager.checkForDuplicateName = true;          
			imp2.show();
		} else
			IJ.error("The internal clipboard is empty.");
	}

}



