package ij.plugin;
import ij.*;
import ij.gui.*;
import java.awt.EventQueue;

/** This plugin implements the Plugins/Utilities/Monitor Events command.
	By implementing the IJEventListener, CommandListener, ImageListener
	and RoiListener interfaces, it is able to monitor foreground and background
	color changes, tool switches, Log window closings, command executions, image
	window openings, closings and updates, and ROI changes.
*/
public class EventListener implements PlugIn, IJEventListener, ImageListener, RoiListener, CommandListener {

	public void run(String arg) {
		IJ.addEventListener(this);
		Executer.addCommandListener(this);
		ImagePlus.addImageListener(this);
		Roi.addRoiListener(this);
		IJ.log("EventListener started");
	}
	
	public void eventOccurred(int eventID) {
		switch (eventID) {
			case IJEventListener.FOREGROUND_COLOR_CHANGED:
				String c = Integer.toHexString(Toolbar.getForegroundColor().getRGB());
				c = "#"+c.substring(2);
				IJ.log("Changed foreground color to "+c);
				break;
			case IJEventListener.BACKGROUND_COLOR_CHANGED:
				c = Integer.toHexString(Toolbar.getBackgroundColor().getRGB());
				c = "#"+c.substring(2);
				IJ.log("Changed background color to "+c);
				break;
			case IJEventListener.TOOL_CHANGED:
				String name = IJ.getToolName();
				IJ.log("Switched to the "+name+(name.endsWith("Tool")?"":" tool"));
				break;
			case IJEventListener.COLOR_PICKER_CLOSED:
				IJ.log("Color picker closed");
				break;
			case IJEventListener.LOG_WINDOW_CLOSED:
				IJ.removeEventListener(this);
				Executer.removeCommandListener(this);
				ImagePlus.removeImageListener(this);
				Roi.removeRoiListener(this);
				IJ.showStatus("Log window closed; EventListener stopped");
				break;
		}
	}

	// called when an image is opened
	public void imageOpened(ImagePlus imp) {
		IJ.log("Image opened: \""+imp.getTitle()+"\""+edt());
	}

	// Called when an image is closed
	public void imageClosed(ImagePlus imp) {
		IJ.log("Image closed: \""+imp.getTitle()+"\""+edt());
	}

	// Called when an image's pixel data is updated
	public void imageUpdated(ImagePlus imp) {
		IJ.log("Image updated: \""+imp.getTitle()+"\""+edt());
	}
	
	// Called when an image is saved
	public void imageSaved(ImagePlus imp) {
		IJ.log("Image saved: \""+imp.getTitle()+"\""+edt());
	}

	private String edt() {
		return EventQueue.isDispatchThread()?" (EDT)":" (not EDT)";
	}
	
	public String commandExecuting(String command) {
		IJ.log("Command executed: \""+command+"\" command");
		return command;
	}
	
	public  void roiModified(ImagePlus img, int id) {
		String type = "UNKNOWN";
		switch (id) {
			case CREATED: type="CREATED"; break;
			case MOVED: type="MOVED"; break;
			case MODIFIED: type="MODIFIED"; break;
			case EXTENDED: type="EXTENDED"; break;
			case COMPLETED: type="COMPLETED"; break;
			case DELETED: type="DELETED"; break;
		}
		IJ.log("ROI modified: "+(img!=null?img.getTitle():"")+", "+type);
	}


}
