  run("Blobs (25K)");
  makeLine(56, 64, 90, 15);
  y1 = getProfile();
  n = y1.length;
  x = Array.getSequence(n);
  Fit.doFit("Gaussian", x, y1);
  y2 = newArray(n);
  for (i=0; i<n; i++)
     y2[i] = Fit.f(i);
  Plot.create("Example Plot", "Distance in Pixels", "Intensity");
  Plot.setLimits(0, n-1, 0, 225);
  Plot.setColor("red", "red");
  Plot.add("circle", x, y1);
  Plot.setColor("black");
  Plot.setLineWidth(2);
  Plot.add("line", x, y2);
  Plot.setFontSize(14);
  Plot.addLegend("Data Points\nGaussian Fit");
  


