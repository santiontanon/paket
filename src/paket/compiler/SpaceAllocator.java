/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.compiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import paket.pak.PAKGame;


/**
 *
 * @author santi
 */
public class SpaceAllocator {
    public static class Block {
        public String name;
        // Two slots to specify assembler associated with this block, each 
        // platform will use these as they want. For example, "assembler1" could
        // be used as the code that contains the data, and "assembler2" as code
        // that just reserves some space, if data from assembler1 is to be
        // moved there later.
        public String assembler1;
        public String assembler2;
        public int size;
        public String spaceVariable;

        
        public Block(String a_name, String a_assembler1, String a_assembler2, int a_size, String a_spaceVariable)
        {
            name = a_name;
            assembler1 = a_assembler1;
            assembler2 = a_assembler2;
            size = a_size;
            spaceVariable = a_spaceVariable;
        }
        

        @Override
        public String toString() {
            return name;
        }
    }
    
    public static class Space {
        public String startStatement = "";
        public String endStatement = "";
        public int size = 0;
        public int spaceLeft = 0;
        
        // Depending on the allocation routine, we will use either "content" or
        // "rawContent" to place blocks in this space:
        public List<Block> content = new ArrayList<>();
        public String rawContent = "";
        
        public String page = null;  // used to store the MegaROM/6128 page this corresponds to

        
        public Space(String a_startStatement, String a_endStatement, int a_spaceLeft, String a_page)
        {
            startStatement = a_startStatement;
            endStatement = a_endStatement;
            spaceLeft = a_spaceLeft;
            size = spaceLeft;
            page = a_page;
        }
    }
    
               
    public static void RAMAllocation(HashMap<String, String> assemblerVariables, 
                                     List<Space> spaces, List<Block> blocks,
                                     PAKGame game, PAKETConfig config) throws Exception
    {        
        config.info("RAMAllocation: with " + blocks.size() + " blocks, and " + spaces.size() + " spaces:");      
        String spacesStr = "";
        for(Space space:spaces) {
            spacesStr += space.spaceLeft + ", ";
        }
        String blocksStr = "";
        for(Block block:blocks) {
            blocksStr += block.name + ":" + block.size + ", ";
        }
        config.info("   Spaces: " + spacesStr);
        config.info("   Blocks: " + blocksStr);
        Collections.sort(blocks, new Comparator<Block>() {
            @Override
            public int compare(Block o1, Block o2) {
                return -Integer.compare(o1.size, o2.size);
            }
        });
        
        for(Block block:blocks) {
            // Heuristic: find the smallest space that has enough space for the block:
            Space best = null;
            for(Space s:spaces) {
                if (s.spaceLeft >= block.size) {
                    if (best == null || s.spaceLeft < best.spaceLeft) {
                        best = s;
                    }
                }
            }
            if (best == null) {
                String spaceLeftStr = "";
                for(Space s:spaces) {
                    spaceLeftStr += s.spaceLeft + ",";
                }
                throw new Exception("Could not allocate RAM space for block " + block.name + "(size " + block.size + ", space left: "+spaceLeftStr+")");
            }
            best.content.add(block);
            best.spaceLeft -= block.size;
        }
        
        for(Space s:spaces) {
            config.info("Space: left " + s.spaceLeft + ", content: " + s.content);
        }
    }
    
    
    // Slightly different version of the routine above, which is used to allocate
    // data in MegaROM pages, or 6128 RAM banks.
    public static void allocateBlocksIntoPages(List<Space> spaces, List<Block> blocksToAllocate, HashMap<String, String> assemblerVariables, PAKETConfig config) throws Exception {
        // Sort blocks by size:
        Collections.sort(blocksToAllocate, new Comparator<Block>() {
            @Override
            public int compare(SpaceAllocator.Block o1, SpaceAllocator.Block o2) {
                return -Integer.compare(o1.size, o2.size);
            }
        });

        for (SpaceAllocator.Block b : blocksToAllocate) {
            boolean allocated = false;
            for (int page = 0; page < spaces.size(); page++) {
                if (spaces.get(page).spaceLeft > 0 &&
                    spaces.get(page).spaceLeft >= b.size) {
                    spaces.get(page).spaceLeft -= b.size;
                    spaces.get(page).rawContent += b.assembler1 + "\n\n";
                    config.info("allocateBlocksIntoPages allocation: '" + b.name + "' (" + b.size + " bytes) allocated to page " + page);
                    if (b.spaceVariable != null) {
                        assemblerVariables.put(b.spaceVariable, "" + spaces.get(page).page);
                    }
                    allocated = true;
                    break;
                }
            }
            if (!allocated) {
                throw new Exception("No space for " + b.name + " (size " + b.size + ") in the cartridge!");
            }
        }
    }


    // This method is only used for debugging, and assigns things to as many different pages as possible,
    // to test the page swapping code:
    public static void allocateBlocksIntoPagesDebug(List<Space> spaces, List<Block> blocksToAllocate, HashMap<String, String> assemblerVariables, PAKETConfig config) throws Exception {
        // Sort blocks by size:
        Collections.sort(blocksToAllocate, new Comparator<Block>() {
            @Override
            public int compare(SpaceAllocator.Block o1, SpaceAllocator.Block o2) {
                return -Integer.compare(o1.size, o2.size);
            }
        });

        int nextPage = 0;
        for (SpaceAllocator.Block b : blocksToAllocate) {
            boolean allocated = false;
            for (int i = 0; i < spaces.size(); i++) {
                int page = (nextPage + i) % spaces.size();
                if (spaces.get(page).spaceLeft > 0 &&
                    spaces.get(page).spaceLeft >= b.size) {
                    spaces.get(page).spaceLeft -= b.size;
                    spaces.get(page).rawContent += b.assembler1 + "\n\n";
                    config.info("allocateBlocksIntoPages allocation: '" + b.name + "' (" + b.size + " bytes) allocated to page " + page);
                    if (b.spaceVariable != null) {
                        assemblerVariables.put(b.spaceVariable, "" + spaces.get(page).page);
                    }
                    allocated = true;
                    break;
                }
            }
            if (!allocated) {
                throw new Exception("No space for " + b.name + " (size " + b.size + ") in the cartridge!");
            }
            nextPage++;
        }
    }
}
