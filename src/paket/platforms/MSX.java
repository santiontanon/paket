/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.platforms;

import paket.util.CIEDE2000;
import paket.compiler.PAKET;
import paket.compiler.PAKETCompiler;
import paket.compiler.PAKETConfig;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import javax.imageio.ImageIO;
import paket.compiler.optimizers.PAKETOptimizer;
import paket.pak.PAKCutsceneImage;
import paket.pak.PAKGame;
import paket.pak.PAKItem;
import paket.pak.PAKObject;
import paket.pak.PAKObjectType;
import paket.pak.PAKObjectType.PAKObjectState;
import paket.pak.PAKRoom;
import paket.pak.PAKRule.PAKScript;
import paket.tiles.ExtractTilesMSX;
import paket.tiles.TileBankMSX;
import paket.util.Pair;
import paket.util.Z80Assembler;

/**
 *
 * @author santi
 *
 * In this MSX platform: - Tiles are 16*8 pixels in size to match the 8*8 tiles
 * of the CPC mode 0 platform. - Hence, each tile is 32 bytes (16 bytes for
 * pattern, and 16 for attributes).
 */
public class MSX extends Platform {

    public static final String END_OF_RAM_TAG = "end_of_RAM:";
    public static final String END_OF_ROM_TAG = "endOfROM";
    public static final String PLAYER_OBJECTTYPE_NAME = "player";

    public static final int TILES_PER_BANK = 16;

    public static final int NAME_TABLE_BANK1 = 0x1800;
    public static final int NAME_TABLE_BANK2 = 0x1800 + 256;
    public static final int NAME_TABLE_BANK3 = 0x1800 + 256 * 2;

    public static int expected_player_n_sprites = -1;
    public static int MSX_TILES_PER_ENGINE_TILE = 2;

    public ExtractTilesMSX tileExtractor = null;
    public List<int[]> gui_tiles = null;
    public List<List<Integer>> itemTileIndexes = new ArrayList<>();

    public MSX(int a_tileWidth, HashMap<String, String> a_variables, PAKETConfig a_config) {
        super(a_variables, a_config);

        supportedSaveGameModes.clear();
        supportedSaveGameModes.add("password");
        defaultSaveGameMode = "password";
        
        START_ADDRESS = 0x4000;
        targetSystemName = "msx";
        saveByColumns = false;
        TILE_WIDTH = a_tileWidth;
        MSX_TILES_PER_ENGINE_TILE = TILE_WIDTH / 8;
        basePSGReg7Value = 0xb8;
        PSGMasterFrequency = 111861;
        tileExtractor = new ExtractTilesMSX(this, config);

        OBJECT_MASK_HORIZONTAL_RESOLUTION = 4;
        OBJECT_MASK_VERTICAL_RESOLUTION = 4;
        
        PATH_FINDING_WALK_TILE_WIDTH = 8;

        gui_tiles = new ArrayList<>();
        int empty_tile[] = new int[TILE_WIDTH * PAKRoom.TILE_HEIGHT];
        for (int i = 0; i < empty_tile.length; i++) {
            empty_tile[i] = 0;
        }
        gui_tiles.add(empty_tile);
        updateScreenWidth();
    }

    @Override
    public void initPlatform(HashMap<String, String> assemblerVariables, PAKGame game) {
    }
    
    @Override
    public Pair<String[], String[]> saveGameCodeFileNames()
    {
        return new Pair<>(
                new String[]{"passwordsave.asm-pattern"},
                 new String[]{});
    }    

    @Override
    public void clearBasePalette() {
    }

    @Override
    public void clearPalette(String paletteName) {
    }

    @Override
    public void setPalette(String paletteName, List<Integer> palette) {

    }

    @Override
    public List<Integer> getBasePalette() {
        return null;
    }

    @Override
    public List<Integer> getPalette(String paletteName) {
        return null;
    }

    @Override
    public int getPaletteID(String paletteName) {
        return -1;
    }

    @Override
    public void addToBasePalette(String imageFileName) throws Exception {
    }

    @Override
    public void addToPalette(String imageFileName, String paletteName) throws Exception {
    }

    @Override
    public void addToPalette(BufferedImage image, String paletteName, String imageName) throws Exception {
    }

    @Override
    public void generateColorPalettes(PAKGame game, HashMap<String, String> assemblerVariables) {
    }

    @Override
    public int generateGUIData(BufferedImage guiImage, String outputImageName, String asmFolder, String imageName, PAKGame game) throws Exception {
        int outputWidth = 16 * TILE_WIDTH; // This is the width of a file that has the different tiles of the GUI 
        List<Integer> indexes = tileExtractor.findTiles(guiImage, TILE_WIDTH, gui_tiles);
        config.info("Tiles in GUI: " + gui_tiles.size());

        int first_gui_vdp_tile = (GUI_DIMENSIONS[1] - 16) * 32;  // 16 is the row where bank 3 in the VDP starts
        String indexes_data = "";
        for (int i = 0; i < guiImage.getHeight() / PAKRoom.TILE_HEIGHT; i++) {
            int tileidx = indexes.get(0 + i * (guiImage.getWidth() / TILE_WIDTH));
            if (MSX_TILES_PER_ENGINE_TILE == 2) {
                indexes_data += "    db " + (first_gui_vdp_tile + tileidx * 2) + ", " + (first_gui_vdp_tile + tileidx * 2 + 1);
            } else if (MSX_TILES_PER_ENGINE_TILE == 1) {
                indexes_data += "    db " + (first_gui_vdp_tile + tileidx);
            }
            for (int j = 1; j < guiImage.getWidth() / TILE_WIDTH; j++) {
                tileidx = indexes.get(j + i * (guiImage.getWidth() / TILE_WIDTH));
                if (MSX_TILES_PER_ENGINE_TILE == 2) {
                    indexes_data += ", " + (first_gui_vdp_tile + tileidx * 2) + ", " + (first_gui_vdp_tile + tileidx * 2 + 1);
                } else if (MSX_TILES_PER_ENGINE_TILE == 1) {
                    indexes_data += ", " + (first_gui_vdp_tile + tileidx);
                }
            }
            indexes_data += "\n";
        }

        int tilesPerRow = outputWidth / TILE_WIDTH;
        int outputHeight = ((gui_tiles.size() + (tilesPerRow - 1)) / tilesPerRow) * PAKRoom.TILE_HEIGHT;
        BufferedImage img = new BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < gui_tiles.size(); i++) {
            int x = (i % tilesPerRow) * TILE_WIDTH;
            int y = (i / tilesPerRow) * PAKRoom.TILE_HEIGHT;
            tileExtractor.drawTile(img, gui_tiles.get(i), x, y, TILE_WIDTH);
        }
        // ImageIO.write(img, "png", new File(outputImageName));

        game.gui_compressed_size = TileBankMSX.generateAndCompressTilesAssemblerFileWithPrefix(
                indexes_data,
                img,
                0, gui_tiles.size(),
                asmFolder, "data/gui-data", true, false, this, config);
        int gui_tiles_size = (int) (new File(asmFolder + "/data/gui-data.bin").length());
        return gui_tiles_size;
    }

    @Override
    public void addItemData(BufferedImage sourceImage,
            int x0, int y0, int x1, int y1,
            String ID, String name, String description,
            String defaultUseMessage,
            PAKGame game,
            String imageName) throws Exception {
        List<Integer> item_indexes = tileExtractor.findTiles(sourceImage, x0, y0, x1, y1, TILE_WIDTH, gui_tiles);
        itemTileIndexes.add(item_indexes);
//        if (ID != itemTileIndexes.size()) throw new Exception("Items entered out of order!");
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
            HashMap<String, Pair<Integer, Integer>> textIDHash, String outputFolderRoot,
            PAKGame game) throws Exception {
        int first_gui_vdp_tile = (GUI_DIMENSIONS[1] - 16) * 32;
        int item_data_uncompressed_size = 0;
        int n_items = itemTileIndexes.size();
        String outputFolder = outputFolderRoot + "/src/";

        FileWriter fw = new FileWriter(new File(outputFolder + "data/items.asm"));
        for (int i = 0; i < n_items; i++) {
            List<Integer> item_data = new ArrayList<>();
            // pattern indexes:
            for (int idx : itemTileIndexes.get(i)) {
                if (MSX_TILES_PER_ENGINE_TILE == 2) {
                    item_data.add(idx * 2 + first_gui_vdp_tile);
                    item_data.add(idx * 2 + 1 + first_gui_vdp_tile);
                } else if (MSX_TILES_PER_ENGINE_TILE == 1) {
                    item_data.add(idx + first_gui_vdp_tile);                    
                }
            }
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
            for (String itemID : itemIDs) {
                PAKItem item = itemHash.get(itemID);
                String itemName = item.getInGameName();
                name_lengths.add(itemName.length() + 1);
                Pair<Integer, Integer> name_idx = textIDHash.get(item.getInGameName());
                Pair<Integer, Integer> desc_idx = textIDHash.get(item.getDescription());
                String useMessage = game.cannotUseErrorMessage;
                if (item.defaultUseMessage != null) {
                    useMessage = item.defaultUseMessage;
                }
                Pair<Integer, Integer> use_idx = textIDHash.get(useMessage);
                itemTextData += "  db " + name_idx.m_a + ", " + name_idx.m_b + ",  "
                        + desc_idx.m_a + ", " + desc_idx.m_b + ",  "
                        + use_idx.m_a + ", " + use_idx.m_b
                        + "  ; " + itemName + " \n";
            }
            // Calculate the size that we need for the buffer that will store inventory item names:
            Collections.sort(name_lengths, new Comparator<Integer>() {
                @Override
                public int compare(Integer o1, Integer o2) {
                    return o2.compareTo(o1);
                }
            });
            while (name_lengths.size() > inventorySize()) {
                name_lengths.remove(name_lengths.size() - 1);
            }
            int inventory_names_buffer_size = 0;
            for (int l : name_lengths) {
                inventory_names_buffer_size += l;
            }

            variables.put("INVENTORY_NAME_BUFFER_SIZE", "" + inventory_names_buffer_size);
            variables.put("ITEM_TEXT_DATA", itemTextData);
        }

        variables.put("GUI_N_TILES", "" + gui_tiles.size());
        variables.put("GUI_DATA",
                "gui_data_compressed:\n"
                + "    incbin \"data/gui-data." + PAKETConfig.compressorExtension[config.compressor] + "\"\n");

        return item_data_uncompressed_size;
    }

    // Given a tile, this method returns:
    // - an array of TILE_WIDTH * 2 ints
    // - TILE_WIDTH ints for "pattern"
    // - TILE_WIDTH ints for "attributes"
    @Override
    public int[] getTileData(BufferedImage img, int tile, String paletteName, String imageName) throws Exception {
        int tilesPerRow = img.getWidth() / TILE_WIDTH;
        int x = tile % tilesPerRow;
        int y = tile / tilesPerRow;
        int pixels[] = tileExtractor.getTile(img, x * TILE_WIDTH, y * PAKRoom.TILE_HEIGHT, TILE_WIDTH);
        int patternData[] = TileBankMSX.patternBytes(pixels);
        return patternData;
    }

    @Override
    public boolean isEmptyTile(int tile[]) {
        for (int i = 0; i < tile.length; i++) {
            if (tile[i] != 0) {
                return false;
            }
        }
        return true;
    }

    // This is an array with the "tile assembler data", so, it'll be:
    // - 32 bytes
    // - 16 bytes of pattern, and 16 bytes of attributes
    @Override
    public int[] mirrorTileDataHorizontally(int tileData[]) throws Exception {
        if (tileData.length != (TILE_WIDTH * PAKRoom.TILE_HEIGHT / 4)) {
            throw new Exception("tileData had " + tileData.length + " bytes, and expected " + (TILE_WIDTH * PAKRoom.TILE_HEIGHT / 4));
        }
        int width_in_bytes = TILE_WIDTH / 8;
        int tileData2[] = new int[tileData.length];
        for (int i = 0; i < PAKRoom.TILE_HEIGHT; i++) {
            for (int j = 0; j < width_in_bytes; j++) {
                int v = tileData[i + j * PAKRoom.TILE_HEIGHT];
                int rv = 0;
                for (int k = 0; k < 8; k++) {
                    int bit = v % 2;
                    v /= 2;
                    rv = rv * 2 + bit;
                }
                tileData2[i + (width_in_bytes - 1 - j) * PAKRoom.TILE_HEIGHT] = rv;  // pattern
                tileData2[i + (width_in_bytes - 1 - j) * PAKRoom.TILE_HEIGHT + width_in_bytes * PAKRoom.TILE_HEIGHT]
                        = tileData[i + j * PAKRoom.TILE_HEIGHT + width_in_bytes * PAKRoom.TILE_HEIGHT];  // attribute
            }
        }

        return tileData2;
    }

    @Override
    public int getTileSizeInBytes() {
        return TILE_WIDTH * 2;
    }

    @Override
    public int generateTileBanks(List<int[]> mergedTiles, int bankSet_idx, String outputFolder, PAKGame game) throws Exception {
        int accumulatedSize = 0;
        String tileBanksCode1 = "tileBanksPointers_" + bankSet_idx + ":\n";
        String tileBanksCode2 = "";
        if (game != null) {
            game.tileBankSizes = new ArrayList<>();
        }
        for (int i = 0; i < (mergedTiles.size() + TILES_PER_BANK - 1) / TILES_PER_BANK; i++) {
            int size = TileBankMSX.generateAndCompressTilesAssemblerFile(mergedTiles,
                    i * TILES_PER_BANK, (i + 1) * TILES_PER_BANK,
                    outputFolder, "data/tileBank_" + bankSet_idx + "_" + i, false, true, config);
            if (size > 0 && outputFolder != null) {
                config.info("    Tile bank " + i + ": " + size + " bytes");
                tileBanksCode1 += "    dw tileBank_" + bankSet_idx + "_" + i + "\n";
                tileBanksCode2 += "tileBank_" + bankSet_idx + "_" + i + ":\n    incbin \"data/tileBank_" + bankSet_idx + "_" + i + "." + PAKETConfig.compressorExtension[config.compressor] + "\"\n";
            }
            accumulatedSize += size;
            if (game != null) {
                game.tileBankSizes.add(size);
            }
        }

        if (outputFolder != null) {
            String tmp = "";
            if (variables.containsKey("TILE_BANKS")) {
                tmp = variables.get("TILE_BANKS") + "\n";
            }
            variables.put("TILE_BANKS", tmp + tileBanksCode1 + "\n" + tileBanksCode2 + "\n");

            String tmp2;
            if (variables.containsKey("TILE_BANKSETS")) {
                tmp2 = variables.get("TILE_BANKSETS");
            } else {
                tmp2 = "tileBankSetsPointers:\n";
            }
            variables.put("TILE_BANKSETS", tmp2 + "    dw tileBanksPointers_" + bankSet_idx + "\n");
        }

        return accumulatedSize;
    }

    @Override
    public List<Integer> convertObjectStateImageToBytes(BufferedImage imgToUse, int selection, PAKObjectType type, int xoffs, int yoffs, List<PAKRoom> rooms, String imageName) throws Exception {
        if (type.ID.equals(PLAYER_OBJECTTYPE_NAME)) {
            return convertPlayerObjectImageToBytes(imgToUse, selection);
        }

        List<Integer> bytes = new ArrayList<>();
        List<Integer> selectionMaskBytes = new ArrayList<>();

        bytes.add(selection);

        // 1) Find objects of this type in the rooms, and get their x coordinates:
        PAKRoom targetRoom = null;
        PAKObject targetObject = null;
        int ox = 0;
        int count = 0;
        for (PAKRoom room : rooms) {
            for (PAKObject o : room.objects) {
                if (o.type == type) {
                    if (count == 0) {
                        ox = (o.x) % 8;
                        targetRoom = room;
                        targetObject = o;
                    } else {
                        if ((ox % 8) != ((o.x) % 8)) {
                            throw new Exception("MSX: object type " + type.ID + " appears in inconsistent x coordinates: " + ox + " vs " + o.x);
                        }
                    }
                    count++;
                }
            }
        }

        if (targetObject == null) {
            throw new Exception("Object of type " + type.ID + " does not appear in any room!");
        }

        // 2) Write image width (in MSX tiles: 8 pixels, regardless of whether the platform uses 8 or 16 pixel-wide tiles), and height (in pixels):
        int tileStart = ox / 8;
        int tileEnd = (ox + imgToUse.getWidth() - 1) / 8;
        bytes.add((tileEnd - tileStart) + 1);
        bytes.add(imgToUse.getHeight());

        // Render the room, up to the depth of the target object, so that we can grab the background:
        BufferedImage background = targetRoom.renderUpToDepthAndObject(targetObject.depth, targetObject, true, true, this, config);
        // ImageIO.write(background, "png", new File("tmp"+type.ID+".png"));  // For debugging

        int background_x_start = (targetObject.x) - ox;

        // 3) Write the image data:
        for (int y = 0; y < imgToUse.getHeight(); y++) {
            for (int tx = tileStart; tx <= tileEnd; tx++) {
                int pixels[] = new int[8];
                int selectionMaskByte = 0;
                for (int i = 0; i < 8; i++) {
                    int img_x = tx * 8 + i - ox;
                    if (img_x >= 0 && img_x < imgToUse.getWidth()) {
                        pixels[i] = MSXColors.getImageColor(imgToUse, img_x, y, config);
                        if (pixels[i] == -1) {
                            pixels[i] = MSXColors.getImageColor(background, img_x + background_x_start + xoffs,
                                    y + targetObject.y + yoffs, config);
                        } else {
                            selectionMaskByte |= 1 << (7 - i);
                            //selectionMaskByte |= 1 << i;
                        }
                    } else {
                        // grab the color from the background
                        pixels[i] = MSXColors.getImageColor(background, img_x + background_x_start + xoffs,
                                y + targetObject.y + yoffs, config);
                    }

                    if (pixels[i] == -1) {
                        config.info("Color not found in object type " + type.name);
                    }
                }
                selectionMaskBytes.add(selectionMaskByte);
                try {
                    // try to see if we can convert:
                    int tileRow_bytes[] = TileBankMSX.pixelRowBytes(pixels, 0);
                    bytes.add(tileRow_bytes[0]);
                    bytes.add(tileRow_bytes[1]);
                } catch (Exception e) {
                    // If we are here, it means there are more than 2 colors, so, we need to convert:
                    int newPixels[] = CIEDE2000.convertPixelBlock(pixels, y);
                    int tileRow_bytes[] = TileBankMSX.pixelRowBytes(newPixels, 0);
                    bytes.add(tileRow_bytes[0]);
                    bytes.add(tileRow_bytes[1]);
                }
            }
        }

        if (selection == PAKObjectState.SELECTION_PIXEL) {
            bytes.addAll(selectionMaskBytes);
            config.info("    " + type.ID + " selectionMask: " + selectionMaskBytes);
        }

        return bytes;
    }

    public List<Integer> convertPlayerObjectImageToBytes(BufferedImage imgToUse, int selection) throws Exception {
        List<Integer> bytes = new ArrayList<>();
        // Find the different sprites:
        List<Integer> sprite_y = new ArrayList<>();
        List<Integer> sprite_color = new ArrayList<>();
        List<List<Integer>> sprite_pattern = new ArrayList<>();

        if (imgToUse.getWidth() > 16) {
            throw new Exception("Player image is wider than 16 pixels in MSX!: " + imgToUse.getWidth() + "*" + imgToUse.getHeight());
        }

        if (selection == PAKObjectState.SELECTION_PIXEL) {
            selection = PAKObjectState.SELECTION_BOX;  // no pixel-perfect selection for player in MSX
        }
        bytes.add(selection);   // no special selection mask

        for (int y = 0; y < imgToUse.getHeight(); y++) {
            for (int x = 0; x < imgToUse.getWidth(); x++) {
                int color = MSXColors.getImageColor(imgToUse, x, y, config);
                if (color < 0) {
                    continue;
                }
                // see if we can add it to a previous sprite:
                boolean found = false;
                for (int i = 0; i < sprite_y.size(); i++) {
                    if (sprite_color.get(i) == color
                            && sprite_y.get(i) + 16 > y) {
                        found = true;
                        // config.info("x+(y-sprite_y.get(i))*16 = " + x + "+(" + y +"-" + sprite_y.get(i) + "))*16 = " + (x+(y-sprite_y.get(i))*16));
                        sprite_pattern.get(i).set(x + (y - sprite_y.get(i)) * 16, 1);
                        break;
                    }
                }
                if (!found) {
                    sprite_y.add(y);
                    sprite_color.add(color);
                    List<Integer> pattern = new ArrayList<>();
                    for (int i = 0; i < 16 * 16; i++) {
                        pattern.add(0);
                    }
                    sprite_pattern.add(pattern);
                    pattern.set(x, 1);
                }
            }
        }
        if (expected_player_n_sprites == -1) {
            expected_player_n_sprites = sprite_y.size();
        } else {
            if (sprite_y.size() != expected_player_n_sprites) {
                throw new Exception("Player expeced to have " + expected_player_n_sprites + " sprites, but has " + sprite_y.size());
            }
        }

        variables.put("N_PLAYER_SPRITES", "" + expected_player_n_sprites);

        // Try to shift the first of them around to have just 2 sprites per line at most:
        for (int i = 0; i < 3; i++) {
            boolean bottom_line_clear;
            do {
                bottom_line_clear = true;
                for (int j = 0; j < 16; j++) {
                    if (sprite_pattern.get(i).get(15 * 16 + j) != 0) {
                        bottom_line_clear = false;
                    }
                }
                if (bottom_line_clear) {
                    sprite_y.set(i, sprite_y.get(i) - 1);
                    for (int j = 0; j < 16; j++) {
                        sprite_pattern.get(i).remove(16 * 16 - 1);
                        sprite_pattern.get(i).add(0, 0);
                    }
                }
            } while (bottom_line_clear);
        }

        // Calculate sprite overlap:
//        List<Integer> overlap = new ArrayList<>();
//        for(int i = 0;i<imgToUse.getHeight();i++) {
//            int n = 0;
//            for(int j = 0;j<sprite_y.size();j++) {
//                if (sprite_y.get(j) <= i && sprite_y.get(j) + 16 > i) n++;
//            }
//            overlap.add(n);
//        }
//        config.info("convertPlayerObjectImageToBytes:");
//        config.info("    sprite_y: " + sprite_y);
//        config.info("    sprite_color: " + sprite_color);
//        config.info("    overlap: " + overlap);
        // Generate sprite attribute data:
        for (int i = 0; i < expected_player_n_sprites; i++) {
            int y = sprite_y.get(i);
            if (y < 0) {
                y += 256;
            }
            bytes.add(y); // Y
            bytes.add(sprite_color.get(i)); // color
        }
        // Generate the pattern data:
        for (int i = 0; i < expected_player_n_sprites; i++) {
            for (int x = 0; x < 16; x += 8) {
                for (int y = 0; y < 16; y++) {
                    int mask = 0;
                    int pixel = 128;
                    for (int j = 0; j < 8; j++) {
                        if (sprite_pattern.get(i).get(y * 16 + x + j) != 0) {
                            mask |= pixel;
                        }
                        pixel /= 2;
                    }
                    bytes.add(mask);
                }
            }
        }

        return bytes;
    }

    @Override
    public void roomVideoMemoryStartAddress(PAKRoom room, List<Integer> bytes) {
        bytes.add((room.screen_position_x + GAME_AREA_DIMENSIONS[0]) * MSX_TILES_PER_ENGINE_TILE);
        bytes.add(room.screen_position_y + GAME_AREA_DIMENSIONS[1]);
    }

    @Override
    public int extractPointers(String inputImagesName, String outputFolder) throws Exception {
        BufferedImage img = ImageIO.read(new File(inputImagesName));
        int nPointers = img.getWidth() / 16;
        config.info("    nPointers = " + nPointers);

        variables.put("N_POINTER_TYPES", "" + nPointers);

        List<Integer> data = new ArrayList<>();

        for (int i = 0; i < nPointers; i++) {
            List<Integer> white_data = new ArrayList<>();
            List<Integer> black_data = new ArrayList<>();
            for (int col = 0; col < 2; col++) {
                for (int j = 0; j < 16; j++) {
                    int white_sprite = 0;
                    int black_sprite = 0;
                    int mask = 1;
                    for (int k = 0; k < 8; k++, mask *= 2) {
                        int c = MSXColors.getImageColor(img, (i * 2 + col) * 8 + (7 - k), j, config);
                        if (c >= 0) {
                            if (c == MSXColors.COLOR_WHITE) {
                                white_sprite += mask;
                            } else {
                                black_sprite += mask;
                            }
                        }
                    }
                    white_data.add(white_sprite);
                    black_data.add(black_sprite);
                }
            }
            data.addAll(white_data);
            data.addAll(black_data);
        }

        PAKET.createFoldersIfNecessary(outputFolder);
        FileWriter fw = new FileWriter(new File(outputFolder + "pointers.asm"));
        Z80Assembler.dataBlockToAssembler(data, "pointers", fw, 16);
        fw.close();

        PAKETCompiler.callMDL(new String[]{outputFolder + "pointers.asm", "-bin", outputFolder + "pointers.bin"}, config);
        PAKET.compress(outputFolder + "pointers.bin", outputFolder + "pointers", config);

        return data.size();
    }

    // returns the size of the largest bank:
    @Override
    public int generateObjectTypeBanks(String outputFolder, List<PAKObjectType> objectTypes, PAKGame game,
            PAKETOptimizer optimizerState) throws Exception {
        Pair<Integer, Integer> tmp = generateObjectTypeBanksInternal(outputFolder, objectTypes, game, optimizerState);
        int n_banks = tmp.m_a;
        int largest_size = tmp.m_b;

        String objectBanksCode = "objectTypeBanksPointers:\n";
        for (int i = 0; i < n_banks; i++) {
            objectBanksCode += "    dw objectTypeBank" + i + "\n";
        }
        objectBanksCode += "\n";
        for (int i = 0; i < n_banks; i++) {
            objectBanksCode += "objectTypeBank" + i + ":\n";
            objectBanksCode += "    incbin \"data/objectTypeBank" + i + "." + PAKETConfig.compressorExtension[config.compressor] + "\"\n";
        }

        variables.put("OBJECT_TYPE_BANKS", objectBanksCode);
        return largest_size;
    }

    @Override
    public void screenVariables(int gui_width, int gui_height, int max_room_height, PAKGame game) throws Exception {
        int inventory_start_column = 4;
        if (MSX_TILES_PER_ENGINE_TILE == 1) {
            inventory_start_column = 8;
        }

        // Check that area dimensions do not violate constraints:
        if (TEXT_AREA_DIMENSIONS[1] + TEXT_AREA_DIMENSIONS[3] > GAME_AREA_DIMENSIONS[1]) {
            throw new Exception("Game area top coordinate (" + (GAME_AREA_DIMENSIONS[1]) + ") is lower than the bottom of the text area (" + (TEXT_AREA_DIMENSIONS[1] + TEXT_AREA_DIMENSIONS[3]) + ")!");
        }
        if (GAME_AREA_DIMENSIONS[1] + GAME_AREA_DIMENSIONS[3] > GUI_DIMENSIONS[1]) {
            throw new Exception("GUI top coordinate (" + (GUI_DIMENSIONS[1]) + ") is lower than the bottom of the text area (" + (GAME_AREA_DIMENSIONS[1] + GAME_AREA_DIMENSIONS[3]) + ")!");
        }
        if (GUI_DIMENSIONS[1] < 16) {
            throw new Exception("GUI must start at least in Y coordinate 16, but starts at " + GUI_DIMENSIONS[1] + "!");
        }
        if (TEXT_AREA_DIMENSIONS[0] < 0 || TEXT_AREA_DIMENSIONS[0] + TEXT_AREA_DIMENSIONS[2] > (256 / TILE_WIDTH)
                || TEXT_AREA_DIMENSIONS[1] < 0 || TEXT_AREA_DIMENSIONS[1] + TEXT_AREA_DIMENSIONS[3] > 24) {
            throw new Exception("Text area is not withiin the MSX screen (remember, it is divided into 16x24 PAKET tiles).");
        }
        if (GAME_AREA_DIMENSIONS[0] < 0 || GAME_AREA_DIMENSIONS[0] + GAME_AREA_DIMENSIONS[2] > (256 / TILE_WIDTH)
                || GAME_AREA_DIMENSIONS[1] < 0 || GAME_AREA_DIMENSIONS[1] + GAME_AREA_DIMENSIONS[3] > 24) {
            throw new Exception("Game area is not withiin the MSX screen (remember, it is divided into 16x24 PAKET tiles).");
        }
        if (GUI_DIMENSIONS[0] < 0 || GUI_DIMENSIONS[0] + GUI_DIMENSIONS[2] > (256 / TILE_WIDTH)
                || GUI_DIMENSIONS[1] < 0 || GUI_DIMENSIONS[1] + GUI_DIMENSIONS[3] > 24) {
            throw new Exception("GUI area is not withiin the MSX screen (remember, it is divided into 16x24 PAKET tiles).");
        }

        variables.put("MSX_TILES_PER_ENGINE_TILE", "" + MSX_TILES_PER_ENGINE_TILE);
        variables.put("SCREEN_WIDTH_IN_TILES", "" + SCREEN_WIDTH_IN_TILES);
        variables.put("GAME_AREA_HEIGHT_IN_TILES", "" + GAME_AREA_HEIGHT_IN_TILES);
        variables.put("FIRST_SCREEN_ROOM_ROW", "" + GAME_AREA_DIMENSIONS[1]);

        variables.put("INVENTORY_VIDEO_MEM_START", "" + (NAME_TABLE_BANK1 + 32 * (GUI_DIMENSIONS[1] + 1) + (GUI_DIMENSIONS[0] + inventory_start_column) * MSX_TILES_PER_ENGINE_TILE));
        variables.put("INVENTORY_START_X", "" + ((GUI_DIMENSIONS[0] + inventory_start_column - SCREEN_LEFT_MOST_COORDINATE) * TILE_WIDTH));
        variables.put("INVENTORY_START_Y", "" + ((GUI_DIMENSIONS[1] - GAME_AREA_DIMENSIONS[1] + 1) * 8));
        variables.put("INVENTORY_ROWS", "" + INVENTORY_ROWS);
        variables.put("INVENTORY_ITEMS_PER_LINE", "" + INVENTORY_ITEMS_PER_LINE);

        variables.put("GUI_START_PIXEL_X", "" + (GUI_DIMENSIONS[0] * TILE_WIDTH));
        variables.put("GUI_START_PIXEL_Y", "" + ((GUI_DIMENSIONS[1] - GAME_AREA_DIMENSIONS[1]) * 8));

        variables.put("MIN_POINTER_X", "0");
        variables.put("MAX_POINTER_X", "" + (SCREEN_WIDTH_IN_TILES * TILE_WIDTH - 9));
        variables.put("MIN_POINTER_Y", "0");
        variables.put("MAX_POINTER_Y", "" + ((GUI_DIMENSIONS[1] + GUI_DIMENSIONS[3] - GAME_AREA_DIMENSIONS[1]) * PAKRoom.TILE_HEIGHT - 7));

        variables.put("GUI_VDP_PTR", "NAMTBL2 + " + (GUI_DIMENSIONS[0] * MSX_TILES_PER_ENGINE_TILE + GUI_DIMENSIONS[1] * 32));
        variables.put("FIRST_GUI_VDP_TILE", "" + ((GUI_DIMENSIONS[1] - 16) * 32));

        if (!variables.containsKey("N_PLAYER_SPRITES")) {
            variables.put("N_PLAYER_SPRITES", "0");
        }

        variables.put("OBJECT_TYPES_PER_BANK", "" + config.objectTypesPerBank);

        // Safety checks:
        if (INVENTORY_ITEMS_PER_LINE <= 0 || INVENTORY_ITEMS_PER_LINE > (gui_width - 4)) {
            throw new Exception("Inventory can have between 1 and " + (gui_width - 4) + " items in a row, but " + INVENTORY_ITEMS_PER_LINE + " were requested.");
        }
        if (INVENTORY_ROWS <= 0 || INVENTORY_ROWS > 1) {
            throw new Exception("Inventory can have between 1 and 1 rows in MSX, but " + INVENTORY_ROWS + " were requested.");
        }
    }

    @Override
    public void addLoadingScreen(String fileName, int mode_or_screen, String destinationFolder) throws Exception {
        config.info("MSX: addLoadingScreen not supported, ignoring...");
    }

    @Override
    public Pair<Integer, Integer> generateCutsceneImageData(PAKCutsceneImage image, String destinationFolder) throws Exception {
        List<Integer> data = new ArrayList<>();
        
        // The data will be: tiles size, tiles, w, h, nametable
          
        // Tiles:
        List<Integer> tileData;
        tileData = TileBankMSX.generateCutSceneTileData(image.tiles, tileExtractor, image.name, this, config);
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
    public int getAdditionalCutsceneSpaceRequirement() throws Exception
    {
        // Returns the number of extra bytes needed to render a cutscene in the general buffer, other than what is needed to decompress the data (e.g. to compute mirror tiles)
        return 16 * MSX_TILES_PER_ENGINE_TILE;
    }
    

    @Override
    public int clearScreenType(String tag) throws Exception {
        throw new Exception("No specific types of clear-screen can be specified for MSX.");
    }

    
    @Override
    public void printScriptArguments(PAKScript s, PAKGame game, List<Integer> bytes) throws Exception {
        int CHRTBL2_ADDRESS = 0x0000;
        int y = s.y / 8;  // round to 8 pixels
        int x = s.x / 8;  // round to 8 pixels
        int address = CHRTBL2_ADDRESS + (y * SCREEN_WIDTH_IN_TILES + x) * 8;
        bytes.add(address%256);
        bytes.add(address/256);
        bytes.add(s.color * 16);
    }

    
    @Override
    public void addCutsceneImageMetaData(int x, int y, PAKCutsceneImage image, List<Integer> bytes) throws Exception 
    {
        int CHRTBL2_ADDRESS = 0x0000;
        
        int address = CHRTBL2_ADDRESS + (y * SCREEN_WIDTH_IN_TILES + x) * 8;
        bytes.add(address%256);
        bytes.add(address/256);
    }

    
    @Override
    public void generateBinary(PAKGame game, String destinationFolder) throws Exception {
        String binaryName = destinationFolder + File.separator + "pac-" + game.language;
        if (config.run_mdl_optimizers) {
            config.diggest("PAKETCompiler: compiling game (with MDL optimization)...");
            PAKETCompiler.callMDL(new String[]{destinationFolder + "/src/main-autogenerated.asm", "-bin", binaryName + ".rom", "-st", binaryName + ".sym", "-st-constants", "-po", "-ro", "-asm+:html", binaryName + ".html"}, config);
        } else {
            config.diggest("PAKETCompiler: compiling game...");
            PAKETCompiler.callMDL(new String[]{destinationFolder + "/src/main-autogenerated.asm", "-bin", binaryName + ".rom", "-st", binaryName + ".sym", "-st-constants",}, config);
        }

        calculateUsedAndFreeSpace(binaryName + ".sym");
    }

    public void calculateUsedAndFreeSpace(String symbolFile) throws Exception {
        int start_of_rom = 16 * 1024;
        int end_of_rom = 0;
        int start_of_ram = 48 * 1024;
        int end_of_ram = 0;
        int max_usable_ram = 0xf380;
        int stack_size = 128;

        BufferedReader br = new BufferedReader(new FileReader(symbolFile));
        while (true) {
            String line = br.readLine();
            if (line == null) {
                break;
            }
            if (line.contains(END_OF_RAM_TAG)) {
                end_of_ram = getSymbolTableAddress(line);
            }
            if (line.contains(END_OF_ROM_TAG)) {
                end_of_rom = getSymbolTableAddress(line);
            }
        }

        int rom_free = (48 * 1024 - end_of_rom);
        int ram_free = ((max_usable_ram - stack_size) - end_of_ram);

        config.diggest("---- General Space Analysis ----");
        config.diggest("ROM used: " + (end_of_rom - start_of_rom));
        config.diggest("ROM available: " + rom_free);
        config.diggest("Total RAM space used: " + (end_of_ram - start_of_ram));
        config.diggest("Total RAM space available: " + ram_free);
    }

    @Override
    public void MemoryAllocation(String outputFolder, HashMap<String, String> assemblerVariables, PAKGame game) throws Exception {
    }

    @Override
    public List<Pair<String, String>> getAsmPatternsToInstantiate() {
        List<Pair<String, String>> patterns = new ArrayList<>();
        patterns.add(new Pair<>("main.asm-pattern", "/src/main-autogenerated.asm"));
        patterns.add(new Pair<>("rules.asm-pattern", "/src/rules-autogenerated.asm"));
        patterns.add(new Pair<>("gfx.asm-pattern", "/src/gfx-autogenerated.asm"));
        return patterns;
    }
}
