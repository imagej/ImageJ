// This macro demonstrates how to display a dialog box.
// The dialog it creates contains one string field, one
// popup menu, two numeric fields and one check box.

  title = "Untitled";
  width=512; height=512;
  types = newArray("8-bit", "16-bit", "32-bit", "RGB");
  Dialog.create("New Image");
  Dialog.addString("Title:", title);
  Dialog.addChoice("Type:", types);
  Dialog.addNumber("Width:", width);
  Dialog.addNumber("Height:", height);
  Dialog.addCheckbox("Ramp", true);
  Dialog.show();
  title = Dialog.getString();
  width = Dialog.getNumber();
  height = Dialog.getNumber();;
  type = Dialog.getChoice();
  ramp = Dialog.getCheckbox();
  if (ramp)
     type += " ramp";
  else
     type += " black";
  newImage(title, type, width, height, 1);
