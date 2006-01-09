package ij.process;
import ij.*;
import ij.gui.Toolbar;
import java.awt.Rectangle;


/**	This class, which does flood filling, is used by the floodFill() macro function and
	by the particle analyzer
	The Wikipedia at "http://en.wikipedia.org/wiki/Flood_fill" has a good 
	description of the algorithm used here as well as examples in C and Java. 
*/
public class FloodFiller {

	int maxStackSize = 500; // will be increased as needed
	int[] stack = new int[maxStackSize];
	int stackSize;
	ImageProcessor ip;
	int max;
	boolean isFloat;
  
	public FloodFiller(ImageProcessor ip) {
		this.ip = ip;
		isFloat = ip instanceof FloatProcessor;
	}

	public boolean fill(int x, int y) {
		int width = ip.getWidth();
		int height = ip.getHeight();
		int color = ip.getPixel(x, y);
		ip.drawLine(x, y, x, y);
		int newColor = ip.getPixel(x, y);
		ip.putPixel(x, y, color);
		if (color==newColor) return false;
		stackSize = 0;
		push(x, y);
		while(true) {   
			int coordinates = pop(); 
			if (coordinates ==-1) return true;
			x = coordinates&0xffff;
			y = coordinates>>16;
			int x1 = x; int x2 = x;
			while (ip.getPixel(x1,y)==color && x1>=0) x1--; // find start of scan-line
			x1++;
			while (ip.getPixel(x2,y)==color && x2<width) x2++;  // find end of scan-line                 
			x2--;
			ip.drawLine(x1,y, x2,y); // fill scan-line
			boolean inScanLine = false;
			for (int i=x1; i<=x2; i++) { // find scan-lines above this one
				if (!inScanLine && y>0 && ip.getPixel(i,y-1)==color)
					{push(i, y-1); inScanLine = true;}
				else if (inScanLine && y>0 && ip.getPixel(i,y-1)!=color)
					inScanLine = false;
			}
			inScanLine = false;
			for (int i=x1; i<=x2; i++) { // find scan-lines below this one
				if (!inScanLine && y<height-1 && ip.getPixel(i,y+1)==color)
					{push(i, y+1); inScanLine = true;}
				else if (inScanLine && y<height-1 && ip.getPixel(i,y+1)!=color)
					inScanLine = false;
			}
		}        
	}
	
	int count=0;
	
	/** This method is used by the particle analyzer to remove interior holes from particle masks. */
	public void particleAnalyzerFill(int x, int y, double level1, double level2, ImageProcessor mask, Rectangle bounds) {
		//if (count>100) return;
		int width = ip.getWidth();
		int height = ip.getHeight();
		mask.setColor(0);
		mask.fill();
		mask.setColor(255);
		stackSize = 0;
		push(x, y);
		while(true) {   
			int coordinates = pop(); 
			if (coordinates ==-1) return;
			x = coordinates&0xffff;
			y = coordinates>>16;
			int x1 = x; int x2 = x;
			while (inParticle(x1,y,level1,level2) && x1>=0) x1--; // find start of scan-line
			x1++;
			while (inParticle(x2,y,level1,level2) && x2<width) x2++;  // find end of scan-line                 
			x2--;
			mask.drawLine(x1-bounds.x,y-bounds.y, x2-bounds.x,y-bounds.y); // fill scan-line i mask
			ip.drawLine(x1,y, x2,y); // fill scan-line in image
			boolean inScanLine = false;
			if (x1>0) x1--; if (x2<width-1) x2++;
			for (int i=x1; i<=x2; i++) { // find scan-lines above this one
				if (!inScanLine && y>0 && inParticle(i,y-1,level1,level2))
					{push(i, y-1); inScanLine = true;}
				else if (inScanLine && y>0 && !inParticle(i,y-1,level1,level2))
					inScanLine = false;
			}
			inScanLine = false;
			for (int i=x1; i<=x2; i++) { // find scan-lines below this one
				if (!inScanLine && y<height-1 && inParticle(i,y+1,level1,level2))
					{push(i, y+1); inScanLine = true;}
				else if (inScanLine && y<height-1 && !inParticle(i,y+1,level1,level2))
					inScanLine = false;
			}
		}        
	}
	
	final boolean inParticle(int x, int y, double level1, double level2) {
		if (isFloat)
			return ip.getPixelValue(x,y)>=level1 &&  ip.getPixelValue(x,y)<=level2;
		else
			return ip.getPixel(x,y)>=level1 &&  ip.getPixel(x,y)<=level2;
	}
	
	final void push(int x, int y) {
		//IJ.log("push: "+x+"  "+y);
		//if (count++>100) return;
		stackSize++;
		if (stackSize==maxStackSize) {
			int[] newStack = new int[maxStackSize*2];
			System.arraycopy(stack, 0, newStack, 0, maxStackSize);
			stack = newStack;
			maxStackSize *= 2;
		}
		//if (stackSize>max) max = stackSize;
		stack[stackSize-1] = x + (y<<16);
	}
	
	final int pop() {
		//IJ.log("pop ");
		if (stackSize==0)
			return -1;
		else {
			int value = stack[stackSize-1];
			stackSize--;
			return value;
		}
	}

}
