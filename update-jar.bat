set PATH=d:\jdk1.3\bin;%PATH%
jar xf ij.jar
jar cfm ij.jar Manifest.mf ij IJ_Props.txt
copy ij.jar D:\ImageJ\ij.jar
