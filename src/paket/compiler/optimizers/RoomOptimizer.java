/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.compiler.optimizers;

import java.util.ArrayList;
import java.util.List;
import paket.compiler.PAKET;
import paket.compiler.PAKETConfig;
import paket.pak.PAKGame;
import paket.pak.PAKObjectType;
import paket.pak.PAKRoom;
import paket.platforms.Platform;

/**
 *
 * @author santi
 */
public class RoomOptimizer extends PAKETOptimizer {
    PAKGame game = null;
    Platform platform;
    List<PAKObjectType> objectTypes;
    int rooms_per_bank = 1;
    
    public RoomOptimizer(String a_name, int a_randomSeed, int a_rooms_per_bank, 
                         List<PAKObjectType> a_objectTypes, PAKGame a_game,
                         Platform a_platform)
    {
        super(a_name, a_randomSeed, true);
        rooms_per_bank = a_rooms_per_bank;
        objectTypes = a_objectTypes;
        game = a_game;
        platform = a_platform;
    }


    @Override
    public int extimateSizeWithOrder(List<Integer> order, List<? extends Object> objects, String compressor, PAKETConfig config) throws Exception
    {
        int totalSize = 0;
        for(int i = 0;i<order.size();i+=rooms_per_bank) {
            List<Integer> bankBytes = new ArrayList<>();
            for(int j = 0;j<rooms_per_bank;j++) {
                if (i+j < order.size()) {
                    PAKRoom room = (PAKRoom)objects.get(order.get(i+j));
                    List<Integer> roomBytes = room.toBytesForAssembler(
                            objectTypes, platform.SCREEN_WIDTH_IN_TILES*8, 
                            game, game.dialogues,
                            false, platform, config);
                    if (rooms_per_bank > 1) {
                        int size = roomBytes.size() + 2;
                        bankBytes.add(size%256);
                        bankBytes.add(size/256);
                        bankBytes.addAll(roomBytes);
                    } else {
                        bankBytes.addAll(roomBytes);            
                    }
                }
            }
            totalSize += PAKET.estimateCompressedSize(bankBytes, compressor);
        }
        return totalSize;
    }

    
    @Override
    public int uncompressedSize(List<? extends Object> objects, PAKETConfig config) throws Exception
    {
        int size = 0;
        for(Object o: objects) {
            PAKRoom room = (PAKRoom)o;
            List<Integer> roomBytes = room.toBytesForAssembler(
                    objectTypes, platform.SCREEN_WIDTH_IN_TILES*8, 
                    game, game.dialogues,
                    false, platform, config);
            size += roomBytes.size();
        }
        
        return size;
    }
    
    
    @Override
    public String IDFromObject(Object object) {
        return ((PAKRoom)object).ID;
    }
    
    
    @Override
    public List<Integer> uncompressedBytes(Object object, PAKETConfig config) throws Exception
    {
        PAKRoom room = (PAKRoom)object;
        return room.toBytesForAssembler(
            objectTypes, platform.SCREEN_WIDTH_IN_TILES*8, 
            game, game.dialogues,
            false, platform, config);
    }    
    
}
