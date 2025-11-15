/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.platforms;

import cl.MDLConfig;
import code.CodeBase;
import paket.compiler.PAKET;
import paket.compiler.PAKETCompiler;
import paket.compiler.PAKETConfig;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import javax.imageio.ImageIO;
import paket.compiler.SpaceAllocator;
import paket.compiler.optimizers.PAKETOptimizer;
import paket.pak.PAKCutsceneImage;
import paket.pak.PAKGame;
import paket.pak.PAKItem;
import paket.pak.PAKObject;
import paket.pak.PAKObjectType;
import paket.pak.PAKRoom;
import paket.pak.PAKRule.PAKScript;
import paket.tiles.ExtractTilesCPC;
import paket.tiles.TileBankCPC;
import paket.tiles.TileBankCPCMode1;
import paket.util.ConsoleExecution;
import paket.util.Pair;
import paket.util.Z80Assembler;
import paket.util.cpc2cdt.CPC2CDT;
import paket.util.idsk.IDsk;

/**
 *
 * @author santi
 */
public class CPC extends Platform {
    public static int TAPE_LOADER_START_ADDRESS = 0xa400;
    public static int DSK_LOADER_START_ADDRESS = 0xa400;
    public static final String END_OF_RAM_TAG = "end_of_RAM:";
    public static final String END_OF_CODE_TAG = "end_of_code:";
    public static final String START_OF_RAM_TAG = "start_of_RAM:";
    public static String DEFAULT_LOADER_PALETTE_STRING = "84, 68, 85, 92, 88, 76, 69, 87, 94, 64, 78, 71, 79, 91, 67, 75";
    public static String DEFAULT_DSK_LOADER_PALETTE_INDEXES_STRING = "0,  1,  2,  3,  4,  6,  7, 11, 12, 13, 15, 16, 17, 23, 25, 26, 0";
    public static List<String> LOADING_SCREEN_PALETTES = new ArrayList<>();
    public static List<String> LOADING_SCREEN_DSK_PALETTE_INDEXES = new ArrayList<>();

    public static List<Integer> LOADING_SCREEN_START_ADDRESSES = null;
    public static List<Integer> LOADING_SCREEN_SIZES = null;
    public static List<Integer> LOADING_SCREEN_SIZES_RLE = null;
    public static List<Integer> LOADING_SCREEN_MODES = null;
    
    public static boolean use_rle_for_loading_screens = false;
    
    public static final int TILES_PER_BANK = 16;
    
    public int SCREEN_HEIGHT_IN_TILES = 21;
    public int STACK_ADDRESS = 0xc000;
    
    public String LOADING_SCREEN_PAUSE = "5-sec";
//    public String LOADING_SCREEN_PAUSE = "keypress";
    
    
    public List<Integer> basePalette = new ArrayList<>();
    public HashMap<String, List<Integer>> palettes = new HashMap<>();
    List<int []> itemSprites = new ArrayList<>();
    List<SpaceAllocator.Space> spaces = null;
    
    HashMap<String, Integer> paletteNamesToIDs = new HashMap<>();
    
    // Blocks of data to allocate between RAM and VRAM:
    List<SpaceAllocator.Block> dataBanksToPotentiallyRelocateToVRAM = new ArrayList<>();
    
    public int objectTypeBanksTotalSize = 0;
    

    public CPC(HashMap<String, String> a_variables, PAKETConfig a_config)
    {
        super(a_variables, a_config);
        
        supportedSaveGameModes.clear();
        supportedSaveGameModes.add("tape");
        defaultSaveGameMode = "tape";
        
        TEXT_AREA_DIMENSIONS = new int[]{0, 0, 14, 5};
        GAME_AREA_DIMENSIONS = new int[]{0, 6, 14, 11};
        GUI_DIMENSIONS = new int[]{0, 17, 14, 4};
        
        START_ADDRESS = 0x0040;
        targetSystemName = "cpc";
        SCREEN_WIDTH_IN_TILES = 14;
        TEXT_AREA_WIDTH_IN_PIXELS = SCREEN_WIDTH_IN_TILES*2*8;
        basePSGReg7Value = 0x38;
        PSGMasterFrequency = 62500;
        INTERRUPTS_PER_FRAME = 6;      
        
        OBJECT_MASK_HORIZONTAL_RESOLUTION = 2;
        OBJECT_MASK_VERTICAL_RESOLUTION = 4;
    }

    
    @Override
    public void initPlatform(HashMap<String, String> assemblerVariables, PAKGame game) {
        assemblerVariables.put("IS_6128", "0");
        assemblerVariables.put("STACK_ADDRESS", "" + STACK_ADDRESS);
    }

    @Override
    public Pair<String[], String[]> saveGameCodeFileNames()
    {
        return new Pair<>(
                new String[]{"tape.asm"},
                new String[]{});
    }    
    
    @Override
    public void setGuiDimensions(int x, int y, int w, int h) {
        super.setGuiDimensions(x, y, w, h);
        SCREEN_HEIGHT_IN_TILES = y + h;
    }     

    
    @Override
    public void updateScreenWidth() {
        super.updateScreenWidth();
        TEXT_AREA_WIDTH_IN_PIXELS = SCREEN_WIDTH_IN_TILES*2*8;
    }
        
    
    @Override
    public void clearBasePalette()
    {    
        basePalette.clear();
    }
    

    @Override
    public void clearPalette(String paletteName)
    {
        List<Integer> palette = new ArrayList<>();
        palette.addAll(basePalette);
        palettes.put(paletteName, palette);
    }
    
    
    @Override
    public void setPalette(String paletteName, List<Integer> palette)
    {
        palettes.put(paletteName, palette);
    }
    
    
    @Override
    public List<Integer> getBasePalette()
    {
        return basePalette;
    }

    
    @Override    
    public List<Integer> getPalette(String paletteName)
    {
        if (!palettes.containsKey(paletteName)) {
            clearPalette(paletteName);
        }
        return palettes.get(paletteName);
    }
    
    
    @Override    
    public int getPaletteID(String paletteName)
    {
        return paletteNamesToIDs.get(paletteName);
    }    

    
    @Override
    public void addToBasePalette(String imageFileName) throws Exception
    {
        List<String> files = new ArrayList<>();
        files.add(imageFileName);
        CPCColors.paletteFromImageFileNames(files, basePalette, config);
        if (basePalette.size() > 15) {
            throw new Exception("Too many colors in the base palette ("+basePalette.size()+")!");
        }
    }

    
    @Override
    public void addToPalette(String imageFileName, String paletteName) throws Exception
    {
        List<String> files = new ArrayList<>();
        files.add(imageFileName);
        List<Integer> palette = getPalette(paletteName);
        CPCColors.paletteFromImageFileNames(files, palette, config);
        if (palette.size() > 15) {
            throw new Exception("Too many colors in palette "+paletteName+" ("+palette.size()+")! " + palette + "\n\tbasePalette: " + basePalette);
        }
    }
    
    
    @Override
    public void addToPalette(BufferedImage image, String paletteName, String imageName) throws Exception
    {
        List<BufferedImage> files = new ArrayList<>();
        files.add(image);
        List<Integer> palette = getPalette(paletteName);
        CPCColors.paletteFromImages(files, palette, imageName, config);
        if (palette.size() > 15) {
            throw new Exception("Too many colors in palette "+paletteName+" ("+palette.size()+")! " + palette + "\n\tbasePalette: " + basePalette);
        }
    }

    
    @Override
    public void generateColorPalettes(PAKGame game, HashMap<String, String> assemblerVariables) throws Exception
    {   
        // First, consolidate the room color palettes:
        for(String paletteName:palettes.keySet()) {
            for(String paletteName2:palettes.keySet()) {
                if (paletteName.equals(paletteName2)) continue;
                if (!paletteName.contains("room_")) continue;
                List<Integer> palette = palettes.get(paletteName);
                List<Integer> palette2 = palettes.get(paletteName2);
                boolean subset = true;
                for(int color:palette) {
                    if (!palette2.contains(color)) {
                        subset = false;
                        break;
                    }
                }
                if (subset) {
                    // replace the smallest by the largest:
                    palette.clear();
                    palette.addAll(palette2);
                }
            }
        }        
        
        // Find the different palettes (ignore the basePalette, as it's a subset of one of the others):
        List<List<Integer>> differentPalettes = new ArrayList<>();
        List<List<String>> differentPaletteNames = new ArrayList<>();
        for(String paletteName:palettes.keySet()) {
            List<Integer> palette = palettes.get(paletteName);
            int found = -1;
            for(int i = 0;i<differentPalettes.size();i++) {
                List<Integer> palette2 = differentPalettes.get(i);
                if (palette.equals(palette2)) {
                    found = i;
                    break;
                }
            }
            if (found < 0) {
                found = differentPalettes.size();
                differentPalettes.add(palette);
                differentPaletteNames.add(new ArrayList<>());
            }
            differentPaletteNames.get(found).add(paletteName);
            paletteNamesToIDs.put(paletteName, found);
        }
        
        // Set the palette of each room:
        for(PAKRoom room:game.rooms) {
            room.paletteID = paletteNamesToIDs.get("room_" + room.ID);
            if (room.paletteID == null) {
                throw new Exception("Something went wrong when fetching the paletteID for room " + room.ID + ". Please report this as a bug!");
            }
        }
        
        config.info("base palette: " + basePalette);
        config.info("Consolidated palettes of " + palettes.size() + " rooms/scripts into " + differentPalettes.size() + " palettes.");
        String assemblerCode = "";
        for(int i = 0;i<differentPalettes.size();i++) {
            assemblerCode += "    dw palette__" + i + "\n";
        }
        for(int i = 0;i<differentPalettes.size();i++) {
            assemblerCode += "palette__" + i + ":\n";
            for(String name:differentPaletteNames.get(i)) {
                assemblerCode += "palette_" + PAKETCompiler.nameToAssemblerLabel(name) + ":\n";                
            }
            assemblerCode += "    db " + CPCColors.CPCHardwareColorCodes[0];
            for(int j = 0;j<15;j++) {
                int color = 0;
                if (differentPalettes.get(i).size() > j) {
                    color = differentPalettes.get(i).get(j);
                }
                assemblerCode += ", " + CPCColors.CPCHardwareColorCodes[color];
            }
            assemblerCode += "\n";
        }
        assemblerVariables.put("GAME_COLOR_PALETTES", assemblerCode);
    }
    
    
    @Override
    public int generateGUIData(BufferedImage sourceImage, 
                               String outputImageName, String asmFolder, String imageName, PAKGame game) throws Exception
    {
        int outputWidth = 16*8; // This is the width of a file that has the different tiles of the GUI 
        List<int[]> tiles = new ArrayList<>();
                        
        List<Integer> indexes = ExtractTilesCPC.findTiles(sourceImage, tiles, imageName, false, this, config);
        config.info("Tiles in GUI: " + tiles.size());

        String indexes_data = "";
        for(int i = 0;i<sourceImage.getHeight()/PAKRoom.TILE_HEIGHT;i++) {
            indexes_data += "    db " + (indexes.get(0+i*(sourceImage.getWidth()/TILE_WIDTH)));
            for(int j = 1;j<sourceImage.getWidth()/TILE_WIDTH;j++) {
                indexes_data += ", " + (indexes.get(j+i*(sourceImage.getWidth()/TILE_WIDTH)));
            }
            indexes_data += "\n";
        }
                        
        int tilesPerRow = outputWidth/TILE_WIDTH;
        int outputHeight = ((tiles.size()+(tilesPerRow-1))/tilesPerRow)*PAKRoom.TILE_HEIGHT;
        BufferedImage img = new BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_ARGB);
        for(int i = 0;i<tiles.size();i++) {
            int x = (i%tilesPerRow)*TILE_WIDTH;
            int y = (i/tilesPerRow)*PAKRoom.TILE_HEIGHT;
            ExtractTilesCPC.drawTile(img, tiles.get(i), x, y, this);
        }
        // ImageIO.write(img, "png", new File(outputImageName));

        game.gui_compressed_size = TileBankCPC.generateAndCompressTilesAssemblerFileWithPrefix(
                indexes_data,
                img, basePalette, 
                0, tiles.size(), 
                asmFolder, "data/gui-data",
                this, config, imageName);

        int gui_tiles_size = (int)(new File(targetSystemName+"/src/data/gui-data.bin").length());
        variables.put("GUI_DATA", 
            "gui_data_compressed:\n" +
            "    incbin \"data/gui-data."+PAKETConfig.compressorExtension[config.compressor]+"\"\n");
        
        return gui_tiles_size;
    }
    
    
    public int[] extractItem(BufferedImage img, int x, int y, int width, int height, String imageName) throws Exception
    {
        int sprite[] = new int[width*height];
     
        int offs = 0;
        boolean allTransparent = true;
        for(int i = 0;i<height;i++) {
            for(int j = 0;j<width;j++) {
                sprite[offs] = CPCColors.getImageColor(img, x+j, y+i, imageName, config);
                if (sprite[offs]>=0) allTransparent = false;
                offs++;
            }
        }
        if (allTransparent) return null;
        
        int data[] = new int[sprite.length/2];
        for(int k = 0;k<sprite.length;k+=2) {
            int idx1 = basePalette.indexOf(sprite[k]);
            int idx2 = basePalette.indexOf(sprite[k+1]);
            if (idx1<0) throw new Exception("Item color not found in base palette! " + sprite[k] + " not in " + basePalette);
            if (idx2<0) throw new Exception("Item color not found in base palette! " + sprite[k+1] + " not in " + basePalette);
            idx1++; // add 1 to consider transparency
            idx2++; // add 1 to consider transparency
            int byteToWrite = CPCColors.mode0ColorTranslation[idx1]*2 + CPCColors.mode0ColorTranslation[idx2];
            data[k/2] =  byteToWrite;
        }
        
        this.config.debug("Item data ("+data.length+"): " + Arrays.toString(data));

        return data;
    }    


    @Override
    public void addItemData(BufferedImage sourceImage, 
                           int x0, int y0, int x1, int y1,
                           String ID, String name, String description,
                           String defaultUseMessage,
                           PAKGame game,
                           String imageName) throws Exception
    {
        itemSprites.add(extractItem(sourceImage, x0, y0, x1-x0, y1-y0, imageName));
        PAKItem item = new PAKItem(ID);
        item.inGameNameInLanguage = name;
        item.descriptionInLanguage = description;
        item.defaultUseMessage = defaultUseMessage;
        if (game.itemIDs.contains(ID)) {
            throw new Exception("Item type '" + ID + "' is defined twice!");
        }
        game.itemIDs.add(ID);
        game.itemHash.put(ID, item);
    }    
        
    
    @Override
    public int generateItemData(String asmFolder, 
            List<String> itemIDs, HashMap<String, PAKItem> itemHash, 
            HashMap<String, Pair<Integer,Integer>> textIDHash, String outputFolderRoot,
            PAKGame game) throws Exception
    {
        int item_data_uncompressed_size = 0;
        String outputFolder = outputFolderRoot+"/src/";
                
        FileWriter fw = new FileWriter(new File(outputFolder + "data/items.asm"));
        for(int i = 0;i<itemSprites.size();i++) {
            int []data = itemSprites.get(i);
            List<Integer> item_data = new ArrayList<>();
            for(int b:data) item_data.add(b);
            Z80Assembler.dataBlockToAssembler(item_data, "item_" + i, fw, 16);
            item_data_uncompressed_size += item_data.size();
        }        
        if (item_data_uncompressed_size == 0) {
            // special case when no item was defined (minimum size, so the compressor does not complain):
            fw.write("  db 0, 0, 0, 0\n");
            item_data_uncompressed_size = 4;
        }
        fw.close();

        PAKETCompiler.callMDL(new String[]{outputFolder + "data/items.asm", "-bin", outputFolder + "data/items.bin"}, config);
        PAKET.compress(outputFolder + "data/items.bin", outputFolder + "data/items", config);

        // Generate item text data:
        {
            List<Integer> name_lengths = new ArrayList<>();
            String itemTextData = "";
//            List<Integer> itemTextDataBytes = new ArrayList<>();
            for(String itemID:itemIDs) {
                PAKItem item = itemHash.get(itemID);
                String itemName = item.getInGameName();
                name_lengths.add(itemName.length()+1);
                Pair<Integer, Integer> name_idx = textIDHash.get(item.getInGameName());
                Pair<Integer, Integer> desc_idx = textIDHash.get(item.getDescription());
                String useMessage = game.cannotUseErrorMessage;
                if (item.defaultUseMessage != null) useMessage = item.defaultUseMessage;
                Pair<Integer, Integer> use_idx = textIDHash.get(useMessage);
                itemTextData += "  db " + name_idx.m_a + ", " + name_idx.m_b + ",  " +
                                          desc_idx.m_a + ", " + desc_idx.m_b + ",  " + 
                                          use_idx.m_a + ", " + use_idx.m_b +
                                          "  ; " + itemName + " \n";
//                itemTextDataBytes.add(name_idx.m_a);
//                itemTextDataBytes.add(name_idx.m_b);
//                itemTextDataBytes.add(desc_idx.m_a);
//                itemTextDataBytes.add(desc_idx.m_b);
//                itemTextDataBytes.add(use_idx.m_a);
//                itemTextDataBytes.add(use_idx.m_b);
            }
            // Calculate the size that we need for the buffer that will store inventory item names:
            Collections.sort(name_lengths, new Comparator<Integer>() {
                @Override
                public int compare(Integer o1, Integer o2) {
                    return o2.compareTo(o1);
                }
            });
            while(name_lengths.size() > inventorySize()) name_lengths.remove(name_lengths.size()-1);
            int inventory_names_buffer_size = 0;
            for(int l:name_lengths) inventory_names_buffer_size += l;

//            int compressedSize = PAKET.estimateCompressedSize(itemTextDataBytes, config);
//            config.info("Compressed item size: " + compressedSize);
            
            variables.put("INVENTORY_NAME_BUFFER_SIZE", ""+inventory_names_buffer_size);
            variables.put("ITEM_TEXT_DATA", itemTextData);
        }
        
        return item_data_uncompressed_size;
    }    
  
    
    @Override
    public int[] getTileData(BufferedImage img, int tile, String paletteName, String imageName) throws Exception
    {
        List<Integer> colorPalette = getPalette(paletteName);
        int tilesPerRow = img.getWidth()/TILE_WIDTH;
        int x = tile%tilesPerRow;
        int y = tile/tilesPerRow;
    
        int pixels[] = ExtractTilesCPC.getTile(img, x*TILE_WIDTH, y*PAKRoom.TILE_HEIGHT, imageName, this, config);
        
        int data[] = new int[pixels.length/2];
        for(int k = 0;k<pixels.length;k+=2) {
            int idx1 = colorPalette.indexOf(pixels[k]);
            int idx2 = colorPalette.indexOf(pixels[k+1]);
            idx1++; // add 1 to consider transparency
            idx2++; // add 1 to consider transparency
            int byteToWrite = CPCColors.mode0ColorTranslation[idx1]*2 + CPCColors.mode0ColorTranslation[idx2];
            data[k/2] = byteToWrite;
        }
        return data;
    }
    
    
    @Override
    public boolean isEmptyTile(int tile[])
    {
        int blackColor = 1;
        int blackByte = CPCColors.mode0ColorTranslation[blackColor]*2 + CPCColors.mode0ColorTranslation[blackColor];
        for(int b:tile) {
            if (b != blackByte) return false;
        }
        return true;
    }

    
    @Override
    public int[] mirrorTileDataHorizontally(int tile[])
    {
        int tile2[] = new int[tile.length];
        for(int i = 0;i<PAKRoom.TILE_HEIGHT;i++) {
            for(int j = 0;j<TILE_WIDTH/2;j++) {
//                int v = tile[j*PAKRoom.TILE_HEIGHT+i];
                int v = tile[i*TILE_WIDTH/2 + j];
                int color1 = v & 0x55;
                int color2 = v & 0xaa;
                int v2 = (color1 << 1) + (color2 >> 1);
//                tile2[(PAKRoom.TILE_WIDTH/2 - (j+1))*PAKRoom.TILE_HEIGHT+i] = v2;
                tile2[i*TILE_WIDTH/2 + (TILE_WIDTH/2 - (j+1))] = v2;
            }
        }
        return tile2;
    }
    
    
    @Override
    public int getTileSizeInBytes()
    {
        return 32;
    }
    
    
    @Override
    public int generateTileBanks(List<int []> mergedTiles, int bankSet_idx, String outputFolder, PAKGame game) throws Exception
    {
        int accumulatedSize = 0;
        String tileBanksCode1 = "tileBanksPointers_"+bankSet_idx+":\n";
        int nbanks = (mergedTiles.size() + TILES_PER_BANK - 1)/TILES_PER_BANK;
        if (game != null) {
            game.tileBankSizes = new ArrayList<>();
            game.tile_data_size = 0;
        }
        for(int i = 0;i<nbanks;i++) {
            int size = TileBankCPC.generateAndCompressTilesAssemblerFileByColumns(mergedTiles, 
                                                            i*TILES_PER_BANK, (i+1)*TILES_PER_BANK, 
                                                            outputFolder, "data/tileBank_" + bankSet_idx + "_" + i,
                                                            this, config);
            if (size > 0 && outputFolder != null) {
                config.info("    Tile bank " + bankSet_idx + " - " + i + ": " + size + " bytes");
                tileBanksCode1 += "    dw tileBank_" + bankSet_idx + "_" + i + "\n";
                if (game != null) {
                    game.tile_data_size += 2;
                }
                dataBanksToPotentiallyRelocateToVRAM.add(new SpaceAllocator.Block(
                        "data:tileBank_" + bankSet_idx + "_" + i,
                        "tileBank_"+bankSet_idx+"_"+i+":\n ds virtual "+size+"\n",
                        "tileBank_"+bankSet_idx+"_"+i+"_temporary:\n    incbin \"data/tileBank_" + bankSet_idx + "_" + i + "."+PAKETConfig.compressorExtension[config.compressor]+"\"\n",
                        size, null));
                String tmp = "";
                if (variables.containsKey("TILE_BANKS_DATA")) {
                    tmp = variables.get("TILE_BANKS_DATA") + "\n";
                }
                tmp += "tileBank_"+bankSet_idx+"_"+i+":\n    incbin \"data/tileBank_" + bankSet_idx + "_" + i + "."+PAKETConfig.compressorExtension[config.compressor]+"\"\n";
                variables.put("TILE_BANKS_DATA", tmp);
            }
            accumulatedSize += size;
            if (game != null) {
                game.tileBankSizes.add(size);
                game.tile_data_size += size;
            }
        }
        
        if (outputFolder != null) {
            String tmp = "";
            if (variables.containsKey("TILE_BANKS")) {
                tmp = variables.get("TILE_BANKS") + "\n";
            }
            variables.put("TILE_BANKS", tmp + tileBanksCode1 + "\n");
            
            String tmp2;
            if (variables.containsKey("TILE_BANKSETS")) {
                tmp2 = variables.get("TILE_BANKSETS");
            } else {
                tmp2 = "tileBankSetsPointers:\n";
            }
            variables.put("TILE_BANKSETS", tmp2 + "    dw tileBanksPointers_"+bankSet_idx+"\n");
            if (game != null) {
                game.tile_data_size += 2;
            }
        }
        return accumulatedSize;
    }
    
    
    @Override
    public int generateObjectTypeBanks(String outputFolder, List<PAKObjectType> objectTypes, PAKGame game,
            PAKETOptimizer optimizerState) throws Exception {
        Pair<Integer, Integer> tmp = generateObjectTypeBanksInternal(outputFolder, objectTypes, game, optimizerState);
        int n_banks = tmp.m_a;
        int largest_size = tmp.m_b;
        
        String objectBanksCode = "objectTypeBanksPointers:\n";
        for(int i = 0;i<n_banks;i++) {
            objectBanksCode += "    dw objectTypeBank" + i + "\n"; 
        }
        objectBanksCode += "\n";
        for(int i = 0;i<n_banks;i++) {
            int size = (int)(new File(outputFolder + "data/objectTypeBank" + i + "." + PAKETConfig.compressorExtension[config.compressor]).length());
            objectTypeBanksTotalSize += size;
            dataBanksToPotentiallyRelocateToVRAM.add(new SpaceAllocator.Block(
                    "data:objectTypeBank" + i,
                    "objectTypeBank"+i+":\n ds virtual "+size+"\n",
                    "objectTypeBank_"+i+"_temporary:\n    incbin \"data/objectTypeBank" + i + "."+PAKETConfig.compressorExtension[config.compressor]+"\"\n",
                    size, null));
        }
        
        variables.put("OBJECT_TYPE_BANKS", objectBanksCode);
        return largest_size;
    }    
    
    
    public boolean isEmptyImage(BufferedImage image)
    {
        for (int i = 0; i < image.getHeight(); i++) {
            for (int j = 0; j < image.getWidth(); j++) {
                int color = image.getRGB(j, i);
                int a = (color & 0xff000000) >> 24;
                if (a != 0) return false;
            }
        }
        return true;        
    }

    
    @Override
    public List<Integer> convertObjectStateImageToBytes(BufferedImage imgToUse, int selection, PAKObjectType type, int xoffs, int yoffs, List<PAKRoom> rooms, String imageName) throws Exception
    {
        List<Integer> bytes = new ArrayList<>();
        boolean imageIsEmpty = isEmptyImage(imgToUse);  // all transparent pixels image
        
        if (imageIsEmpty) {
            bytes.add(selection | 0x80);
            return bytes;
        } else {
            bytes.add(selection);
        }
        
        bytes.add(imgToUse.getWidth()/2);
        bytes.add(imgToUse.getHeight());
        
        List<Integer> usedColors = CPCColors.findColors(imgToUse, imageName, config);
        
        // 1) Find objects of this type in the rooms, and see if palettes are
        // consistent
        List<Integer> palette = null;
        for(PAKRoom room:rooms) {
            for(PAKObject o:room.objects) {
                if (o.type == type) {
                    List<Integer> roomPalette = getPalette("room_" + room.ID);
                    if (palette == null) {
                        palette = roomPalette;
                    } else {
                        boolean match = true;
                        if (roomPalette.size() < palette.size()) {
                            // always keep the smallest:
                            List<Integer> tmp = palette;
                            palette = roomPalette;
                            roomPalette = tmp;
                        }
                        
                        for(int color:usedColors) {
                            int idx1 = palette.indexOf(color);
                            int idx2 = roomPalette.indexOf(color);
                            if (idx1 != idx2) {
                                match = false;
                                break;
                            }
                        }
                        if (!match) {
                            throw new Exception("CPC: object type "+type.ID+" appears in rooms where the object colors have different indexes in the room's palettes!\n\tusedColors: " + usedColors + "\n\t" + palette + "\n\t" + roomPalette);
                        }
                    }
                }
            }
        }
        
        if (palette == null) {
            throw new Exception("CPC: object type "+type.ID+" appears in no room!");
        }
        
        for(int j = 0;j<imgToUse.getWidth();j+=2) {
            for(int i = 0;i<imgToUse.getHeight();i++) {
                int c1 = CPCColors.getImageColor(imgToUse, j, i, imageName, config);
                int c2 = CPCColors.getImageColor(imgToUse, j+1, i, imageName, config);
                bytes.add(CPCColors.Mode02ColorBlockByte(c1, c2, palette));
            }
        }
        return bytes;
    }    
    
    
    @Override
    public void roomVideoMemoryStartAddress(PAKRoom room, List<Integer> bytes)
    {
        // In CPC, we do not compute the video memory address, but the offset from the top left in x and y:
        bytes.add((room.screen_position_x + GAME_AREA_DIMENSIONS[0] - SCREEN_LEFT_MOST_COORDINATE)*4);  // in bytes
        bytes.add(room.screen_position_y + GAME_AREA_DIMENSIONS[1]);  // in tiles
    }
            
    
    @Override
    public int extractPointers(String inputImagesName, String outputFolder) throws Exception
    {        
        int width = 8;
        List<int[]> pointers = new ArrayList<>();
        
        //int emptyTile[] = new int[tileHeight*tileWidth];
        //for(int i = 0;i<emptyTile.length;i++) emptyTile[i] = -1;
        //tiles.add(emptyTile);
        
        BufferedImage sourceImage = ImageIO.read(new File(inputImagesName));
        for(int i = 0;i<sourceImage.getWidth()/width;i++) {
            int pointer[] = extractPointer(sourceImage, i*width, 0, width, sourceImage.getHeight(), inputImagesName);
            pointers.add(pointer);
            int pointer_shifted[] = new int[pointer.length];
            pointer_shifted[0] = -1;
            for(int j = 0;j<pointer.length-1;j++) {
                pointer_shifted[j+1] = pointer[j];
            }
            pointers.add(pointer_shifted);
        }

        // generate assembler data:
        PAKET.createFoldersIfNecessary(outputFolder);
        FileWriter fw = new FileWriter(new File(outputFolder + "pointers.asm"));
        for(int []pointer:pointers) {
            List<Integer> data= new ArrayList<>();
            for(int k = 0;k<pointer.length;k+=2) {
                int idx1 = basePalette.indexOf(pointer[k]);
                int idx2 = basePalette.indexOf(pointer[k+1]);
                idx1++; // add 1 to consider transparency
                idx2++; // add 1 to consider transparency
                int byteToWrite = CPCColors.mode0ColorTranslation[idx1]*2 + CPCColors.mode0ColorTranslation[idx2];
                data.add(byteToWrite);
            }
            Z80Assembler.dataBlockToAssembler(data, "pointer_" + pointers.indexOf(pointer), fw, width/2);
        }
        fw.close();
        PAKETCompiler.callMDL(new String[]{outputFolder + "pointers.asm", "-bin", outputFolder + "pointers.bin"}, config);
        PAKET.compress(outputFolder + "pointers.bin", outputFolder + "pointers", config);

        return 0;
    }
    

    public int[] extractPointer(BufferedImage img, int x, int y, int width, int height, String imageName)
    {
        int tile[] = new int[width*height];
        
        int offs = 0;
        boolean allTransparent = true;
        for(int i = 0;i<height;i++) {
            for(int j = 0;j<width;j++) {
                tile[offs] = CPCColors.getImageColor(img, x+j, y+i, imageName, config);
                if (tile[offs]>=0) allTransparent = false;
                offs++;
            }
        }
        if (allTransparent) return null;
        return tile;
    }
    
    
    public int videoMemPointerFromCoordinates(int x, int y)
    {
        int width_in_bytes_game_screen = SCREEN_WIDTH_IN_TILES*2*2;
        int videomem = 12*4096;
        int y_tiles = y/8;
        int y_pixels = y%8;
        return (videomem + (x/2) + y_tiles*width_in_bytes_game_screen + y_pixels*2048);
    }
    
    
    @Override
    public void screenVariables(int gui_width, int gui_height, int max_room_height, PAKGame game) throws Exception
    {
        // Check that area dimensions do not violate constraints:
        if (TEXT_AREA_DIMENSIONS[0] != SCREEN_LEFT_MOST_COORDINATE) {
            throw new Exception("Text area must use all the horizontal space in CPC (starting at "+SCREEN_LEFT_MOST_COORDINATE+" based on game area and gui area definitions), but starts at "+TEXT_AREA_DIMENSIONS[0]+"!");
        }
        if (TEXT_AREA_DIMENSIONS[2] != SCREEN_WIDTH_IN_TILES) {
            throw new Exception("Text area must use all the horizontal space in CPC ("+SCREEN_WIDTH_IN_TILES+" based on game area and gui area definitions), but uses only "+TEXT_AREA_DIMENSIONS[2]+"!");
        }
        if (TEXT_AREA_DIMENSIONS[3] != 5) {
            throw new Exception("Text area must be 5 tiles tall in CPC, but is "+TEXT_AREA_DIMENSIONS[3]+"!");
        }
        if (GAME_AREA_DIMENSIONS[1] < 6) {
            throw new Exception("Game are must start at least in y coordiante 6 in CPC, but starts at " + GAME_AREA_DIMENSIONS[1]+"!");
        }
        if (TEXT_AREA_DIMENSIONS[1] + TEXT_AREA_DIMENSIONS[3] > GAME_AREA_DIMENSIONS[1]) {
            throw new Exception("Game area top coordinate ("+ (GAME_AREA_DIMENSIONS[1]) +") is lower than the bottom of the text area ("+(TEXT_AREA_DIMENSIONS[1] + TEXT_AREA_DIMENSIONS[3])+")!");
        }
        if (GAME_AREA_DIMENSIONS[1] + GAME_AREA_DIMENSIONS[3] > GUI_DIMENSIONS[1]) {
            throw new Exception("GUI top coordinate ("+ (GUI_DIMENSIONS[1]) +") is lower than the bottom of the text area ("+(GAME_AREA_DIMENSIONS[1] + GAME_AREA_DIMENSIONS[3])+")!");
        }
        if (GUI_DIMENSIONS[1] < 16) {
            throw new Exception("GUI must start at least in Y coordinate 16, but starts at " + GUI_DIMENSIONS[1] + "!");
        }
        if (TEXT_AREA_DIMENSIONS[0] < 0 || TEXT_AREA_DIMENSIONS[0] + TEXT_AREA_DIMENSIONS[2] > 16 ||
            TEXT_AREA_DIMENSIONS[1] < 0 || TEXT_AREA_DIMENSIONS[1] + TEXT_AREA_DIMENSIONS[3] > 24) {
            throw new Exception("Text area is not withiin the MSX screen (remember, it is divided into 16x24 PAKET tiles).");
        }
        if (GAME_AREA_DIMENSIONS[0] < 0 || GAME_AREA_DIMENSIONS[0] + GAME_AREA_DIMENSIONS[2] > 16 ||
            GAME_AREA_DIMENSIONS[1] < 0 || GAME_AREA_DIMENSIONS[1] + GAME_AREA_DIMENSIONS[3] > 24) {
            throw new Exception("Game area is not withiin the MSX screen (remember, it is divided into 16x24 PAKET tiles).");
        }
        if (GUI_DIMENSIONS[0] < 0 || GUI_DIMENSIONS[0] + GUI_DIMENSIONS[2] > 16 ||
            GUI_DIMENSIONS[1] < 0 || GUI_DIMENSIONS[1] + GUI_DIMENSIONS[3] > 24) {
            throw new Exception("GUI area is not withiin the MSX screen (remember, it is divided into 16x24 PAKET tiles).");
        }
                
        
        int max_width_needed_in_mode0 = SCREEN_WIDTH_IN_TILES * 2;  // Width in "characters"
        int width_in_bytes_game_screen = max_width_needed_in_mode0 * 2;
        int mode0_horizontal_sync = 46 - (40 - max_width_needed_in_mode0)/2;
        int FIRST_SCREEN_ROOM_ROW = GAME_AREA_DIMENSIONS[1];
        
        variables.put("SCREEN_MODE0_INTERRUPT_COUNT", "2");
        variables.put("SCREEN_VERTICAL_SYNC", "" + 30);  // original value
        variables.put("SCREEN_VERTICAL_DISPLAYED", "" + SCREEN_HEIGHT_IN_TILES);
        variables.put("SCREEN_MODE0_HORIZONTAL_SYNC", "" + mode0_horizontal_sync); // default is 46, and subtracting, we move to the right
        variables.put("SCREEN_MODE0_WIDTH", "" + max_width_needed_in_mode0);
        variables.put("SCREEN_WIDTH_IN_BYTES", "" + width_in_bytes_game_screen);
        variables.put("GAME_AREA_HEIGHT_IN_TILES", "" + GAME_AREA_HEIGHT_IN_TILES);
        variables.put("FIRST_SCREEN_ROOM_ROW", "" + FIRST_SCREEN_ROOM_ROW);
        variables.put("FIRST_SCREEN_ROOM_COLUMN", "" + ((GAME_AREA_DIMENSIONS[0] - SCREEN_LEFT_MOST_COORDINATE)*4));
        
        int gui_start_x = (GUI_DIMENSIONS[0] - SCREEN_LEFT_MOST_COORDINATE)*8;
        int gui_start_y = GUI_DIMENSIONS[1]*8;
        int inventory_start_x = gui_start_x + 24 + 4;
        int inventory_start_y = gui_start_y + 3;
        int inventory_item_height = 12;
        variables.put("GUI_VIDEO_MEM_START", "" + videoMemPointerFromCoordinates(gui_start_x, gui_start_y));
        variables.put("INVENTORY_VIDEO_MEM_START", "" + videoMemPointerFromCoordinates(inventory_start_x, inventory_start_y));
        variables.put("INVENTORY_VIDEO_MEM_START_LINE2", "" + videoMemPointerFromCoordinates(inventory_start_x, inventory_start_y + inventory_item_height + 2));
        variables.put("INVENTORY_START_X",  "" + inventory_start_x);
        variables.put("INVENTORY_START_Y",  "" + (inventory_start_y - FIRST_SCREEN_ROOM_ROW*8));
        variables.put("GUI_START_X",  "" + gui_start_x);
        variables.put("GUI_START_Y",  "" + (gui_start_y - FIRST_SCREEN_ROOM_ROW*8));
        variables.put("INVENTORY_ROWS", "" + INVENTORY_ROWS);
        variables.put("INVENTORY_ITEMS_PER_LINE", "" + INVENTORY_ITEMS_PER_LINE);
        
        variables.put("MIN_POINTER_X", "0");
        variables.put("MAX_POINTER_X", "" + (SCREEN_WIDTH_IN_TILES*TILE_WIDTH-7));
        variables.put("MIN_POINTER_Y", "0");
        variables.put("MAX_POINTER_Y", "" + ((GUI_DIMENSIONS[1] + GUI_DIMENSIONS[3] - FIRST_SCREEN_ROOM_ROW) * 8 - 12));
        variables.put("MAX_POINTER_Y", "" + ((GAME_AREA_HEIGHT_IN_TILES+1+gui_height)*PAKRoom.TILE_HEIGHT-12));
        
        // Calculate how much objects overflow from below rooms, to allow drawing objects that
        // go beyond the room bottom edge:
        int max_object_overflow_from_below = 0;
        for(PAKRoom room:game.rooms) {
            for(PAKObject object:room.objects) {
                int maxy = object.y + object.type.getPixelHeight(object.state, object.direction);
                int overflow = maxy - room.height * 8;
                if (overflow > max_object_overflow_from_below) {
                    max_object_overflow_from_below = overflow;
                }
            }
        }
        this.config.info("max_object_overflow_from_below = " + max_object_overflow_from_below);
        variables.put("COLUMN_DRAW_BUFFER_SIZE", "MAX_ROOM_HEIGHT*8 + " + max_object_overflow_from_below);
        
        // Safety checks:
        if (INVENTORY_ITEMS_PER_LINE <= 0 || INVENTORY_ITEMS_PER_LINE > (gui_width - 4)) {
            throw new Exception("Inventory can have between 1 and " + (gui_width - 4) + " items in a row, but " + INVENTORY_ITEMS_PER_LINE + " were requested.");
        }
        if (INVENTORY_ROWS <= 0 || INVENTORY_ROWS > 2) {
            throw new Exception("Inventory can have between 1 and 2 rows in CPC, but " + INVENTORY_ROWS + " were requested.");
        }        
    }
    
    
    @Override
    public void addLoadingScreen(String fileName, int mode_or_screen, String destinationFolder) throws Exception
    {
        if (mode_or_screen == -1) mode_or_screen = 0;
        int palette_size = 16;
        if (mode_or_screen == 1) palette_size = 4;
        
        // Extract color palette:
        File f = new File(fileName);
        BufferedImage sourceImageForPalette = ImageIO.read(f);
        List<Integer> colorPalette = CPCColors.findColors(sourceImageForPalette, fileName, config);
        while(colorPalette.size() < palette_size) colorPalette.add(0);
        String LOADER_PALETTE_STRING = null;
        String DSK_LOADER_PALETTE_INDEXES_STRING = null;
        for(int i = 0;i<palette_size;i++) {
            if (LOADER_PALETTE_STRING == null) {
                LOADER_PALETTE_STRING = "" + CPCColors.CPCHardwareColorCodes[colorPalette.get(i)];
                DSK_LOADER_PALETTE_INDEXES_STRING = "" + colorPalette.get(i);
            } else {
                LOADER_PALETTE_STRING += ", " + CPCColors.CPCHardwareColorCodes[colorPalette.get(i)];
                DSK_LOADER_PALETTE_INDEXES_STRING += ", " + colorPalette.get(i);
            }
        }
        
        // Generate binary file:
        f = new File(fileName);
        BufferedImage sourceImage = ImageIO.read(f);
        List<Integer> data = new ArrayList<>();                
        switch(mode_or_screen) {
            case 0:
                if (sourceImage.getWidth() != 160) {
                    throw new Exception("Mode 0 loading screen width is not 160! " + sourceImage.getWidth());
                }
                for(int i = 0;i<sourceImage.getHeight();i++) {
                    for(int j = 0;j<sourceImage.getWidth();j+=2) {
                        int idx1 = CPCColors.getImageColorIndex(sourceImage, j, i, colorPalette, fileName, config);
                        int idx2 = CPCColors.getImageColorIndex(sourceImage, j+1, i, colorPalette, fileName, config);
                        if (idx1 == -1 || idx2 == -1) config.error("Color not found at " + j + ", " + i);
                        int byteToWrite = CPCColors.mode0ColorTranslation[idx1]*2 + CPCColors.mode0ColorTranslation[idx2];
                        data.add(byteToWrite);
                    }
                }
                break;
                
            case 1:
                if (mode_or_screen == 1 && sourceImage.getWidth() != 320) {
                    throw new Exception("Mode 1 loading screen width is not 320! " + sourceImage.getWidth());
                }
                for(int i = 0;i<sourceImage.getHeight();i++) {
                    for(int j = 0;j<sourceImage.getWidth();j+=4) {
                        int idx1 = CPCColors.getImageColorIndex(sourceImage, j, i, colorPalette, fileName, config);
                        int idx2 = CPCColors.getImageColorIndex(sourceImage, j+1, i, colorPalette, fileName, config);
                        int idx3 = CPCColors.getImageColorIndex(sourceImage, j+2, i, colorPalette, fileName, config);
                        int idx4 = CPCColors.getImageColorIndex(sourceImage, j+3, i, colorPalette, fileName, config);
                        if (idx1 == -1 || idx2 == -1 || idx3 == -1 || idx4 == -1) config.error("Color not found at " + j + ", " + i);
                        int byteToWrite = 8*CPCColors.mode1ColorTranslation[idx1] + 4*CPCColors.mode1ColorTranslation[idx2] + 2*CPCColors.mode1ColorTranslation[idx3] + CPCColors.mode1ColorTranslation[idx4];
                        data.add(byteToWrite);
                    }
                }
                break;
        }

        int finalData[] = new int[2048*8];
        for(int i = 0;i<16000;i++) {
            int y = i/80;
            int x = i%80;
            int y_pixel = y%8;
            int y_row = y/8;
            finalData[y_pixel*2048+y_row*80+x] = data.get(i);
        }
        if (LOADING_SCREEN_START_ADDRESSES == null) {
            LOADING_SCREEN_START_ADDRESSES = new ArrayList<>();
            LOADING_SCREEN_SIZES = new ArrayList<>();
            LOADING_SCREEN_SIZES_RLE = new ArrayList<>();
            LOADING_SCREEN_MODES = new ArrayList<>();
        }
        
        List<Integer> finalDataList = new ArrayList<>();
        for(int i=0;i<8;i++) {
            for(int j=0;j<2048;j++) {
                finalDataList.add(finalData[i*2048+j]);
            }
        }
        
        // Skip all the initial/final bytes that are 0 to save some loading time:
        int startAddress = 0xc000;
        while(finalDataList.get(0) == 0) {
            startAddress++;
            finalDataList.remove(0);
        }
        while(finalDataList.get(finalDataList.size()-1) == 0) {
            finalDataList.remove(finalDataList.size()-1);
        }
        int screen_index = LOADING_SCREEN_SIZES.size();
        
        LOADING_SCREEN_START_ADDRESSES.add(startAddress);
        LOADING_SCREEN_SIZES.add(finalDataList.size());
        LOADING_SCREEN_SIZES_RLE.add(finalDataList.size());
        LOADING_SCREEN_MODES.add(mode_or_screen);
        LOADING_SCREEN_PALETTES.add(LOADER_PALETTE_STRING);
        LOADING_SCREEN_DSK_PALETTE_INDEXES.add(DSK_LOADER_PALETTE_INDEXES_STRING);
        variables.put("LOADING_SCREEN_ADDRESS","" + startAddress);
        // RAW:
        {
            FileWriter fw = new FileWriter(destinationFolder + "/src/data/screen" + screen_index + ".asm");
            Z80Assembler.dataBlockToAssembler(finalDataList, "loadingScreen_block", fw, 80);
            fw.close();
            PAKETCompiler.callMDL(new String[]{destinationFolder + "/src/data/screen" + screen_index + ".asm", "-bin", destinationFolder + "/src/data/screen" + screen_index + ".bin"}, config);
        }

        // RLE:
        List<Integer> finalDataListRLE = encodeDataUsingRLE(finalDataList);
        {
            FileWriter fw = new FileWriter(destinationFolder + "/src/data/screenRLE" + screen_index + ".asm");
            Z80Assembler.dataBlockToAssembler(finalDataListRLE, "loadingScreen_block", fw, 80);
            fw.close();
            PAKETCompiler.callMDL(new String[]{destinationFolder + "/src/data/screenRLE" + screen_index + ".asm", "-bin", destinationFolder + "/src/data/screenRLE" + screen_index + ".bin"}, config);
        }
    }   
    
    
    public List<Integer> encodeDataUsingRLE(List<Integer> data) 
    {
        List<Integer> encoded = new ArrayList<>();
        int meta = 0xff;
        int maxSequence = 16;
        int previousValue = -1;
        int count = 0;
        for(int i = 0;i<data.size();i++) {
            int value = data.get(i);
            if (value == previousValue) {
                count++;
            } else {
                while(count > 0) {
                    if (count<3 && previousValue != meta) {
                        for(int j = 0;j<count;j++) {
                            encoded.add(previousValue);
                        }
                        count = 0;
                    } else {
                        int n = Math.min(maxSequence, count);
                        encoded.add(meta);
                        encoded.add(n);
                        encoded.add(previousValue);
                        count -= n;
                    }
                }
                previousValue = value;
                count = 1;
            }
        }

        while(count > 0) {
            if (count<3 && previousValue != meta) {
                for(int j = 0;j<count;j++) {
                    encoded.add(previousValue);
                }
                count = 0;
            } else {
                encoded.add(meta);
                int n = Math.min(maxSequence, count);
                encoded.add(meta);
                encoded.add(n);
                encoded.add(previousValue);
                count -= n;
            }
        }

        return encoded;
    }
              
    
    @Override
    public Pair<Integer, Integer> generateCutsceneImageData(PAKCutsceneImage image, String destinationFolder) throws Exception
    {
        List<Integer> data = new ArrayList<>();
        
        // The data will be: palette size, palette, w, h, tiles size, tiles, nametable
        
        // Palette:
        List<Integer> palette = new ArrayList<>();
        List<BufferedImage> images = new ArrayList<>();
        images.add(image.tiles);
        CPCColors.paletteFromImages(images, palette, image.name, config);
        boolean storePalette = true;
        boolean externalPalette = false;
        int mode = 0;
        int maxPaletteSize = 16;
        for(String option:image.platformOptions) {
            switch(option) {
                case "cpc-mode0":
                    maxPaletteSize = 16;
                    mode = 0;
                    break;
                case "cpc-mode1":
                    maxPaletteSize = 4;
                    mode = 1;
                    break;
                case "no-palette":
                    storePalette = false;
                    break;
                default:
                    if (option.startsWith("palette=")) {
                        String paletteName = option.substring("palette=".length());
                        if (!palettes.containsKey(paletteName)) {
                            throw new Exception("Undefined palette " + paletteName + " in the definition of cutsceneImage " + image.name);
                        }
                        palette.clear();
                        palette.addAll(palettes.get(paletteName));
                        storePalette = false;
                        externalPalette = true;
                    } else {
                        throw new Exception("generateCutsceneImageData: Unrecognized platform option " + option);
                    }
            }
                    
        }
        if (palette.get(0) != 0 || externalPalette) {
            palette.add(0, 0);
        }
        if (palette.size() > maxPaletteSize) {
            throw new Exception("Too many colors in cutscene image " + image.name + ": " + palette);
        }
        if (storePalette) {
            data.add(palette.size());
            for(int color:palette) {
                data.add(CPCColors.CPCHardwareColorCodes[color]);
            }
        } else {
            data.add(0);
        }
        
        config.info("generateCutsceneImageData: "+image.name+" palette " + palette);
        
        // Tiles:
        List<Integer> tileData;
        if (mode == 0) {
            tileData = TileBankCPC.generateCutSceneTileData(image.tiles, palette, image.name, this, config);            
        } else {
            tileData = TileBankCPCMode1.generateCutSceneTileData(image.tiles, palette, image.name, this, config);            
        }
        data.add(tileData.size()%256);
        data.add(tileData.size()/256);
        data.addAll(tileData);
        
        // Name table:
        data.add(image.width);
        data.add(image.height);
        for(int i = 0;i<image.height;i++) {
            for(int j = 0;j<image.width;j++) {
                data.add(image.nameTable[j][i]-1);
            }
        }
        
        String outputFolder = destinationFolder + "/src/data/cutscenes/";
        PAKET.createFoldersIfNecessary(outputFolder);
        String fileName = "cutscene-" + image.assembler_name;
        FileOutputStream fos = new FileOutputStream(outputFolder + fileName + ".bin");
        for(int v:data) {
            fos.write(v);
        }
        fos.close();
        int compressedSize = PAKET.compress(outputFolder + fileName + ".bin", outputFolder + fileName, config);        
        return new Pair<>(data.size(), compressedSize);
    }
    

    @Override
    public int getAdditionalCutsceneSpaceRequirement()
    {
        return 32;
    }
    
    
    @Override
    public int clearScreenType(String tag) throws Exception
    {
        switch(tag) {
            case "cpc-mode0":
                return 2;
            case "cpc-mode1":
                return 1;
            case "cpc-split-mode1-mode0":
                return 0;
            default:
                throw new Exception("Unsupported clear-screen parameter " + tag);
        }
    }

    
    @Override
    public void printScriptArguments(PAKScript s, PAKGame game, List<Integer> bytes) throws Exception
    {
        int VIDEO_MEMORY = 0xc000;
        int SCREEN_WIDTH_IN_BYTES = SCREEN_WIDTH_IN_TILES*4;
        int y = s.y / 8;  // round to 8 pixels
        int x = s.x / 4;  // round to 4 pixels
        int address = VIDEO_MEMORY + y*SCREEN_WIDTH_IN_BYTES + x;
        bytes.add(address%256);
        bytes.add(address/256);
        int colorTable[] = {0, 0x80, 0x08, 0x88};
        bytes.add(colorTable[s.color]);
    }
    
    
    @Override
    public void addCutsceneImageMetaData(int x, int y, PAKCutsceneImage image, List<Integer> bytes) throws Exception
    {
        int VIDEO_MEMORY = 0xc000;
        int SCREEN_WIDTH_IN_BYTES = SCREEN_WIDTH_IN_TILES*4;
        
        if (image.platformOptions.contains("cpc-mode1")) {
            int address = VIDEO_MEMORY + y*SCREEN_WIDTH_IN_BYTES + x*2;
            bytes.add(address%256);
            bytes.add(address/256);
            bytes.add(1);
        } else {
            int address = VIDEO_MEMORY + y*SCREEN_WIDTH_IN_BYTES + x*4;
            bytes.add(address%256);
            bytes.add(address/256);
            bytes.add(0);            
        }
    }

    
    @Override
    public void generateBinary(PAKGame game, String destinationFolder) throws Exception
    {
//        int max_first_binary_piece_size = 40*1024 - 64;
//        int max_second_binary_piece_size = 8*1024;
        int max_first_binary_piece_size_tape = TAPE_LOADER_START_ADDRESS - START_ADDRESS;
        int max_second_binary_piece_size_tape = 8*1024;
        int max_binary_size_tape = max_first_binary_piece_size_tape + max_second_binary_piece_size_tape;

        int max_first_binary_piece_size_dsk = 0xa000 - START_ADDRESS;
        int max_second_binary_piece_size_dsk = 8*1024;
        int max_binary_size_dsk = max_first_binary_piece_size_dsk + max_second_binary_piece_size_dsk;
                
        String binaryName = destinationFolder + File.separator + "pac-" + game.language;
        String loaderName = destinationFolder + File.separator + "loader-" + game.language;
        if (config.run_mdl_optimizers) {
            config.diggest("PAKETCompiler: compiling game (with MDL optimization)...");
            PAKETCompiler.callMDL(new String[]{destinationFolder + "/src/main-autogenerated.asm", "-bin", binaryName+".bin", "-st", binaryName+".sym", "-st-constants", "-po", "-ro", "-cpu", "z80cpc", "-asm+:html", binaryName + ".html"}, config);
        } else {
            config.diggest("PAKETCompiler: compiling game...");
            PAKETCompiler.callMDL(new String[]{destinationFolder + "/src/main-autogenerated.asm", "-bin", binaryName+".bin", "-st", binaryName+".sym", "-st-constants", }, config);
        }
        int binary_size = (int)(new File(binaryName+".bin").length());
        config.info("    binary size: " + binary_size);
        if (binary_size >= max_binary_size_tape) throw new Exception("Binary sizes larger than "+max_binary_size_tape+" are not possible in the CPC 464 tape loader!");
        if (binary_size >= max_binary_size_dsk) throw new Exception("Binary sizes larger than "+max_binary_size_dsk+" are not possible in the CPC 464 disk loader!");
        
        // Split binary into pieces, if necessary:
        if (binary_size > max_first_binary_piece_size_tape) {
            InputStream is = new FileInputStream(binaryName+".bin"); 
            FileOutputStream os1 = new FileOutputStream(binaryName+"-1.bin"); 
            FileOutputStream os2 = new FileOutputStream(binaryName+"-2.bin"); 
            byte binary_bytes_part1[] = new byte[max_first_binary_piece_size_tape];
            byte binary_bytes_part2[] = new byte[binary_size - max_first_binary_piece_size_tape];
            for(int i = 0;i<binary_bytes_part1.length;i++) {
                os1.write(is.read());
            }
            for(int i = 0;i<binary_bytes_part2.length;i++) {
                os2.write(is.read());
            }
            is.close();
            os1.close();
            os2.close();
        }
        
        if (binary_size > max_first_binary_piece_size_dsk) {
            InputStream is = new FileInputStream(binaryName+".bin"); 
            FileOutputStream os1 = new FileOutputStream(binaryName+"-dsk1.bin"); 
            FileOutputStream os2 = new FileOutputStream(binaryName+"-dsk2.bin"); 
            byte binary_bytes_part1[] = new byte[max_first_binary_piece_size_dsk];
            byte binary_bytes_part2[] = new byte[binary_size - max_first_binary_piece_size_dsk];
            for(int i = 0;i<binary_bytes_part1.length;i++) {
                os1.write(is.read());
            }
            for(int i = 0;i<binary_bytes_part2.length;i++) {
                os2.write(is.read());
            }
            is.close();
            os1.close();
            os2.close();
        }
        
        // Generate the loader:
        config.info("PAKETCompiler: generating loader...");
        
        String loading_code = "";        
        String DSK_LOADING_SCREEN_FILENAMES = "";
        String DSK_LOADING_SCREEN_CODE = "";
        if (LOADING_SCREEN_SIZES != null) {
            for(int i = 0;i<LOADING_SCREEN_SIZES.size();i++) {
                loading_code += "    call clear_screen\n" + 
                                "    call set_mode_"+LOADING_SCREEN_MODES.get(i)+"\n" +
                                "    di\n" +
                                "    ld hl, loader_palette_" + (i + 1) + "\n" +
                                "    call set_palette_mode_" + LOADING_SCREEN_MODES.get(i) + "\n" +
                                "    ld a, "+(use_rle_for_loading_screens ? 1:0)+"\n" +
                                "    ld (use_rle), a\n" +
                                "    ld ix, "+LOADING_SCREEN_START_ADDRESSES.get(i)+"		; load the loading screen\n" +
                                "    ld de, "+(use_rle_for_loading_screens ? LOADING_SCREEN_SIZES_RLE.get(i):LOADING_SCREEN_SIZES.get(i))+"		; size to load\n" +
                                "    ld a, #ff		; synchronization byte\n" +
                                "    call topoload\n";
                if (i < LOADING_SCREEN_SIZES.size() - 1) {
                    switch(LOADING_SCREEN_PAUSE) {
                        case "5-sec":
                            // 5 second pause for all but the last screen:
                            loading_code += "    ld bc, 50*5*6  ; 5 second pause\n" +
                                            "    ei\n" +
                                            "    call wait_bc_halts\n"+
                                            "    di\n\n";
                            break;
                        case "keypress":
                            loading_code += "    ei\n" +
                                            "    call wait_for_space\n"+
                                            "    di\n\n";
                            break;
                        default:
                            throw new Exception("Unsupported loading screen pause mode: " + LOADING_SCREEN_PAUSE);
                    }
                }
                DSK_LOADING_SCREEN_FILENAMES += "filename_img" + i + ":\n" + 
                                                "    db \"screen" + i + ".bin\"\n" +
                                                "end_filename_img" + i + ":\n\n";
                
                DSK_LOADING_SCREEN_CODE += "    ld a, " + LOADING_SCREEN_MODES.get(i) + "\n" + 
                                           "    call scr_set_mode\n" +
                                           "    ld hl, loader_palette_indexes_"+(i+1)+"\n" +
                                           "    call setup_colors_mode"+LOADING_SCREEN_MODES.get(i)+"\n" +
                                           "    ld b, end_filename_img" + i + " - filename_img" + i + "\n" +
                                           "    ld hl, filename_img" + i + "\n" +
                                           "    call load_block\n";
                switch(LOADING_SCREEN_PAUSE) {
                    case "5-sec":
                        DSK_LOADING_SCREEN_CODE += "    ld bc, 50*5*6  ; 5 second pause\n" +
                                                   "    call wait_bc_halts\n\n";
                        break;
                    case "keypress":
                        DSK_LOADING_SCREEN_CODE += "    call wait_for_space\n\n";
                        break;
                    default:
                        throw new Exception("Unsupported loading screen pause mode: " + LOADING_SCREEN_PAUSE);
                }
            }
        }
                
        
        if (binary_size <= max_first_binary_piece_size_tape) {
            loading_code += "\n" +
                            "    xor a\n" +
                            "    ld (use_rle),a\n" +
                            "    ld ix, "+variables.get("START_ADDRESS")+"\n" +
                            "    ld de, "+binary_size+"  ; size of the binary to load\n" +
                            "    ld a, #ff  ; synchronization byte\n" +
                            "    call topoload\n";
        } else {
            loading_code += "\n" +
                            "    xor a\n" +
                            "    ld (use_rle),a\n" +
                            "    ld ix, "+variables.get("START_ADDRESS")+"\n" +
                            "    ld de, "+max_first_binary_piece_size_tape+"  ; size of the binary to load\n" +
                            "    ld a, #ff  ; synchronization byte\n" +
                            "    call topoload\n";
            loading_code += "\n" +
                            "    xor a\n" +
                            "    ld (use_rle), a\n" +
                            "    ld ix, copy_block2_and_start_game_end\n" +
                            "    ld de, "+(binary_size - max_first_binary_piece_size_tape)+"  ; size of the binary to load\n" +
                            "    ld a, #ff  ; synchronization byte\n" +
                            "    call topoload\n";
        }
        
        variables.put("LOADER_START_ADDRESS", "" + TAPE_LOADER_START_ADDRESS);
        variables.put("LOADER_LOADING_CODE", loading_code);
        variables.put("DSK_LOADING_SCREEN_FILENAMES", DSK_LOADING_SCREEN_FILENAMES);
        if (LOADING_SCREEN_SIZES != null) {
            variables.put("N_LOADING_SCREENS", "" + LOADING_SCREEN_SIZES.size());
        } else {
            variables.put("N_LOADING_SCREENS", "0");
        }
        variables.put("DSK_LOADING_SCREEN_CODE", DSK_LOADING_SCREEN_CODE);
        
        String LOADER_PALETTES_STRING = "";
        for(int i = 0;i<LOADING_SCREEN_PALETTES.size();i++) {
            LOADER_PALETTES_STRING += "loader_palette_" + (i+1) + ":\n    db " + LOADING_SCREEN_PALETTES.get(i) + "\n";
        } 
        variables.put("LOADER_COLOR_PALETTES", LOADER_PALETTES_STRING);
        String DSK_LOADER_PALETTES_STRING = "";
        for(int i = 0;i<LOADING_SCREEN_DSK_PALETTE_INDEXES.size();i++) {
            DSK_LOADER_PALETTES_STRING += "loader_palette_indexes_" + (i+1) + ":\n    db " + LOADING_SCREEN_DSK_PALETTE_INDEXES.get(i) + "\n";
        } 
        variables.put("DSK_LOADER_COLOR_PALETTES_INDEXES", DSK_LOADER_PALETTES_STRING);
        variables.put("BINARY_FILENAME1", "\"data.bin\"");
        if (binary_size > max_first_binary_piece_size_tape) {
            variables.put("BINARY2_TAPE_SIZE", "" + (binary_size - max_first_binary_piece_size_tape));
            variables.put("LOADER_SECOND_BLOCK", "1");
        } else {
            variables.put("BINARY2_TAPE_SIZE", "0");
            variables.put("LOADER_SECOND_BLOCK", "0");
        }
        if (binary_size > max_first_binary_piece_size_dsk) {
            variables.put("BINARY_FILENAME2", "\"data2.bin\"");
            variables.put("BINARY2_DSK_SIZE", "" + (binary_size - max_first_binary_piece_size_dsk));
            variables.put("LOADER_DSK_SECOND_BLOCK", "1");
        } else {
            variables.put("BINARY_FILENAME2", "\"\"");
            variables.put("BINARY2_DSK_SIZE", "0");
            variables.put("LOADER_DSK_SECOND_BLOCK", "0");
        }
        PAKETCompiler.instantiateAsmPatternFromResources("loader.asm-pattern", destinationFolder+"/src/loader-autogenerated.asm", variables, this);
        PAKETCompiler.instantiateAsmPatternFromResources("loader-dsk.asm-pattern", destinationFolder+"/src/loader-dsk-autogenerated.asm", variables, this);
        
        // Compile the loader:
        PAKETCompiler.callMDL(new String[]{destinationFolder + "/src/loader-autogenerated.asm", "-bin", loaderName+".bin", "-st", loaderName+".sym"}, config);
        int loader_size = (int)(new File(loaderName+".bin").length());
        config.info("    CDT loader size: " + loader_size);
        
        // Generate the CDT:
        // #a400 = 41984 (start address of the loader)
        CPC2CDT.main(new String[] {"-n", "-b", "1000", "-x", "41984", "-l", "41984", "-r", "pac", "-p", "5000", "-ip", "5000", loaderName+".bin", binaryName+".cdt"}); 
//        ConsoleExecution.execute(new String[] {"./2cdt", "-n", "-s", "0", "-X", "41984", "-L", "41984", "-r", "pac", "-p", "5000", loaderName+".bin", binaryName+".cdt"}, config); 
        if (LOADING_SCREEN_SIZES != null) {
            for(int i = 0;i<LOADING_SCREEN_SIZES.size();i++) {
                if (use_rle_for_loading_screens) {
                    CPC2CDT.main(new String[] {"-m", "zxraw", "-p", "3000",  destinationFolder+"/src/data/screenRLE"+i+".bin", binaryName+".cdt"});
//                    ConsoleExecution.execute(new String[] {"./2cdt", "-m", "2", "-p", "3000",  destinationFolder+"/src/data/screenRLE"+i+".bin", binaryName+".cdt"}, config);
                } else {
                    CPC2CDT.main(new String[] {"-m", "zxraw", "-p", "3000",  destinationFolder+"/src/data/screen"+i+".bin", binaryName+".cdt"});
//                    ConsoleExecution.execute(new String[] {"./2cdt", "-m", "2", "-p", "3000",  destinationFolder+"/src/data/screen"+i+".bin", binaryName+".cdt"}, config);
                }
            }
        }
        if (binary_size > max_first_binary_piece_size_tape) {
            CPC2CDT.main(new String[] {"-m", "zxraw", "-p", "3000", binaryName+"-1.bin", binaryName+".cdt"}); 
            CPC2CDT.main(new String[] {"-m", "zxraw", "-p", "3000", binaryName+"-2.bin", binaryName+".cdt"}); 
//            ConsoleExecution.execute(new String[] {"./2cdt", "-m", "2", "-p", "3000", binaryName+"-1.bin", binaryName+".cdt"}, config); 
//            ConsoleExecution.execute(new String[] {"./2cdt", "-m", "2", "-p", "3000", binaryName+"-2.bin", binaryName+".cdt"}, config); 
        } else {
            CPC2CDT.main(new String[] {"-m", "zxraw", "-p", "3000", binaryName+".bin", binaryName+".cdt"}); 
//            ConsoleExecution.execute(new String[] {"./2cdt", "-m", "2", "-p", "3000", binaryName+".bin", binaryName+".cdt"}, config); 
        }
        
        // Generate the DSK:
        PAKETCompiler.callMDL(new String[]{destinationFolder + "/src/loader-dsk-autogenerated.asm", "-bin", loaderName+"-dsk.bin", "-st", loaderName+"-dsk.sym"}, config);
        int dsk_loader_size = (int)(new File(loaderName+"-.bin").length());
        config.info("    DSK loader size: " + dsk_loader_size);
        ConsoleExecution.execute(new String[] {"mv", loaderName+"-dsk.bin", "pac.bin"}, config); 
        IDsk.main(new String[] {binaryName+".dsk", "-n", "-i", "pac.bin", "-e", "a400", "-c", "a400", "-t", "1"}, config); 

        ConsoleExecution.execute(new String[] {"mv", "pac.bin", loaderName+"-dsk.bin"}, config); 
        if (LOADING_SCREEN_SIZES != null) {
            for(int i = 0;i<LOADING_SCREEN_SIZES.size();i++) {
                IDsk.main(new String[] {binaryName+".dsk", "-i", destinationFolder+"/src/data/screen"+i+".bin", "-c", Z80Assembler.toHex16bit(LOADING_SCREEN_START_ADDRESSES.get(i), false), "-t", "1"}, config); 
                IDsk.main(new String[] {binaryName+".dsk", "-i", destinationFolder+"/src/data/screen"+i+".bin", "-c", Z80Assembler.toHex16bit(LOADING_SCREEN_START_ADDRESSES.get(i), false), "-t", "1"}, config); 
            }
        }
        if (binary_size > max_first_binary_piece_size_dsk) {
            ConsoleExecution.execute(new String[] {"cp", binaryName+"-dsk1.bin", "data.bin"}, config); 
            IDsk.main(new String[] {binaryName+".dsk", "-i", "data.bin", "-c", "40", "-t", "1"}, config); 
            ConsoleExecution.execute(new String[] {"mv", "data.bin", loaderName+".bin"}, config); 

            ConsoleExecution.execute(new String[] {"cp", binaryName+"-dsk2.bin", "data2.bin"}, config); 
            IDsk.main(new String[] {binaryName+".dsk", "-i", "data2.bin", "-c", "d000", "-t", "1"}, config); 
            ConsoleExecution.execute(new String[] {"mv", "data2.bin", loaderName+".bin"}, config); 
        } else {
            ConsoleExecution.execute(new String[] {"cp", binaryName+".bin", "data.bin"}, config); 
            IDsk.main(new String[] {binaryName+".dsk", "-i", "data.bin", "-c", "40", "-t", "1"}, config); 
            ConsoleExecution.execute(new String[] {"mv", "data.bin", loaderName+".bin"}, config); 
        }

        calculateUsedAndFreeSpace(binaryName+".bin", binaryName+".sym", this, game);
    }
            
    
    public List<SpaceAllocator.Space> getRAMSpaces() throws Exception
    {
        int RAMAvailableForBuffers = 16*1024;
        spaces = new ArrayList<>();

        spaces.add(new SpaceAllocator.Space("start_of_RAM:", "end_of_RAM:", RAMAvailableForBuffers, ""));
        for(Pair<Integer,Integer> space:calculateFreeVRAMSpace()) {
            spaces.add(new SpaceAllocator.Space("  ; VRAM block\n  org " + Z80Assembler.toHex16bit(space.m_a, true), 
                                              "", space.m_b, ""));
        }
        // 56 bytes at the very beginning of the memory (just before the interrupt address):
        spaces.add(new SpaceAllocator.Space("  ; memory_start_block:\n  org #0000", "", 56, ""));
        
        return spaces;
    }
    
    
    public void addRAMBlock(String f_name, String f_path, List<SpaceAllocator.Block> blocks, String outputFolder, PAKGame game) throws Exception
    {
        MDLConfig mdl_config = new MDLConfig();
        CodeBase code = new CodeBase(mdl_config);
        mdl_config.parseArgs(f_path, "-I", outputFolder, 
                             "-equ", "MUSIC_BUFFER_SIZE="+game.music_buffer_size,
                             "-equ", "ACTION_TEXT_BUFFER_SIZE="+game.actionTextBufferSize,
                             "-equ", "ROOM_SPECIFIC_ON_LOAD_RULES_BUFFER_SIZE="+game.maxRoomSpecificOnLoadRulesSize);
        mdl_config.codeBaseParser.parseMainSourceFiles(mdl_config.inputFiles, code);
        Integer start = (Integer)code.getSymbolValue("RAMBlock_start", false);
        Integer end = (Integer)code.getSymbolValue("RAMBlock_end", false);
        config.info("   "+f_name+": " + (end - start));
        String assembler = getRAMBlockAssembler(f_path);
        assembler = assembler.replace("MUSIC_BUFFER_SIZE", "{MUSIC_BUFFER_SIZE}");
        assembler = assembler.replace("ACTION_TEXT_BUFFER_SIZE", "{ACTION_TEXT_BUFFER_SIZE}");
        assembler = assembler.replace("ROOM_SPECIFIC_ON_LOAD_RULES_BUFFER_SIZE", "{ROOM_SPECIFIC_ON_LOAD_RULES_BUFFER_SIZE}");
        blocks.add(new SpaceAllocator.Block("ram:" + f_name, assembler, "", end-start, null));
    }
    
    public List<SpaceAllocator.Block> getRAMBlocks(String outputFolder, PAKGame game) throws Exception
    {
        List<SpaceAllocator.Block> blocks = new ArrayList<>();
        
        config.info("getRAMBlocks ... ");
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL url = classLoader.getResource("paket/templates/cpc/ramblocks");
        String path = url.getPath();
        for(File f:new File(path).listFiles()) {
            // Compile and get size of "f":
            String f_path = f.getPath();
            String f_name = f_path.substring(f_path.lastIndexOf(File.separator)+1);
            addRAMBlock(f_name, f_path, blocks, outputFolder, game);
        }
        
        if (game.loadSaveGameScript != null) {
            for(String f_name:saveGameCodeFileNames().m_b) {
                URL f_url = classLoader.getResource("paket/templates/cpc/" + f_name);
                String f_path = f_url.getPath();
                addRAMBlock(f_name, f_path, blocks, outputFolder, game);
            }
        }
    
        config.info("getRAMBlocks: " + blocks.size());
                
        return blocks;
    }
    
    
    public String getFirstAssemblerLabel(String assembler) throws Exception
    {
        String lines[] = assembler.split("\n");
        String line = lines[0].strip();
        if (!line.endsWith(":")) {
            throw new Exception("Assembler block expected to start with a label, but didn't! First line was: " + line);
        }
        return line.substring(0, line.length()-1);
    }
    
    
    public void generateRAMAllocationAssembler(HashMap<String, String> assemblerVariables) throws Exception {        
        // Generate assembler code for the spaces:
        String assembler = "";
        String assembler_non_relocated = "";
        boolean VRAMSpace = false;
        for(SpaceAllocator.Space s:spaces) {
            assembler += s.startStatement + "\n";
            for(SpaceAllocator.Block b:s.content) {
                if (!VRAMSpace && !b.assembler2.isBlank()) {
                    // not relocated block:
                    assembler_non_relocated += getFirstAssemblerLabel(b.assembler1) + ":\n";
                    assembler_non_relocated += b.assembler2 + "\n";
                } else {
                    assembler += b.assembler1 + "\n";
                }
            }
            assembler += s.endStatement + "\n\n";
            VRAMSpace = true;
        }
                
        // Generate the assembler code that will move data to these spaces if
        // needed:
        String assembler2 = "";
        String assembler2_back = "";
        List<String> labelsToRelocateOrigin = new ArrayList<>();
        VRAMSpace = false;
        for(SpaceAllocator.Space s:spaces) {
            // skip the first space, since that's RAM:
            if (!VRAMSpace) {
                VRAMSpace = true;
                continue;
            }
            for(SpaceAllocator.Block b:s.content) {
                if (!b.assembler2.isBlank()) {
                    assembler2_back += b.assembler2 + "\n";

                    // We assume that each block starts with labels:
                    labelsToRelocateOrigin.add(getFirstAssemblerLabel(b.assembler2));

                    assembler2 += "  ld hl," + getFirstAssemblerLabel(b.assembler2) + "\n";
                    assembler2 += "  ld de," + getFirstAssemblerLabel(b.assembler1) + "\n";
                    assembler2 += "  ld bc," + b.size + "\n";
                    assembler2 += "  ldir\n";
                }
            }
        }
        assembler2 += "  ret\n";
        assembler2 += assembler2_back;
                
        // replace variables in these blocks, since they will not be replaced recursively later:
        for(String v:assemblerVariables.keySet()) {
            assembler = assembler.replace("{"+v+"}", assemblerVariables.get(v));
            assembler2 = assembler2.replace("{"+v+"}", assemblerVariables.get(v));
        }
        assemblerVariables.put("NOT_RELOCATED_DATA_BLOCKS", assembler_non_relocated);
        assemblerVariables.put("RAM_BLOCKS", assembler);
        assemblerVariables.put("RAM_BLOCKS_DATA_RELOCATION", assembler2);
        assemblerVariables.put("N_RAM_BLOCKS_TO_RELOCATE", "" + labelsToRelocateOrigin.size());
    }
    
    
    @Override
    public void MemoryAllocation(String outputFolder, HashMap<String, String> assemblerVariables, PAKGame game) throws Exception
    {
        spaces = getRAMSpaces();
        
        // Allocate the RAM blocks:
        List<SpaceAllocator.Block> blocks = getRAMBlocks(outputFolder, game);
        blocks.addAll(dataBanksToPotentiallyRelocateToVRAM);
        SpaceAllocator.RAMAllocation(assemblerVariables, spaces, blocks, game, config);
        
        // Allocate tile banks:
//        RAMAllocator.RAMAllocation(assemblerVariables, spaces, dataBanksToPotentiallyRelocateToVRAM, game, config);
        
        generateRAMAllocationAssembler(assemblerVariables);
    }    

    
    
    public static String getRAMBlockAssembler(String f_path) throws Exception
    {
        String assembler = "";
        BufferedReader br = new BufferedReader(new FileReader(f_path));
        String line = br.readLine();
        boolean started = false;
        while(line != null) {
            if (started) {
                if (line.startsWith("RAMBlock_end:")) {
                    break;
                }
                assembler += line + "\n";
            } else {
                if (line.startsWith("RAMBlock_start:")) {
                    started = true;
                }
            }
            line = br.readLine();
        }
        return assembler;
    }
    
    
    public void calculateUsedAndFreeSpace(String binaryFile, String symbolFile, Platform platform, PAKGame game) throws Exception
    {
        int binary_size = (int)(new File(binaryFile).length());
        int start_of_code = 4*16;   // #0040
        int start_of_ram = 0;
        int end_of_code = 0;
        int end_of_ram = 0;
        int start_of_text = 0, end_of_text = 0;
        int start_of_tiles = 0, end_of_tiles = 0;
        int start_of_object_types = 0, end_of_object_types = 0;
        int start_of_rooms = 0, end_of_rooms = 0;
        int start_of_dialogue = 0, end_of_dialogue = 0;
        int start_of_gui = 0, end_of_gui = 0;
        int start_of_item = 0, end_of_item = 0;
        int start_of_font = 0, end_of_font = 0;
        int start_of_pointers = 0, end_of_pointers = 0;
        int start_of_global_rules = 0, end_of_global_rules = 0;
        int start_of_other_data = 0, end_of_other_data = 0;
        int start_of_sfx_data = 0, end_of_sfx_data = 0;
        int start_of_music_data = 0, end_of_music_data = 0;
        int stack_size = 128;
        
        BufferedReader br = new BufferedReader(new FileReader(symbolFile));
        while(true) {
            String line = br.readLine();
            if (line == null) break;
            if (line.contains(END_OF_RAM_TAG)) {
                end_of_ram = getSymbolTableAddress(line);
            }
            if (line.contains(END_OF_CODE_TAG)) {
                end_of_code = getSymbolTableAddress(line);
            }
            if (line.contains(START_OF_RAM_TAG)) {
                start_of_ram = getSymbolTableAddress(line);
            }
            if (line.contains("textBankPointers:")) {
                start_of_text = getSymbolTableAddress(line);
            }
            if (line.contains("tileBanksPointers_0:")) {
                end_of_text = getSymbolTableAddress(line);
                start_of_tiles = getSymbolTableAddress(line);
            }
            if (line.contains("objectTypeBanksPointers:")) {
                end_of_tiles = getSymbolTableAddress(line);
                start_of_object_types = getSymbolTableAddress(line);
            }
            if (line.contains("roomBankPointers:")) {
                end_of_object_types = getSymbolTableAddress(line);
                start_of_rooms = getSymbolTableAddress(line);
            }
            if (line.contains("dialoguePointers:")) {
                end_of_rooms = getSymbolTableAddress(line);
                start_of_dialogue = getSymbolTableAddress(line);
            }
            if (line.contains("gui_data_compressed:")) {
                end_of_dialogue = getSymbolTableAddress(line);
                start_of_gui = getSymbolTableAddress(line);                
            }
            if (line.contains("item_sprite_data:")) {
                end_of_gui = getSymbolTableAddress(line);
                start_of_item = getSymbolTableAddress(line);
            }
            if (line.contains("pointer_sprites:")) {
                end_of_item = getSymbolTableAddress(line);
                start_of_pointers = getSymbolTableAddress(line);
            }
            if (line.contains("font:")) {
                end_of_pointers = getSymbolTableAddress(line);
                start_of_font = getSymbolTableAddress(line);
            }
            if (line.contains("global_rules_item:")) {
                end_of_font = getSymbolTableAddress(line);
                start_of_global_rules = getSymbolTableAddress(line);
            }
            if (line.contains("sfx_pointers:")) {
                end_of_global_rules = getSymbolTableAddress(line);
                start_of_sfx_data = getSymbolTableAddress(line);
            }
            if (line.contains("song_pointers:")) {
                end_of_sfx_data = getSymbolTableAddress(line);
                start_of_music_data = getSymbolTableAddress(line);
            }
            if (line.contains("text_area_palette:")) {
                end_of_music_data = getSymbolTableAddress(line);
                start_of_other_data = getSymbolTableAddress(line);
            }
            if (line.contains("end_of_binary_in_memory:")) {
                end_of_other_data = getSymbolTableAddress(line);
            }            
        }
        
        if (end_of_ram == 0) throw new Exception("Cannot find " + END_OF_RAM_TAG + " tag!");
        
        int free_space = 48*1024 - (stack_size + end_of_ram);
        List<Integer> free_vram_list = new ArrayList<>();
        int free_vram_space = 0;
        int vram_used = 0;
        for(SpaceAllocator.Space space:spaces) {
            if (space.startStatement.contains("VRAM")) {
                free_vram_space += space.spaceLeft;
                vram_used += space.size - space.spaceLeft;
                free_vram_list.add(space.spaceLeft);
            }
        }
        
//        int objectTypesSpace = objectTypeBanksTotalSize + (end_of_object_types - start_of_object_types);
        config.info("---- Detailed Space Analysis (approximate, as data relocated to VRAM is not included) ----");
        config.info("Code: " + (end_of_code - start_of_code));
        config.info("Text: " + (end_of_text - start_of_text));
        config.info("Tiles (non relocated): " + (end_of_tiles - start_of_tiles));
        config.info("Object types (non relocated): " + (end_of_object_types - start_of_object_types) + " (relocated banks add up to: " + objectTypeBanksTotalSize + ")");
        config.info("Rooms: " + (end_of_rooms - start_of_rooms));
        config.info("Dialogue Rules: " + (end_of_dialogue - start_of_dialogue));
        config.info("GUI: " + (end_of_gui - start_of_gui));
        config.info("Items: " + (end_of_item - start_of_item));
        config.info("Pointers: " + (end_of_pointers - start_of_pointers));
        config.info("Font: " + (end_of_font - start_of_font));
        config.info("Global Rules: " + (end_of_global_rules - start_of_global_rules));
        config.info("SFX: " + (end_of_sfx_data - start_of_sfx_data));
        config.info("Music: " + (end_of_music_data - start_of_music_data));        
        config.info("Other: " + (end_of_other_data - start_of_other_data) + "  (palettes, action name pointers, cutscene images, other data to relocate)");
        config.info("RAM variables/buffers: " + (end_of_ram - start_of_ram) + 
                    " (general buffer: "+game.general_buffer_size+
                    ", tiles/object type buffer: "+game.room_tiles_objects_combined_buffer_size+")");
        config.info("Free RAM blocks: " + free_space);
        config.info("Free VRAM blocks: " + free_vram_list);
        config.diggest("---- General Space Analysis ----");
        config.diggest("Binary size: " + binary_size);
        config.diggest("Total RAM/VRAM space used: " + (end_of_ram + vram_used));
        config.diggest("RAM space available: " + free_space);        
        config.diggest("Total space available (free RAM + free VRAM): " + (free_space+free_vram_space));
        
        // Calculate space per room, and how many more rooms can we expect to fit:
        {
            int roomSpace = 0;
            roomSpace += (end_of_text - start_of_text);
            roomSpace += (end_of_tiles - start_of_tiles);
            roomSpace += (end_of_object_types - start_of_object_types);
            roomSpace += (end_of_rooms - start_of_rooms);
            roomSpace += (end_of_dialogue - start_of_dialogue);
            roomSpace += (end_of_item - start_of_item);
            roomSpace += (end_of_global_rules - start_of_global_rules);
            roomSpace += (end_of_other_data - start_of_other_data);
            roomSpace += (end_of_sfx_data - start_of_sfx_data);
            roomSpace += (end_of_music_data - start_of_music_data);
            float perRoom = roomSpace / (float)(game.rooms.size());
            int totalFreeSpace = free_space+free_vram_space;
            config.diggest("Avg room size: " + perRoom + " (you can fit " + ((int)(totalFreeSpace/perRoom)) + " more rooms, for a total of "+ (game.rooms.size() + ((int)(totalFreeSpace/perRoom))) +")");
        }
        
    }
    
    
    // This function returns the blocks of VRAM that are not used for displaying
    // graphics, and can be used as additional RAM by the game:
    public List<Pair<Integer,Integer>> calculateFreeVRAMSpace()
    {
        List<Pair<Integer,Integer>> free_vram_blocks = new ArrayList<>();
        int VRAMStart = 48*1024;
        int bytes_per_line = SCREEN_WIDTH_IN_TILES*4;
        // We add "1" since we use the line below the current screen as a buffer for rendering text to
        int n_lines_used = SCREEN_HEIGHT_IN_TILES + 1;
        
        int usedSpace_per_bank = bytes_per_line * n_lines_used;
        for(int i = 0;i<8;i++) {
            int block_address = VRAMStart + 2048*i + usedSpace_per_bank;
            int block_space = 2048 - usedSpace_per_bank;
            free_vram_blocks.add(new Pair<>(block_address, block_space));
        }
        
        return free_vram_blocks;
    }
        
    
    @Override
    public List<Pair<String, String>> getAsmPatternsToInstantiate()
    {
        List<Pair<String, String>> patterns = new ArrayList<>();
        patterns.add(new Pair<>("main.asm-pattern", "/src/main-autogenerated.asm"));
        patterns.add(new Pair<>("rules.asm-pattern", "/src/rules-autogenerated.asm"));
        patterns.add(new Pair<>("gfx.asm-pattern", "/src/gfx-autogenerated.asm"));
        return patterns;
    }
}
