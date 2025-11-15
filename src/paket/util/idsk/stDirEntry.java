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
public class stDirEntry {

    int user;
    int[] name = new int[8];
    int[] extension = new int[3];
    int pageNumber;
    int[] unused = new int[2];
    int nPages;
    int[] blocks = new int[16];
    
    
    public stDirEntry()
    {
    }
    
    
    public stDirEntry(int data[], int offset)
    {
        readBytes(data, offset);
    }

    
    public static int getSize()
    {
        return 1 + 8 + 3 + 1 + 2 + 1 + 16;
    }
    
    
    public void readBytes(int data[], int offset)
    {
        user = data[offset];
        for(int i = 0;i<name.length;i++) {
            name[i] = data[offset + 1 + i];
        }
        for(int i = 0;i<extension.length;i++) {
            extension[i] = data[offset + 1 + name.length + i];
        }
        pageNumber = data[offset + 1 + name.length + extension.length];
        for(int i = 0;i<unused.length;i++) {
            unused[i] = data[offset + 1 + name.length + extension.length + 1 + i];
        }
        nPages = data[offset + 1 + name.length + extension.length + 1 + unused.length];
        for(int i = 0;i<blocks.length;i++) {
            blocks[i] = data[offset + 1 + name.length + extension.length + 1 + unused.length + 1 + i];
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
    
    public void writeBytes(int data[], int offset)
    {
        data[offset] = user;
        for(int i = 0;i<name.length;i++) {
            data[offset + 1 + i] = name[i];
        }
        for(int i = 0;i<extension.length;i++) {
            data[offset + 1 + name.length + i] = extension[i];
        }
        data[offset + 1 + name.length + extension.length] = pageNumber;
        for(int i = 0;i<unused.length;i++) {
            data[offset + 1 + name.length + extension.length + 1 + i] = unused[i];
        }
        data[offset + 1 + name.length + extension.length + 1 + unused.length] = nPages;
        for(int i = 0;i<blocks.length;i++) {
            data[offset + 1 + name.length + extension.length + 1 + unused.length + 1 + i] = blocks[i];
        }
    }
        
}
