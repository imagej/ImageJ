package ij.io;
import java.io.*;

/**
 * A class for reading arbitrary numbers of bits from an input stream.
 * @author Eric Kjellman egkjellman at wisc.edu
 */
public class BitBuffer {

  private static final int BUFFER_SIZE = 8192;

  private InputStream in;
  private int currentByte;
  private int currentBit;
  private byte[] byteBuffer;
  private int eofByte;
  private int[] backMask;
  private int[] frontMask;
  private boolean eofFlag;

  public BitBuffer(InputStream i) throws IOException {
    byteBuffer = new byte[BUFFER_SIZE];
    in = i;
    currentByte = 0;
    currentBit = 0;
    eofByte = in.read(byteBuffer);
    // System.out.println(" eofByte: " + eofByte);
    eofFlag = false;
    if (eofByte < 1) {
      eofFlag = true;
    }
    backMask = new int[] {0x0000, 0x0001, 0x0003, 0x0007,
                          0x000F, 0x001F, 0x003F, 0x007F};
    frontMask = new int[] {0x0000, 0x0080, 0x00C0, 0x00E0,
                           0x00F0, 0x00F8, 0x00FC, 0x00FE};
  }

  public long skipBits(long bitsToSkip) throws IOException {
    long skipBytes = (long) bitsToSkip / 8;
    long skipBits = bitsToSkip % 8;
    long newByte = currentByte + skipBytes;
    long newBit = currentBit + skipBits;
    long toReturn = bitsToSkip;
    if (newBit > 8) {
      newBit -= 8;
      newByte++;
    }
    if (newByte >= eofByte) {
      // The byte to skip to is out of the current block.
      if (eofByte != BUFFER_SIZE) {
        // meaning yeah, we actually reached the end of the file.
//        System.out.println("1");
        eofFlag = true;
        currentByte = eofByte;
        currentBit = 0;
        toReturn = (8 - currentBit) + 8 * (eofByte - currentByte);
      }
      else {
        // meaning maybe we haven't, but we don't know, so trying to skip the
        // correct number of bytes.
//        System.out.println("2");
        newByte -= BUFFER_SIZE; // need to account for the current buffer.
        long skipped = -1;
        // This part may not suffice. Why would in.skip() fail?
        while(skipped != 0) {
          skipped = in.skip(newByte);
          newByte -= skipped;
        }
        if (newByte != 0) {
          // When we are unable to skip all of the bytes, the
          // file is assumed to be finished.
//          System.out.println("3");
          eofFlag = true;
        }
        else {
          // Otherwise, we have bytes we can still read:
//          System.out.println("4");
          currentByte = 0;
          currentBit = (int) newBit;
          eofByte = in.read(byteBuffer);
        }
      }
    }
    else {
      // The byte to skip to is in the current block, and readable
      currentByte = (int) newByte;
      currentBit = (int) newBit;
    }
    return toReturn;
  }

  public int getBits(int bitsToRead)
    throws IOException, FileNotFoundException
  {
    if (bitsToRead == 0) {
      return 0;
    }
    if (eofFlag) {
      return -1; // Already at end of file
    }
    int toStore = 0;
    while(bitsToRead != 0  && !eofFlag) {
//      System.out.println("byte: " + currentByte + " bit: " + currentBit);
      if (bitsToRead >= 8 - currentBit) {
        if (currentBit == 0) { // special
          toStore = toStore << 8;
          int cb = ((int) byteBuffer[currentByte]);
          toStore += (cb<0 ? (int) 256 + cb : (int) cb);
          bitsToRead -= 8;
          currentByte++;
        }
        else {
          toStore = toStore << (8 - currentBit);
          toStore += ((int) byteBuffer[currentByte]) &
            backMask[8 - currentBit];
          bitsToRead -= (8 - currentBit);
          currentBit = 0;
          currentByte++;
        }
      }
      else {
//        System.out.println(bitsToRead);
        toStore = toStore << bitsToRead;

        int cb = ((int) byteBuffer[currentByte]);
        cb = (cb<0 ? (int) 256 + cb : (int) cb);
        toStore += ((cb) & (0x00FF - frontMask[currentBit])) >>
          (8 - (currentBit + bitsToRead));
//        System.out.println("Byte : " + cb);
//        System.out.println("Mask : " + (0x00FF - frontMask[currentBit] -
//          backMask[8 - (currentBit + bitsToRead)]));
//        System.out.println("Shift: " + (8 - (currentBit + bitsToRead)));
//        System.out.println("Res 1: " + ((cb) & (0x00FF -
//          frontMask[currentBit] - backMask[8 - (currentBit + bitsToRead)])));
//        System.out.println("Res 2: " + (((cb) & (0x00FF -
//          frontMask[currentBit])) >> (8 - (currentBit + bitsToRead))));

        currentBit += bitsToRead;
        bitsToRead = 0;
      }
      if (currentByte == BUFFER_SIZE) {
        eofByte = in.read(byteBuffer);
        currentByte = 0;
      }
      if (currentByte == eofByte) {
        eofFlag = true;
        return toStore;
      }
    }
    return toStore;
  }

}
