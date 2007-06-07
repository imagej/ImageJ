package ij.plugin;
import java.awt.*;
import java.awt.event.*;
import ij.*;
import ij.process.*;
import ij.gui.*;

/** Generates an image useful for choosing colors. */
public class Colors extends ImagePlus implements PlugIn {

	public void run(String arg) {
		int colorWidth = 16;
		int colorHeight = 16;
		int columns = 18;
		int rows = 3;
		int width = columns*colorWidth;
		int height = rows*colorHeight;
		ColorGenerator cg = new ColorGenerator(width, height, new int[width*height]);
		cg.drawColors(colorWidth, colorHeight, columns, rows);
		setProcessor("Colors", cg);
		show();
	}

	/** Overrides ImagePlus.show(). */
	public void show() {
		if (img==null && ip!=null)
			img = ip.createImage();
		win = new ImageWindow(this, new ColorCanvas(this));
		draw();
		IJ.showStatus("");
	}

}


class ColorGenerator extends ColorProcessor {
	int w, h;
	int[] colors = {0x8b0000,0xcd5c5c,0xff0000,0xffb6c1,0xdb7093,0xff1493,0x800080,0xba55d3,0xee82ee,
		0xff00ff,0x483d8b,0x0000cd,0x0000ff,0xb0c4de,0x00bfff,0x5f9ea0,0x00ffff,0x20b2aa,
		0x66cdaa,0x00fa9a,0x228b22,0x00ff00,0x9acd32,0x808000,0xffff00,0xbdb76b,0xf0e68c,
		0xb8860b,0xffc800,0xffdead,0xff9600,0xd2691e,0xff5000,0xe9967a,0xffdedc,0xffebcb};

	public ColorGenerator(int width, int height, int[] pixels) {
		super(width, height, pixels);
	}
	
	void drawColors(int colorWidth, int colorHeight, int columns, int rows) {
		w = colorWidth;
		h = colorHeight;
		drawColor(0, 0, Color.black);
		drawRamp();
		drawColor(columns-1, 0, Color.white);
		//setColor(Color.white);
		//moveTo(15,0); lineTo(15,15);
		//setColor(Color.black);
		//moveTo(272,0); lineTo(272,15);
		int x = 0;
		int y = 1;
		for (int i=0; i<colors.length; i++) {
			drawColor(x, y, new Color(colors[i]));
			if (++x==columns)
				{x = 0; y++; if (y==rows) break;}
		}
		setRoi(null);
	}

	void drawColor(int x, int y, Color c) {
		setRoi(x*w, y*h, w, h);
		setColor(c);
		fill();
	}
	
	void drawRamp() {
		int r,g,b;
		for (int y=0; y<h; y++) {
			for (int x=w; x<(w+256); x++) {
				r = g = b = (byte)(x-w);
				pixels[y*width+x] = 0xff000000 | ((r<<16)&0xff0000) | ((g<<8)&0xff00) | (b&0xff);
			}
		}
	}
}


class ColorCanvas extends ImageCanvas {

	public ColorCanvas(ImagePlus imp) {
		super(imp);
	}

	public void mousePressed(MouseEvent e) {
		//super.mousePressed(e);
		setDrawingColor(offScreenX(e.getX()), offScreenY(e.getY()),IJ.altKeyDown());
	}

}
