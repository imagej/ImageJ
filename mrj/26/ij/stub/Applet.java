package ij.stub;

import java.awt.Dimension;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.Panel;
import java.net.URL;
import java.util.Locale;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;

public class Applet extends Panel {
    public Applet() throws HeadlessException {
        throw new UnsupportedOperationException("Java 26 has removed Applets");
    }

    public final void setStub(AppletStub stub) {
        throw new UnsupportedOperationException("Java 26 has removed Applets");
    }

    public boolean isActive() {
        throw new UnsupportedOperationException("Java 26 has removed Applets");
    }

    public URL getDocumentBase() {
        throw new UnsupportedOperationException("Java 26 has removed Applets");
    }

    public URL getCodeBase() {
        throw new UnsupportedOperationException("Java 26 has removed Applets");
    }

    public String getParameter(String name) {
        throw new UnsupportedOperationException("Java 26 has removed Applets");
    }

    public AppletContext getAppletContext() {
        throw new UnsupportedOperationException("Java 26 has removed Applets");
    }

    public void resize(int width, int height) {
        throw new UnsupportedOperationException("Java 26 has removed Applets");
    }

    public void resize(Dimension d) {
        throw new UnsupportedOperationException("Java 26 has removed Applets");
    }

    @Override
    public boolean isValidateRoot() {
        throw new UnsupportedOperationException("Java 26 has removed Applets");
    }

    public void showStatus(String msg) {
        throw new UnsupportedOperationException("Java 26 has removed Applets");
    }

    public Image getImage(URL url) {
        throw new UnsupportedOperationException("Java 26 has removed Applets");
    }

    public Image getImage(URL url, String name) {
        throw new UnsupportedOperationException("Java 26 has removed Applets");
    }

    public static final AudioClip newAudioClip(URL url) {
        throw new UnsupportedOperationException("Java 26 has removed Applets");
    }

    public AudioClip getAudioClip(URL url) {
        throw new UnsupportedOperationException("Java 26 has removed Applets");
    }

    public AudioClip getAudioClip(URL url, String name) {
        throw new UnsupportedOperationException("Java 26 has removed Applets");
    }

    public String getAppletInfo() {
        throw new UnsupportedOperationException("Java 26 has removed Applets");
    }

    public Locale getLocale() {
        throw new UnsupportedOperationException("Java 26 has removed Applets");
    }

    public String[][] getParameterInfo() {
        throw new UnsupportedOperationException("Java 26 has removed Applets");
    }

    public void play(URL url) {
        throw new UnsupportedOperationException("Java 26 has removed Applets");
    }

    public void play(URL url, String name) {
        throw new UnsupportedOperationException("Java 26 has removed Applets");
    }

    public void init() {
        throw new UnsupportedOperationException("Java 26 has removed Applets");
    }

    public void start() {
        throw new UnsupportedOperationException("Java 26 has removed Applets");
    }

    public void stop() {
        throw new UnsupportedOperationException("Java 26 has removed Applets");
    }

    public void destroy() {
        throw new UnsupportedOperationException("Java 26 has removed Applets");
    }

    @SuppressWarnings("serial") // Not statically typed as Serializable
            AccessibleContext accessibleContext = null;

    public AccessibleContext getAccessibleContext() {
        throw new UnsupportedOperationException("Java 26 has removed Applets");
    }

    protected class AccessibleApplet extends AccessibleAWTPanel {
        protected AccessibleApplet() {}

        public AccessibleRole getAccessibleRole() {
            return AccessibleRole.FRAME;
        }

        public AccessibleStateSet getAccessibleStateSet() {
            AccessibleStateSet states = super.getAccessibleStateSet();
            states.add(AccessibleState.ACTIVE);
            return states;
        }
    }
}
