package ij.stub;

import java.net.URL;

public interface AppletStub {
    boolean isActive();

    URL getDocumentBase();

    URL getCodeBase();

    String getParameter(String name);

    AppletContext getAppletContext();

    void appletResize(int width, int height);
}
