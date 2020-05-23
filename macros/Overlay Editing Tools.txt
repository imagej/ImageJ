// hit = 0x01<<i (left, middle, right, top, center, bottom)
// r remarkable points, 6 for image + 6 per ROIs, same order as hit
// dragged roi snaps to guides.

macro "Move Overlay Tool - C037O33aaL03f3L303f" { 
  getCursorLoc( x, y, z, flags ); 
  selectNone( ); 
  r = newArray( 0, getWidth / 2, getWidth, 0, getHeight / 2, getHeight ); 
  n = Overlay.size; 
  if( n < 1 ) exit( "Overlay required" ); 
  id = Overlay.indexAt( x, y ); 
  for( i = 0; i < n; i ++ ) { 
    if( i != id ) { 
      Overlay.getBounds( i, bx, by, bw, bh ); 
      er = newArray( bx, bx + bw / 2, bx + bw, by, by + bh / 2, by + bh ); 
      r = Array.concat( r, er ); 
    } 
  } 
  while( flags & 16 > 0 ) { 
    getCursorLoc( x, y, z, flags ); 
    Overlay.getBounds( id, bx, by, bw, bh ); 
    if( id < 0 ) break; 
    Overlay.moveSelection( id, x - bw / 2, y - bh / 2 ); 
    Overlay.getBounds( id, bx, by, bw, bh ); 
    d = newArray( bx, bx + bw / 2, bx + bw, by, by + bw / 2, by + bh ); 
    while( Overlay.size > n ) Overlay.removeSelection( Overlay.size - 1 ); 
    hit = 0x00; 
    for( i = 0; i < r.length; i = i + 6 ) { // each element
      for( p = 0; p < 6; p ++ ) // each remarkable point
      if( abs( d [ p ]- r [ i + p ] )< 5 ) { 
        hit = hit |( 0x01 << p ); // allows for multiple hits
      } 
      if( hit > 0 ) { 
        ihit = i; 
        break; 
      } 
    } 
    for( i = 0; i < 6; i ++ ) { 
      if( hit &( 1 << i )> 0 ) { 
        if( i < 3 ) { 
          Overlay.getBounds( id, bx, by, bw, bh ); 
          Overlay.drawLine( r [ ihit + i ], 0, r [ ihit + i ], getHeight ); 
          if( i == 0 ) Overlay.moveSelection( id, r [ ihit + i ], by ); 
          if( i == 1 ) Overlay.moveSelection( id, r [ ihit + i ]- bw / 2, by ); 
          if( i == 2 ) Overlay.moveSelection( id, r [ ihit + i ]- bw, by ); 
        } 
        if( i >= 3 ) { 
          Overlay.getBounds( id, bx, by, bw, bh ); 
          Overlay.drawLine( 0, r [ ihit + i ], getWidth, r [ ihit + i ] ); 
          if( i == 3 ) Overlay.moveSelection( id, bx, r [ ihit + i ] ); 
          if( i == 4 ) Overlay.moveSelection( id, bx, r [ ihit + i ]- bh / 2 ); 
          if( i == 5 ) Overlay.moveSelection( id, bx, r [ ihit + i ]- bh ); 
        } 
      } 
    } 
    Overlay.show; 
    wait( 20 ); 
  } 
  while( Overlay.size > n ) Overlay.removeSelection( Overlay.size - 1 ); 
} 
// a tool to select one or more overlay elements
// selection is stored in preferences
// click outside a roi to deselect all
// a menu tool to align selected overlay elements

macro "Select Overlay Tool - Ce00R22fbC0e0o0044C037L77ffL777aL77a7" { 
  getCursorLoc( x, y, z, flags ); 
  Overlay.removeRois( 'ToolSelectedOverlayElement' ); 
  Overlay.show; 
  n = Overlay.size; 
  if( n < 1 ) exit( "Overlay required" ); 
  id = Overlay.indexAt( x, y ); 
  if( id < 0 ) { 
    selectNone( ); 
    call( 'ij.Prefs.set', 'overlaytoolset.selected', '' ); 
    exit( ); 
  } 
  if( flags & 1 > 0 ) { 
    Overlay.activateSelection( id ); 
    if( Roi.getName != 'ToolSelectedOverlayElement' ) 
      selectElement( id, true ); 
  }  else { 
    Overlay.activateSelection( id ); 
    if( Roi.getName != 'ToolSelectedOverlayElement' ) 
      selectElement( id, false ); 
  } 
  run( "Select None" ); 
  highlightSelectedROIs( ); 
} 
function highlightSelectedROIs( ) { 
  Overlay.removeRois( 'ToolSelectedOverlayElement' ); 
  selected = getSelectedElements( ); 
  s = split( selected, ',' ); 
  //print( selected ); 
  for( i = 0; i < s.length; i ++ ) { 
    id = s [ i ]; 
    Overlay.getBounds( id, bx, by, bw, bh ); 
    makeRectangle( bx, by, bw, bh ); 
    Roi.setName( 'ToolSelectedOverlayElement' ); 
    Overlay.addSelection( '#90ff0000', 3 ); 
  } 
  run( "Select None" ); 
  Overlay.show; 
} 
function selectNone( ) { 
  Overlay.removeRois( 'ToolSelectedOverlayElement' ); 
  Overlay.show; 
  //call( 'ij.Prefs.set', 'overlaytoolset.selected', '' ); 
} 
function selectElement( id, add ) { 
  if( add == true ) { 
    selected = getSelectedElements( ); 
    s = split( selected, ',' ); 
    isSelected = false; 
    for( i = 0; i < s.length; i ++ ) { 
      if( 1 * s [ i ]== id ) isSelected = true; 
    } 
    if( ! isSelected ) { 
      call( 'ij.Prefs.set', 'overlaytoolset.selected', selected + ',' + id  ); 
      selected = getSelectedElements( ); 
    } else { 
      unselectElement( id ); 
    } 
  } else { 
    call( 'ij.Prefs.set', 'overlaytoolset.selected',  id  ); 
  } 
  highlightSelectedROIs( ); 
} 
function unselectElement( id ) { 
  selected = getSelectedElements( ); 
  s = split( selected, ',' ); 
  selected = ''; 
  for( i = 0; i < s.length; i ++ ) { 
    if( s [ i ]!= id ) selected = selected +  s [ i ]+ ','; 
  } 
  call( 'ij.Prefs.set', 'overlaytoolset.selected', selected ); 
  selected = getSelectedElements( ); 
  highlightSelectedROIs( ); 
  run( "Select None" ); 
} 
function getSelectedElements( ) { 
  selected = call( 'ij.Prefs.get', 'overlaytoolset.selected', '' ); 
  while( selected.endsWith( "," ) ) selected = substring( selected, 0, lengthOf( selected )- 1 ); 
  while( selected.startsWith( "," ) ) selected = substring( selected, 1, lengthOf( selected ) ); 
  return selected; 
} 
var dCmds = newMenu( "Align Overlay Menu Tool", 
  newArray( "Top", "Middle", "Bottom", "-", "Left", "Center", "Right" ) ); 
// a menu tool to align selected ROIs 
macro "Align Overlay Menu Tool - C037L00f0R2244R8248" { 
  cmd = getArgument( ); 
  if( cmd == "Top" ) { 
    s = getSelectedElements( ); 
    s = split( s, ',' ); 
    if( s.length < 1 ) exit( ); 
    tops = newArray( s.length ); 
    for( i = 0; i < tops.length; i ++ ) { 
      Overlay.getBounds( s [ i ], x, y, w, h ); 
      tops [ i ]= y; 
    } 
    //Array.print( tops ); 
    Array.getStatistics( tops, min, max, mean, stdDev ); 
    for( i = 0; i < tops.length; i ++ ) { 
      Overlay.getBounds( s [ i ], x, y, w, h ); 
      Overlay.moveSelection( s [ i ], x, min ); 
    } 
    Overlay.show( ); 
  } else if( cmd == "Bottom" ) { 
    s = getSelectedElements( ); 
    s = split( s, ',' ); 
    if( s.length < 1 ) exit( ); 
    tops = newArray( s.length ); 
    for( i = 0; i < tops.length; i ++ ) { 
      Overlay.getBounds( s [ i ], x, y, w, h ); 
      tops [ i ]= y + h; 
    } 
    Array.getStatistics( tops, min, max, mean, stdDev ); 
    for( i = 0; i < tops.length; i ++ ) { 
      Overlay.getBounds( s [ i ], x, y, w, h ); 
      Overlay.moveSelection( s [ i ], x, max - h ); 
    } 
    selectNone( ); 
  } else if( cmd == "Left" ) { 
    s = getSelectedElements( ); 
    s = split( s, ',' ); 
    if( s.length < 1 ) exit( ); 
    tops = newArray( s.length ); 
    for( i = 0; i < tops.length; i ++ ) { 
      Overlay.getBounds( s [ i ], x, y, w, h ); 
      tops [ i ]= x; 
    } 
    Array.getStatistics( tops, min, max, mean, stdDev ); 
    for( i = 0; i < tops.length; i ++ ) { 
      Overlay.getBounds( s [ i ], x, y, w, h ); 
      Overlay.moveSelection( s [ i ], min, y ); 
    } 
    selectNone( ); 
  } else if( cmd == "Right" ) { 
    s = getSelectedElements( ); 
    s = split( s, ',' ); 
    if( s.length < 1 ) exit( ); 
    tops = newArray( s.length ); 
    for( i = 0; i < tops.length; i ++ ) { 
      Overlay.getBounds( s [ i ], x, y, w, h ); 
      tops [ i ]= x + w; 
    } 
    Array.getStatistics( tops, min, max, mean, stdDev ); 
    for( i = 0; i < tops.length; i ++ ) { 
      Overlay.getBounds( s [ i ], x, y, w, h ); 
      Overlay.moveSelection( s [ i ], max - w, y ); 
    } 
    selectNone( ); 
  } else if( cmd == "Middle" ) { 
    s = getSelectedElements( ); 
    s = split( s, ',' ); 
    if( s.length < 1 ) exit( ); 
    Overlay.getBounds( s [ 0 ], x, y, w, h ); 
    middle = y + h / 2; 
    for( i = 0; i < s.length; i ++ ) { 
      Overlay.getBounds( s [ i ], x, y, w, h ); 
      Overlay.moveSelection( s [ i ], x, middle - h / 2 ); 
    } 
    selectNone( ); 
  } else if( cmd == "Center" ) { 
    s = getSelectedElements( ); 
    s = split( s, ',' ); 
    if( s.length < 1 ) exit( ); 
    Overlay.getBounds( s [ 0 ], x, y, w, h ); 
    center = x + w / 2; 
    for( i = 0; i < s.length; i ++ ) { 
      Overlay.getBounds( s [ i ], x, y, w, h ); 
      Overlay.moveSelection( s [ i ], center - w / 2, y ); 
    } 
    selectNone( ); 
  } 
  highlightSelectedROIs( ); 
} 
// Click to delete Overlay element
// A confirm dialog is shown
// you can choose not to show it.
macro "Delete Overlay Tool - C037R00ddB58Cd00L0088L0880" { 
  getCursorLoc( x, y, z, flags ); 
  selectNone( ); 
  call( 'ij.Prefs.set', 'overlaytoolset.selected', '' ); 
  id = Overlay.indexAt( x, y ); 
  if( id != - 1 ) { 
    showWarning = call( "ij.Prefs.get", "overlaytoolset.deletewarning", true ); 
    mustDelete = true; 
    if( showWarning == true ) { 
      Dialog.create( "Delete ROI tool options" ); 
      Dialog.addCheckbox( "Delete this ROI", true ); 
      Dialog.addCheckbox( "Show this dialog", showWarning ); 
      Dialog.show( );  
      mustDelete = Dialog.getCheckbox( ); 
      showWarning = Dialog.getCheckbox( ); 
      call( "ij.Prefs.set", "overlaytoolset.deletewarning", showWarning ); 
    } 
    if( mustDelete ) Overlay.removeSelection( id ); 
  } 
} 
macro "Overlay Toolset Help Action Tool - C037T3f18?" { 
  html = "<html>" + 
  "Toolset Description<br>" + 
  "<br><p>* Move overlay tool" + 
  "<br>Click and drag an overlay element;" + 
  "<br>It snaps to alignment guides to other elements.</p>" + 
  "<br><p>* Overlay select tool" + 
  "<br>Click an overlay element to select it." + 
  "<br>Shift-click to add elements to the selection." + 
  "<br>Shift-click a selected element to deselect it.</p>" + 
  "<br><p>* Overlay align tool menu" + 
  "<br>Use this menu to align selected elements</p>" + 
  "<br><p>* Overlay delete tool" + 
  "<br>Click to remove an overlay element</p>" + 
  "<br><p>* Overlay toolset help action tool" + 
  "<br>This dialog. You can leave it open to try toolset functions.</p>" + 
  "</html>"; 
  Dialog.createNonBlocking( "Overlay Toolset Help" ); 
  Dialog.addMessage( html ); 
  Dialog.show( ); 
} 
