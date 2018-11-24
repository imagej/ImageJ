// This macro runs "Measure" on a all the slices in a stack. With a
// hyperstack, it runs "Measure" in a user-selected (c,t,z) order.
// Unchecking "Channels", "Slices" or "Frames" will select the 
// current channel, slice or frame while running "Measure".
//
// Ved P. Sharma, March 21, 2012
// Albert Einstein College of Medicine, New York

    saveSettings;
    setOption("Stack position", true);
    if (!Stack.isHyperstack) {
        getVoxelSize(width, height, depth, unit);
        for (n=1; n<=nSlices; n++) {
          setSlice(n);
          run("Measure");
       }
       if (unit!="pixels") {
           depths = newArray(Table.size);
           for (i=0; i<Table.size; i++)
               depths[i] = i*depth;
           Table.setColumn(unit, depths);
           Table.update;
       }
       restoreSettings;
       exit;
    }
    Stack.getDimensions(w, h, channels, slices, frames);
    Stack.getPosition(ch, sl, fr);
    Dialog.create("Measure Hyperstack");
    Dialog.addCheckbox("Channels ("+channels+")", true);
    Dialog.addCheckbox("Slices ("+slices+")", true);
    Dialog.addCheckbox("Frames ("+frames+")", true);
    choices = newArray("czt(default)", "ctz", "zct", "ztc", "tcz", "tzc");
    Dialog.addChoice("Order:", choices, choices[0]);
    Dialog.show();
    state1 = Dialog.getCheckbox();
    state2 = Dialog.getCheckbox();
    state3 = Dialog.getCheckbox();
    order = Dialog.getChoice();

    if(state1) {startCh = 1; endCh = channels;}
    else {startCh = ch; endCh = ch;}
    if(state2) {startSl = 1; endSl = slices;}
    else {startSl = sl; endSl = sl;}
    if(state3) {startFr = 1; endFr = frames;}
    else {startFr = fr; endFr = fr;}

    if(order == "czt(default)") {
        for (u=startFr; u<=endFr; u++) {
            Stack.setFrame(u);
            for (v=startSl; v<=endSl; v++) {
                Stack.setSlice(v);
                for (w=startCh; w<=endCh; w++) {
                    Stack.setChannel(w);
                    run("Measure");
                }
            }
        }
    }
    if(order == "ctz") {
        for (u=startSl; u<=endSl; u++) {
            Stack.setSlice(u);
            for (v=startFr; v<=endFr; v++) {
                Stack.setFrame(v);
                for (w=startCh; w<=endCh; w++) {
                    Stack.setChannel(w);
                    run("Measure");
                }
            }
        }
    }
    if(order == "zct") {
        for (u=startFr; u<=endFr; u++) {
            Stack.setFrame(u);
            for (v=startCh; v<=endCh; v++) {
                Stack.setChannel(v);
                for (w=startSl; w<=endSl; w++) {
                    Stack.setSlice(w);
                    run("Measure");
                }
            }
        }
    }
    if(order == "ztc") {
        for (u=startCh; u<=endCh; u++) {
            Stack.setChannel(u);
            for (v=startFr; v<=endFr; v++) {
                Stack.setFrame(v);
                for (w=startSl; w<=endSl; w++) {
                    Stack.setSlice(w);
                    run("Measure");
                }
            }
        }
    }
    if(order == "tcz") {
        for (u=startSl; u<=endSl; u++) {
            Stack.setSlice(u);
            for (v=startCh; v<=endCh; v++) {
                Stack.setChannel(v);
                for (w=startFr; w<=endFr; w++) {
                    Stack.setFrame(w);
                    run("Measure");
                }
            }
        }
    }
    if(order == "tzc") {
        for (u=startCh; u<=endCh; u++) {
            Stack.setChannel(u);
            for (v=startSl; v<=endSl; v++) {
                Stack.setSlice(v);
                for (w=startFr; w<=endFr; w++) {
                    Stack.setFrame(w);
                    run("Measure");
                }
            }
        }
    }
    Stack.setPosition(ch, sl, fr);
    restoreSettings;
