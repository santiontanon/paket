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
public class CPCEMUSect {
    int track;
    int head;
    int sect;
    int size;
    int un1;
    int sizeInBytes;
    
    
    public CPCEMUSect()
    {
    }
    
    
    public CPCEMUSect(int data[], int offset)
    {
        track = data[offset];
        head = data[offset + 1];
        sect = data[offset + 2];
        size = data[offset + 3];
        un1 = data[offset + 4] + data[offset + 5] * 256;
        sizeInBytes = data[offset + 6] + data[offset + 6] * 256;
    }

    
    public static int getSize() {
        return 8;
    }
    
    public int[] toBytes() {
        int data[] = new int[CPCEMUSect.getSize()];
        writeBytes(data, 0);
        return data;
    }
        
        
    public void writeBytes(int data[], int offset)
    {
        data[offset] = track;
        data[offset + 1] = head;
        data[offset + 2] = sect;
        data[offset + 3] = size;
        data[offset + 4] = (int) (un1 % 256);
        data[offset + 5] = (int) (un1 / 256);
        data[offset + 6] = (int) (sizeInBytes % 256);
        data[offset + 7] = (int) (sizeInBytes / 256);
    }
}
