package ij.process;
import ij.*;
import ij.gui.Toolbar;


/** This class does flood filling, used by the Analyze Particles command
and the floodFill() macro function. */
public class FloodFiller {

	int maxStackSize = 500; // will be increased as needed
	int[] stack = new int[maxStackSize];
	int stackSize;
	ImageProcessor ip;
	int max;
  
	public FloodFiller(ImageProcessor ip) {
		this.ip = ip;
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
	
	final void push(int x, int y) {
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
		if (stackSize==0)
			return -1;
		else {
			int value = stack[stackSize-1];
			stackSize--;
			return value;
		}
	}

}
