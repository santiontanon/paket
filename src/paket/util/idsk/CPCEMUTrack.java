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
public class CPCEMUTrack {
    
    public static final int ID_SIZE = 0x10;

    int[] id = new int[ID_SIZE];  // "Track-Info\r\n"
    int track;
    int head;
    int unused = 0;
    int sectSize = 2 ;  // 2
    int nSect = 9;  // 9
    int gap3 = 0x4e;  // 0x4e
    int octRemp = (int) 0xe5;  // 0xe5
    CPCEMUSect[] sect = new CPCEMUSect[29];
    
    
    public CPCEMUTrack()
    {
    }
    
    
    public CPCEMUTrack(int []data, int offset)
    {
        for(int i = 0;i<ID_SIZE;i++) {
            id[i] = data[offset + i];
        }
        track = data[offset + ID_SIZE];
        head = data[offset + ID_SIZE + 1];
        unused = data[offset + ID_SIZE + 2] + data[offset + ID_SIZE + 3] * 256;
        sectSize = data[offset + ID_SIZE + 4];
        nSect = data[offset + ID_SIZE + 5];
        gap3 = data[offset + ID_SIZE + 6];
        octRemp = data[offset + ID_SIZE + 7];
        for(int i = 0;i<sect.length;i++) {
            sect[i] = new CPCEMUSect(data, offset + ID_SIZE + 8 + CPCEMUSect.getSize() * i);
        }
    }
    
    
    public static int getSize() {
        return ID_SIZE + 8 + 29 * CPCEMUSect.getSize();  // = 256
    }
    
    public int[] toBytes() {
        int data[] = new int[CPCEMUTrack.getSize()];
        writeBytes(data, 0);
        return data;
    }
        
        
    public void writeBytes(int data[], int offset)
    {
        for(int i = 0;i<ID_SIZE;i++) {
            data[offset + i] = id[i];
        }
        data[offset + ID_SIZE] = track;
        data[offset + ID_SIZE + 1] = head;
        data[offset + ID_SIZE + 2] = (int) (unused % 256);
        data[offset + ID_SIZE + 3] = (int) (unused / 256);
        data[offset + ID_SIZE + 4] = sectSize;
        data[offset + ID_SIZE + 5] = nSect;
        data[offset + ID_SIZE + 6] = gap3;
        data[offset + ID_SIZE + 7] = octRemp;
        for(int i = 0;i<sect.length;i++) {
            sect[i].writeBytes(data, offset + ID_SIZE + 8 + CPCEMUSect.getSize() * i);
        }
    }    
}
