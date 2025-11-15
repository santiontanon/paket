/*
 * This code is a translation from the ZX0 compressor by Einar Saukas to Java.
 * The original source code by Einar Saukas can be found here:
 * https://github.com/einar-saukas/ZX0
 * Translation by Santiago Ontañón
 */
package paket.util.zx0;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;

/**
 *
 * @author santi
 * 
 */
public class ZX0 {
    public static final int MAX_OFFSET_ZX0 = 32640;
    public static final int MAX_OFFSET_ZX7 = 2176;
    public static final int MAX_OFFSET_QUICK = 1024;
    public static final int INITIAL_OFFSET = 1;

    
    public static byte[] compress(byte input_data[], int skip, int max_offset) throws Exception 
    { 
        Compressor c = new Compressor();
        Optimizer o = new Optimizer();
        
        BLOCK optimal = o.optimize(input_data, skip, max_offset);
        byte output_data[] = c.compress(optimal, input_data, skip, false);

        return output_data;
    }
    
    
    public static int compressFile(String inputFileName, String outputFileName, int max_offset) throws Exception
    {
        // Read the file:
        FileInputStream fis = new FileInputStream(new File(inputFileName));
        byte data[] = fis.readAllBytes();
        fis.close();
        // Compress it:
        byte compressed[] = compress(data, 0, max_offset);
        // Save the compressed version:
        FileOutputStream fos = new FileOutputStream(new File(outputFileName));
        fos.write(compressed);
        fos.close();
        return compressed.length;
    }
    
    
    public static int sizeOfCompressedBuffer(List<Integer> input_data_list, int max_offset) 
    {
        byte input_data[] = new byte[input_data_list.size()];
        for(int i = 0;i<input_data.length;i++) {
            input_data[i] = (byte)(int)input_data_list.get(i);
        }
        Compressor c = new Compressor();
        Optimizer o = new Optimizer();
        
        BLOCK optimal = o.optimize(input_data, 0, max_offset);
        byte output_data[] = c.compress(optimal, input_data, 0, false);

        return output_data.length;
    }

}
