import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;

public class Macro_ implements PlugIn {

	public void run(String arg) {
int j = 0;
for (int i=0; i<2; i++) 
     do 
        IJ.log(i+" "+j);
     while (j++<2);	}

}
