package ij.gui;
import ij.*;
import ij.process.*;
import java.awt.*;
import java.util.Vector;

/** This class implements the Plot Window's More>Contents Style dialog */
public class PlotContentsStyleDialog implements DialogListener {
	private Plot plot;

	/** Creator that sets a plot to use */
	public PlotContentsStyleDialog(Plot plot) {
		this.plot = plot;
	}

	/** Shows the dialog, with a given parent Frame */
	public void showDialog(Frame parent) {
		String[] designations = plot.getPlotObjectDesignations();
		if (designations.length==0) {
			IJ.error("Empty Plot");
			return;
		}
		String[] stylesBackup = new String[designations.length];
		for (int i=0; i<stylesBackup.length; i++)
			stylesBackup[i] = plot.getPlotObjectStyles(i);
		GenericDialog gd = new GenericDialog("Plot Contents Style", parent);
		IJ.wait(100);	//needed to avoid hanging
		gd.addChoice("Item:", designations, designations[0]);
		gd.addStringField("Color:", "#########");
		gd.addStringField("Secondary (fill) color:", "#########");
		gd.addNumericField("Line width: ", 1.0, 1);
		gd.addChoice("Symbol:", Plot.SORTED_SHAPES, Plot.SORTED_SHAPES[2]);
		gd.setInsets(10, 60, 0);
		gd.addCheckbox("Hidden", false);
		gd.addDialogListener(this);
		IJ.wait(100);	//needed to avoid hanging
		updateDialog(gd, 0);	//fill in style for index 0

		gd.showDialog();
		if (gd.wasCanceled()) {
			for (int i=0; i<stylesBackup.length; i++)
				plot.setPlotObjectStyles(i, stylesBackup[i]);
		}
	}
	
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		if (e == null) return true;	//gets called with e=null upon OK
		//IJ.log("dialogItemChanged e="+e);IJ.wait(50);
		int index = gd.getNextChoiceIndex();
		String color = gd.getNextString();
		String color2 = gd.getNextString();
		double width = gd.getNextNumber();
		String symbol = gd.getNextChoice();
		Boolean hidden = gd.getNextBoolean();
		Choice designationsC = (Choice)(gd.getChoices().get(0));
		if (e.getSource() == designationsC)
			updateDialog(gd, index);
		else
			plot.setPlotObjectStyles(index, color.trim()+","+color2.trim()+","+(float)width+","+symbol+(hidden?",hidden":""));
		return true;
	}

	private void updateDialog(GenericDialog gd, int index) {
		Vector stringFields = gd.getStringFields();
		Vector choices = gd.getChoices();
		Choice designationsC = (Choice)(choices.get(0));
		TextField colorF = (TextField)(stringFields.get(0));
		TextField color2F = (TextField)(stringFields.get(1));
		TextField widthF = (TextField)(gd.getNumericFields().get(0));
		Choice symbolC = (Choice)(choices.get(1));
		Checkbox hiddenC = (Checkbox)gd.getCheckboxes().get(0);
		String styleString = plot.getPlotObjectStyles(index);
		String[] items = styleString.split(",");
		//IJ.log(items.length+" items from "+allStyles[index]);
		colorF.setText(items[0]);
		color2F.setText(items[1]);
		widthF.setText(items[2]);
		if (items.length >= 4)
			symbolC.select(items[3]);
		String designation = designationsC.getSelectedItem();
		boolean isData = designation.startsWith("Data");
		boolean isText = designation.startsWith("Text");
		color2F.setEnabled(isData);	//only (some) data symbols have secondary color
		widthF.setEnabled(!isText); //all non-Text types have line width
		symbolC.setEnabled(isData); //only data have a symbol to choose
		hiddenC.setState(styleString.contains("hidden"));
	}

	public void setNPasses(int nPasses) {
	}

}
