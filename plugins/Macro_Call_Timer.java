import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;

public class Macro_Call_Timer implements PlugIn {

//start = getTime;
//for (i=0; i<100000; i++)
//    id = getImageID();
//print((getTime-start)/100);

    public void run(String arg) {
        long start = System.currentTimeMillis();
        for (int i=0; i<10000; i++)
        IJ.runMacro("id=getImageID();");
        IJ.log(""+(System.currentTimeMillis()-start)/10);
    }

}
