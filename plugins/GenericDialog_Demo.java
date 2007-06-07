import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;

  public class GenericDialog_Demo implements PlugIn {
    static String title="Example";
     static int width=512,height=512;
     public void run(String arg) {
       GenericDialog gd = new GenericDialog("New Image");
       gd.addStringField("Title: ", title);
       gd.addNumericField("Width: ", width, 0, 4, "pixels");
       gd.addNumericField("Height: ", height, 0, 4, "pixels");
       gd.addNumericField("1: ", 123456789, 0, 1, "");
       gd.addNumericField("2: ", 123456789, 0, 2, "");
       gd.addNumericField("3: ", 123456789, 0, 3, "");
       gd.addNumericField("4: ", 123456789, 0, 4, "");
       gd.addNumericField("5: ", 123456789, 0, 5, "");
       gd.addNumericField("6: ", 123456789, 0, 6, "");
       gd.addNumericField("7: ", 123456789, 0, 7, "");
       gd.showDialog();
       if (gd.wasCanceled()) return;
      title = gd.getNextString();
      width = (int)gd.getNextNumber();
      height = (int)gd.getNextNumber();
       IJ.run("New...", "name="+title+" type='8-bit Unsigned' width="+width+" height="+height);
    }
 }



