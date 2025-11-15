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
import java.util.Arrays;

// ** rewritten in Java by Santiago Ontañón (May 6, 2024):
//    - Added additional flags: -n, and -ip, -nh
// ** reescrito en C (antes Pascal) por CNGSOFT el Domingo 7 de Agosto de 2016 por la noche **
public class CPC2CDT {

    /*
    Information about headers
    AMSDOS - 128-BYTE HEADER
       16-BIT CHECKSUM [0..$42] = W[$43]
       TYPE = B[$12]
       LOAD = W[$15]
       SIZE = W[$18], W[$40]
       BOOT = W[$1A]
    PLUS3DOS - 128-BYTE HEADER
       8-BIT CHECKSUM [0..$7E] = [$7F]
       SIZE = W[$10], W[$14]

    AMSTRAD - 28-BYTE HEADER (FLAG $2C)
       CHAR[16] : FILENAME PADDED WITH $00
       BYTE : BLOCK.ID (1,2,3...)
       BYTE : LASTBLOCK?$FF:$00
       BYTE : TYPE
       WORD : BLOCK SIZE ($0800)
       WORD : START+PAST BLOCKS
       BYTE : FIRSTBLOCK?$FF:$00
       WORD : LENGTH
       WORD : BOOT
    SPECTRUM - 17-BYTE HEADER (FLAG $00)
       BYTE : TYPE
       CHAR[10] : FILENAME PADDED WITH $20
       WORD : START
       WORD : LENGTH
       WORD : LENGTH (?)
     */
    static final int FT_Basic = 0;
    static final int FT_protected = 1;
    static final int FT_binary = 2;
    static final int FT_ascii = 0x16;
    static final int FT_none = -1;

    static final int TM_cpc = 0;
    static final int TM_cpcraw = 1;
    static final int TM_zx = 2;
    static final int TM_zxraw = 3;
    static final int TM_raw1full = 4;
    static final int TM_raw1half = 5;
    static final int TM_raw2full = 6;
    static final int TM_raw2half = 7;
    static final int TM_cpctxt = 8;
    static final int TM_ntotalModes = 9;
    public static int inter_block_pause = 1000;

    byte body[] = new byte[64 * 1024];
    byte head[] = new byte[256];
    InputStream fi = null;
    OutputStream fo = null;
    String si = null, so = null, sn = null;
    String mode = null;
    int flag_n = 0;
    int flag_b = 1000;
    int flag_bb;
    int flag_m = 0;
    int flag_i = 255;
    int flag_o = 4096;
    boolean flag_t = false;
    int flag_h = 2560;
    int flag_initial_pause = 0;  // initial pause
    int flag_p = 10240;  // pause after the final block
    int flag_z = 4;
    int filetype = 0;
    int filesize = 0;
    int fileload = -1;
    int fileboot = -1;
    int pulselength = -1;
    int bitspersec = -1;
    int bytesperpage = -1;
    int bitsize;
    boolean detectedHeader = false;
    boolean blankBeforeUse = false;
    boolean tryToDetectHeader = true;
    TinyTape tinyTape = new TinyTape();

    void create11(int filetype_override) {
        for (int i = 0; i < 16; i++) {
            head[i] = 0;
        }
        if (sn != null) {
            for (int i = 0; i < sn.length(); i++) {
                head[i] = (byte) sn.charAt(i);
            }
        }
        if (filetype_override == FT_none) {
            head[0x12] = (byte) filetype;
        } else {
            head[0x12] = (byte) filetype_override;
        }
        head[0x18] = (byte) (filesize % 256);
        head[0x19] = (byte) (filesize >> 8);
        head[0x1A] = (byte) (fileboot % 256);
        head[0x1B] = (byte) (fileboot >> 8);
    }

    void update11(int n, int is1st, int islast, int l) {
        head[0x10] = (byte) n;
        head[0x11] = (byte) (islast != 0 ? -1 : 0);
        head[0x13] = (byte) l;
        head[0x14] = (byte) (l >> 8);
        head[0x15] = (byte) fileload;
        head[0x16] = (byte) (fileload >> 8);
        head[0x17] = (byte) (is1st != 0 ? -1 : 0);
    }

    void fputcc(int i, OutputStream f) throws IOException {
        f.write(i & 0xff);
        f.write(i >> 8);
    }

    void fputccc(int i, OutputStream f) throws IOException {
        f.write(i & 0xff);
        f.write((i >> 8) & 0xff);
        f.write(i >> 16);
    }

    // Standard Speed Data Block (as in TAP files)
    void record10(byte t[], int first, int l, int p) throws IOException {
        fo.write(0x10);
        fputcc(p, fo);
        fputcc(l + 2, fo);
        fo.write(first);
        fo.write(t, 0, l);
        int i = first, j = 0;
        while (j < l) {
            i ^= t[j++];
        }
        fo.write(i);
    }

    // Turbo Loading Data Block
    void record11(byte t[], int t_offset, int first, int l, int p) throws IOException {
        fo.write(0x11);
        fputcc(flag_bb, fo);
        fputcc(flag_b, fo);
        fputcc(flag_b, fo);
        fputcc(flag_b, fo);
        fputcc(flag_bb, fo);
        fputcc(flag_o, fo);
        fo.write(8);
        fputcc(p, fo);
        p = 1 + (((l + 255) / 256) * 258) + flag_z;
        fputccc(p, fo);
        fo.write(first);
        p = 0;
        while (l > 0) {
            fo.write(t, t_offset + p, 256);
            int crc16 = 0xffff;
            first = 256;
            while (first != 0) {  // early CRC-16-CCITT as used by Amstrad
                first--;
                int xor8 = (t[t_offset + p] << 8) + 1;
                p++;
                while ((xor8 & 0xff) != 0) {
                    {
                        if (((xor8 ^ crc16) & 0x8000) != 0) {
                            crc16 = ((crc16 ^ 0x0810) << 1) + 1;
                        } else {
                            crc16 <<= 1;
                        }
                        xor8 <<= 1;
                    }
                }
            }
            crc16 = ~crc16;
            fo.write(crc16 >> 8); // HI FIRST,
            fo.write(crc16); // AND LO NEXT!
            l -= 256;
        }
        l = flag_z;
        while (l != 0) {
            l--;
            fo.write(255);
        }
    }
    
    // Pause (Silence) or "Stop the Tape" Command
    void record20(int pause) throws IOException
    {
        fo.write(0x20);
        fputcc(pause, fo);        
    }

    void usage() {
        System.out.println("Usage: CPC2CDT [option..] infile outfile"
                + "\n   -n  Blank CDT before use"
                + "\n   -r  FILE name to record on tape, unnamed file if missing"
                + "\n   -t       record CPC file as a standard 2k block and a giant block"
                + "\n   -m  N    mode: one of these { cpc cpcraw cpctxt zx zxraw raw1full raw1half raw2full raw2half }"
                + "\n             / cpc:      Standard CPC File (basic/binary) with/adding AMSDOS header."
                + "\n             | cpctxt:   ASCII text file with CRLF line endings, adding AMSDOS header."
                + "\n    CPC2CDT  | cpcraw:   CPC File without AMSDOS header."
                + "\n      MODES  | zx:       Standard ZX spectrum file with PLUS3DOS header."
                + "\n             \\ zxraw:    ZX spectrum file without PLUS3DOS header."
                + "\n             / raw1full: RAW data codified as 1 bit per each full pulse (2 pulses) (standard)"
                + "\n       TINY  | raw1half: RAW data codified as 1 bit per each half pulse (1 pulse)"
                + "\n      MODES  | raw2full: RAW data codified as 2 bits per each full pulse (2 pulses)"
                + "\n             \\ raw2half: RAW data codified as 2 bits per each half pulse (1 pulse)"
                + "\n   -b  N    baud rate for CPC blocks (1000)"
                + "\n   -i  N    ID byte for raw blocks (Default -1 = No ID)"
                + "\n   -o  N    number of pilot pulses for CPC blocks (Default 4096)"
                + "\n   -z  N    length of CPC block trailing tone in bytes (Default 4)"
                + "\n   -h  N    pause between CPC file blocks, in milliseconds (Default 2560)"
                + "\n   -ip  N   initial pause, in milliseconds (Default 0, only affects CPC2CDT modes)"
                + "\n   -p  N    pause after the final block, in milliseconds (Default 10240)"
                + "\n   -l  N    load address (Default 16384)"
                + "\n   -x  N    run/execute address (Default 16384)"
                + "\n   -nh      skip AMSDOS header detection (useful, since some binary files are detected as having a header by mistake))"
                + "\n"
                + "\nSPECIFIC OPTIONS FOR TINY MODES"
                + "\n   -rl N    length of a single pulse (half) in Ts (1T=1/3500000s)"
                + "\n   -rb N    cadence in bits per second (ignored if -rp is set)"
                + "\n   -rp N    bytes per page. It adds an extra byte afer each page (0xFC/0xFE), for counter-enabled loaders"
                + "\n"
        );
    }

    int hexstr2int(String value, int max) throws Exception {
        int v = 0;
        int val_idx = 2;
        while (val_idx < value.length()) {
            v *= 16;
            if (value.charAt(val_idx) >= '0' && value.charAt(val_idx) <= '9') {
                v += value.charAt(val_idx) - '0';
            } else if (value.charAt(val_idx) >= 'A' && value.charAt(val_idx) <= 'F') {
                v += value.charAt(val_idx) - 'A' + 10;
            } else if (value.charAt(val_idx) >= 'a' && value.charAt(val_idx) <= 'f') {
                v += value.charAt(val_idx) - 'a' + 10;
            } else {
                throw new Exception("ERROR: Incorrectly formatted hexadecimal value '" + value + "'\n");
            }
            ++val_idx;
        }
        // Check maximum 
        if (v > max) {
            throw new Exception("ERROR: Hexadecimal value out of range '" + value + "' (max: " + max + ")\n");
        }

        return v;
    }

    int str2int(String value, int max) throws Exception {
        int v = Integer.parseInt(value);
        if (v > max) {
            throw new Exception("ERROR: Integer value out of range '" + value + "' (max: " + max + ")\n");
        }
        return v;
    }

    boolean hasHexPrefix(String value) {
        return value.startsWith("0x");
    }

    boolean isDecimal(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    int get16bitValue(String value) throws Exception {
        if (hasHexPrefix(value)) {
            return hexstr2int(value, 0xFFFF);
        } else if (isDecimal(value)) {
            return str2int(value, 0xFFFF);
        }

        throw new Exception("ERROR: Expected decimal/hexadecimal number but found '" + value + "'\n");
    }

    int str2conversionMode(String _mode) throws Exception {
        String modes[] = {
            "cpc", "cpcraw",
            "zx", "zxraw",
            "raw1full", "raw1half",
            "raw2full", "raw2half",
            "cpctxt"
        };
        mode = _mode.toLowerCase();
        for (int i = 0; i < modes.length; i++) {
            if (mode.equals(modes[i])) {
                return i;
            }
        }
        throw new Exception("ERROR: Expected mode but found '%s'. Valid modes are: { " + Arrays.toString(modes) + " }\n");
    }

    void parseCommandLineArgs(String argv[]) throws Exception {
        for (int i = 0; i < argv.length; ++i) {
            if (argv[i].equals("-n")) {
                blankBeforeUse = true;
            } else if (argv[i].equals("-b")) {
                if (i + 1 < argv.length) {
                    ++i;
                } else {
                    throw new Exception("ERROR: Flag '-b' requires a value\n");
                }
                flag_b = get16bitValue(argv[i]);
            } else if (argv[i].equals("-m")) {
                if (i + 1 < argv.length) {
                    ++i;
                } else {
                    throw new Exception("ERROR: Flag '-m' requires a value\n");
                }
                flag_m = str2conversionMode(argv[i]);
            } else if (argv[i].equals("-i")) {
                if (i + 1 < argv.length) {
                    ++i;
                } else {
                    throw new Exception("ERROR: Flag '-i' requires a value\n");
                }
                flag_i = get16bitValue(argv[i]);
            } else if (argv[i].equals("-o")) {
                if (i + 1 < argv.length) {
                    ++i;
                } else {
                    throw new Exception("ERROR: Flag '-o' requires a value\n");
                }
                flag_o = get16bitValue(argv[i]);

            } else if (argv[i].equals("-t")) {
                flag_t = true;
            } else if (argv[i].equals("-r")) {
                if (i + 1 < argv.length) {
                    ++i;
                } else {
                    throw new Exception("ERROR: Flag '-r' requires a value\n");
                }
                sn = argv[i];
            } else if (argv[i].equals("-h")) {
                if (i + 1 < argv.length) {
                    ++i;
                } else {
                    throw new Exception("ERROR: Flag '-h' requires a value\n");
                }
                flag_h = get16bitValue(argv[i]);
            } else if (argv[i].equals("-p")) {
                if (i + 1 < argv.length) {
                    ++i;
                } else {
                    throw new Exception("ERROR: Flag '-p' requires a value\n");
                }
                flag_p = get16bitValue(argv[i]);
            } else if (argv[i].equals("-ip")) {
                if (i + 1 < argv.length) {
                    ++i;
                } else {
                    throw new Exception("ERROR: Flag '-ip' requires a value\n");
                }
                flag_initial_pause = get16bitValue(argv[i]);
            } else if (argv[i].equals("-z")) {
                if (i + 1 < argv.length) {
                    ++i;
                } else {
                    throw new Exception("ERROR: Flag '-z' requires a value\n");
                }
                flag_z = get16bitValue(argv[i]);
            } else if (argv[i].equals("-l")) {
                if (i + 1 < argv.length) {
                    ++i;
                } else {
                    throw new Exception("ERROR: Flag '-l' requires a value\n");
                }
                fileload = get16bitValue(argv[i]);
            } else if (argv[i].equals("-x")) {
                if (i + 1 < argv.length) {
                    ++i;
                } else {
                    throw new Exception("ERROR: Flag '-x' requires a value\n");
                }
                fileboot = get16bitValue(argv[i]);
            } else if (argv[i].equals("-rl")) {
                if (i + 1 < argv.length) {
                    ++i;
                } else {
                    throw new Exception("ERROR: Flag '-rl' requires a value\n");
                }
                pulselength = get16bitValue(argv[i]);
            } else if (argv[i].equals("-rb")) {
                if (i + 1 < argv.length) {
                    ++i;
                } else {
                    throw new Exception("ERROR: Flag '-rb' requires a value\n");
                }
                bitspersec = get16bitValue(argv[i]);
            } else if (argv[i].equals("-rp")) {
                if (i + 1 < argv.length) {
                    ++i;
                } else {
                    throw new Exception("ERROR: Flag '-rp' requires a value\n");
                }
                bytesperpage = get16bitValue(argv[i]);
            } else if (argv[i].equals("-nh")) {
                tryToDetectHeader = false;
            } else if (si == null) {
                si = argv[i];
            } else if (so == null) {
                so = argv[i];
            } else {
                throw new Exception("ERROR: Unexpected parameter '" + argv[i] + "'\n");
            }
        }
    }
    
    int unsignedBodyByte(int position)
    {
        if (body[position] < 0) return ((int)body[position]) + 256;
        return body[position];
    }

    void detectHeaderInInputFile() throws IOException, Exception {
        // Open and process input file
        fi = new FileInputStream(si);

        // READ HEADER AND DETECT FORMAT
        for (int i = 0; i < 256; i++) {
            head[i] = 0;
        }
        for (int i = 0; i < 64 * 1024; i++) {
            body[i] = 0;
        }
        filesize = fi.readNBytes(body, 0, 128);
        if (filesize == 128 && tryToDetectHeader) {
            // Prevent detection of sequence of 128 zeros as an AMSDOS header
            int j = 0;
            for (int i = 0; i < 128; ++i) {
                j += body[i];
            }
            // j must be not 0 for this to be recognized as a valid AMSDOS header
            if (j != 0) {
                int i = j = 0;
                while (i < 0x43) {
                    j += body[i++];
                }
                if ((unsignedBodyByte(0x43) + 256 * unsignedBodyByte(0x44)) == j) { // AMSDOS!
                    filetype = body[0x12];
                    filesize = unsignedBodyByte(0x40) + unsignedBodyByte(0x41) * 256;
                    fileload = unsignedBodyByte(0x15) + unsignedBodyByte(0x16) * 256;
                    fileboot = unsignedBodyByte(0x1A) + unsignedBodyByte(0x1B) * 256;
                    if (fi.read(body, 0, filesize) < 1) {
                        throw new Exception("ERROR: short read while reading AMSDOS header");
                    }
                    detectedHeader = true;
                } else {
                    while (i < 0x7F) {
                        j += body[i++];
                    }
                    if ((j & 0xFF) == body[0x7F]) { // PLUS3DOS!
                        filetype = body[0x0F];
                        filesize = unsignedBodyByte(0x10) + unsignedBodyByte(0x11) * 256;
                        fileload = unsignedBodyByte(0x12) + unsignedBodyByte(0x13) * 256;
                        fileboot = unsignedBodyByte(0x14) + unsignedBodyByte(0x15) * 256;
                        if (fi.read(body, 0, filesize) < 1) {
                            throw new Exception("ERROR: short read while reading PLUS3DOS header");
                        }
                        detectedHeader = true;
                    }
                }
            }
        }
        // If not AMSDOS, nor PLUS3DOS, consider it RAW binary
        if (!detectedHeader) {
            filesize += fi.readNBytes(body, filesize, (1 << 16) - filesize);
            filetype = FT_binary;
            if (fileboot < 0) {
                fileboot = 0x4000;
            }
            if (fileload < 0) {
                fileload = 0x4000;
            }
        }

        fi.close();
    }

    ///
    /// Writes a basic/binary or ascii file to the tape
    /// using CPC firmware blocks
    ///
    void writeCPCFile(int mode) throws IOException {
        // Set correct filetype
        int ft = FT_none;
        if (mode == TM_cpctxt) {
            ft = FT_ascii;
        }

        // Create header
        create11(ft);

        // Create tape blocks
        if (filesize > 0x800) {
            int j = 1;
            update11(j, 1, 0, 0x800); // FIRST BLOCK
            record11(head, 0, 44, 28, 16);
            record11(body, 0, 22, 0x800, flag_h);
            int k = filesize - 0x800;
            int i = 0x800;
            if (!flag_t) {
                while (k > 0x800) {
                    fileload += 0x800;
                    update11(++j, 0, 0, 0x800); // MID BLOCK
                    record11(head, 0, 44, 28, 16);
                    record11(body, i, 22, 0x800, flag_h);
                    k -= 0x800;
                    i += 0x800;
                }
            }
            fileload += 0x800;
            update11(++j, 0, 1, k); // LAST BLOCK
            record11(head, 0, 44, 28, 16);
            record11(body, i, 22, k, flag_p);
        } else {
            update11(1, 1, 1, filesize); // SINGLE BLOCK
            record11(head, 0, 44, 28, 16);
            record11(body, 0, 22, filesize, flag_p);
        }
    }

    void cpc2cdt_modes() throws FileNotFoundException, IOException {
        // Set up bauds
        if (flag_b > 0) {
            flag_b = (3500000 / 3 + flag_b / 2) / flag_b;
        } else {
            flag_b = -flag_b;
        }
        flag_bb = flag_b * 2;

        // Open and process output file (CDT)
        File fo_file = new File(so);
        if (fo_file.exists() && !blankBeforeUse) {
            // File already exists. Open for append mode
            fo = new FileOutputStream(so, true);
        } else {
            // File does not exist, create a new one
            fo = new FileOutputStream(so);
            System.out.println(Arrays.toString("ZXTape!\032\001\000\040\000\012".getBytes()));
            fo.write("ZXTape!\032\001\000\040\000\012".getBytes(), 0, 13);
            
            if (flag_initial_pause > 0) {
                record20(flag_initial_pause);
            }
        }

        // Process file depending on selected mode
        switch (flag_m) {
            //----------------------------------------------------------
            case TM_cpc:
            case TM_cpctxt:
                writeCPCFile(flag_m);
                break;
            //----------------------------------------------------------
            case TM_cpcraw:
                record11(body, 0, flag_i, filesize, flag_p);
                break;
            //----------------------------------------------------------
            case TM_zx:
                head[0] = (byte) filetype;
                for (int i = 0; i < 10; i++) {
                    head[i + 1] = 32;
                }
                if (sn != null) {
                    for (int i = 0; i < sn.length(); i++) {
                        head[i + 1] = (byte) sn.charAt(i);
                    }
                }
                //strcpy(head,sn);
                head[11] = (byte) (filesize % 256);
                head[12] = (byte) (filesize >> 8);
                head[13] = (byte) (fileload % 256);
                head[14] = (byte) (fileload >> 8);
                head[15] = (byte) (fileboot % 256);
                head[16] = (byte) (fileboot >> 8);
                record10(head, 0, 17, inter_block_pause);
                record10(body, 255, filesize, flag_p);
                break;
            //----------------------------------------------------------
            case TM_zxraw:
                record10(body, flag_i, filesize, flag_p);
                break;
        }
        fo.close();
    }

    public int mainInternal(String argv[]) throws Exception {
        // Parse arguments
        parseCommandLineArgs(argv);
        if (so == null) {
            usage();
        }

        // Detect header in input file
        detectHeaderInInputFile();

        // Process depending on mode
        switch (flag_m) {
            case TM_cpc:
            case TM_cpctxt:
            case TM_cpcraw:
            case TM_zxraw:
            case TM_zx: {
                // CPC2CDT Modes
                cpc2cdt_modes();
                break;
            }
            default: {
                // TINYTAPE Modes

                // Convert ID value to negative if it has not been set
                if (flag_i == 255) {
                    flag_i = -1;
                }

                // Pulselength or bitspersec must be set for tiny tape
                if (pulselength > 0) {
                    bitsize = -pulselength;
                } else if (bitspersec > 0) {
                    bitsize = bitspersec;
                } else {
                    throw new Exception("ERROR: Mode '" + mode + "' requires '-rp' or '-rb' to be set.\n");
                }

                // Set Bitgaps
                // Bitgaps are the number of bytes per page. At the end of each page
                // a byte (0xFE or 0xFC) is written to act as counter, for counter-enabled loaders.
                // bitgaps = -1 by default, meaning that nothing should be written in-between pages
                tinyTape.tiny_tape_setBitGaps(bytesperpage);

                // If there is a header, be it AMSDOS or PLUS3DOS, skip it
                tinyTape.tiny_tape_setSkipHeader(detectedHeader ? 1 : 0);

                // Generate tape with Tiny Modes. Parameters:
                // 	 srcfile:  Source file (RAW/BIN)
                // 	 tzxfile:  Output file (if it exists, srcfile is appended, else tzxfile is created with srcfile)
                // 	 _bittype: 0 (1 bit, full pulse), 1 (1 bit, half pulse), 2 (2 bits, full pulse), 3 (2 bits, half pulse)
                // 	 _bitsize: (<0) Pulse Lenght in Ts, (>0) Bits per second
                // 	 _bitbyte: Data block ID (first byte at the start of the blogk). (-1) for no block ID
                // 	 _bithold: Pause in milliseconds
                //void tiny_tape_gen(	const char* srcfile, const char* tzxfile, int _bittype
                //				, int _bitsize, int _bitbyte, int _bithold) {
                tinyTape.tiny_tape_gen(si, so, flag_m - 4, bitsize, flag_i, flag_h);
            }
        }

        return 0;
    }

    public static int main(String argv[]) throws Exception {
        CPC2CDT cpc2cdt = new CPC2CDT();
        return cpc2cdt.mainInternal(argv);
    }
}
