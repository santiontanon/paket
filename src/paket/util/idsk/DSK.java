/*
 * This code is a translation from the original cpc2cdt tool by:
 * - cpcemu - Marco Vieth
 * - manageDSK - Ludovic Deplanque
 * - iDSK - Sid from IMPACT / PulkoMandy from the Shinra Team
 * The original source code that I used for translation can be found here:
 *  - https://github.com/jeromelesaux/idsk
 * Translation by Santiago Ontañón
 */
package paket.util.idsk;

// - rewritten in Java by Santiago Ontañón (May 9, 2024):
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import paket.compiler.PAKETConfig;

//   - Only those flags and functionalities needed by the PAKET engine were
//     translated.
/**
 *
 * @author santi
 */
public class DSK {

    public static final int USER_DELETED = 0xe5;
    public static final int SECTSIZE = 512;

    public static final int MODE_ASCII = 0;
    public static final int MODE_BINAIRE = 1;

    public static final int ERR_NO_ERR = 0;
    public static final int ERR_NO_DIRENTRY = 1;
    public static final int ERR_NO_BLOCK = 2;
    public static final int ERR_FILE_EXIST = 3;

    int[] DSKImage = new int[0x80000];
    int[] bitmap = new int[256];
    
    PAKETConfig config;

    
    public DSK(PAKETConfig a_config) {
        config = a_config;
    }    
    

    //
    // Write an AMSDOS block (1 block = 2 sectors).
    //
    public void writeBlock(int block, int data[], int offset) {
        config.debug("writeBlock: " + block);
        int track = ( block << 1 ) / 9;
        int sect = ( block << 1 ) % 9;
        int minSect = getSmallestSector();
        if ( minSect == 0x41 ) {
            track += 2;
        } else {
            if ( minSect == 0x01 ) {
                track++;
            }
        }

        // Adjusts the number of tracks if capacity is exceeded
        CPCEMUEnt header = new CPCEMUEnt(DSKImage, 0);
        if ( track > header.nTracks - 1 ) {
            header.nTracks = ( int )( track + 1 );
            formatTrack( header, track, minSect, 9 );
        }
        header.writeBytes(DSKImage, 0);

        int pos = getPosData( track, sect + minSect, true );
        config.debug("writing "+SECTSIZE+" ints at " + pos + ", offset: " + offset);
        for(int i = 0;i<SECTSIZE;i++) {
            DSKImage[pos + i] = data[offset + i];
        }
        if ( ++sect > 8 ) {
            track++;
            sect = 0;
        }
        pos = getPosData( track, sect + minSect, true );
        config.debug("writing "+SECTSIZE+" ints at " + pos);
        for(int i = 0;i<SECTSIZE;i++) {
            DSKImage[pos + i] = data[offset + SECTSIZE + i];
        }

    }

    //
    // Fills a bitmap to know where there are files on the floppy disk.
    // Returns the number of KB used on the floppy disk.
    //
    public int fillBitmap() {
        int nKb = 0;
        for (int i = 0; i < bitmap.length; i++) {
            bitmap[i] = 0;
        }
        bitmap[0] = bitmap[1] = 1;
        for (int i = 0; i < 64; i++) {
            stDirEntry Dir = GetInfoDirEntry(i);
            if (Dir.user != USER_DELETED) {
                for (int j = 0; j < 16; j++) {
                    int b = Dir.blocks[j];
                    if (b > 1 && (bitmap[b] == 0)) {
                        bitmap[b] = 1;
                        nKb++;
                    }
                }
            }
        }
        return nKb;
    }

    //
    // Find the smallest sector in a track.
    //
    public int getSmallestSector() {
        int sect = 0x100;
        CPCEMUTrack tr = new CPCEMUTrack(DSKImage, CPCEMUEnt.getSize());
        for (int s = 0; s < tr.nSect; s++) {
            if (sect > tr.sect[s].sect) {
                sect = tr.sect[s].sect;
            }
        }

        return sect;
    }

    //
    // Return the position of a sector in a DSK file.
    //
    public int getPosData(int track, int sect, boolean physicalSector) {
        int pos = CPCEMUEnt.getSize();
        CPCEMUTrack tr = new CPCEMUTrack(DSKImage, pos);
        int sizeByte;
        for (int t = 0; t <= track; t++) {
            pos += CPCEMUTrack.getSize();
            for (int s = 0; s < tr.nSect; s++) {
                if (t == track) {
                    if (((tr.sect[s].sect == sect) && physicalSector) ||
                        ((s == sect) && !physicalSector)) {
                        break;
                    }
                }
                sizeByte = tr.sect[s].sizeInBytes;
                if (sizeByte != 0) {
                    pos += sizeByte;
                } else {
                    pos += (128 << tr.sect[s].size);
                }
            }
        }
        return pos;
    }

    public int findEmptyBlock(int maxBloc) {
        for (int i = 2; i < maxBloc; i++) {
            if (bitmap[i] == 0) {
                bitmap[i] = 1;
                return (i);
            }
        }
        return (0);
    }

    public void formatTrack(CPCEMUEnt Infos, int t, int MinSect, int NbSect) {
        for (int i = 0; i < 0x200 * NbSect; i++) {
            DSKImage[CPCEMUEnt.getSize() + CPCEMUTrack.getSize() + (t * Infos.dataSize) + i] = (int) 0xe5;
        }
        CPCEMUTrack tr = new CPCEMUTrack(DSKImage, CPCEMUEnt.getSize() + t * (int) Infos.dataSize);
        String data = "Track-Info\r\n";
        for (int i = 0; i < data.length(); i++) {
            tr.id[i] = data.charAt(i);
        }
        tr.id[data.length()] = 0;
        tr.track = (int) t;
        tr.head = 0;
        tr.sectSize = 2;
        tr.nSect = (int) NbSect;
        tr.gap3 = 0x4E;
        tr.octRemp = (int) 0xe5;
        int ss = 0;
        //
        // Sector inteleaving
        //
        for (int s = 0; s < NbSect;) {
            tr.sect[s].track = (int) t;
            tr.sect[s].head = 0;
            tr.sect[s].sect = (int) (ss + MinSect);
            tr.sect[s].size = 2;
            tr.sect[s].sizeInBytes = 0x200;
            ss++;
            if (++s < NbSect) {
                tr.sect[s].track = (int) t;
                tr.sect[s].head = 0;
                tr.sect[s].sect = (int) (ss + MinSect + 4);
                tr.sect[s].size = 2;
                tr.sect[s].sizeInBytes = 0x200;
                s++;
            }
        }

        tr.writeBytes(DSKImage, CPCEMUEnt.getSize() + t * (int) Infos.dataSize);
    }

    public stDirEntry getDirectoryName(String fileName) {
        stDirEntry dirLoc = new stDirEntry();
        dirLoc.clear();
        for (int i = 0; i < 8; i++) {
            dirLoc.name[i] = ' ';
        }
        for (int i = 0; i < 3; i++) {
            dirLoc.extension[i] = ' ';
        }
        int p = fileName.indexOf('.');
        if (p >= 0) {
            for (int i = 0; i < p; i++) {
                dirLoc.name[i] = (int) fileName.toUpperCase().charAt(i);
            }
            for (int i = 0; i < 3; i++) {
                dirLoc.extension[i] = (int) fileName.toUpperCase().charAt(p + 1 + i);
            }
        } else {
            for (int i = 0; i < Math.min(8, fileName.length()); i++) {
                dirLoc.name[i] = (int) fileName.toUpperCase().charAt(i);
            }
        }
        return dirLoc;
    }

    //
    // Copy a file to disk
    //
    public int copyFile(int fileBuffer[], String fileName, int fileSize, int maxBlock, int userNumber, boolean systemFile, boolean readOnly) throws Exception {
        config.debug("CopyFile: " + fileName + ", with size: " + fileSize);
        int nPages = 0, posDir, pageSize;
        fillBitmap();
        stDirEntry dirLoc = getDirectoryName(fileName);
        for (int filePosition = 0; filePosition < fileSize;) {
            config.debug("  CopyFile: filePosition: " + filePosition);
            posDir = findFreeDirectory();
            if (posDir == -1) {
                throw new Exception("No free directory to save the file!");
            }
            dirLoc.user = (int) userNumber;
            if (systemFile) {
                dirLoc.name[9] |= 0x80;
            }
            if (readOnly) {
                dirLoc.name[8] |= 0x80;
            }
            dirLoc.pageNumber = (int) nPages++;  // Page number inside the file
            pageSize = (fileSize - filePosition + 127) >> 7;	// page size
            if (pageSize > 128) {
                pageSize = 128;
            }
            dirLoc.nPages = (int) pageSize;
            int l = (pageSize + 7) >> 3;
            for (int i = 0; i < 16; i++) {
                dirLoc.blocks[i] = 0;
            }
            for (int j = 0; j < l; j++) {
                int block = findEmptyBlock(maxBlock);
                if (block != 0) {
                    dirLoc.blocks[j] = (int) block;
                    writeBlock(block,  fileBuffer, filePosition);
                    filePosition += SECTSIZE * 2;  // next block
                } else {
                    return ERR_NO_BLOCK;
                }

            }
            setInfoDirEntry(posDir, dirLoc);
        }
        return ERR_NO_ERR;
    }

    //
    // Save a DSK file to disc.
    //
    public boolean writeDsk(String fileName) throws FileNotFoundException, IOException {
        CPCEMUEnt infos = new CPCEMUEnt(DSKImage, 0);

        FileOutputStream fp = new FileOutputStream(new File(fileName));

        if (infos.dataSize == 0) {
            infos.dataSize = 0x100 + SECTSIZE * 9;
            infos.writeBytes(DSKImage, 0);
        }
        int size = infos.nTracks * infos.dataSize + CPCEMUEnt.getSize();
        byte imgDskBytes[] = new byte[DSKImage.length];
        for(int i = 0;i<DSKImage.length;i++) imgDskBytes[i] = (byte)DSKImage[i];
        fp.write(imgDskBytes, 0, size);
        fp.close();
        return true;
    }

    public boolean readDsk(String fileName) throws Exception {
        boolean ret = false;
        if (CPCEMUEnt.getSize() != 0x100) {
            throw new Exception("INVALID DSK BUILD");
        }
        FileInputStream fp = new FileInputStream(new File(fileName));
        byte imgDskBytes[] = fp.readAllBytes();
        for(int i = 0;i<imgDskBytes.length;i++) {
            int v = imgDskBytes[i];
            if (v < 0) v += 256;
            DSKImage[i] = v;
        }
        CPCEMUEnt Infos = new CPCEMUEnt(DSKImage, 0);
        if ((Infos.header[0] == 'M'
                && Infos.header[1] == 'V'
                && Infos.header[2] == ' '
                && Infos.header[3] == '-')
                || (Infos.header[0] == 'E'
                && Infos.header[1] == 'X'
                && Infos.header[2] == 'T'
                && Infos.header[3] == 'E'
                && Infos.header[4] == 'N'
                && Infos.header[5] == 'D'
                && Infos.header[6] == 'E'
                && Infos.header[7] == 'D'
                && Infos.header[8] == ' '
                && Infos.header[9] == 'C'
                && Infos.header[10] == 'P'
                && Infos.header[11] == 'C'
                && Infos.header[12] == ' '
                && Infos.header[13] == 'D'
                && Infos.header[14] == 'S'
                && Infos.header[15] == 'K')) {
            ret = true;
        }
        fp.close();
        return ret;
    }

    //
    // Returns a directory entry.
    //
    public stDirEntry GetInfoDirEntry(int NumDir) {
        int minSect = getSmallestSector();
        int s = (NumDir >> 4) + minSect;
        int t = (minSect == 0x41 ? 2 : 0);
        if (minSect == 1) {
            t = 1;
        }
        stDirEntry dir = new stDirEntry(DSKImage, ((NumDir & 15) << 5) + getPosData(t, s, true));
        return dir;
    }

    //
    // Finds a free directory entry.
    //
    public int findFreeDirectory() {
        for (int i = 0; i < 64; i++) {
            stDirEntry dir = GetInfoDirEntry(i);
            if (dir.user == (int) USER_DELETED) {
                return i;
            }
        }
        return -1;
    }

    void formatDsk(int nSectors, int nTracks) {
        config.debug("Formatting disk...");
        for (int i = 0; i < DSKImage.length; i++) {
            DSKImage[i] = 0;
        }
        CPCEMUEnt infos = new CPCEMUEnt(DSKImage, 0);
        String data = "MV - CPCEMU Disk-File\r\nDisk-Info\r\n";
        for (int i = 0; i < data.length(); i++) {
            infos.header[i] = data.charAt(i);
        }
        infos.header[data.length()] = 0;
        infos.dataSize = (CPCEMUTrack.getSize() + (0x200 * nSectors));
        infos.nTracks = (int) nTracks;
        infos.nHeads = 1;
        for (int t = 0; t < nTracks; t++) {
            formatTrack(infos, t, 0xc1, nSectors);
        }

        fillBitmap();
        infos.writeBytes(DSKImage, 0);
    }

    public void setInfoDirEntry(int numDir, stDirEntry dir) {
        config.debug("SetInfoDirEntry... " + numDir);
        int minSect = getSmallestSector();
        int s = (numDir >> 4) + minSect;
        int t = (minSect == 0x41 ? 2 : 0);
        if (minSect == 1) {
            t = 1;
        }

        for (int i = 0; i < 16; i++) {
            dir.writeBytes(DSKImage, ((numDir & 15) << 5) + getPosData(t, s, true));
        }
    }

    public boolean putFileInDsk(String rawFileName, int TypeModeImport, int loadAdress, int exeAdress, int userNumber, boolean systemFile, boolean readOnly) throws FileNotFoundException, IOException, Exception {
        config.debug("PutFileInDsk..." + rawFileName);
        String cFileName = Utils.getAmsdosName(rawFileName);
        FileInputStream fi = new FileInputStream(new File(rawFileName));
        // This is a bit awkward, but it's just to deal with signed vs unsigned
        // behavior of data types between Java and the original C code:
        int buff[] = new int[0x20000];
        byte buffBytes[] = fi.readAllBytes();
        for(int i = 0;i<buffBytes.length;i++) {
            int v = buffBytes[i];
            if (v < 0) v += 256;
            buff[i] = v;
        }
        int Lg = buffBytes.length;
        boolean createHeader = false;

        StAmsdos e = null;
        if (Lg >= StAmsdos.getSize()) {
            e = new StAmsdos(buff, 0);
        }
        if (Lg > 0x10080) {
            throw new Exception("File is too long: " + Lg);
        }

        if (TypeModeImport == MODE_ASCII) {
            for (int i = 0; i < 0x20000; i++) {
                // last ascii char
                if (buff[i] > 136) {
                    buff[i] = '?'; // replace by unknown char
                }
            }
        }

        boolean isAmsdos = checkAmsdos(buff);

        if (!isAmsdos) {
            // Create a default amsdos header
            config.debug("Creating an automatic amsdos header ...");
            e = createAmsdosHeader(cFileName, Lg);
            if (loadAdress != 0) {
                e.adress = loadAdress;
                TypeModeImport = MODE_BINAIRE;
            }
            if (exeAdress != 0) {
                e.entryAdress = exeAdress;
                TypeModeImport = MODE_BINAIRE;
            }
            updateChecksum(e);
        } else {
            config.debug("The file already has a header\n");
        }
        if (e == null) throw new Exception("Somehow a header was detected, but it is too small.");
        switch (TypeModeImport) {
            case MODE_ASCII:
                if (isAmsdos) {
                    // Remove header if it exists.
                    for (int i = 0; i < Lg - StAmsdos.getSize(); i++) {
                        buff[i] = buff[StAmsdos.getSize() + i];
                    }

                    Lg -= StAmsdos.getSize();
                }
                break;

            case MODE_BINAIRE:
                if (!isAmsdos) {
                    createHeader = true;
                }
                break;
        }

        if (createHeader) {
            for (int i = Lg - 1; i >= 0; i--) {
                buff[StAmsdos.getSize() + i] = buff[i];
            }
            e.writeBytes(buff, 0);
            Lg += StAmsdos.getSize();
        }
        return copyFile(buff, cFileName, Lg, 256, userNumber, systemFile, readOnly) == ERR_NO_ERR;
    }

    //
    // Check if AMSDOS header is valid.
    //
    boolean checkAmsdos(int buf[]) {
        int i, checksum = 0;
        boolean modeAmsdos = false;
        int checkSumFile = (buf[0x43] + buf[0x43] + 1 * 256);
        for (i = 0; i < 67; i++) {
            checksum += buf[i];
        }

        if ((checkSumFile == checksum) && checksum != 0) {
            modeAmsdos = true;
        }

        return modeAmsdos;
    }

    //
    // Create an Amsdos default header.
    //
    public StAmsdos createAmsdosHeader(String fileName, int len) {
        String realName = fileName;
        StAmsdos header = new StAmsdos();
        int name[] = {' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '};

        header.clear();
        int p;
        do {
            p = realName.indexOf("/");
            if (p != -1) {
                realName = realName.substring(p + 1);
            }
        } while (p != -1);
        p = realName.indexOf('.');
        String extension = "   ";
        if (p != -1) {
            extension = realName.substring(p + 1);
            realName = realName.substring(0, p);
        }

        int l = realName.length();
        if (l > 8) {
            l = 8;
        }

        for (int i = 0; i < l; i++) {
            name[i] = (int) realName.toUpperCase().charAt(i);
        }

        if (p != -1) {
            for (int i = 0; i < 3; i++) {
                name[i + 8] = (int) extension.toUpperCase().charAt(i);
            }
        }

        for (int i = 0; i < 11; i++) {
            header.fileName[i] = name[i];
        }
        header.length = 0;
        header.realLength = header.logicalLength = len;
        header.fileType = 2;  // Binary file

        updateChecksum(header);

        return header;
    }

    public void updateChecksum(StAmsdos header) {
        int i, checksum = 0;
        int p[] = header.toBytes();
        for (i = 0; i < 67; i++) {
            int v = p[i];
            if (v < 0) v += 256;
            checksum += v;
        }

        header.checkSum = checksum;
    }
}
