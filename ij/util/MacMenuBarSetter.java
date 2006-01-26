package ij.util;
import ij.*;
import java.awt.*;

/** On Macs, places the ImageJ menu bar at the top of the screen. 
	This is done on a separate thread to avoid potential thread deadlock 
	issues with setting the MenuBar on the event dispatch thread. */
public class MacMenuBarSetter implements Runnable {
	Frame win;

	public MacMenuBarSetter(Frame win) {
		// IJ.log("MacMenuBarSetter: "+win);
		this.win = win;
		Thread thread = new Thread(this, "MacMBSetter");
		thread.setPriority(Thread.NORM_PRIORITY);
		thread.start(); 
	}

	public void run() {
		IJ.wait(10); // may be needed for Java 1.4 on OS X
		win.setMenuBar(Menus.getMenuBar());
	}

}
