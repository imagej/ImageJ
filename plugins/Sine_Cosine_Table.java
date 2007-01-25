import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;
import ij.measure.ResultsTable;

/**
This plugin adds a sine/cosine table to the ImageJ results table
and displays it in the Results window. It is equivalent to this macro:
<pre>
        run("Clear Results");
        row = 0;
        for (n=0; n<=2*PI; n += 0.1) {
            setResult("n", row, n);
            setResult("Sine(n)", row, sin(n));
            setResult("Cos(n)", row, cos(n));
            row++;
        }
        updateResults()
</pre>
*/
public class Sine_Cosine_Table implements PlugIn {

	public void run(String arg) {
		ResultsTable rt = new ResultsTable();
		rt.reset();
		for (double n=0; n<=2*Math.PI; n += 0.1) {
			rt.incrementCounter();
			rt.addValue("n", n);
			rt.addValue("Sine(n)", Math.sin(n));
			rt.addValue("Cos(n)", Math.cos(n));
		}
		rt.show("Results2");
	}

}
