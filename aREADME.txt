To compile and run ImageJ on a Unix machine:

    alias javac /usr/local/jdk/bin/javac
    alias java /usr/local/jdk/bin/java
    javac ij/ImageJ.java
    javac ij/ImageJApplet.java
    javac ij/plugin/*.java
    javac ij/plugin/filter/*.java
    javac ij/plugin/frame/*.java
    javac plugins/*.java
    java ij.ImageJ

where JDK 1.2 or JDK 1.3 are located in /usr/local/jdk.
A copy of /usr/local/jdk/lib/tools.jar must be in
/usr/local/jdk/jre/lib/ext to provide access to the
javac compiler used by ImageJ's Compile and Run command.

Or to compile and run on a Windows machine:

    set CLASSPATH=.;plugins
    javac ij\ImageJ.java
    javac ij\plugin\*.java
    javac ij\plugin\filter\*.java
    javac ij\plugin\frame\*.java
    java ij.ImageJ

    JDK 1.1.7 or later must be installed and the PATH
    updated using a command something like:

        set PATH=C:\jdk1.1.7\bin;%PATH%
