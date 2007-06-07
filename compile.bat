set PATH=C:\jdk1.3\bin;%PATH%

set CLASSPATH=.;C:\jdk1.3\lib\tools.jar
javac ij\ImageJ.java
javac ij\plugin\*.java
javac ij\plugin\filter\*.java
javac ij\plugin\frame\*.java
java ij.ImageJ
