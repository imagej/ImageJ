package ij.gui;

import java.awt.Button;
import java.awt.Frame;
import java.awt.event.ActionEvent;

/** A modal dialog box with a one line message and
	"Yes", "No" and "Cancel" buttons. 
* @author github.com/tufanbarisyildirim       
*/
public class YesYesAllNoCancelDialog  extends YesNoCancelDialog{
    
    private Button yestoallB;
    public static boolean yestoallPressed;
    
    
    public YesYesAllNoCancelDialog(Frame parent, String title, String msg)
    {
        super( parent,  title,  msg);
        yestoallB = new Button(" Yes To All ");
        yestoallB.addActionListener(this);
        yestoallB.addKeyListener(this);
        panel.add(yestoallB,1);
        
        
    }
    
     @Override
    public void actionPerformed(ActionEvent e){
        if(e.getSource() == yestoallB)
            yestoallPressed = true;
        
        super.actionPerformed(e);
    }
    
    
}
