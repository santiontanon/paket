/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.compiler.optimizers;

import java.util.ArrayList;
import java.util.List;
import paket.compiler.PAKETConfig;
import paket.pak.PAKGame;
import paket.pak.PAKObjectType;
import paket.platforms.Platform;

/**
 *
 * @author santi
 */
public class ObjectTypesOptimizer extends PAKETOptimizer {
    List<List<Integer>> objectTypeBytes = null;
    PAKGame game = null;
    Platform platform = null;
    int objects_per_bank = 4;
    
    public ObjectTypesOptimizer(String a_name, int a_randomSeed, int a_objects_per_bank, PAKGame a_game, Platform a_platform)
    {
        super(a_name, a_randomSeed, false);
        game = a_game;
        platform = a_platform;
        objects_per_bank = a_objects_per_bank;
    }
    
    
    void computeObjectTypeBytes(List<? extends Object> objects, PAKETConfig config) throws Exception
    {
        objectTypeBytes = new ArrayList<>();
        for(int i = 0;i<objects.size();i++) {
            PAKObjectType ot = (PAKObjectType)objects.get(i);
            List<Integer> bytes = ot.toBytesForAssembler(platform, game.rooms, false, config);
            objectTypeBytes.add(bytes);                
        }
    }


    @Override
    public int extimateSizeWithOrder(List<Integer> order, List<? extends Object> objects, String compressor, PAKETConfig config) throws Exception
    {
        if (objectTypeBytes == null) {
            computeObjectTypeBytes(objects, config);
        }
        return Platform.estimateSizeOfAllObjectBanks(order, objectTypeBytes, objects_per_bank, compressor);
    }

    
    @Override
    public int uncompressedSize(List<? extends Object> objects, PAKETConfig config) throws Exception
    {
        int size = 0;
        if (objectTypeBytes == null) {
            computeObjectTypeBytes(objects, config);
        }
        for(List<Integer> objectBytes: objectTypeBytes) {
            size += objectBytes.size();
        }
        
        return size;
    }
    
    
    @Override
    public String IDFromObject(Object object) {
        return ((PAKObjectType)object).ID;
    }
    
    
    @Override
    public List<Integer> uncompressedBytes(Object object, PAKETConfig config) throws Exception
    {
        PAKObjectType ot = (PAKObjectType)object;
        return ot.toBytesForAssembler(platform, game.rooms, false, config);        
    }    
}
