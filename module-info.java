module ij {
   requires java.desktop;
	requires java.rmi;
   requires java.compiler;
   requires java.scripting;
   exports ij;
   exports ij.gui;
   exports ij.io;
   exports ij.macro;
   exports ij.measure;
   exports ij.plugin;
   exports ij.plugin.filter;
   exports ij.plugin.frame;
   exports ij.plugin.tool;
   exports ij.process;
   exports ij.text;
   exports ij.util;
}