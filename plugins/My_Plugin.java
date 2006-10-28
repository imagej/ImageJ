import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;

public class My_Plugin implements PlugIn {

    static String title="Example";
    static int width=512,height=512;
	String[] types = {"xxx", "yyy"};

    public void run(String arg) {
      GenericDialog gd = new GenericDialog("New Image");
      gd.addStringField("Title: ", title);
      gd.addNumericField("Image_Width: ", width, 0);
      gd.addNumericField("Image_Height: ", height, 0);
	gd.addChoice("Image_Type: ", types, "xxx");
      gd.showDialog();
      if (gd.wasCanceled()) return;
      title = gd.getNextString();
      width = (int)gd.getNextNumber();
      height = (int)gd.getNextNumber();
	String c = gd.getNextChoice();
      IJ.newImage(title, "8-bit", width, height, 1);
   }
 
}
