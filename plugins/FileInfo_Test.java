import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;
import ij.io.*;

public class FileInfo_Test implements PlugIn {

    public void run(String arg) {
        FileInfo fi = new FileInfo();
        fi.width = 256;
        fi.height = 254;
        fi.offset = 768;
        fi.fileName = "blobs.tif";
        fi.directory = "/Users/wayne/Desktop/";
        new FileOpener(fi).open();
    }  

}
