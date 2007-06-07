rem Compiles all the plug-ins in this folder and then runs ImageJ

rem set PATH=c:\jdk1.1.7\bin;%PATH%
set CLASSPATH=../ij.jar;.
javac *.java
java ij.ImageJ
