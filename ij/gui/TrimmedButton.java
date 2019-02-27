package ij.gui;
import java.awt.*;
import javax.swing.*;

/** This is an extended Button class used to reduce the width of the HUGE buttons on Mac OS X. */
public class TrimmedButton extends Button {
    private int trim = 0;
    
    public TrimmedButton(String title, int trim) {
        super(title);
        if (trim>0) {
        	LookAndFeel laf = UIManager.getLookAndFeel();
        	String name = laf!=null?laf.getName():"";
        	if (ij.IJ.isMacOSX() && name!=null && !name.equals("Mac OS X"))
        		trim = 0;
        }
        this.trim = trim;
    }

    public Dimension getMinimumSize() {
        return new Dimension(super.getMinimumSize().width-trim, super.getMinimumSize().height);
    }

    public Dimension getPreferredSize() {
        return getMinimumSize();
    }
    
}
