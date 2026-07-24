package ij.stub;

import java.awt.Image;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;

public interface AppletContext {

    AudioClip getAudioClip(URL url);

    Image getImage(URL url);

    Applet getApplet(String name);

    Enumeration<Applet> getApplets();

    void showDocument(URL url);

    public void showDocument(URL url, String target);

    void showStatus(String status);

    public void setStream(String key, InputStream stream) throws IOException;

    public InputStream getStream(String key);

    public Iterator<String> getStreamKeys();
}
