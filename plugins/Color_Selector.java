import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.*;
import ij.util.*;
import java.awt.*;

public class Color_Selector implements PlugIn {

    public void run(String arg) {
        ColorSelector cs = new ColorSelector(new Color(255, 0, 0), false);
        Color c = cs.getColor();
        if (c!=null)
            IJ.log(""+c);
    }

}
