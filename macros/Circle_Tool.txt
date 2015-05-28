// This example macro tool creates circular selections.
// Press ctrl-i (Macros>Install Macros) to reinstall after
// making changes. Double click on the tool icon (a circle)
// to set the radius of the circle.
// There is more information about macro tools at
//   http://imagej.nih.gov/ij/developer/macro/macros.html#tools
// and many more examples at
//   http://imagej.nih.gov/ij/macros/tools/

var radius = 20;

macro "Circle Tool - C00cO11cc" {
   getCursorLoc(x, y, z, flags);
   makeOval(x-radius, y-radius, radius*2, radius*2);
}

macro "Circle Tool Options" {
   radius = getNumber("Radius: ", radius);
}
