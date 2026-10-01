package ij.gui;

import ij.ImagePlus;
import ij.Prefs;
import ij.process.ColorProcessor;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;

/** Deterministic rendering tests; no display peer or microscopy data required. */
public class ImageCanvasHiDpiTest {
    private static class Canvas extends ImageCanvas {
        Canvas(ImagePlus imp) { super(imp); }
        public Image createImage(int width, int height) {
            return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        }
    }

    private static class SyntheticImage extends ImagePlus {
        ImageCanvas canvas;
        SyntheticImage(ColorProcessor processor) { super("synthetic checkerboard", processor); }
        public ImageCanvas getCanvas() { return canvas; }
    }

    private SyntheticImage image() { return image(128); }

    private SyntheticImage image(int size) {
        int[] pixels = new int[size*size];
        for (int y=0; y<size; y++)
            for (int x=0; x<size; x++)
                pixels[y*size+x] = ((x+y)%2==0) ? 0xffffff : 0;
        return new SyntheticImage(new ColorProcessor(size,size,pixels));
    }

    private boolean translated;

    private BufferedImage render(Canvas canvas, double sx, double sy) {
        BufferedImage result = new BufferedImage(512,512,BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        if (translated) g.translate(0.25,0.5);
        g.scale(sx,sy);
        canvas.paint(g);
        g.dispose();
        return result;
    }

    private void sameInterior(BufferedImage expected, BufferedImage actual, double sx, double sy, double zoom) {
        // Keep comparison away from selection outlines and the zoom indicator.
        for (int y=(int)(40*zoom*sy); y<(int)(100*zoom*sy); y++)
            for (int x=(int)(40*zoom*sx); x<(int)(100*zoom*sx); x++)
                assertEquals("pixel at "+x+","+y,expected.getRGB(x,y),actual.getRGB(x,y));
    }

    @Test public void roiOverlayAndShowAllPreserveDetail() { checkRendering(false, false); }
    @Test public void croppedSourcePreservesDetail() { checkRendering(true, false); }
    @Test public void oddCroppedSourcePreservesDetail() { checkRendering(true, false, 129); }
    @Test public void interpolatedSourcePreservesDetail() { checkRendering(false, true); }

    @Test public void fractionalScaleWithOddLogicalSizePreservesDetail() { checkRendering(false, false, 130); }
    @Test public void translatedDeviceTransformPreservesDetail() {
        translated = true;
        try { checkRendering(false, false, 130); } finally { translated = false; }
    }
    @Test public void odd129SourcePreservesDetail() { checkRendering(false, false, 129); }
    @Test public void oddSourceSizePreservesDetail() { checkRendering(false, false, 131); }

    private void checkRendering(boolean cropped, boolean interpolateImages) { checkRendering(cropped, interpolateImages, 128); }
    private void checkRendering(boolean cropped, boolean interpolateImages, int size) {
        boolean buffered = Prefs.paintDoubleBuffered;
        boolean interpolate = Prefs.interpolateScaledImages;
        try {
            Prefs.paintDoubleBuffered = false;
            Prefs.interpolateScaledImages = interpolateImages;
            SyntheticImage imp = image(size);
            int[] original = ((int[])imp.getProcessor().getPixels()).clone();
            Canvas canvas = new Canvas(imp);
            imp.canvas = canvas;
            if (cropped) {
                canvas.setSourceRect(new java.awt.Rectangle(16,16,size-32,size-32));
                canvas.hideZoomIndicator(true);
            }
            for (double zoom : new double[]{0.5,1.0,2.0}) {
                canvas.setMagnification(zoom);
                for (double[] scale : new double[][]{{1,1},{2,2},{1.25,1.5},{1.5,1.5}}) {
                    BufferedImage direct = render(canvas,scale[0],scale[1]);
                    for (Roi roi : new Roi[]{new Roi(2,2,10,10),new OvalRoi(2,2,10,10),new Line(2,2,12,12),new PointRoi(4,4),new TextRoi(2,2,"ROI"),new PolygonRoi(new int[]{2,12,2},new int[]{2,2,12},3,Roi.POLYGON),new PolygonRoi(new int[]{2,12,8,2},new int[]{2,2,12,8},4,Roi.FREEROI)}) {
                        imp.setRoi(roi);
                        sameInterior(direct,render(canvas,scale[0],scale[1]),scale[0],scale[1],zoom);
                        imp.deleteRoi();
                        sameInterior(direct,render(canvas,scale[0],scale[1]),scale[0],scale[1],zoom);
                    }
                    Overlay overlay = new Overlay(new Roi(2,2,10,10));
                    imp.setOverlay(overlay);
                    sameInterior(direct,render(canvas,scale[0],scale[1]),scale[0],scale[1],zoom);
                    imp.setOverlay(null);
                    canvas.setShowAllList(overlay);
                    sameInterior(direct,render(canvas,scale[0],scale[1]),scale[0],scale[1],zoom);
                    canvas.setShowAllList(null);
                    Prefs.paintDoubleBuffered = true;
                    sameInterior(direct,render(canvas,scale[0],scale[1]),scale[0],scale[1],zoom);
                    Prefs.paintDoubleBuffered = false;
                }
            }
            assertArrayEquals(original,(int[])imp.getProcessor().getPixels());
        } finally {
            Prefs.paintDoubleBuffered = buffered;
            Prefs.interpolateScaledImages = interpolate;
        }
    }
}
