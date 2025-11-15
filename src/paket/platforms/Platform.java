/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.platforms;

import paket.compiler.PAKET;
import paket.compiler.PAKETConfig;
import java.awt.image.BufferedImage;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import paket.compiler.PAKETCompiler;
import paket.compiler.optimizers.PAKETOptimizer;
import paket.pak.PAKCutsceneImage;
import paket.pak.PAKGame;
import paket.pak.PAKItem;
import paket.pak.PAKObjectType;
import paket.pak.PAKRoom;
import paket.pak.PAKRule.PAKScript;
import paket.util.Pair;
import paket.util.Z80Assembler;

/**
 *
 * @author santi
 */
public abstract class Platform {    
    public int OBJECT_MASK_HORIZONTAL_RESOLUTION = 2;  // Width in pixels of a collision block
    public int OBJECT_MASK_VERTICAL_RESOLUTION = 4;  // Height in pixels of a collision block
    
    public int START_ADDRESS = 0;
    public int SCREEN_WIDTH_IN_TILES = 16;
    public int GAME_AREA_HEIGHT_IN_TILES = 11;
    public int TILE_WIDTH = 8;
    public int TEXT_AREA_WIDTH_IN_PIXELS = SCREEN_WIDTH_IN_TILES*TILE_WIDTH;
    public int INTERRUPTS_PER_FRAME = 1;
    public int SCREEN_LEFT_MOST_COORDINATE = 0;
    
    public int TEXT_AREA_DIMENSIONS[] = new int[]{0, 0, 16, 5};
    public int GAME_AREA_DIMENSIONS[] = new int[]{0, 5, 16, 11};
    public int GUI_DIMENSIONS[] = new int[]{0, 16, 16, 4};  // x1, y1, x2, y2 in tiles
    
    public String targetSystemName = null;
    public boolean saveByColumns = true;
    public HashMap<String, String> variables = null;    // these are global variables for compilation, it is shared in Platform and PAKGame

    public int INVENTORY_ITEMS_PER_LINE = 8;
    public int INVENTORY_ROWS = 2;

    public int PATH_FINDING_WALK_TILE_WIDTH = 4;
    
    public int basePSGReg7Value = 0xb8;
    public int PSGMasterFrequency = 111861;
    
    public PAKETConfig config;
    
    public List<String> supportedSaveGameModes = new ArrayList<>();
    public String defaultSaveGameMode = null;
    
    
    public Platform(HashMap<String, String> a_variables, PAKETConfig a_config)
    {
        variables = a_variables;
        config = a_config;
    }
    

    public void setTextAreaDimensions(int x, int y, int w, int h) {
        TEXT_AREA_DIMENSIONS[0] = x;
        TEXT_AREA_DIMENSIONS[1] = y;
        TEXT_AREA_DIMENSIONS[2] = w;
        TEXT_AREA_DIMENSIONS[3] = h;
        updateScreenWidth();
    }


    public void setGameAreaDimensions(int x, int y, int w, int h) {
        GAME_AREA_DIMENSIONS[0] = x;
        GAME_AREA_DIMENSIONS[1] = y;
        GAME_AREA_DIMENSIONS[2] = w;
        GAME_AREA_DIMENSIONS[3] = h;
        updateScreenWidth();
    }

    
    public void setGuiDimensions(int x, int y, int w, int h) {
        GUI_DIMENSIONS[0] = x;
        GUI_DIMENSIONS[1] = y;
        GUI_DIMENSIONS[2] = w;
        GUI_DIMENSIONS[3] = h;
        updateScreenWidth();
    }
    
    
    public void updateScreenWidth() {
        int minx = Math.min(TEXT_AREA_DIMENSIONS[0],
                                               Math.min(GAME_AREA_DIMENSIONS[0],
                                                        GUI_DIMENSIONS[0]));
        int maxx = Math.max(TEXT_AREA_DIMENSIONS[0] + TEXT_AREA_DIMENSIONS[2],
                                               Math.max(GAME_AREA_DIMENSIONS[0] + GAME_AREA_DIMENSIONS[2],
                                                        GUI_DIMENSIONS[0] + GUI_DIMENSIONS[2]));        
        SCREEN_LEFT_MOST_COORDINATE = minx;

        int sw = maxx - minx;
        SCREEN_WIDTH_IN_TILES = sw;
        TEXT_AREA_WIDTH_IN_PIXELS = SCREEN_WIDTH_IN_TILES*TILE_WIDTH;
    }
    

    public abstract void initPlatform(HashMap<String, String> assemblerVariables, PAKGame game);
    
    // Returns two arrays: one for code, and one for RAM blocks
    public abstract Pair<String[], String[]> saveGameCodeFileNames();

    // Palettes:
    // - Each platform has a "base palette", that is common throughout the game
    // - Then each room can have additional custom colors
    public abstract void clearBasePalette();
    public abstract void clearPalette(String paletteName);
    public abstract void setPalette(String paletteName, List<Integer> palette);
    public abstract void generateColorPalettes(PAKGame game, HashMap<String, String> assemblerVariables) throws Exception;
    public abstract List<Integer> getBasePalette();
    public abstract List<Integer> getPalette(String paletteName);
    public abstract int getPaletteID(String paletteName);
    
    // Adds colors from a given image to the base palette. It will error out, if
    // the base palette grows beyond the palette limit of the given platform.
    public abstract void addToBasePalette(String imageFileName) throws Exception;
    
    // If "paletteName" does not exist, it will create a new palette, starting
    // from the base palette. Then it will add colors from the image passed as
    // parameter. It will error out if the palette grows beyond the palette
    // limit of the given platform.
    public abstract void addToPalette(String imageFileName, String paletteName) throws Exception;
    public abstract void addToPalette(BufferedImage image, String paletteName, String imageName) throws Exception;
    
    public abstract int generateGUIData(BufferedImage guiImage, 
                                        String outputImageName, String asmFolder,
                                        String imageName, PAKGame game) throws Exception;

    public abstract void addItemData(BufferedImage sourceImage, 
                           int x0, int y0, int x1, int y1,
                           String ID, String name, String description,
                           String defaultUseMessage,
                           PAKGame game,
                           String imageName) throws Exception;    
    public abstract int generateItemData(String asmFolder, 
            List<String> itemIDs, HashMap<String, PAKItem> itemHash,
            HashMap<String, Pair<Integer, Integer>> textIDHash, String outputFolder,
            PAKGame game) throws Exception;

    public abstract int[] getTileData(BufferedImage img, int tile, String paletteName, String imageName) throws Exception;
    public abstract boolean isEmptyTile(int tile[]);
    public abstract int[] mirrorTileDataHorizontally(int tile[]) throws Exception;
    public abstract int getTileSizeInBytes();
    
    // Returns the accumulated size of the compressed banks.
    // If outputFolder == null, it just calculates the size, without saving anything to disk.
    public abstract int generateTileBanks(List<int []> mergedTiles, int bankSet_idx, String outputFolder, PAKGame game) throws Exception;
    
    public abstract List<Integer> convertObjectStateImageToBytes(BufferedImage imgToUse, int selection, PAKObjectType type, int xoffs, int yoffs, List<PAKRoom> rooms, String imageName) throws Exception;
    
    public abstract void roomVideoMemoryStartAddress(PAKRoom room, List<Integer> bytes);
    
    public abstract int extractPointers(String inputImagesName, String outputFolder) throws Exception;
    
    public abstract void screenVariables(int gui_width, int gui_height, int max_room_height, PAKGame game) throws Exception;
    
    public abstract void addLoadingScreen(String fileName, int mode_or_screen, String destinationFolder) throws Exception;
    public abstract void generateBinary(PAKGame game, String destinationFolder) throws Exception;
            
    public abstract Pair<Integer, Integer> generateCutsceneImageData(PAKCutsceneImage image, String destinationFolder) throws Exception;
    public abstract int getAdditionalCutsceneSpaceRequirement() throws Exception;  // Returns the number of extra bytes needed to render a cutscene in the general buffer, other than what is needed to decompress the data (e.g. to compute mirror tiles)
    
    public abstract int clearScreenType(String tag) throws Exception;
    public abstract void printScriptArguments(PAKScript s, PAKGame game, List<Integer> bytes) throws Exception;
    
    // This will add both the ptr to video memory where to draw it, and any other platform specific information.
    // For example in CPC, this adds a byte to mark if this is a mode 0 or mode 1 image.
    public abstract void addCutsceneImageMetaData(int x, int y, PAKCutsceneImage image, List<Integer> bytes) throws Exception;
    
    public abstract void MemoryAllocation(String outputFolder, HashMap<String, String> assemblerVariables, PAKGame game) throws Exception;
    public abstract List<Pair<String, String>> getAsmPatternsToInstantiate();

    public static int getSymbolTableAddress(String line) throws Exception
    {
        String hexAlphabet="0123456789ABCDEF";
        line = line.toUpperCase();
        StringTokenizer st = new StringTokenizer(line);
        st.nextToken(); // label
        st.nextToken(); // equ
        String hex = st.nextToken();
        if (hex.startsWith("0X")) {
            hex = hex.substring(2); // remove the startinc "0x"
        } else {
            hex = hex.substring(0, hex.length()-1); // remove the final "H"
        }
        int value = 0;
        for(int i= 0;i<hex.length();i++) {
            value = value*16 + hexAlphabet.indexOf(hex.charAt(i));
        }
        return value;
    }     
    
    
    public Pair<Integer, Integer> generateObjectTypeBanksInternal(String outputFolder, List<PAKObjectType> objectTypes, PAKGame game,
            PAKETOptimizer optimizerState) throws Exception {
        int largest_size = 0;
        int n_banks = 0;
        List<List<Integer>> bank_bytes = new ArrayList<>();
        game.objectTypeBankSizes = new ArrayList<>();
        if (config.maxObjectOptimizationIterations == 0) {
            List<PAKObjectType> objectTypes2 = (List<PAKObjectType>) optimizerState.heuristicCompressionOrder(objectTypes, config);            
            objectTypes.clear();
            objectTypes.addAll(objectTypes2);
        } else {
            List<PAKObjectType> objectTypes2 = (List<PAKObjectType>) optimizerState.optimizeCompressionOrder(objectTypes, config);
            objectTypes.clear();
            objectTypes.addAll(objectTypes2);
        }
        
        for(PAKObjectType ot:objectTypes) {
            List<Integer> bytes = ot.toBytesForAssembler(this, game.rooms, false, config);
            bank_bytes.add(bytes);
            config.info("    object type " + ot.ID + " uncompressed size: " + bytes.size());
            if (bank_bytes.size() >= config.objectTypesPerBank) {
                int uncompressed_bank_size = generateObjectTypeBank(outputFolder, bank_bytes, n_banks, game, config);
                if (uncompressed_bank_size > largest_size) largest_size = uncompressed_bank_size;
                bank_bytes.clear();
                n_banks++;
            }
        }

        if (!bank_bytes.isEmpty()) {
            int uncompressed_bank_size = generateObjectTypeBank(outputFolder, bank_bytes, n_banks, game, config);
            if (uncompressed_bank_size > largest_size) largest_size = uncompressed_bank_size;
            n_banks++;
        }
        return new Pair<>(n_banks, largest_size);
    }

    
    // returns the size of the largest bank:
    public abstract int generateObjectTypeBanks(String outputFolder, List<PAKObjectType> objectTypes, PAKGame game,
                                                PAKETOptimizer optimizerState) throws Exception;

    
    // returns the uncompressed bank size:
    public static int generateObjectTypeBank(String outputFolder, List<List<Integer>> bank_bytes, int bankNumber, PAKGame game, PAKETConfig config) throws Exception
    {
        int total_size = 0;
        FileWriter fw = new FileWriter(outputFolder + "data/objectTypeBank" + bankNumber + ".asm");
        for(int i = 0;i<bank_bytes.size();i++) {
            List<Integer> bytes = new ArrayList<>();
            int len = bank_bytes.get(i).size();
            bytes.add(len%256);
            bytes.add(len/256);
            bytes.addAll(bank_bytes.get(i));
            Z80Assembler.dataBlockToAssembler(bytes, "object_type_bank_object_" + i, fw, 16);
            total_size += bytes.size();
        }
        
        fw.close();
        PAKETCompiler.callMDL(new String[]{outputFolder + "data/objectTypeBank" + bankNumber + ".asm", "-bin", outputFolder + "data/objectTypeBank" + bankNumber + ".bin"}, config);
        int compressedSize = PAKET.compress(outputFolder + "data/objectTypeBank" + bankNumber + ".bin", outputFolder + "data/objectTypeBank" + bankNumber, config);
        game.objectTypeBankSizes.add(compressedSize);

        config.info("Object bank uncompressed / compressed size: " + total_size + " / " + compressedSize);
        return total_size;
    }


    // Returns the size of all the object banks + the size of the largest (since that will determine the
    // size of the general buffer, and we want to minimize that too).
    public static int estimateSizeOfAllObjectBanks(List<Integer> order, List<List<Integer>> object_bytes, int objects_per_bank, String compressor) throws Exception
    {
        int banks_size = 0;
        int largest_bank_size = 0;
        List<List<Integer>> bank_bytes = new ArrayList<>();
        for(int idx:order) {
            List<Integer> bytes = object_bytes.get(idx);
            bank_bytes.add(bytes);
            if (bank_bytes.size() >= objects_per_bank) {
                int uncompressed_bank_size = 0;
                for(List<Integer> tmp:bank_bytes) uncompressed_bank_size+=tmp.size();
                int bank_size = estimateSizeObjectBank(bank_bytes, compressor);
                banks_size += bank_size;
                if (uncompressed_bank_size > largest_bank_size) largest_bank_size = uncompressed_bank_size;
                bank_bytes.clear();
            }
        }   
        if (!bank_bytes.isEmpty()) {
            int uncompressed_bank_size = 0;
            for(List<Integer> tmp:bank_bytes) uncompressed_bank_size+=tmp.size();
            int bank_size = estimateSizeObjectBank(bank_bytes, compressor);
            banks_size += bank_size;
            if (uncompressed_bank_size > largest_bank_size) largest_bank_size = uncompressed_bank_size;
        }
        return banks_size + largest_bank_size;
    }   
    
    
    public static int estimateSizeObjectBank(List<List<Integer>> bank_bytes, String compressor) throws Exception
    {
        List<Integer> bytes = new ArrayList<>();
        for(int i = 0;i<bank_bytes.size();i++) {
            int len = bank_bytes.get(i).size();
            bytes.add(len%256);
            bytes.add(len/256);
            bytes.addAll(bank_bytes.get(i));
        }
        
        return PAKET.estimateCompressedSize(bytes, compressor);
    }
    
    
    public int inventorySize()
    {
        return INVENTORY_ITEMS_PER_LINE * INVENTORY_ROWS;        
    }

}
