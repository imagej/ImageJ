package ij.plugin;
import ij.*;
import ij.io.*;
import ij.gui.*;
import ij.process.*;
import java.io.*;
import java.awt.*;

/*	ImageJ/NIH Image 64 byte ROI outline header
	2 byte numbers are big-endian signed shorts
	
	0-3		"Iout"
	4-5		version (>=217)
	6-7		roi type
	8-9		top
	10-11	left
	12-13	bottom
	14-15	right
	16-17	NCoordinate
	18-33	x1,y1,x2,y2 (float, unused)
	34-35	line width (unused)
	36-63	reserved (zero)
*/


/** Opens ImageJ, NIH Image and Scion Image for windows ROI outlines. */
public class RoiReader implements PlugIn {
	final int polygon=0, rect=1, oval=2, line=3,freeLine=4, segLine=5, noRoi=6,freehand=7, traced=8;
	byte[] data;

	public void run(String arg) {
		OpenDialog od = new OpenDialog("Open ROI...", arg);
		String dir = od.getDirectory();
		String name = od.getFileName();
		if (name==null)
			return;
		try {
			openRoi(dir, name);
		} catch (IOException e) {
			String msg = e.getMessage();
			if (msg==null || msg.equals(""))
				msg = ""+e;
			IJ.showMessage("ROI Reader", msg);
		}
	}

	public void openRoi(String dir, String name) throws IOException {
		String path = dir+name;
		File f = new File(path);
		int size = (int)f.length();
		if (size>5000)
			throw new IOException("This is not an ImageJ ROI");
		FileInputStream fis = new FileInputStream(path);
		data = new byte[size];

		int total = 0;
		while (total<size)
			total += fis.read(data, total, size-total);
		if (getByte(0)!=73 || getByte(1)!=111)  //"Iout"
			throw new IOException("This is not an ImageJ ROI");
		int type = getByte(6);
		int top= getShort(8);
		int left = getShort(10);
		int bottom = getShort(12);
		int right = getShort(14);
		int width = right-left;
		int height = bottom-top;
		int n = getShort(16);
		ImagePlus img = WindowManager.getCurrentImage();
		if (img==null || img.getWidth()<(left+width) || img.getHeight()<(top+height)) {
			ImageProcessor ip =  new ByteProcessor(left+width+10, top+height+10);
			ip.setColor(Color.white);
			ip.fill();
			img = new ImagePlus(name, ip);
			img.show();
		}

		Roi roi = null;
		switch (type) {
		case rect:
			img.setRoi(left, top, width, height);
			break;
		case oval:
			roi = new OvalRoi(left, top, width, height, img);
			img.setRoi(roi);
			break;
		case line:
			int x1 = (int)getFloat(18);		
			int y1 = (int)getFloat(22);		
			int x2 = (int)getFloat(26);		
			int y2 = (int)getFloat(30);		
			//IJ.write("line roi: "+x1+" "+y1+" "+x2+" "+y2);
			break;
		case polygon: case freehand: case traced:
				//IJ.write("type: "+type);
				//IJ.write("n: "+n);
				//IJ.write("rect: "+left+","+top+" "+width+" "+height);
				if (n==0) break;
				int[] x = new int[n];
				int[] y = new int[n];
				int base1 = 64;
				int base2 = base1+2*n;
				int xtmp, ytmp;
				for (int i=0; i<n; i++) {
					xtmp = getShort(base1+i*2);
					if (xtmp<0) xtmp = 0;
					ytmp = getShort(base2+i*2);
					if (ytmp<0) ytmp = 0;
					x[i] = left+xtmp;
					y[i] = top+ytmp;
					//IJ.write(i+" "+getShort(base1+i*2)+" "+getShort(base2+i*2));
				}
				int roiType;
				if (type==polygon)
					roiType = Roi.POLYGON;
				else if (type==freehand)
					roiType = Roi.FREEROI;
				else
					roiType = Roi.TRACED_ROI;
				roi = new PolygonRoi(x, y, n, img, roiType);
				img.setRoi(roi);
				break;
		default:
		}
	}

	int getByte(int base) {
		return data[base]&255;
	}

	int getShort(int base) {
		int b0 = data[base]&255;
		int b1 = data[base+1]&255;
		return (short)((b0<<8) + b1);		
	}
	
	int getInt(int base) {
		int b0 = data[base]&255;
		int b1 = data[base+1]&255;
		int b2 = data[base+2]&255;
		int b3 = data[base+3]&255;
		return ((b0<<24) + (b1<<16) + (b2<<8) + b3);
	}

	float getFloat(int base) {
		return Float.intBitsToFloat(getInt(base));
	}

}

/*
		RoiTypeType = (PolygonRoi, RectRoi, OvalRoi, LineRoi, FreeLineRoi, SegLineRoi, NoRoi, FreehandRoi, TracedRoi);

				RoiHeader = record
				rID: packed array[1..4] of char;  {4   4}
				rVersion: integer;                              {2   6}
				rRoiType: RoiTypeType;                    {2   8}
				rRoiRect: rect;                                   {8   16}
				rNCoordinates: integer;                     {2   18}
				rX1, rY1, rX2, rY2: real;                 {16 34}
				rLineWidth: integer;                          {2   36}
				rUnused: array[1..14] of integer;    {28 64}
			end;

	
			procedure SaveOutline (fname: str255; RefNum: integer);
		var
			err: integer;
			TheInfo: FInfo;
			i, f: integer;
			ByteCount, DataSize: LongInt;
			hdr: RoiHeader;
			SaveCoordinates: boolean;
			dX1, dY1, dX2, dY2: extended;
	begin
		with info^ do begin
				if not RoiShowing then begin
						PutError('No outline available to save.');
						exit(SaveOutline);
					end;
				if (RoiType = FreeLineRoi) or (RoiType = SegLineRoi) then begin
						PutError('Freehand and segmented line selections cannot be saved.');
						exit(SaveOutline);
					end;
				SaveCoordinates := (RoiType = PolygonRoi) or (RoiType = FreehandRoi) or (RoiType = TracedRoi);
				if SaveCoordinates then
					if not CoordinatesAvailableMsg then begin
							exit(SaveOutline);
						end;
				err := GetFInfo(fname, RefNum, TheInfo);
				case err of
					NoErr: 
						if TheInfo.fdType <> 'Iout' then begin
								TypeMismatch(fname);
								exit(SaveOutline)
							end;
					FNFerr:  begin
							err := create(fname, RefNum, 'Imag', 'Iout');
							if CheckIO(err) <> 0 then
								exit(SaveOutline);
						end;
					otherwise
						if CheckIO(err) <> 0 then
							exit(SaveOutline);
				end;
				with hdr do begin
						rID := 'Iout';
						rVersion := version;
						rRoiType := RoiType;
						rRoiRect := RoiRect;
						rNCoordinates := nCoordinates;
						GetLoi(dX1, dY1, dX2, dY2);
						rX1:=dX1; rY1:=dY1; rX2:=dX2; rY2:=dY2;
						rLineWidth := LineWidth;
						for i := 1 to 14 do
							rUnused[i] := 0;
					end;
				err := fsopen(fname, RefNum, f);
				if CheckIO(err) <> 0 then
					exit(SaveOutline);
				err := SetFPos(f, FSFromStart, 0);
				ByteCount := SizeOf(RoiHeader);
				if ByteCount <> 64 then
					PutError('Roi header size <> 32.');
				err := fswrite(f, ByteCount, @hdr);
				if SaveCoordinates then begin
						ByteCount := nCoordinates * 2;
						err := fswrite(f, ByteCount, ptr(xCoordinates));
						ByteCount := nCoordinates * 2;
						err := fswrite(f, ByteCount, ptr(yCoordinates));
						DataSize := nCoordinates * 4;
					end
				else
					DataSize := 0;
				if CheckIO(err) <> 0 then begin
						err := fsclose(f);
						err := FSDelete(fname, RefNum);
						exit(SaveOutline)
					end;
				err := SetEOF(f, SizeOf(RoiHeader) + DataSize);
				err := fsclose(f);
				err := GetFInfo(fname, RefNum, TheInfo);
				if TheInfo.fdCreator <> 'Imag' then begin
						TheInfo.fdCreator := 'Imag';
						err := SetFInfo(fname, RefNum, TheInfo);
					end;
				err := FlushVol(nil, RefNum);
			end; {with info^}
	end;


	procedure OpenOutline (fname: str255; RefNum: integer);
		var
			err, f, i: integer;
			count: LongInt;
			hdr: RoiHeader;
			okay: boolean;
	begin
		if Info = NoInfo then begin
				if (NewPicWidth * NewPicHeight) <= UndoBufSize then begin
						if not NewPicWindow('Untitled', NewPicWidth, NewPicHeight) then
							exit(OpenOutline)
					end
				else begin
						beep;
						exit(OpenOutline)
					end;
			end;
		KillRoi;
		err := fsopen(fname, RefNum, f);
		with info^, hdr do begin
				count := SizeOf(RoiHeader);
				err := fsread(f, count, @hdr);
				if rID <> 'Iout' then begin
						err := fsclose(f);
						PutError('File is corrupted.');
						exit(OpenOutline)
					end;
				if (rRoiRect.right > PicRect.right) or (rRoiRect.bottom > PicRect.bottom) then begin
						err := fsclose(f);
						PutError('Image is too small for the outline.');
						exit(OpenOutline)
					end;
				case rRoiType of
					LineRoi:  begin
							LX1 := rX1;
							LY1 := rY1;
							LX2 := rX2;
							LY2 := rY2;
							RoiType := LineRoi;
							MakeRegion;
							SetupUndo;
							RoiShowing := true;
						end;
					RectRoi, OvalRoi:  begin
							RoiType := rRoiType;
							RoiRect := rRoiRect;
							MakeRegion;
							SetupUndo;
							RoiShowing := true;
						end;
					PolygonRoi, FreehandRoi, TracedRoi: 
						if (rNCoordinates > 2) and (rNCoordinates <= MaxCoordinates) then begin
								count := rNCoordinates * 2;
								err := fsread(f, count, ptr(xCoordinates));
								count := rNCoordinates * 2;
								err := fsread(f, count, ptr(yCoordinates));
								if CheckIO(err) = 0 then begin
										nCoordinates := rNCoordinates;
										SelectionMode := NewSelection;
										if rVersion >= 148 then
											for i := 1 to nCoordinates do
												with rRoiRect do begin
														xCoordinates^[i] := xCoordinates^[i] + left;
														yCoordinates^[i] := yCoordinates^[i] + top;
													end;
										MakeOutline(rRoiType);
										SetupUndo;
									end;
							end;
				end;
			end;
		err := fsclose(f);
	end;
*/


