package ij.plugin;
import ij.*;
import ij.io.*;
import java.awt.Desktop;
import java.awt.desktop.*;
import java.io.File;
import java.util.Vector;

/** This Mac-specific plugin is designed to handle the "About ImageJ" 
 * command in the ImageJ menu, to open files dropped on ImageJ.app 
 * and to open double-clicked files with creator code "imgJ". 
 * With Java 9 or newer, we use java.awt.desktop instead of the
 * previous com.apple.eawt.* classes.
 * @author Alan Brooks
*/
public class MacAdapter9 implements PlugIn, AboutHandler, OpenFilesHandler, QuitHandler, Runnable {
   static Vector<String> paths = new Vector<String>();

   public void run(String arg) {
      Desktop dtop = Desktop.getDesktop();
      dtop.setOpenFileHandler(this);
      dtop.setAboutHandler(this);
      dtop.setQuitHandler(this);
   }

   @Override
   public void handleAbout(AboutEvent e) {
      IJ.doCommand("About ImageJ...");
   }

   @Override
   public void openFiles(OpenFilesEvent e) {
      for (File file: e.getFiles()) {
         paths.add(file.getPath());
         Thread thread = new Thread(this, "Open");
         thread.setPriority(thread.getPriority()-1);
         thread.start();
      }
   }

   @Override
   public void handleQuitRequestWith(QuitEvent e, QuitResponse response) {
      new Executer("Quit", null); // works with the CommandListener
   }
 
   // Not adding preference handling
   // because we don't have the equivalent of app.setEnabledPreferencesMenu(true);
   // @Override
   // public void handlePreferences(PreferencesEvent e) {
   //    IJ.error("The ImageJ preferences are in the Edit>Options menu.");
   // }
  
    public void run() {
      if (paths.size() > 0) {
         (new Opener()).openAndAddToRecent(paths.remove(0));
      }
    }
}
