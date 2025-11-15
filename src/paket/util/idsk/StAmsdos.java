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
public class StAmsdos {

    int userNumber;  // 00 User
    int[] fileName = new int[15];  // 01-0f  Name + extension
    int blockNumber;  // 10
    int lastBlock;  // 11  last block flag
    int fileType;  // 12  File type
    int length;  // 13-14
    int adress;  // 15-16
    int firstBlock;  // 17  First block flag
    int logicalLength;  // 18-19
    int entryAdress;  // 1a-1b
    int[] unused = new int[0x24];
    int realLength;  // 40-42
    int bigLength;
    int checkSum;  // 43-44  CheckSum Amsdos
    int[] unused2 = new int[0x3b];
    
    
    public StAmsdos()
    {
    }
    
    
    public StAmsdos(int data[], int offset)
    {
        readBytes(data, offset);
    }
    

    public void readBytes(int data[], int offset)
    {
        userNumber = data[offset];
        for(int i = 0;i<15;i++) {
            fileName[i] = data[offset + 1 + i];
        }
        blockNumber = data[offset + 1 + 15];
        lastBlock = data[offset + 1 + 15 + 1];
        fileType = data[offset + 1 + 15 + 2];
        length = data[offset + 1 + 15 + 3] + data[offset + 1 + 15 + 4] * 256;
        adress = data[offset + 1 + 15 + 5] + data[offset + 1 + 15 + 6] * 256;
        firstBlock = data[offset + 1 + 15 + 7];
        logicalLength = data[offset + 1 + 15 + 8] + data[offset + 1 + 15 + 9] * 256;
        entryAdress = data[offset + 1 + 15 + 10] + data[offset + 1 + 15 + 11] * 256;
        for(int i = 0;i<0x24;i++) {
            unused[i] = data[offset + 1 + 15 + 12 + i];
        }
        realLength = data[offset + 1 + 15 + 12 + 0x24] + data[offset + 1 + 15 + 12 + 0x24 + 1] * 256;
        bigLength = data[offset + 1 + 15 + 12 + 0x24 + 2];
        checkSum = data[offset + 1 + 15 + 12 + 0x24 + 3] + data[offset + 1 + 15 + 12 + 0x24 + 4] * 256;
        for(int i = 0;i<0x3b;i++) {
            unused2[i] = data[offset + 1 + 15 + 12 + 0x24 + 5 + i];
        }        
    }
    
    
    public void clear()
    {
        int data[] = new int[getSize()];
        for(int i = 0;i<data.length;i++) {
            data[i] = 0;
        }
        readBytes(data, 0);
    }    

    
    public static int getSize()
    {
        return 15 + 0x24 + 0x3b + 6 + 6 * 2;  // 128
    }
    
    
    public int[] toBytes() {
        int data[] = new int[StAmsdos.getSize()];
        writeBytes(data, 0);
        return data;
    }
        
        
    public void writeBytes(int data[], int offset)
    {
        data[offset] = userNumber;
        for(int i = 0;i<15;i++) {
            data[offset + 1 + i] = fileName[i];
        }
        data[offset + 1 + 15] = blockNumber;
        data[offset + 1 + 15 + 1] = lastBlock;
        data[offset + 1 + 15 + 2] = fileType;
        data[offset + 1 + 15 + 3] = (byte) (length % 256);
        data[offset + 1 + 15 + 4] = (byte) (length / 256);
        data[offset + 1 + 15 + 5] = (byte) (adress % 256);
        data[offset + 1 + 15 + 6] = (byte) (adress / 256);        
        data[offset + 1 + 15 + 7] = firstBlock;
        data[offset + 1 + 15 + 8] = (byte) (logicalLength % 256);
        data[offset + 1 + 15 + 9] = (byte) (logicalLength / 256);        
        data[offset + 1 + 15 + 10] = (byte) (entryAdress % 256);
        data[offset + 1 + 15 + 11] = (byte) (entryAdress / 256);        
        for(int i = 0;i<0x24;i++) {
            data[offset + 1 + 15 + 12 + i] = unused[i];
        }
        data[offset + 1 + 15 + 12 + 0x24] = (byte) (realLength % 256);
        data[offset + 1 + 15 + 12 + 0x24 + 1] = (byte) (realLength / 256);        
        data[offset + 1 + 15 + 12 + 0x24 + 2] = bigLength;
        data[offset + 1 + 15 + 12 + 0x24 + 3] = (byte) (checkSum % 256);
        data[offset + 1 + 15 + 12 + 0x24 + 4] = (byte) (checkSum / 256);        
        for(int i = 0;i<0x3b;i++) {
            data[offset + 1 + 15 + 12 + 0x24 + 5 + i] = unused2[i];
        }     
    }    
}
