// This macro processes all the files in a folder and any subfolders.

  extension = ".tif";
  dir1 = getDirectory("Choose Source Directory ");
  //dir2 = getDirectory("Choose Destination Directory ");
  setBatchMode(true);
  n = 0;
  processFolder(dir1);

  function processFolder(dir1) {
     list = getFileList(dir1);
     for (i=0; i<list.length; i++) {
          if (endsWith(list[i], "/"))
              processFolder(dir1+list[i]);
          else if (endsWith(list[i], extension))
             processImage(dir1, list[i]);
      }
  }

  function processImage(dir1, name) {
     open(dir1+name);
     print(n++, name);
     // add code here to analyze or process the image
     //saveAs(extension, dir2+name);
     close();
  }
