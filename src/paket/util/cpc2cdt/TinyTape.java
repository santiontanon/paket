/*
 * This code is a translation from the original cpc2cdt tool by:
 * - César Nicolás González (@CNGSoft)
 * - ronaldo / Fremos / Cheesetea / ByteRealms (@FranGallegoBR)
 * The original source code that I used for translation can be found here:
 *  - https://github.com/mojontwins/MK1_Pestecera/tree/master/src/utils/src/cpc2cdt
 * Translation by Santiago Ontañón
 */
package paket.util.cpc2cdt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// Original usage string:
// TINYTAPE [+]SOURCE.RAW|BIN [+]TARGET.TZX|CDT <0-3> [-]SAMPLE_H <0..255|-1> PAUSE_MS [PAGE_LEN]
//**********************************
//
//Record a raw or AMSDOS binary file
//into a TZX/CDT file using a signal
//that encodes single or double bits
//into either half or full pulses.
//
//**********************************
public class TinyTape {

    static final int DATA_BUFFER_SIZE = 512 * 1024;  // 512k

    InputStream fi = null;
    OutputStream fo = null;
    int bittype;
    int bitsize;
    int bithold;
    int bitbyte;
    int bitgaps = -1;
    int bit_tzx;
    int databytes;
    int lastbyte;
    int polarity;
    int skipHeader = 0;
    byte databyte[] = new byte[DATA_BUFFER_SIZE];
    int bip8;
    int gaps;

    void fputcc(int i, OutputStream f) throws IOException {
        f.write(i & 0xff);
        f.write(i >> 8);
    }

    void write_sample(int i) {
        if (lastbyte >= 256) {
            databyte[databytes++] = (byte) lastbyte;
            lastbyte = 1;
        }
        lastbyte = lastbyte * 2 + i;
    }

    void repeat_write() {
        write_sample(polarity);
    }

    void toggle_write() {
        polarity = 1 - polarity;
        write_sample(polarity);
    }

    void write_byte(int i) {
        if (bit_tzx != 0) {
            databyte[databytes++] = (byte) i;
        } else {
            int j, k, l;
            switch (bittype & 3) {
                case 0: // 2B: single full bit (100%)
                    j = 256;
                    j >>= 1;
                    while (j != 0) {
                        toggle_write();
                        if ((i & j) != 0) {
                            repeat_write();
                            toggle_write();
                            repeat_write();
                        } else {
                            toggle_write();
                        }
                        j >>= 1;
                    }
                    break;
                case 1: // 4B: double full bit (120%)
                    j = 768;
                    k = 8;
                    j >>= 2;
                    while (j != 0) {
                        l = (i & j) >> (k -= 2);
                        toggle_write();
                        l--;
                        if (l != 0) {
                            repeat_write();
                            l--;
                            if (l != 0) {
                                repeat_write();
                                l--;
                                if (l != 0) {
                                    repeat_write();
                                }
                            }
                        } // while (l--) repeat_write();
                        l = (i & j) >> k;
                        toggle_write();
                        l--;
                        if (l != 0) {
                            repeat_write();
                            l--;
                            if (l != 0) {
                                repeat_write();
                                l--;
                                if (l != 0) {
                                    repeat_write();
                                }
                            }
                        } // while (l--) repeat_write();
                        j >>= 2;
                    }
                    break;
                case 2: // 2A: single half bit (200%)
                    j = 256;
                    j >>= 1;
                    while (j != 0) {
                        toggle_write();
                        if ((i & j) != 0) {
                            repeat_write();
                        }
                        j >>= 1;
                    }
                    break;
                case 3: // 4A: double half bit (240%)
                    j = 768;
                    k = 8;
                    j >>= 2;
                    while (j != 0) {
                        l = (i & j) >> (k -= 2);
                        toggle_write();
                        l--;
                        if (l != 0) {
                            repeat_write();
                            l--;
                            if (l != 0) {
                                repeat_write();
                                l--;
                                if (l != 0) {
                                    repeat_write();
                                }
                            }
                        } // while (l--) repeat_write();
                        j >>= 2;
                    }
                    break;
            }
        }
    }

    void creatblock(int j) {
        int i;
        gaps = bitgaps;
        lastbyte = 1;
        databytes = polarity = bip8 = 0;  // BIP-8 // 20160605: bittype&8=XOR8!
        if (bit_tzx == 0) {
            for (i = 1; i < j; i++) {
                write_byte(0xFF); // 0xFF is safe, (bittype&1)?0xAA:0xFF isn't
            }
            write_byte(((bittype & 1) != 0) ? 0xFC : 0xFE); // 0xFC is safe, 0xA8 isn't either!
        }
    }

    void char2block(int i) {
        bip8 ^= i;
        write_byte((bittype & 8) != 0 ? bip8 : i);
        if ((bittype & 8) != 0) {
            bip8 = i;
        }
        --gaps;
        if (gaps == 0) {
            write_byte(((bittype & 1) != 0) ? 0xFC : 0xFE);
            gaps = bitgaps;
        }
    }

    void closeblock(int h) throws IOException {
        write_byte(0xFF ^ bip8); // ~BIP-8
        int lastbits = 8;
        if (bit_tzx != 0) {
            fo.write(0x11);
            fputcc(bitsize * 2, fo);
            fputcc(bitsize, fo);
            fputcc(bitsize, fo);
            fputcc(bitsize, fo);
            fputcc(bitsize * 2, fo);
            fputcc(256 * 16 - 2 - 1, fo);
            fo.write(lastbits);
            fputcc(h, fo);
        } else {
            toggle_write(); // EOF!
            while (lastbyte < 256) {
                lastbits--;
                lastbyte <<= 1;
            }
            databyte[databytes++] = (byte) lastbyte;
            fo.write(0x15);
            fputcc(bitsize, fo);
            fputcc(h, fo);
            fo.write(lastbits);
        }
        fputcc(databytes, fo);
        fo.write(databytes >> 16);
        fo.write(databyte, 0, databytes);
        System.out.println(databytes + " bytes.\n");
    }

    // Bitgaps are the number of bytes per page. At the end of each page
    // a byte (0xFE or 0xFC) is written to act as counter, for counter-enabled loaders.
    // bitgaps = -1 by default, meaning that nothing should be written in-between pages
    void tiny_tape_setBitGaps(int bg) {
        bitgaps = bg;
    }

    // Set for skipping the 128 byte header if the srcfile has
    // skipHeader = 0 by default, meaning no header has to be skipped (srcfile has no header)
    void tiny_tape_setSkipHeader(int sk) {
        skipHeader = sk;
    }

    // srcfile:  Source file (RAW/BIN)
    // tzxfile:  Output file (if it exists, srcfile is appended, else tzxfile is created with srcfile)
    // _bittype: 0 (1 bit, full pulse), 1 (1 bit, half pulse), 2 (2 bits, full pulse), 3 (2 bits, half pulse)
    // _bitsize: (<0) Pulse Lenght in Ts (1T=1/3500000s), (>0) Bits per second
    // _bitbyte: Data block ID (first byte at the start of the blogk). (-1) for no block ID
    // _bithold: Pause in milliseconds
    void tiny_tape_gen(String srcfile, String tzxfile, int _bittype,
            int _bitsize, int _bitbyte, int _bithold) throws FileNotFoundException, IOException {
        int i;
        bittype = _bittype;
        bitsize = _bitsize;
        bitbyte = _bitbyte;
        bithold = _bithold;

        // Calculate blocksize
        File fi_file = new File(srcfile);
        File fo_file = new File(tzxfile);
        int blocksize = (int) fi_file.length();
        fi = new FileInputStream(srcfile);

        // Check if we have to skip header or not, and recalculate block size
        if (skipHeader != 0) {
            skipHeader = 128;
        }
        fi.readNBytes(128);
        blocksize -= 128;

        // Open tzxfile (if file already exist, then add, else create new)
        if (fo_file.exists()) {
            fo = new FileOutputStream(fo_file, true);
        } else {
            fo = new FileOutputStream(fo_file, true);
            fo.write("ZXTape!\032\001\000".getBytes(), 0, 10);
        }

        // Output codification
        // Set Bittzx
        bit_tzx = ((bittype & 7) == 0 ? 1 : 0);  // "4" rather than "0" forces sub-optimal encoding

        // If we have a ID, add 1 byte for it to the blocksize
        if ((bitbyte >= 0) && (bitbyte < 256)) {
            ++blocksize;
        }

        // Fix bitsize and create block
        if (bitsize < 0) {
            bitsize = -bitsize;
        } else {
            bitsize = 3500000 / bitsize;
        }
        creatblock(256);

        // Add the ID, if there is one
        if ((bitbyte >= 0) && (bitbyte < 256)) {
            char2block(bitbyte);
        }

        // Add contents
        while (fi.available() > 0) {
            i = fi.read();
            char2block(i);
        }
        closeblock(bithold);

        // Close files
        fi.close();
        fo.close();
    }
}
