/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.compiler.optimizers;

import java.util.ArrayList;
import java.util.List;
import paket.compiler.PAKETConfig;
import paket.platforms.Platform;
import paket.util.Z80Assembler;

/**
 *
 * @author santi
 */
public class TilesOptimizer extends PAKETOptimizer {
    Platform platform = null;
    
    public TilesOptimizer(String a_name, int a_randomSeed, Platform a_platform)
    {
        super(a_name, a_randomSeed, true);
        platform = a_platform;
    }


    @Override
    public int extimateSizeWithOrder(List<Integer> order, List<? extends Object> objects, String compressor, PAKETConfig config) throws Exception
    {
        List<int []> tiles_new_order = new ArrayList<>();
        for(int idx:order) {
            tiles_new_order.add((int [])objects.get(idx));
        }
        
        return platform.generateTileBanks(tiles_new_order, 0, null, null);
    }
    
    
    @Override
    public int uncompressedSize(List<? extends Object> objects, PAKETConfig config) throws Exception
    {
        return platform.getTileSizeInBytes() * objects.size();
    }    

    
    @Override
    public String IDFromObject(Object object) throws Exception {
        int tile[] = (int [])object;
        String tileString = "";
        for(int pixel:tile) {
            tileString += Z80Assembler.toHex8bit(pixel, false);
        }
        return tileString;
    }
    

    @Override
    public List<Integer> uncompressedBytes(Object object, PAKETConfig config) throws Exception
    {
        List<Integer> bytes = new ArrayList<>();
        for(int i:(int [])object) {
            bytes.add(i);
        }
        return bytes;
    }    
}
