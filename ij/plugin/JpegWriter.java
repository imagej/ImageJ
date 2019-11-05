package ij.plugin;
import ij.*;
import ij.process.*;
import ij.io.FileSaver;
import ij.io.SaveDialog;
import java.awt.image.*;
import java.awt.*;
import java.io.*;
import java.util.Iterator;
import javax.imageio.*;
import javax.imageio.stream.*;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javax.imageio.metadata.IIOMetadata;


/** The File/Save As/Jpeg command (FileSaver.saveAsJpeg() method) 
      uses this plugin to save images in JPEG format. */
public class JpegWriter implements PlugIn {
	public static final int DEFAULT_QUALITY = 75;
	private static boolean disableChromaSubsampling;
	private static boolean chromaSubsamplingSet;

	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null) return;
		imp.startTiming();
		saveAsJpeg(imp,arg,FileSaver.getJpegQuality());
		IJ.showTime(imp, imp.getStartTime(), "JpegWriter: ");
	}

	/** Thread-safe method. */
	public static String save(ImagePlus imp, String path, int quality) {
		if (imp==null)
			imp = IJ.getImage();
		if (path==null || path.length()==0)
			path = SaveDialog.getPath(imp, ".jpg");
		if (path==null)
			return null;
		String error = (new JpegWriter()).saveAsJpeg(imp, path, quality);
		return error;
	}

	String saveAsJpeg(ImagePlus imp, String path, int quality) {
		int width = imp.getWidth();
		int height = imp.getHeight();
		int biType = BufferedImage.TYPE_INT_RGB;
		boolean overlay = imp.getOverlay()!=null && !imp.getHideOverlay();
		ImageProcessor ip = imp.getProcessor();
		if (ip.isDefaultLut() && !imp.isComposite() && !overlay && ip.getMinThreshold()==ImageProcessor.NO_THRESHOLD)
			biType = BufferedImage.TYPE_BYTE_GRAY;
		BufferedImage bi = new BufferedImage(width, height, biType);
		String error = null;
		try {
			Graphics g = bi.createGraphics();
			Image img = imp.getImage();
			if (overlay)
				img = imp.flatten().getImage();
			g.drawImage(img, 0, 0, null);
			g.dispose();            
			Iterator iter = ImageIO.getImageWritersByFormatName("jpeg");
			ImageWriter writer = (ImageWriter)iter.next();
			File f = new File(path);
			String originalPath = null;
			boolean replacing = f.exists();
			if (replacing) {
				originalPath = path;
				path += ".temp";
				f = new File(path);
			}
			ImageOutputStream ios = ImageIO.createImageOutputStream(f);
			writer.setOutput(ios);
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(param.MODE_EXPLICIT);
			param.setCompressionQuality(quality/100f);
			if (quality == 100)
				param.setSourceSubsampling(1, 1, 0, 0);						
			IIOImage iioImage = null;
			boolean disableSubsampling = quality>=90;
			if (chromaSubsamplingSet)
				disableSubsampling = disableChromaSubsampling;
			if (!disableSubsampling)  // Use chroma subsampling YUV420
				iioImage = new IIOImage(bi, null, null);
			else {
				// Disable JPEG chroma subsampling
				// http://svn.apache.org/repos/asf/shindig/trunk/java/gadgets/src/main/java/org/apache/shindig/gadgets/rewrite/image/BaseOptimizer.java
				// http://svn.apache.org/repos/asf/shindig/trunk/java/gadgets/src/main/java/org/apache/shindig/gadgets/rewrite/image/JpegImageUtils.java
				// Peter Haub, Okt. 2019
				IIOMetadata metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(bi.getColorModel(), bi.getSampleModel()), param);			
				Node rootNode = metadata.getAsTree("javax_imageio_jpeg_image_1.0");				
				boolean metadataUpdated = false;
				// The top level root node has two children, out of which the second one will
				// contain all the information related to image markers.
				if (rootNode.getLastChild() != null) {
					Node markerNode = rootNode.getLastChild();
					NodeList markers = markerNode.getChildNodes();
					// Search for 'SOF' marker where subsampling information is stored.
					for (int i = 0; i < markers.getLength(); i++) {
						Node node = markers.item(i);
						// 'SOF' marker can have
						//   1 child node if the color representation is greyscale,
						//   3 child nodes if the color representation is YCbCr, and
						//   4 child nodes if the color representation is YCMK.
						// This subsampling applies only to YCbCr.
						if (node.getNodeName().equalsIgnoreCase("sof") && node.hasChildNodes() && node.getChildNodes().getLength() == 3) {
							// In 'SOF' marker, first child corresponds to the luminance channel, and setting
							// the HsamplingFactor and VsamplingFactor to 1, will imply 4:4:4 chroma subsampling.
							NamedNodeMap attrMap = node.getFirstChild().getAttributes();
							// SamplingModes: UNKNOWN(-2), DEFAULT(-1), YUV444(17), YUV422(33), YUV420(34), YUV411(65)					
							int samplingmode = 17;   // YUV444
							attrMap.getNamedItem("HsamplingFactor").setNodeValue((samplingmode & 0xf) + "");
							attrMap.getNamedItem("VsamplingFactor").setNodeValue(((samplingmode >> 4) & 0xf) + "");
							metadataUpdated = true;
							break;
						}
					}
				}
				// Read the updated metadata from the metadata node tree.
				if (metadataUpdated)
					metadata.setFromTree("javax_imageio_jpeg_image_1.0", rootNode);					
				iioImage = new IIOImage(bi, null, metadata);				
			} // end of code adaption (Disable JPEG chroma subsampling)			
			writer.write(null, iioImage, param);
			ios.close();
			writer.dispose();
			if (replacing) {
				File f2 = new File(originalPath);
				boolean ok = f2.delete();
				if (ok) f.renameTo(f2);
			}
		} catch (Exception e) {
			error = ""+e;
			IJ.error("Jpeg Writer", ""+error);
		}
		return error;
	}
	
	public static void setQuality(int jpegQuality) {
		FileSaver.setJpegQuality(jpegQuality);
	}

	public static int getQuality() {
		return FileSaver.getJpegQuality();
	}
	
	/** Enhance quality of JPEGs by disabing chroma subsampling. 
		By default, enhanced quality is automatically used
		when the Quality setting is 90 or greater. */		
	public static void enhanceQuality(boolean enhance) {
		disableChromaSubsampling = enhance;
		chromaSubsamplingSet = true;
	}

	public static void disableChromaSubsampling(boolean disable) {
		enhanceQuality(disable);
	}


}
