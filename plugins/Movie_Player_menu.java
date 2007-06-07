import ij.*;
import ij.plugin.PlugIn;

import java.awt.*;
import java.awt.event.*;
import java.applet.*;
import java.io.IOException;

import quicktime.*;
import quicktime.io.*;
import quicktime.qd.*;
import quicktime.std.*;
import quicktime.std.movies.*;
import quicktime.std.movies.media.*;
import quicktime.app.display.*;
import quicktime.app.players.*;

/**
    Plays a QuickTime movie in a window. Requires that QTJava.zip be in the classpath. On the Mac, this means that
    QTJava.zip must be in the System Folder:Extension:MRJ Libraries: MRJClasses folder. QTJava.zip comes with
    QuickTime 4.0 (requires custom install).    This plugin has not been tested on a windows PC.
*/

public class Movie_Player_menu implements PlugIn {

    public void run(String arg) {
        try { 
            QTSession.open();
            // make a window and show it - we only have one window/one movie at a time
            PlayMovie pm = new PlayMovie("Movie Player");
            pm.show();
            pm.toFront();
                        ij.WindowManager.addWindow(pm);
        } catch (QTException e) {
            // at this point we close down QT if an exception is generated because it means
            // there was a problem with the initialization of QT>
            e.printStackTrace();
            QTSession.close ();
        }
    }

}

class PlayMovie extends Frame implements Errors {

    boolean running;
    MoviePlayer mp;
    private QTDrawable myPlayer;
    private Movie m;
    private QTCanvas myQTCanvas;
                      public PlayMovie pm;  
    
    public PlayMovie (String title) {
        super (title);
        myQTCanvas = new QTCanvas();        
        add(myQTCanvas);            
        addWindowListener(new WindowAdapter () {
            public void windowClosing (WindowEvent e) {
                IJ.log("closing");
                            removeMovie();
                QTSession.close();
                setVisible(false);
                dispose();
                //System.exit(0);
            }           
            public void windowClosed (WindowEvent e) { 
                //IJ.write("exiting");
                //setVisible(false);
                //dispose();
                //System.exit(0);
            }
        });
        openMovie();
        if (IJ.altKeyDown())
            presentMovie();
        addKeyListener(IJ.getInstance());
    }

void removeMovie(){
 ij.WindowManager.removeWindow(pm);}

    void openMovie() {
        stopPlayer();
        try {
            QTFile qtf = QTFile.standardGetFilePreview(QTFile.kStandardQTFileTypes);
            createNewMovieFromURL ("file://" + qtf.getPath());
        } catch (QTException e) {
            if (e.errorCode() != Errors.userCanceledErr)
                e.printStackTrace();
        }
    }

    void presentMovie() {
        try {                   
            if (myPlayer==null) return;
            
            // present in full screen mode
            // use the current screen resolution and current movie
            FullScreenWindow w = new FullScreenWindow(new FullScreen(), this);
            mp = new MoviePlayer (m);
            QTCanvas c = new QTCanvas (QTCanvas.kPerformanceResize, 0.5F, 0.5F);
            w.add (c);
            w.setBackground (Color.lightGray);
            
                //remove the movie from its current QTCanvas 
            myQTCanvas.removeClient();
                //put it into the new canvas of the FullScreenWindow
                //we restore this in the HideFSWindow's action
            c.setClient (mp, false);
            
                // show FSWindow and setup hide - it is invoke through a mousePressed action
                // so we set this as a listener for both the window and the canvas
            w.show();
            HideFSWindow hw = new HideFSWindow (w, this, c);
            w.addMouseListener (hw);                            
            c.addMouseListener (hw);
            w.addKeyListener(IJ.getInstance());
            c.addKeyListener(IJ.getInstance());
            
            while (!IJ.spaceBarDown()) {Thread.yield();}
            startMovie();                           
            
        } catch (QTException err) {
            err.printStackTrace();
        }
    }
    
    // start the movie playing
    void startMovie() throws QTException {
        mp.setRate (1);
        //running = true;
    }

    // This will resize the window to the size of the new movie
    public void createNewMovieFromURL (String theURL) {
        try {
            // create the DataRef that contains the information about where the movie is
            DataRef urlMovie = new DataRef(theURL);
            
            // create the movie 
            m = Movie.fromDataRef (urlMovie,StdQTConstants.newMovieActive);
            
            // This shows the steps to use the three different Objects to present a Movie
                // QTPlayer -> presents the MovieController allowing the user to interact with the movie
                // MoviePlayer -> presents the Movie directly to the screen
                // MoviePresenter -> puts the Movie into an offscreen buffer
            if (true) { // QTPlayer
                MovieController mc = new MovieController (m);           
                mc.setKeysEnabled (true);
                myPlayer = new QTPlayer (mc);
            } else if (false) { // make a MoviePlayer version
                myPlayer = new MoviePlayer (m);
            } else if (false) { // make a MoviePresenter out of this
                myPlayer = new MoviePresenter (m);
            }
            
            myQTCanvas.setClient (myPlayer, true);
            
            // this will set the size of the enclosing frame to the size of the incoming movie
            pack();
        
            //no user control over MoviePlayer or MoviePresenter so set rate
            if (false)
                m.setRate(1);
            
        } catch (QTException err) {
            err.printStackTrace();
        }
    }
    
    public QTDrawable getPlayer () { return myPlayer; }
    
    public QTCanvas getCanvas () { return myQTCanvas; }
    
    public Movie getMovie () throws QTException {
        return m;
    }
    
    void stopPlayer () {
        try {
            if (m != null)
                m.setRate(0);
        } catch (QTException err) {
            err.printStackTrace();
        }
    }
    public void keyPressed(KeyEvent e) {
        int keyCode = e.getKeyCode();
        running = true;
        IJ.beep();
    }
    
    public void keyReleased(KeyEvent e) {}
    public void keyTyped(KeyEvent e) {}

}

class HideFSWindow extends MouseAdapter {

    private FullScreenWindow w;
    private PlayMovie pm;
    private QTCanvas c;

    HideFSWindow (FullScreenWindow w, PlayMovie pm, QTCanvas c) {
        this.w = w;
        this.pm = pm;
        this.c = c;
    }
    
    public void mousePressed (MouseEvent me) {
        try {
            // this will stop the movie and reset it back to the previous window
            c.removeClient();
            pm.getCanvas().setClient (pm.getPlayer(), false);
        } catch (QTException e) {
            e.printStackTrace();
        } finally {
            w.hide();
        }
    }
}       






