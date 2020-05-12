// An ImageJ tool that makes nice and easy labels 
// as seen https://twitter.com/Astbury_BSL/status/1258677104696311809
// Author: Jerome Mutterer

   var label = 'label';
   var lineWidth = 2;
   var globalLineWidth = parseInt(call('ij.gui.Line.getWidth'));
  
   macro "Label Tool (double click for options) - C037o0d55L3d65L65f5T830ca" {  
     width = parseInt(call('ij.gui.Line.getWidth'));
      if (width>1 || width!=globalLineWidth) {
         lineWidth=width;
         globalLineWidth = width;
      }
      radius = maxOf(4,lineWidth*2);
      getCursorLoc(x, y, z, flags);
      if (flags&9>0) {  // shift or alt
         removeLabel(x,y);
         exit();
      }
      getDateAndTime(yr, mo, dw, d, h, m, s, ms);
      uid = "labeltool_"+yr+""+mo+""+d+""+h+""+m+""+s+""+ms;
      nbefore = Overlay.size;
      getCursorLoc(x1, y1, z, flags);
      setLineWidth(lineWidth);
      while (flags&16>0) {
         getCursorLoc(x1, y1, z, flags);
         drawItem();
         wait(30);
         while (Overlay.size>nbefore)
            Overlay.removeSelection(Overlay.size-1);
      }
      drawItem();
      label =getString("Enter label", label);
      while (Overlay.size>nbefore)
         Overlay.removeSelection(Overlay.size-1);
      drawItem();
   }

   function drawItem() {
      makeOval(x-radius, y-radius, radius*2, radius*2);
      Roi.setName(uid);
      Overlay.addSelection("",0,""+hexCol());
      makeLine(x, y,x1,y1,x1+(((x1<x)*-1)+((x1>=x)*1))*getStringWidth(label),y1);
      Roi.setName(uid);
      Overlay.addSelection(""+hexCol(), lineWidth);
      setFont("user");
      makeText(label, x1 - (x1<x)*getStringWidth(label), y1-getValue("font.height")-lineWidth);
      Roi.setName(uid);
      Overlay.addSelection(""+hexCol(), lineWidth);
      run("Select None");
   }

   function hexCol() {
      return IJ.pad(toHex(getValue("rgb.foreground")),6);
   }

   function removeLabel(x,y) {
      index = Overlay.indexAt(x,y);
      if (index>=0) {
         Overlay.activateSelection(index);
         name = Roi.getName();
         Overlay.removeRois(name);
         Roi.remove;
      }
   }

   macro "Label Tool (double click for options) Options" {
      Dialog.create("Label Maker Tool Options");
      Dialog.addNumber("Line width:", lineWidth, 0, 3, "pixels");
      m1 = "Shift or alt click to remove a label.\n";
      m2 = "Double click on text tool to change\n";
      m3 = "font size and color.";
      Dialog.setInsets(0, 0, 0);
      Dialog.addMessage(m1+m2+m3);
      Dialog.show();
      lineWidth = Dialog.getNumber();
 }

