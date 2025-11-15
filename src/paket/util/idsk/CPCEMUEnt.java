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

/**
 *
 * @author santi
 */
public class CPCEMUEnt {

    int[] header = new int[0x30];  // "MV - CPCEMU Disk-File\r\nDisk-Info\r\n"
    int nTracks;
    int nHeads;
    int dataSize = 0x1300;  // 0x1300 = 256 + ( 512 * nSectors )
    int[] unused = new int[0xcc];
    
    
    public CPCEMUEnt()
    {
        
    }
    
    
    public CPCEMUEnt(int data[], int offset)
    {
        for(int i = 0;i<header.length;i++) {
            header[i] = data[offset + i];
        }
        nTracks = data[offset + header.length];
        nHeads = data[offset + header.length + 1];
        dataSize = data[offset + header.length + 2] + data[offset + header.length + 3] * 256;
        for(int i = 0;i<unused.length;i++) {
            unused[i]  = data[offset + header.length + 4 + i];
        }
    }
    
    
    public static int getSize()
    {
        return 0x30 + 4 + 0xcc;  // = 256
    }
    
    
    public int[] toBytes() {
        int data[] = new int[CPCEMUEnt.getSize()];
        writeBytes(data, 0);
        return data;
    }
        
        
    public void writeBytes(int data[], int offset)
    {
        for(int i = 0;i<header.length;i++) {
            data[offset + i] = header[i];
        }
        data[offset + header.length] = nTracks;
        data[offset + header.length + 1] = nHeads;
        data[offset + header.length + 2] = (int) (dataSize % 256);
        data[offset + header.length + 3] = (int) (dataSize / 256);
        for(int i = 0;i<unused.length;i++) {
            data[offset + header.length + 4 + i] = unused[i];
        }
    }
    
}
