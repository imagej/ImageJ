import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;

public class Test_P6 implements PlugIn {

	public void run(String arg) {
		ImagePlus imp1 = WindowManager.getCurrentImage();
		ImageProcessor ip1 = imp1.getProcessor();
		int ax, ay, bx, by;
		Roi roi;
		PlotWindow plot1;
		double[] ddata;
		double[] xdata;
		for (int i2=0; i2<10; i2++) {
			ax=62+i2;     	
			ay=82;	
			bx=173+i2;	
			by=24;	
			imp1.setRoi(new Line(ax, ay, bx, by,imp1));
			roi=imp1.getRoi();
			ddata = ((Line)roi).getPixels();
			xdata=new double[ddata.length];
			for (int i1=0; i1<ddata.length; i1++) {
				xdata[i1]=(double) i1;		
				IJ.log(i1+"   "+ddata[i1]);
			}
      			plot1 = new PlotWindow("Plot1","pixel","valore",xdata,ddata);
       			plot1.draw();
			IJ.showMessage("Next plot");
		}
	}  // run

}  // testP6_
