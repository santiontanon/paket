/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.compiler;

import paket.compiler.optimizers.PAKETOptimizer;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.StringTokenizer;
import javax.imageio.ImageIO;
import paket.compiler.optimizers.ObjectTypesOptimizer;
import paket.compiler.optimizers.RoomOptimizer;
import paket.compiler.optimizers.TextStringsOptimizer;
import paket.compiler.optimizers.TilesOptimizer;
import paket.music.TSVInstrument;
import paket.music.TSVNote;
import paket.music.TSVSong;
import paket.music.TSVSongSplitOptimizer;
import paket.music.TSVMusicParser;
import paket.pak.PAKCutsceneImage;
import paket.pak.PAKDialogue;
import paket.pak.PAKDialogue.PAKDialogueState;
import paket.pak.PAKGame;
import paket.pak.PAKItem;
import paket.pak.PAKObject;
import paket.pak.PAKObjectType;
import paket.pak.PAKRoom;
import paket.pak.PAKRule;
import paket.pak.PAKRule.PAKScript;
import paket.pak.PAKSFX;
import paket.pak.PAKSong;
import paket.platforms.Platform;
import paket.text.EncodeText;
import paket.util.Pair;
import paket.util.Z80Assembler;

/**
 *
 * @author santi
 */
public class PAKETCompiler {
    PAKETOptimizer textBankOptimizer = null;
    PAKETOptimizer objectTypeBankOptimizer = null;
    PAKETOptimizer roomBankOptimizer = null;
    List<PAKETOptimizer> tileBankOptimizers = new ArrayList<>();
    public HashMap<String, String> assemblerVariables = null;    // these are global variables for compilation, it is shared in Platform and PAKGame    
    TileBankSetAllocator tileDistributor = new TileBankSetAllocator();
    PasswordGameSaves passwordManager = null;
    
    
    public PAKETCompiler(HashMap<String, String> a_assemblerVariables)
    {
        assemblerVariables = a_assemblerVariables;
    }
    
    
    public void compileGame(PAKGame game, Platform platform, String destinationFolder, List<String> dataFolders, PAKETConfig config) throws Exception
    {        
        List<PAKObjectType> objectTypes = new ArrayList<>();
        textBankOptimizer = new TextStringsOptimizer("text-" + game.language, 1, game.font);
        objectTypeBankOptimizer = new ObjectTypesOptimizer("object-types", 1, config.objectTypesPerBank, game, platform);
        roomBankOptimizer = new RoomOptimizer("rooms", 1, config.roomsPerBank, objectTypes, game, platform);        
        textBankOptimizer.loadOptimizerState(destinationFolder, config);
        objectTypeBankOptimizer.loadOptimizerState(destinationFolder, config);
        roomBankOptimizer.loadOptimizerState(destinationFolder, config);
        
        platform.initPlatform(assemblerVariables, game);

        if (game.loadSaveGameScript != null && game.saveGameMode.equals("password")) {
            passwordManager = new PasswordGameSaves(game, platform, config);
            passwordManager.init();
        }
        
        objectTypes.addAll(game.objectTypesHash.values());
        
        // Get the player height:
        boolean found = false;
        for(PAKRoom r:game.rooms) {
            for(PAKObject o:r.objects) {
                if (o.type.ID.equals("player")) {
                    if (o.ID != config.playerObjectId) {
                        throw new Exception("Player ID in room " + r.ID + " is " + o.ID + " instead of " + config.playerObjectId);
                    }
                    game.playerHeightInPixels = o.type.getPixelMaximumHeightConsideringCropping();
                    game.playerWidthInPixels = o.type.getPixelMaximumWidthConsideringCropping();
                    game.playerAnimationLengths[PAKObjectType.DIRECTION_LEFT] = o.type.getAnimationLength(PAKObjectType.DIRECTION_LEFT);
                    game.playerAnimationLengths[PAKObjectType.DIRECTION_RIGHT] = o.type.getAnimationLength(PAKObjectType.DIRECTION_RIGHT);
                    game.playerAnimationLengths[PAKObjectType.DIRECTION_BACK] = o.type.getAnimationLength(PAKObjectType.DIRECTION_BACK);
                    game.playerAnimationLengths[PAKObjectType.DIRECTION_FRONT] = o.type.getAnimationLength(PAKObjectType.DIRECTION_FRONT);
                    found = true;
                    break;
                }
            }
            if (found) break; 
        }
        
        config.info("PAKETCompiler: generating color palettes...");
        generateColorPalettes(game, platform, config);
        
        config.info("PAKETCompiler: generating tile banks...");
        generateTileData(game, platform, destinationFolder, dataFolders, config);

        config.info("PAKETCompiler: generating font...");
        game.font.saveToAssemblerBinary(destinationFolder + "/src/data/font.bin");

        config.info("PAKETCompiler: generating text banks...");
        int max_text_bank_size = generateTextData(game, destinationFolder, config);
        
        config.info("PAKETCompiler: generating music...");
        generateSongData(game, destinationFolder, dataFolders, platform, config);
        
        config.info("PAKETCompiler: generating sfx...");
        generateSFXData(game, destinationFolder, dataFolders, platform, config);

        config.info("PAKETCompiler: instantiating sound sources...");
        InstantiateSongSourceFiles(game, destinationFolder, dataFolders, platform, config);
        
        config.info("PAKETCompiler: generating cutscene images...");
        int max_cutscene_image_size = generateCutsceneImageData(game, destinationFolder, platform, config);

        // generating the gui and item data:
        config.info("PAKETCompiler: generating the GUI and item data...");
        BufferedImage gui_image = ImageIO.read(new File(PAKET.getFileName(config.guiFileName, dataFolders, config)));
        int gui_data_size = platform.generateGUIData(gui_image, destinationFolder + File.separator + "tiles-gui.png", destinationFolder+"/src/", "gui.png", game);
        game.generalBufferRequirement(gui_data_size, "gui data");
        int gui_width = gui_image.getWidth()/platform.TILE_WIDTH;
        if (gui_width > platform.SCREEN_WIDTH_IN_TILES) throw new Exception("GUI is wider than the screen! " + gui_width + " > " + platform.SCREEN_WIDTH_IN_TILES);
        int gui_height = gui_image.getHeight()/PAKRoom.TILE_HEIGHT;
        assemblerVariables.put("GUI_WIDTH", "" + gui_width);
        assemblerVariables.put("GUI_HEIGHT", "" + gui_height);
        
        // Generate item graphic data:
        int items_data_size = platform.generateItemData(destinationFolder+"/src/", game.itemIDs, game.itemHash, game.textIDHash, destinationFolder, game);
        game.generalBufferRequirement(items_data_size, "item data");
        
        config.info("PAKETCompiler: generating object type data...");
        // Object type data has to be generated BEFORE room data, since the
        // object type order might change here.
        int max_object_type_bank_size = platform.generateObjectTypeBanks(destinationFolder+"/src/", objectTypes, game, objectTypeBankOptimizer);
        game.generalBufferRequirement(max_object_type_bank_size, "largest object type bank");
        objectTypeBankOptimizer.saveOptimizerState(destinationFolder);        
        
        // And then, room data must be generated before the scripts, since room
        // order might change here.
        config.info("PAKETCompiler: generating room data...");
        int largest_uncompressed_room_size = generateRoomData(game, destinationFolder+"/src/", objectTypes, assemblerVariables, platform.SCREEN_WIDTH_IN_TILES*8, game.textIDHash, game.dialogues, platform, config);
        game.generalBufferRequirement(largest_uncompressed_room_size, "largest room");
        roomBankOptimizer.saveOptimizerState(destinationFolder);
        
        // Obtain the set of scripts used by the game:
        config.info("PAKETCompiler: identifying the minimal subset of the script language required for this game...");
        identifyingMinimalScriptLanguage(game, config);        
        
        // Compile the dialogues:
        config.info("PAKETCompiler: compiling dialogues...");
        generateDialogueData(game.dialogues, destinationFolder+"/src/", game.textIDHash, game, assemblerVariables, platform, config);
        
        // Global rules:
        config.info("PAKETCompiler: generating global rules...");
        verifyGlobalRules(game.onRoomLoadRules, game.rooms, config);
        verifyGlobalRules(game.onRoomStartRules, game.rooms, config);
        generateGlobalRulesData(game.onRoomLoadRules, destinationFolder+"/src/data/onroomloadrules.bin", game.textIDHash, game, game.dialogues, platform, config);
        generateGlobalRulesData(game.onRoomStartRules, destinationFolder+"/src/data/onroomstartrules.bin", game.textIDHash, game, game.dialogues, platform, config);
        generateGlobalRulesData(game.itemRules, destinationFolder+"/src/data/itemrules.bin", game.textIDHash, game, game.dialogues, platform, config);
        generateGlobalScriptsData(game.scripts, game.textIDHash, game, game.dialogues, platform, config);
        
        // Savegames:
        if (passwordManager != null) {
             passwordManager.testPasswordSystem();
            passwordManager.fillAssemblerVariables(assemblerVariables);
        }
        
        // Gather bounds for buffer sizes:
        int max_room_width = 1;
        int max_room_height = 1;
        int max_objects_per_room = 1;
        int max_object_types_per_room = 1;
        int max_object_name_buffer_size = 0;
        game.maxRoomSpecificOnLoadRulesSize = 0;
        game.room_tiles_objects_combined_buffer_size = 0;
        for(PAKRoom r:game.rooms) {
            if (r.width > max_room_width) max_room_width = r.width;
            if (r.height > max_room_height) max_room_height = r.height;
            List<Integer> roomTiles = r.getUsedTiles();
            if (roomTiles.size()>game.max_tiles_per_room) game.max_tiles_per_room = roomTiles.size();
            
            if (r.objects.size() > max_objects_per_room) max_objects_per_room = r.objects.size();
            Pair<Integer,Integer> tmp = numBytesForRoomObjectTypes(r, game.rooms, platform, config);
            int n_bytes_for_object_types = tmp.m_a;
            int object_name_buffer_size = tmp.m_b;
            int n_object_types = nObjectTypesInRoom(r);
            if (n_object_types > max_object_types_per_room) max_object_types_per_room = n_object_types;
            if (object_name_buffer_size > max_object_name_buffer_size) max_object_name_buffer_size = object_name_buffer_size;
            int roomSpecificOnLoadRulesSize = 
                r.sizeOfRulesData(r.onLoadRules, game.textIDHash, game, game.dialogues, platform) +
                r.sizeOfRulesData(r.onStartRules, game.textIDHash, game, game.dialogues, platform);
            if (roomSpecificOnLoadRulesSize > game.maxRoomSpecificOnLoadRulesSize) {
                game.maxRoomSpecificOnLoadRulesSize = roomSpecificOnLoadRulesSize;
            }
            int room_bytes_for_room_tiles_object_types = roomTiles.size() * 32 + n_bytes_for_object_types;
            if (room_bytes_for_room_tiles_object_types > game.room_tiles_objects_combined_buffer_size) game.room_tiles_objects_combined_buffer_size = room_bytes_for_room_tiles_object_types; 
        }
        if (max_room_width > platform.SCREEN_WIDTH_IN_TILES) throw new Exception("Room is wider than the screen!");

        // Some safety checks in case of pathfinding:
        if (game.pathfinding != PAKGame.PATHFINDING_OFF) {
            int room_width_in_pixels = max_room_width * platform.TILE_WIDTH;
            int path_finding_map_width = room_width_in_pixels / platform.PATH_FINDING_WALK_TILE_WIDTH;
            if (path_finding_map_width * max_room_height / 2 > 256) {
                throw new Exception("path_finding_map_width * max_room_height > 256: pathfinding code will not support this!");
            }
            int path_finding_open_list_size = 128;
            int path_finding_map_height = max_room_height * 2;
            int pathfinding_minimum_buffer_size = 
                    path_finding_map_width * path_finding_map_height + 
                    path_finding_open_list_size * 4 + 6;
            game.generalBufferRequirement(pathfinding_minimum_buffer_size, "pathfinding");
        }
                
        // During loading, we will split the general buffer in two, so we need to fit these two elements:
        int savegame_data_size = 1 + platform.inventorySize() + game.gameStateVariableNames.size() + 4;                
        game.generalBufferRequirement(max_object_types_per_room*5 + max_object_type_bank_size, "object types + largest object bank");
        game.generalBufferRequirement(max_object_types_per_room*5 + max_text_bank_size, "object types + largest text bank");
        game.generalBufferRequirement(5 + savegame_data_size + max_text_bank_size, "savegame data + largest text bank");
        game.generalBufferRequirement(max_cutscene_image_size + platform.getAdditionalCutsceneSpaceRequirement(), "largest cutscene");
        game.generalBufferRequirement(max_object_types_per_room*5 + config.maxTextBankSize + platform.inventorySize(), "for remove_item_from_inventory");
        
        config.info("MUSIC_BUFFER_SIZE: " + game.music_buffer_size);
        config.info("GENERAL_BUFFER_SIZE: " + game.general_buffer_size + " (defined by " + game.largestBufferRequirementTag + ")");
        assemblerVariables.put("ROOM_OBJECTS_NAME_BUFFER_SIZE", "" + max_object_name_buffer_size);
        
        if (game.doubleClickOnExit == 1 && game.pathfinding == PAKGame.PATHFINDING_OFF) {
            config.error("doubleClickOnExit can only be active if path finding is on!");
            return;
        }
        // Instantiate the pattern variables:
        assemblerVariables.put("START_ADDRESS", "" + platform.START_ADDRESS);
        assemblerVariables.put("GAME_START_LABEL", "game_start");
        assemblerVariables.put("MUSIC_BUFFER_SIZE", ""+game.music_buffer_size);
        assemblerVariables.put("GENERAL_BUFFER_SIZE", ""+game.general_buffer_size);
        assemblerVariables.put("MAX_ROOM_WIDTH", ""+max_room_width);
        assemblerVariables.put("MAX_ROOM_HEIGHT", ""+max_room_height);
        assemblerVariables.put("MAX_TILES_PER_ROOM", ""+game.max_tiles_per_room);
        assemblerVariables.put("MAX_OBJECTS_PER_ROOM", ""+max_objects_per_room);
        assemblerVariables.put("MAX_OBJECT_TYPES_PER_ROOM", ""+max_object_types_per_room);
        assemblerVariables.put("ROOM_TILES_OBJECT_COMBINED_BUFFER_SIZE", ""+game.room_tiles_objects_combined_buffer_size);
        assemblerVariables.put("INVENTORY_SIZE", ""+platform.inventorySize());
        assemblerVariables.put("NUM_ITEMS_IN_GAME", ""+game.itemHash.size());
        assemblerVariables.put("PLAYER_HEIGHT", ""+game.playerHeightInPixels);
        assemblerVariables.put("PLAYER_WIDTH", ""+game.playerWidthInPixels);
        assemblerVariables.put("PLAYER_COLLISION_WIDTH", ""+(game.playerWidthInPixels / 2));
        assemblerVariables.put("PLAYER_WALK_LEFT_N_FRAMES", ""+(game.playerAnimationLengths[PAKObjectType.DIRECTION_LEFT]));
        assemblerVariables.put("PLAYER_WALK_RIGHT_N_FRAMES", ""+(game.playerAnimationLengths[PAKObjectType.DIRECTION_RIGHT]));
        assemblerVariables.put("PLAYER_WALK_UP_N_FRAMES", ""+(game.playerAnimationLengths[PAKObjectType.DIRECTION_BACK]));
        assemblerVariables.put("PLAYER_WALK_DOWN_N_FRAMES", ""+(game.playerAnimationLengths[PAKObjectType.DIRECTION_FRONT]));
        assemblerVariables.put("PLAYER_OBJECT_ID", ""+config.playerObjectId);
        if (game.resetPlayerStateDirection == null) {
            assemblerVariables.put("RESET_PLAYER_DIRECTION_ON_IDLE", "0");
            assemblerVariables.put("PLAYER_DIRECTION_ON_IDLE", "0");
        } else {
            assemblerVariables.put("RESET_PLAYER_DIRECTION_ON_IDLE", ""+1);
            assemblerVariables.put("PLAYER_DIRECTION_ON_IDLE", ""+game.resetPlayerStateDirection);
        }
        assemblerVariables.put("N_GAME_STATE_VARIABLES", ""+game.gameStateVariableNames.size());
        assemblerVariables.put("FRAME_AROUND_SUBROOMS", game.frame_around_subrooms ? "1":"0");
        assemblerVariables.put("USE_PATH_FINDING", "" + game.pathfinding);
        assemblerVariables.put("DOUBLE_CLICK_ON_EXIT", "" + game.doubleClickOnExit);
        if (game.loadSaveGameScript != null) {
            assemblerVariables.put("ALLOW_SAVING_GAME", "1");
            assemblerVariables.put("LOAD_SAVE_GAME_SCRIPT", PAKETCompiler.nameToAssemblerLabel(game.loadSaveGameScript));
        } else {
            assemblerVariables.put("ALLOW_SAVING_GAME", "0");
            assemblerVariables.put("LOAD_SAVE_GAME_SCRIPT", "0");
        }
        assemblerVariables.put("PATH_FINDING_MAX_LENGTH", ""+game.pathfinding_max_length);
        assemblerVariables.put("STOP_EARLY_WHEN_WALKING", game.stopEarlyWhenWalking ? "1":"0");
        assemblerVariables.put("USE_ITEM_WITH_ITEM_SYMMETRIC", game.useItemWithItemSymmetric ? "1":"0");
        assemblerVariables.put("ROOMS_PER_BANK", "" + config.roomsPerBank);
        assemblerVariables.put("RULE_CONSTANTS", PAKRule.generateConstantDefinitionAssembler());
        assemblerVariables.put("TEXT_BANK_MAX_SIZE", "" + config.maxTextBankSize);
        assemblerVariables.put("ROOM_SPECIFIC_ON_LOAD_RULES_BUFFER_SIZE", "" + game.maxRoomSpecificOnLoadRulesSize);
        config.info("gameStateVariableNames:");
        for(String v:game.gameStateVariableNames) {
            if (game.gameStateVariableInitialValues.containsKey(v)) {
                config.info("    " + v + " = " + game.gameStateVariableInitialValues.get(v));
            } else {
                config.info("    " + v);
            }
        }
        switch(config.compressor) {
            case PAKETConfig.COMPRESSOR_ZX0:
                assemblerVariables.put("DECOMPRESSOR_FUNCTION_NAME", "dzx0_standard");
                assemblerVariables.put("DECOMPRESSOR_INCLUDE", "include \"dzx0_standard.asm\"");
                assemblerVariables.put("COMPRESSOR_EXTENSION", "zx0");
                break;
            case PAKETConfig.COMPRESSOR_PLETTER:
                assemblerVariables.put("DECOMPRESSOR_FUNCTION_NAME", "pletter_unpack");
                assemblerVariables.put("DECOMPRESSOR_INCLUDE", "include \"pletter.asm\"");
                assemblerVariables.put("COMPRESSOR_EXTENSION", "plt");
                break;
        }
        generatePlayerScaling(game);
        
        // game state variable initialization (for those that have initial value != 0):
        {
            List<Integer> values = new ArrayList<>();
            HashMap<Integer, List<String>> valueVariables = new HashMap<>();
            for(String variableName:game.gameStateVariableInitialValues.keySet()) {
                int value = game.gameStateVariableInitialValues.get(variableName);
                if (value == 0) {
                    continue;
                    // variables are already at 0 by default
                }
                if (!valueVariables.containsKey(value)) {
                    valueVariables.put(value, new ArrayList<>());
                    values.add(value);
                }
                valueVariables.get(value).add(variableName);
            }
            
            String initializationCode = "";
            int previousValue = -1;
            Collections.sort(values);
            for(int value:values) {
                if (value == 0) {
                    initializationCode += "    xor a\n";
                } else if (value == previousValue -1) {
                    initializationCode += "    inc a\n";
                } else {
                    initializationCode += "    ld a, "+value+"\n";
                }
                for(String variableName:valueVariables.get(value)) {
                    int idx = game.gameStateVariableNames.indexOf(variableName);
                    if (idx < 0) {
                        throw new Exception("Referring un known variable " + variableName);
                    }
                    initializationCode += "    ld (game_state_variables + "+idx+"), a\n";
                }
            }
            assemblerVariables.put("GAME_STATE_VARIABLE_INITIALIZATION", initializationCode);
            
            String labelDefinitionCode = "";
            for(int variableOffset = 0;variableOffset<game.gameStateVariableNames.size();variableOffset++) {
                String variableName = game.gameStateVariableNames.get(variableOffset);
                variableName = variableName.replace(" ", "_");
                variableName = variableName.replace("-", "_");
                labelDefinitionCode += "game_state_variable_" + variableName + ": equ game_state_variables + " + variableOffset + "\n";
            }
            assemblerVariables.put("GAME_STATE_VARIABLE_LABELS", labelDefinitionCode);
        }
        
        // screen variables:
        platform.screenVariables(gui_width, gui_height, max_room_height, game);

        assemblerVariables.put("SPACE_CHARACTER", "0");
        
        // Allocate RAM buffers, variables and data blocks to the different
        // parts of memory available in the target platform:
        instantiateAsmPatternFromResources("constants.asm-pattern", destinationFolder+"/src/constants-autogenerated.asm", assemblerVariables, platform);
        platform.MemoryAllocation(destinationFolder+"/src/", assemblerVariables, game);
        
        String additionalIncludes = "";
        for(String assemblerFileName:game.additionalAssemblerFiles) {
            instantiateAsmPatternFromPath(assemblerFileName, destinationFolder+"/src/" + assemblerFileName, dataFolders, assemblerVariables, platform, config);
            additionalIncludes += "  include \"" + assemblerFileName + "\"\n";
        }
        for(Pair<String, String> pair:game.additionalAssemblerIncbin) {
            // Load the binary and convert it to assembler data directly:
            String inputFilePath = PAKET.getFileName(pair.m_b, dataFolders, config);
            FileInputStream fis = new FileInputStream(new File(inputFilePath));
            byte data[] = fis.readAllBytes();
            additionalIncludes += pair.m_a + ":\n";
            additionalIncludes += Z80Assembler.dataBlockArrayToAssemblerString(data, 16);
        }
        assemblerVariables.put("ADDITIONAL_ASSEMBLER_FILES", additionalIncludes);
        {
            String additionalAssemblerFunctions = "";
            for(String fName:game.assemblerFunctions) {
                additionalAssemblerFunctions += "   dw " + fName + "\n";
            }
            assemblerVariables.put("ADDITIONAL_ASSEMBLER_FUNCTIONS", additionalAssemblerFunctions);
        }
        
//        platform.generateItemData(platform.targetSystemName + "/src/", game.itemIDs, game.itemHash, game.textIDHash, destinationFolder, game);

        config.info("PAKETCompiler: main assembler file...");
        initializeAssemblerSourceCode(destinationFolder+"/src", assemblerVariables, platform, game, config);
        for(Pair<String, String> pair:platform.getAsmPatternsToInstantiate()) {
            instantiateAsmPatternFromResources(pair.m_a, destinationFolder+pair.m_b, assemblerVariables, platform);
        }                
        
        config.info("PAKETCompiler: rules size analysis:");
        config.info("    # rules: " + game.getTotalNumberOfRules());
        config.info("    total rules size: " + game.getTotalRulesSize(platform));
        
        config.info("PAKETCompiler: generating binary...");
        platform.generateBinary(game, destinationFolder);        
    }

    
    public void generateColorPalettes(PAKGame game, Platform platform, PAKETConfig config) throws Exception
    {
        platform.generateColorPalettes(game, assemblerVariables);
    }
    
    
    public void generateTileData(PAKGame game, Platform platform, String destinationFolder, List<String> dataFolders, PAKETConfig config) throws Exception
    {
        // Each tile is directly the bytes used to store it in each platform
        List<List<Integer>> roomTiles = new ArrayList<>();  // Tile indexes (from "mergedTiles") used by each room
        List<int []> mergedTiles = new ArrayList<>();
        List<int []> mergedTilesMirrored = new ArrayList<>();
        
        // Start with an empty tile:
//        {
//            int emptyTile[] = new int[platform.getTileSizeInBytes()];
//            for(int i = 0;i<emptyTile.length;i++) {
//                emptyTile[i] = 0;  // assuming that an empty tile is a tile with all 0s
//            }
//            mergedTiles.add(emptyTile);
//        }
        
        // Step 1: identify the set of tiles in each room (including mirrored tiles):
        int MIRRORED_MASK = 0x8000;  // Assuming we will not have more than 64k tiles :)
        int MIRRORED_MASK_REMOVE = 0x7fff;
        for(PAKRoom room:game.rooms) {  
            List<Integer> thisRoomTiles = new ArrayList<>();
//            thisRoomTiles.add(1);  // always have the empty tile
            // merge with existing ones (translating the tiles in the room):
            HashMap<Integer, Integer> map = new HashMap<>();
            for(int i = 0;i<room.height;i++) {
                for(int j = 0;j<room.width;j++) {
                    int tile = room.background[j][i];
                    if (tile == 0) throw new Exception("Background tile " + j + "," + i + " of room " + room.ID + " is empty!");
                    // check if we have a repeated tile:
                    if (!map.containsKey(tile)) {
                        int idx = findTileMatch(room.tiles, tile-1, "room_" + room.ID, mergedTiles, platform, room.tilesFileName);
                        if (idx == -1) {
                            int idx2 = findTileMatch(room.tiles, tile-1, "room_" + room.ID, mergedTilesMirrored, platform, room.tilesFileName);
                            if (idx2 >= 0) {
                                // The tile is a mirrored version of a tile we already had!
                                map.put(tile, idx2 + 1 | MIRRORED_MASK);
//                                System.out.println("map m: " + tile + " --> " + (idx2 + 1 | MIRRORED_MASK));
                            } else {
                                // we have a new tile, add it to the merged image:
                                map.put(tile, mergedTiles.size() + 1);
//                                System.out.println("map  : " + tile + " --> " + (mergedTiles.size() + 1));
                                int tileData[] = addTileToMergedTiles(room.tiles, tile-1, "room_" + room.ID, mergedTiles, platform, room.tilesFileName);
                                int mirroredTiledata[] = platform.mirrorTileDataHorizontally(tileData);
                                
                                if (mirroredTiledata == null) {
                                    throw new Exception("platform.mirrorTileHorizontally returned null!");
                                }
                                mergedTilesMirrored.add(mirroredTiledata);
                            }
                        } else {
                            // we already had it:
                            map.put(tile, idx + 1);
//                            System.out.println("map  : " + tile + " --> " + (idx + 1));
                        }
                    }
                    int tileIdx = map.get(tile) & MIRRORED_MASK_REMOVE;
                    if (!thisRoomTiles.contains(tileIdx)) {
                        thisRoomTiles.add(tileIdx);
                    }
                }
            }
            roomTiles.add(thisRoomTiles);            
        }
        
        // Step 2: generate tile bank sets:
        int MAX_BANK_SET_SIZE = 127;
        game.bankSets = tileDistributor.distributeTilesInBanks(roomTiles, MAX_BANK_SET_SIZE);
        game.roomToBankSet = new HashMap<>();
        platform.variables.put("N_TILE_BANKSETS", "" + game.bankSets.size());
        config.info(mergedTiles.size() + " tiles divided into " + game.bankSets.size() + " bank sets.");
        for(int bankset_idx = 0;bankset_idx<game.bankSets.size();bankset_idx++) {
            TileBankSet bankset = game.bankSets.get(bankset_idx);
            config.info("  bank set: " + bankset.tiles.size() + " (with rooms: " + bankset.rooms + ")");
            for(Integer room:bankset.rooms) {
                game.roomToBankSet.put(game.rooms.get(room).ID, bankset_idx);
            }
        }

        // Step 3: update rooms with proper tile indexes, and bank sets
        int BYTE_MIRROR_MASK = 0x80;
        for(PAKRoom room: game.rooms) {
            // merge with existing ones (translating the tiles in the room):
            TileBankSet bankset = game.bankSets.get(game.roomToBankSet.get(room.ID));
            HashMap<Integer, Integer> map = new HashMap<>();
            for(int i = 0;i<room.height;i++) {
                for(int j = 0;j<room.width;j++) {
                    int tile = room.background[j][i];
                    if (!map.containsKey(tile)) {
                        int idx = findTileMatch(room.tiles, tile-1, "room_" + room.ID, mergedTiles, platform, room.tilesFileName);
                        if (idx == -1) {
                            int idx2 = findTileMatch(room.tiles, tile-1, "room_" + room.ID, mergedTilesMirrored, platform, room.tilesFileName);
                            // The tile is a mirrored version of a tile we already had!
                            map.put(tile, idx2+1 | MIRRORED_MASK);
                        } else {
                            // we already had it:
                            map.put(tile, idx+1);
                        }
                    }
                    int tileIdx = map.get(tile);
                    boolean mirrored = false;
                    if ((tileIdx & MIRRORED_MASK) != 0) {
                        mirrored = true;
                        tileIdx = tileIdx & MIRRORED_MASK_REMOVE;
                    }
                    int bankSetTileIdx = bankset.tiles.indexOf((Integer)tileIdx) + 1;

                    if (mirrored) {
                        room.background[j][i] = bankSetTileIdx | BYTE_MIRROR_MASK;
                    } else {
                        room.background[j][i] = bankSetTileIdx;
                    }
                }
            }
        }
        
        // Clear the TILE_BANKS variable just in case:
        platform.variables.remove("TILE_BANKS");
        game.tile_data_size = 0;
        for(int bankSet_idx = 0;bankSet_idx<game.bankSets.size();bankSet_idx++) {
            TileBankSet bankset = game.bankSets.get(bankSet_idx);
            List<int []> bankSetMergedTiles = new ArrayList<>();
            config.info("bankset " + bankSet_idx + " tiles: " + bankset.tiles);
            for(int tile:bankset.tiles) {
                bankSetMergedTiles.add(mergedTiles.get(tile - 1));
            }
            PAKETOptimizer tileBankOptimizer = new TilesOptimizer("tiles_" + bankSet_idx, 1, platform);
            tileBankOptimizer.loadOptimizerState(destinationFolder, config);
            tileBankOptimizers.add(tileBankOptimizer);
            List<int []> bankSetMergedTiles2 = bankSetMergedTiles;
            if (config.maxTileOptimizationIterations == 0) {
                bankSetMergedTiles2 = (List<int []>) tileBankOptimizer.heuristicCompressionOrder(bankSetMergedTiles, config);            
            } else {
                for(int i = 0;i<config.maxTileOptimizationIterations;i++) {
                     bankSetMergedTiles2 = (List<int []>) tileBankOptimizer.optimizeCompressionOrder(bankSetMergedTiles, config);
                }
            }
            tileBankOptimizer.saveOptimizerState(destinationFolder);

            // update all the room backgrounds and collision masks with the new indexes:
            HashMap<Integer,Integer> orderMap = new HashMap<>();
            for(int i = 0;i<bankSetMergedTiles.size();i++) {
                int tile[] = bankSetMergedTiles.get(i);
                int idx = bankSetMergedTiles2.indexOf(tile);
                orderMap.put(i, idx);
            }        
            for(int room_idx:bankset.rooms) {
                PAKRoom room = game.rooms.get(room_idx);
                for(int row[]:room.background) {
                    for(int i = 0;i<row.length;i++) {
                        int tileIdx = row[i];
                        int mask = 0;
                        if ((tileIdx & BYTE_MIRROR_MASK) != 0) {
                            mask = BYTE_MIRROR_MASK;
                            tileIdx -= BYTE_MIRROR_MASK;
                        }
                        if (!orderMap.containsKey(tileIdx - 1)) {
                            System.out.println("tileIdx - 1: " + (tileIdx - 1));
                        }
                        row[i] = (orderMap.get(tileIdx - 1) + 1) | mask;
                    }
                }
            }

            platform.generateTileBanks(bankSetMergedTiles2, bankSet_idx, destinationFolder+"/src/", game);
        }
    }
    
    
    public int findTileMatch(BufferedImage roomTiles, int tile, String paletteName, List<int []> mergedTiles, Platform platform, String imageName) throws Exception
    {
        int data[] = platform.getTileData(roomTiles, tile, paletteName, imageName);
        //System.out.println("tile: " + Arrays.toString(data));
        for(int i = 0;i<mergedTiles.size();i++) {
            int data2[] = mergedTiles.get(i);
            if (data.length != data2.length) continue;
            boolean found = true;
            for(int j = 0;j<data.length;j++) {
                if (data[j] != data2[j]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return i;
            }
        }
        
        return -1;        
    }


    public int[] addTileToMergedTiles(BufferedImage roomTiles, int tile, String paletteName, List<int []> mergedTiles, Platform platform, String imageName) throws Exception
    {
        int data[] = platform.getTileData(roomTiles, tile, paletteName, imageName);
        mergedTiles.add(data);
        return data;
    }
    
    
    /*
    Finds all the text strings used by the game. 
    - The "forLocalization" argument is used because certain strings (e.g. the
      argument of "print-paragraph" is split into smaller lines before being
      stores in the scripts. But when creating a localization file, we do not
      want the split strings, but the original unsplit string.
    */
    List<String> getAllGameTextStrings(PAKGame game, boolean forLocalization) throws Exception {
        List<String> textLines = new ArrayList<>();
        for(PAKObjectType ot:game.objectTypesHash.values()) {
            if (!textLines.contains(ot.getInGameName())) textLines.add(ot.getInGameName());
            String description = ot.getDescription();
            if (!textLines.contains(description)) {
                textLines.add(description);
            }
        }
        for(PAKItem it:game.itemHash.values()) {
            if (!textLines.contains(it.getInGameName())) textLines.add(it.getInGameName());
            String description = it.getDescription();
            if (!textLines.contains(description)) {
                textLines.add(description);
            }
            if (it.defaultUseMessage != null &&
                !textLines.contains(it.defaultUseMessage)) {
                textLines.add(it.defaultUseMessage);
            }
        }
        textLines.add(game.takeUntakeableErrorMessage);
        textLines.add(game.cannotUseErrorMessage);
        textLines.add(game.cannotTalkErrorMessage);
        textLines.add(game.takeFromInventoryErrorMessage);
        textLines.add(game.cannotReachErrorMessage);        
        
        for(PAKRoom r:game.rooms) {
            for(PAKRule rule:r.rules) {
                for(String text:rule.getTextLines(forLocalization)) {
                    if (text != null) {
                        if (!textLines.contains(text)) textLines.add(text);
                    }
                }
            }
            for(PAKRule rule:r.onLoadRules) {
                for(String text:rule.getTextLines(forLocalization)) {
                    if (text != null) {
                        if (!textLines.contains(text)) textLines.add(text);
                    }
                }
            }
            for(PAKRule rule:r.onStartRules) {
                for(String text:rule.getTextLines(forLocalization)) {
                    if (text != null) {
                        if (!textLines.contains(text)) textLines.add(text);
                    }
                }
            }
        }
        for(PAKRule rule:game.itemRules) {
            for(String text:rule.getTextLines(forLocalization)) {
                if (text != null) {
                    if (!textLines.contains(text)) textLines.add(text);
                }
            }
        }
        for(PAKRule rule:game.onRoomLoadRules) {
            for(String text:rule.getTextLines(forLocalization)) {
                if (text != null) {
                    if (!textLines.contains(text)) textLines.add(text);
                }
            }
        }
        for(PAKRule rule:game.onRoomStartRules) {
            for(String text:rule.getTextLines(forLocalization)) {
                if (text != null) {
                    if (!textLines.contains(text)) textLines.add(text);
                }
            }
        }
        for(Pair<String, PAKRule> script:game.scripts) {
            for(String text:script.m_b.getTextLines(forLocalization)) {
                if (text != null) {
                    if (!textLines.contains(text)) textLines.add(text);
                }
            }
        }
        for(PAKDialogue d:game.dialogues) {
            for(String text:d.getTextLines()) {
                if (text != null) {
                    if (!textLines.contains(text)) textLines.add(text);
                }
            }
        }
        
        for(String key:game.additionalTextStrings.keySet()) {
            String text = game.additionalTextStrings.get(key);
            if (!textLines.contains(text)) textLines.add(text);
        }
        
        return textLines;
    }
    
    
    public int generateTextData(PAKGame game, String destinationFolder, PAKETConfig config) throws Exception {
        
        // generate text data:
        int longest_description = 48;
        int max_text_bank_size = 0;

        config.info("PAKETCompiler: encoding text...");
        List<String> textLines = getAllGameTextStrings(game, false);
        List<String> textLinesForLocalizationFile = getAllGameTextStrings(game, true);

        for(String line:textLines) {
            if (line.length()+1 > longest_description) longest_description = line.length()+1;                
        }
        
        game.textBankSizes = EncodeText.encodeTextInBanks(textLines, game.font, config.maxTextBankSize, destinationFolder+"/src/", game.textIDHash, textBankOptimizer, config);
        textBankOptimizer.saveOptimizerState(destinationFolder);
        for(int s:game.textBankSizes) {
            if (s > max_text_bank_size) max_text_bank_size = s;
        }
        String textBankData = "textBankPointers:\n";
        for(int i = 0;i<game.textBankSizes.size();i++) {
            textBankData+="    dw textBank" + i + "\n";
        }
        textBankData+="\n";
        for(int i = 0;i<game.textBankSizes.size();i++) {
            textBankData+="textBank" + i + ":\n    incbin \"data/textBank" + i + "."+PAKETConfig.compressorExtension[config.compressor]+"\"\n";
        }

        Pair<Integer, Integer> takeFromInventoryErrorMessageIdx = game.textIDHash.get(game.takeFromInventoryErrorMessage);
        Pair<Integer, Integer> takeUntakeableErrorMessageIdx = game.textIDHash.get(game.takeUntakeableErrorMessage);
        Pair<Integer, Integer> cannotReachErrorMessageIdx = game.textIDHash.get(game.cannotReachErrorMessage);
        Pair<Integer, Integer> cannotUseErrorMessageIdx = game.textIDHash.get(game.cannotUseErrorMessage);
        Pair<Integer, Integer> cannotTalkErrorMessageIdx = game.textIDHash.get(game.cannotTalkErrorMessage);
        assemblerVariables.put("TEXT_BANKS", textBankData);
        assemblerVariables.put("TAKE_FROM_INVENTORY_ERROR_MESSAGE_IDX", "("+takeFromInventoryErrorMessageIdx.m_a + "*256)+" + takeFromInventoryErrorMessageIdx.m_b);
        assemblerVariables.put("UNTAKEABLE_ERROR_MESSAGE_IDX", "("+takeUntakeableErrorMessageIdx.m_a + "*256)+" + takeUntakeableErrorMessageIdx.m_b);
        assemblerVariables.put("CANNOT_REACH_ERROR_MESSAGE_IDX", "("+cannotReachErrorMessageIdx.m_a + "*256)+" + cannotReachErrorMessageIdx.m_b);
        assemblerVariables.put("UNUSEABLE_ERROR_MESSAGE_IDX", "("+cannotUseErrorMessageIdx.m_a + "*256)+" + cannotUseErrorMessageIdx.m_b);
        assemblerVariables.put("UNTALKABLE_ERROR_MESSAGE_IDX", "("+cannotTalkErrorMessageIdx.m_a + "*256)+" + cannotTalkErrorMessageIdx.m_b);   
        assemblerVariables.put("ACTION_TEXT_BUFFER_SIZE", ""+(longest_description+1));
        game.actionTextBufferSize = longest_description+1;
        
        // Generate localization file template:
        if (game.language.equals(game.inputFileLanguage)) {
            Collections.sort(textLinesForLocalizationFile);
            FileWriter fw = new FileWriter(new File(destinationFolder + File.separator + "localization.template"));
            fw.write("line:\n\""+game.examine_action+"\"\n\"\"\n\n");
            fw.write("line:\n\""+game.pickup_action+"\"\n\"\"\n\n");
            fw.write("line:\n\""+game.use_action+"\"\n\"\"\n\n");
            fw.write("line:\n\""+game.talk_action+"\"\n\"\"\n\n");
            fw.write("line:\n\""+game.exit_action+"\"\n\"\"\n\n");
            fw.write("line:\n\""+game.with_action+"\"\n\"\"\n\n");
            for(String line:textLinesForLocalizationFile) {
                fw.write("line:\n\""+line+"\"\n\"\"\n\n");
            }
            fw.close();
        }

        // Save a file with all the text lines after having applied the localization (just for debugging):
//        {
//            Collections.sort(textLinesForLocalizationFile);
//            FileWriter fw = new FileWriter(new File(destinationFolder + File.separator + "all-sentences.txt"));
//            for(String line:textLinesForLocalizationFile) {
//                fw.write(line+"\n\n");
//            }
//            fw.close();
//        }
        
        return max_text_bank_size;
    }
    
    
    public void generateDialogueData(List<PAKDialogue> dialogues, String outputFolder, HashMap<String, Pair<Integer,Integer>> textIDHash, PAKGame game, HashMap<String, String> variables, Platform platform, PAKETConfig config) throws Exception
    {
        String dialogueCode1 = "dialoguePointers:\n";
        String dialogueCode2 = "";
        
        int maxDialgueSize = 0;
        for(int i = 0;i<dialogues.size();i++) {
            PAKDialogue dialogue = dialogues.get(i);
            List<Integer> bytes = dialogue.toBytesForAssembler(textIDHash, game, dialogues, platform);
            
            FileWriter fw = new FileWriter(outputFolder + "data/dialogue" + i + ".asm");
            Z80Assembler.dataBlockToAssembler(bytes, "dialogue", fw, 16);
            fw.close();
            PAKETCompiler.callMDL(new String[]{outputFolder + "data/dialogue" + i + ".asm", "-bin", outputFolder + "data/dialogue" + i + ".bin"}, config);
            // PAKET.compress(outputFolder + "data/dialogue" + i + ".bin", outputFolder + "data/dialogue" + i, config);
            
            config.info("Dialogue "+i+" size: " + bytes.size());
            dialogueCode1 += "    dw dialogue_" + i + "\n";
            dialogueCode2 += "dialogue_" + i + ":\n";
            dialogueCode2 += "    incbin \"data/dialogue"+i+".bin\"\n";
            maxDialgueSize = Math.max(maxDialgueSize, bytes.size());
        }
        variables.put("MAX_DIALOGUE_SIZE", "" + maxDialgueSize);
        variables.put("DIALOGUE_DATA", dialogueCode1+"\n"+dialogueCode2);
    }
    
    
    public void generateGlobalRulesData(List<PAKRule> rules, String outputFile, HashMap<String, Pair<Integer,Integer>> textIDHash, PAKGame game, List<PAKDialogue> dialogues, Platform platform, PAKETConfig config) throws Exception
    {
        List<Integer> bytes = new ArrayList<>();
        
        // We keep this to prevent adding repeated rules:
        List<List<Integer>> rulesBytes = new ArrayList<>();
        
        int n_rules = 0;
        for(PAKRule rule:rules) {
            List<Integer> ruleBytes = rule.toBytesForAssembler(textIDHash, game, dialogues, false, true, platform);
            if (rulesBytes.contains(ruleBytes)) {
                config.info("   (prevented adding a repeated global rule)");
            } else {
                rulesBytes.add(ruleBytes);
                config.info("   Global rule size: " + ruleBytes.size() + " -> " + ruleBytes);
                bytes.addAll(ruleBytes);
                n_rules++;
            }
        }
        
        bytes.add(0, n_rules);
        DataOutputStream os = new DataOutputStream(new FileOutputStream(outputFile));
        for(int b:bytes) {
            os.writeByte(b);
        }
        os.close();
        config.info(outputFile + ": " + bytes.size());
    }
    
    
    public void generateGlobalScriptsData(List<Pair<String, PAKRule>> scripts, HashMap<String, Pair<Integer,Integer>> textIDHash, PAKGame game, List<PAKDialogue> dialogues, Platform platform, PAKETConfig config) throws Exception
    {
        String assembler1 = "script_pointers:\n";
        String assembler2 = "";
        
        for(Pair<String, PAKRule> script:scripts) {
            List<Integer> scriptBytes = script.m_b.toBytesForAssembler(textIDHash, game, dialogues, false, false, platform);
            config.info("   Global script "+script.m_a+" size: " + scriptBytes.size() + " -> " + scriptBytes);
            
            String assembler_name = PAKETCompiler.nameToAssemblerLabel(script.m_a);
            assembler1 += "  dw " + assembler_name + "\n";
            assembler2 += assembler_name + ":\n  db " + Z80Assembler.dataBlockToAssemblerString(scriptBytes) + "\n";
        }
        
        assemblerVariables.put("SCRIPTS", assembler1 + assembler2);
    }   
    
    
    public void verifyGlobalRules(List<PAKRule> rules, List<PAKRoom> rooms, PAKETConfig config) throws Exception
    {
        for(PAKRule rule:rules) {
            for(PAKScript s:rule.scripts) {
                if (s.type == PAKRule.SCRIPT_REMOVE_OBJECT ||
                    s.type == PAKRule.SCRIPT_CHANGE_OBJECT_DESCRIPTION ||
                    s.type == PAKRule.SCRIPT_CHANGE_OBJECT_STATE) {
                    int nRoomsWithObject = 0;
                    for(PAKRoom r:rooms) {
                        for(PAKObject o:r.objects) {
                            if (o.ID == s.ID) {
                                nRoomsWithObject ++;
                                break;
                            }
                        }
                    }
                    config.info("nRoomsWithObject(" + s.ID + ") = " + nRoomsWithObject);
                    if (nRoomsWithObject > 1) {
                        config.info("Note: global rule refers to object "+s.ID+" that appears in more than one room!");
                    } 
                }
            }
        }
    }
    
    
    public int generateRoomData(PAKGame game, String outputFolder, List<PAKObjectType> objectTypes, HashMap<String, String> variables, int screenWidth, HashMap<String, Pair<Integer, Integer>> textIDHash, List<PAKDialogue> dialogues, Platform platform, PAKETConfig config) throws Exception
    {
        int largest_size = 0;
        int largest_rules_size = 0;
        int n_rooms_in_current_bank = 0;
        int n_banks = 0;
        
        if (config.roomsPerBank > 1) {
            if (config.maxRoomOptimizationIterations == 0) {
                List<PAKRoom> rooms2 = (List<PAKRoom>) roomBankOptimizer.heuristicCompressionOrder(game.rooms, config);            
                game.rooms.clear();
                game.rooms.addAll(rooms2);
            } else {
                for (int i = 0;i<config.maxRoomOptimizationIterations;i++) {
                    List<PAKRoom> rooms2 = (List<PAKRoom>) roomBankOptimizer.optimizeCompressionOrder(game.rooms, config);
                    game.rooms.clear();
                    game.rooms.addAll(rooms2);
                }
            }
        }
        
        List<PAKRoom> rooms = game.rooms;
        List<List<Integer>> roomsBytes = new ArrayList<>();
        List<Integer> bankBytes = new ArrayList<>();
        game.roomBankSizes = new ArrayList<>();
        for(int i = 0;i<rooms.size();i++) {    
            if (rooms.get(i).width > platform.GAME_AREA_DIMENSIONS[2]) {
                throw new Exception("Room " + rooms.get(i).ID + " is wider ("+rooms.get(i).width+") than the game area width ("+platform.GAME_AREA_DIMENSIONS[2]+")!");
            }
            if (rooms.get(i).height > platform.GAME_AREA_DIMENSIONS[3]) {
                throw new Exception("Room " + rooms.get(i).ID + " is taller ("+rooms.get(i).height+") than the game area height ("+platform.GAME_AREA_DIMENSIONS[3]+")!");
            }
            
            List<Integer> roomBytes = rooms.get(i).toBytesForAssembler(objectTypes, screenWidth, game, dialogues, true, platform, config);
            roomsBytes.add(roomBytes);
            n_rooms_in_current_bank++;
            if (config.roomsPerBank > 1) {
                int size = roomBytes.size() + 2;
                bankBytes.add(size%256);
                bankBytes.add(size/256);
                bankBytes.addAll(roomBytes);
            } else {
                bankBytes.addAll(roomBytes);            
            }
            if (bankBytes.size() > largest_size) largest_size = bankBytes.size();
            if (n_rooms_in_current_bank >= config.roomsPerBank || i == rooms.size()-1) {
                game.generalBufferRequirement(bankBytes.size(), "room bank " + roomsBytes.size());

                PAKET.createFoldersIfNecessary(outputFolder + "data/rooms");
                FileWriter fw = new FileWriter(outputFolder + "data/rooms/roomBank" + n_banks + ".asm");
                Z80Assembler.dataBlockToAssembler(bankBytes, "roomBank", fw, 16);
                fw.close();
                PAKETCompiler.callMDL(new String[]{outputFolder + "data/rooms/roomBank" + n_banks + ".asm", "-bin", outputFolder + "data/rooms/roomBank" + n_banks + ".bin"}, config);
                int size = PAKET.compress(outputFolder + "data/rooms/roomBank" + n_banks + ".bin", outputFolder + "data/rooms/roomBank" + n_banks, config);
                game.roomBankSizes.add(size);
                bankBytes.clear();
                n_rooms_in_current_bank = 0;
                n_banks++;
            }
            int rules_size = rooms.get(i).sizeOfRulesData(rooms.get(i).rules, textIDHash, game, dialogues, platform);
            if (rules_size > largest_rules_size) largest_rules_size = rules_size;
        }
        for(int i = 0;i<rooms.size();i++) {      
            PAKRoom room = rooms.get(i);
            config.info("Room " + room.ID + " size: " + roomsBytes.get(i).size() + 
                        " (# different tiles: " + room.getUsedTiles().size() +
                        ", object buffer size: " + numBytesForRoomObjectTypes(room, game.rooms, platform, config).m_a + 
                        ", rules size: " + room.sizeOfRulesData(room.rules, textIDHash, game, dialogues, platform));
        }        

        String roomBankPointersCode = "roomBankPointers:\n";
        for(int i = 0;i<n_banks;i++) {
            roomBankPointersCode += "    dw roomBank" + i + "\n"; 
        }
        String roomBanksCode = "";
        for(int i = 0;i<n_banks;i++) {
            roomBanksCode += "roomBank" + i + ":\n";
            roomBanksCode += "    incbin \"data/rooms/roomBank" + i + "."+PAKETConfig.compressorExtension[config.compressor]+"\"\n";
        }
        
        variables.put("ROOM_BANK_PTRS_DATA", roomBankPointersCode);
        variables.put("ROOM_BANKS_DATA", roomBanksCode);
        variables.put("MAX_ROOM_RULES_SIZE", ""+largest_rules_size);
        
        return largest_size;
    }
        
    
    public Pair<Integer,Integer> numBytesForRoomObjectTypes(PAKRoom room, List<PAKRoom> rooms, Platform targetSystem, PAKETConfig config) throws Exception 
    {
        int n = 0;
        int n_opt = 0;
        int n_names_opt = 0;
        HashSet<String> usedTypes = new HashSet<>();
        for(PAKObject o:room.objects) {
            PAKObjectType ot = o.type;
            int len = ot.toBytesForAssembler(targetSystem, rooms, false, config).size();
            n += len;
            if (!usedTypes.contains(ot.ID)) {
                n_opt += len;
                n_names_opt += ot.getInGameName().length()+1;
                usedTypes.add(ot.ID);
            }
        }        
        // return n;
        return new Pair<>(n_opt, n_names_opt);
    }
    
    
    public int nObjectTypesInRoom(PAKRoom room)
    {
        HashSet<String> usedTypes = new HashSet<>();
        for(PAKObject o:room.objects) {
            PAKObjectType ot = o.type;
            if (!usedTypes.contains(ot.ID)) usedTypes.add(ot.ID);
        }
        
        return usedTypes.size();
    }    
    
    
    public void initializeAssemblerSourceCode(String folder, String fileName, boolean overwrite, HashMap<String, String> variables, Platform platform, PAKGame game, PAKETConfig config) throws Exception
    {
        boolean isPattern = false;
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream is = classLoader.getResourceAsStream("paket/templates/"+platform.targetSystemName+"/" + fileName);
        if (is == null) {
            isPattern = true;
            is = classLoader.getResourceAsStream("paket/templates/"+platform.targetSystemName+"/asmpatterns/" + fileName);
            if (is == null) {
                isPattern = false;
                is = classLoader.getResourceAsStream("paket/templates/common/" + fileName);
                if (is == null) {
                    throw new Exception("Cannot find file: " + "paket/templates/"+platform.targetSystemName+"/" + fileName);
                }
            }
        }
        
        String outputFilename = folder + "/" + fileName;
        if (isPattern && fileName.endsWith(".asm-pattern")) {
            outputFilename = outputFilename.replace(".asm-pattern", "-autogenerated.asm");
        }

        File f = new File(outputFilename);
        if (f.exists() && !overwrite) return;

        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        if (isPattern) {
            instantiateAsmPatternInternal(br, fileName, outputFilename, variables, platform);            
        } else {
            FileWriter fw = new FileWriter(f);
            String line = br.readLine();
            while(line != null) {
                fw.write(line + "\n");
                line = br.readLine();
            }
            fw.close();
        }
    }

    
    public void initializeAssemblerSourceCode(String folder, HashMap<String, String> variables, Platform platform, PAKGame game, PAKETConfig config) throws Exception
    {
        boolean overwrite = true;
        List<String> files = new ArrayList<>(Arrays.asList(new String[]{
            "dzx0_standard.asm",
            "auxiliar.asm",
            "dialogue.asm",
            "game.asm",
            "gui.asm",
            "input.asm",
            "interrupt.asm",
            "player.asm",
            "rooms.asm",
            "objects.asm",
            "text.asm",
        }));
        
        if (config.compressor == PAKETConfig.COMPRESSOR_PLETTER) {
            files.set(0, "pletter.asm");
        }
        if (game.pathfinding != PAKGame.PATHFINDING_OFF) {
            files.add("pathfinding.asm");
        }
        if (game.loadSaveGameScript != null) {
            for(String file:platform.saveGameCodeFileNames().m_a) {
                files.add(file);
            }
        }
        
        PAKET.createFoldersIfNecessary(folder);
        for(String fileName:files) {
            if (fileName == null) continue;
            initializeAssemblerSourceCode(folder, fileName, overwrite, variables, platform, game, config);
        }
    }      
    
    
    public void identifyingMinimalScriptLanguage(PAKGame game, PAKETConfig config)
    {
        List<Integer> usedScripts = new ArrayList<>();
        for(PAKRule r:game.itemRules) {
            identifyUsedScripts(r, usedScripts);
        }
        for(PAKRule r:game.onRoomLoadRules) {
            identifyUsedScripts(r, usedScripts);
        }
        for(PAKRule r:game.onRoomStartRules) {
            identifyUsedScripts(r, usedScripts);
        }
        for(Pair<String,PAKRule> r:game.scripts) {
            identifyUsedScripts(r.m_b, usedScripts);
        }
        for(PAKDialogue d:game.dialogues) {
            for(PAKDialogueState s:d.states) {
                for(PAKScript r:s.scripts) {
                    identifyUsedScripts(r, usedScripts);
                }
            }
        }
        for(PAKRoom room:game.rooms) {
            for(PAKRule r:room.rules) {
                identifyUsedScripts(r, usedScripts);
            }
            for(PAKRule r:room.onLoadRules) {
                identifyUsedScripts(r, usedScripts);
            }
            for(PAKRule r:room.onStartRules) {
                identifyUsedScripts(r, usedScripts);
            }
        }
        
        Collections.sort(usedScripts);
        config.info("Scripts used: " + usedScripts);
        int current_script_id = 0;
        String SCRIPT_DISPATCH_CODE = "    ; Script dispatching (autogenerated):\n";
        
        for(int i = 0;i<PAKRule.scriptUseVariableNames.length;i++) {
            if (usedScripts.contains(i)) {
                assemblerVariables.put(PAKRule.scriptUseVariableNames[i], "1");
                String functionName = "executeRuleScript_" + PAKRule.scriptUseVariableNames[i].substring(7, PAKRule.scriptUseVariableNames[i].length() - 5).toLowerCase();
                if (i == current_script_id + 1) {
                    SCRIPT_DISPATCH_CODE += "    dec a\n";
                } else {
                    SCRIPT_DISPATCH_CODE += "    sub " + (i - current_script_id) + "\n";
                }
                SCRIPT_DISPATCH_CODE += "    jp z, " + functionName + "\n";
                current_script_id = i;
            } else {
                assemblerVariables.put(PAKRule.scriptUseVariableNames[i], "0");
            }
        }
        assemblerVariables.put("SCRIPT_DISPATCH_CODE", SCRIPT_DISPATCH_CODE);
        
    }
    
    
    public void identifyUsedScripts(PAKRule r, List<Integer> usedScripts)
    {
        if (r.repeat && !usedScripts.contains(PAKRule.SCRIPT_DO_NOT_DELETE_RULE)) {
            usedScripts.add(PAKRule.SCRIPT_DO_NOT_DELETE_RULE);
        }
        for(PAKScript s:r.scripts) {
            identifyUsedScripts(s, usedScripts);
        }
    }


    public void identifyUsedScripts(PAKScript s, List<Integer> usedScripts)
    {
        if (!usedScripts.contains(s.type)) {
            usedScripts.add(s.type);
        }
        if (s.then_scripts != null) {
            for(PAKScript s2:s.then_scripts) {
                identifyUsedScripts(s2, usedScripts);
            }
        }
        if (s.else_scripts != null) {
            for(PAKScript s2:s.else_scripts) {
                identifyUsedScripts(s2, usedScripts);
            }
        }
    }


    public int generateCutsceneImageData(PAKGame game, String destinationFolder, Platform platform, PAKETConfig config) throws Exception 
    {
        String assembler1 = "cutscene_image_pointers:\n";
        String assembler2 = "";

        game.cutsceneSizes = new ArrayList<>();
        int max_size = 0;
        for(PAKCutsceneImage image:game.cutsceneImages) {
            Pair<Integer,Integer> sizes = platform.generateCutsceneImageData(image, destinationFolder);
            if (sizes.m_a > max_size) max_size = sizes.m_a;
            assembler1 += "    dw cutscene_" + image.assembler_name + "\n";
            assembler2 += "cutscene_" + image.assembler_name + ":\n";
            assembler2 += "    incbin \""+destinationFolder + "/src/data/cutscenes/cutscene-"+image.assembler_name+"."+PAKETConfig.compressorExtension[config.compressor]+"\"\n";
            config.info("Generated cutscene image " + image.name + " with uncompressed/compressed size: " + sizes.m_a + " / " + sizes.m_b);
            game.cutsceneSizes.add(sizes.m_b);
        }
        
        assemblerVariables.put("CUTSCENE_IMAGE_PTRS_DATA", assembler1);
        assemblerVariables.put("CUTSCENE_IMAGES_DATA", assembler2);
        return max_size;
    }
    
    
    public void generateSFXData(PAKGame game, String destinationFolder, List<String> dataFolders, Platform platform, PAKETConfig config) throws Exception            
    {
        if (assemblerVariables.get("MUSIC_TYPE_WYZ").equals("1")) {
            if (config.sfxPlayer != PAKETConfig.SFX_PLAYER_AYFX && config.sfxPlayerOverride) {
                throw new Exception("WYZ player only works with AYFX SFX player!");
            } else {
                config.sfxPlayer = PAKETConfig.SFX_PLAYER_AYFX;
            }
        }
        
        String assembler1 = "sfx_pointers:\n";
        String assembler2 = "";

        // Search all the SFX:
        game.sfx_data_size = 0;
        for(PAKSFX sfx:game.sfxs) {
            List<Integer> sfxData = sfx.toAssemblerBytes(dataFolders, platform, config);
            assembler1 += "    dw " + sfx.name + "\n";
            assembler2 += sfx.name + ":\n    db ";
            boolean first = true;
            for(int v:sfxData) {
                if (first) {
                    first = false;
                    assembler2 += "" + v;
                } else {
                    assembler2 += ", " + v;
                }
            }
            assembler2 += "\n";
            game.sfx_data_size += 2 + sfxData.size();
        }
        
        assemblerVariables.put("SFX_DATA", assembler1 + assembler2);
        assemblerVariables.put("SFX_PLAYER_TO_USE", "" + config.sfxPlayer);
        int reg7InitValue = platform.basePSGReg7Value;
        // We want tone and noise on in the sfx channel:
        reg7InitValue &= ((0x01 << (3+config.sfxChannel)) ^ 0xff);
        assemblerVariables.put("SFX_REG7_INIT_VALUE", "" + reg7InitValue);
        assemblerVariables.put("SFX_REG7_END_VALUE", "" + platform.basePSGReg7Value);
        assemblerVariables.put("SFX_CHANNEL", ""+config.sfxChannel);
    }
    

    public void generateSongData(PAKGame game, String destinationFolder, List<String> dataFolders, Platform platform, PAKETConfig config) throws Exception            
    {
        // Determine the music player we are using:
        int type = PAKSong.TYPE_NONE;
        for(PAKSong song:game.songs) {
            if (type == PAKSong.TYPE_NONE) {
                type = song.type;
            } else {
                if (type != song.type) {
                    throw new Exception("Songs of different type detected in the project! " + PAKSong.typeToString(type) + " vs " + PAKSong.typeToString(song.type));
                }
            }
        }

        // Clear the music type variables (they will be set to 1 by the 'generate???SongData functions)
        assemblerVariables.put("MUSIC_TYPE_TSV", "0");
        assemblerVariables.put("MUSIC_TYPE_WYZ", "0");

        switch(type) {
            case PAKSong.TYPE_NONE:
            case PAKSong.TYPE_TSV:
                generateTSVSongData(game, destinationFolder, dataFolders, platform, config);
                break;
            case PAKSong.TYPE_WYZ:
                generateWYZSongData(game, destinationFolder, dataFolders, platform, config);
                break;
            default:
                throw new Exception("Sunsupported song type: " + PAKSong.typeToString(type));
        }
    }

    
    public void InstantiateSongSourceFiles(PAKGame game, String destinationFolder, List<String> dataFolders, Platform platform, PAKETConfig config) throws Exception            
    {
        // Determine the music player we are using:
        int type = PAKSong.TYPE_NONE;
        for(PAKSong song:game.songs) {
            if (type == PAKSong.TYPE_NONE) {
                type = song.type;
            } else {
                if (type != song.type) {
                    throw new Exception("Songs of different type detected in the project! " + PAKSong.typeToString(type) + " vs " + PAKSong.typeToString(song.type));
                }
            }
        }

        switch(type) {
            case PAKSong.TYPE_NONE:
            case PAKSong.TYPE_TSV:
                instantiateTSVSongSourceFiles(game, destinationFolder, dataFolders, platform, config);
                break;
            case PAKSong.TYPE_WYZ:
                instantiateWYZSongSourceFiles(game, destinationFolder, dataFolders, platform, config);
                break;
            default:
                throw new Exception("Sunsupported song type: " + PAKSong.typeToString(type));
        }
    }    
    
    
    public void generateTSVSongData(PAKGame game, String destinationFolder, List<String> dataFolders, Platform platform, PAKETConfig config) throws Exception            
    {
        TSVMusicParser parser = new TSVMusicParser();
        String assembler1 = "song_pointers:\n";
        String assembler2 = "";
        
        int totalUncompressedSize = 0;
        int totalCompressedSize = 0;
        boolean usesSetVolume = false;

        PAKET.createFoldersIfNecessary(destinationFolder + "/src/data/songs");
        
        List<Integer> notesUsed = new ArrayList<>();
        List<TSVSong> tsvSongs = new ArrayList<>();
        
        // Search all the Songs, and get all the notes and instruments used:
        List<PAKSong> batch = new ArrayList<>();
        List<PAKSong> nextBatch = new ArrayList<>();
        batch.addAll(game.songs);
        while(!batch.isEmpty()) {
            for(PAKSong song:batch) {
                String filePath = PAKET.getFileName(song.fileName, dataFolders, config);
                TSVSong tsvSong = parser.loadTSVMusic(filePath, config);
                tsvSong.fileName = song.fileName;
                tsvSong.loopBackTime = 0;
                tsvSong.findNotesUsedBySong(0, notesUsed);
                if (tsvSong.usesSetVolume()) usesSetVolume = true;
                tsvSongs.add(tsvSong);
                for(String songName:tsvSong.subSongsPlayed) {
                    if (!game.songExists(songName)) {
                        nextBatch.add(game.getOrCreateSong(songName, PAKSong.TYPE_TSV));
                    }
                }
            }
            batch.clear();
            batch.addAll(nextBatch);
            nextBatch.clear();
        }
        
        Collections.sort(notesUsed);
        
        String music_instrument_constants = "";
        String music_instrument_profiles = "";
        int offset = 0;
        for(String instrumentName:parser.instruments.keySet()) {
            TSVInstrument instrument = parser.instruments.get(instrumentName);
            music_instrument_constants += "MUSIC_INSTRUMENT_" + instrumentName.toUpperCase() + ":  equ " + offset + "\n";
            offset += instrument.volume.size();
            music_instrument_profiles += instrumentName + "_instrument_profile:\n    db ";
            for(int i = 0;i<instrument.volume.size();i++) {
                if (i > 0) {
                    music_instrument_profiles += ",";
                }
                music_instrument_profiles += instrument.volume.get(i);
            }
            if (!instrumentName.equals(TSVMusicParser.SQUARE_WAVE)) {
                // Repeat: 
                // Here, we put a negative number:
                // -1: loops the last volume
                // -2: the last 2 volumes, etc.
                offset += 1;
                music_instrument_profiles += "," + instrument.repeat;
            }
            music_instrument_profiles += "\n";
        }
        String music_command_constants = "";
        String music_sfx_command_jumps = "";
        String music_sfx_commands = "";
        int nextCommandId = 11;
        boolean first = true;
        
        if (parser.useInstrumentSpeed) {
            music_command_constants += "MUSIC_CMD_INSTRUMENT_SPEED1: equ " + nextCommandId + "+MUSIC_CMD_FLAG\n";
            nextCommandId++;
            music_command_constants += "MUSIC_CMD_INSTRUMENT_SPEED2: equ " + nextCommandId + "+MUSIC_CMD_FLAG\n";
            nextCommandId++;
            music_command_constants += "MUSIC_CMD_INSTRUMENT_SPEED3: equ " + nextCommandId + "+MUSIC_CMD_FLAG\n";
            nextCommandId++;
            assemblerVariables.put("MUSIC_USE_INSTRUMENT_SPEED", "1");
        } else {
            assemblerVariables.put("MUSIC_USE_INSTRUMENT_SPEED", "0");
        }
        if (usesSetVolume) {
            music_command_constants += "MUSIC_CMD_SET_VOLUME1: equ " + nextCommandId + "+MUSIC_CMD_FLAG\n";
            nextCommandId++;
            music_command_constants += "MUSIC_CMD_SET_VOLUME2: equ " + nextCommandId + "+MUSIC_CMD_FLAG\n";
            nextCommandId++;
            music_command_constants += "MUSIC_CMD_SET_VOLUME3: equ " + nextCommandId + "+MUSIC_CMD_FLAG\n";
            nextCommandId++;
            assemblerVariables.put("MUSIC_USE_SET_VOLUME", "1");
        } else {
            assemblerVariables.put("MUSIC_USE_SET_VOLUME", "0");
        }
        
        for(String sfxName:parser.sfxs.keySet()) {
            PAKSFX sfx = parser.sfxs.get(sfxName);
            game.sfxs.add(parser.sfxs.get(sfxName));
            
            music_command_constants += "MUSIC_CMD_PLAY_SFX_"+sfxName.toUpperCase()+":  equ "+nextCommandId+"+MUSIC_CMD_FLAG\n";
            nextCommandId++;
            
            music_sfx_command_jumps += "    dec a  ; MUSIC_CMD_PLAY_SFX_"+sfxName.toUpperCase()+"\n";
            music_sfx_command_jumps += "    jp z,sound_update_PLAY_SFX_"+sfxName.toUpperCase()+"\n";
            
            if (first) {
                music_sfx_commands = "sound_update_PLAY_SFX_"+sfxName.toUpperCase()+":\n" +
                                     "    push hl\n" +
                                     "    ld hl,"+sfx.name+"\n" +
                                     "sound_update_PLAY_SFX_continue:\n" +
                                     "    ld a,SFX_PRIORITY_MUSIC\n" +
                                     "    call play_SFX_with_priority\n" +
                                     "    pop hl\n" +
                                     "    jp sound_update_internal_loop\n" +
                                     "\n";
                first = false;
            } else {
                music_sfx_commands += "sound_update_PLAY_SFX_"+sfxName.toUpperCase()+":\n" +
                                      "    push hl\n" +
                                      "        ld hl,"+sfx.name+"\n" +
                                      "        jp sound_update_PLAY_SFX_continue\n" +
                                      "\n" +
                                      "\n"; 
            }
        }

        assemblerVariables.put("MUSIC_INSTRUMENT_CONSTANTS", music_instrument_constants);        
        assemblerVariables.put("MUSIC_COMMAND_CONSTANTS", music_command_constants);        
        assemblerVariables.put("MUSIC_INSTRUMENT_PROFILES", music_instrument_profiles);
        assemblerVariables.put("MUSIC_SFX_COMMAND_JUMPS", music_sfx_command_jumps);
        assemblerVariables.put("MUSIC_SFX_COMMANDS", music_sfx_commands);
        assemblerVariables.put("MUSIC_TYPE_TSV", "1");        
        assemblerVariables.put("SOUND_CONSTANTS_INCLUDE", "include \"sound-constants-autogenerated.asm\"\n");
        assemblerVariables.put("SOUND_PLAYER_INCLUDE", "include \"sound-autogenerated.asm\"");
        assemblerVariables.put("INIT_SOUND_CODE",
                "    xor a\n" +
                "    ld (MUSIC_tempo), a\n" +
                "    ld (MUSIC_current_song), a\n" +
                "    ld (MUSIC_muted), a\n" +
                "    call StopPlayingMusic\n"
        );
        
        instantiateAsmPatternFromResources("sound-constants.asm-pattern", destinationFolder+"/src/sound-constants-autogenerated.asm", assemblerVariables, platform);
        
        if (config.attemptToSplitSongs) {
            TSVSongSplitOptimizer o = new TSVSongSplitOptimizer(notesUsed, destinationFolder, parser, game, config);
            tsvSongs = o.optimallySplitSongs(tsvSongs);
        }
        
        // Align the PAKSongs with the new optimally split songs:
        game.songs.clear();
        for(TSVSong s:tsvSongs) {
            game.getOrCreateSong(s.fileName, PAKSong.TYPE_TSV);
        }
        
        // Now that we have all the notes, convert to assembler:
        game.song_data_size = 0;
        int song_compressed_size = 0;
        int song_uncompressed_size = 0;
        for(int i = 0;i<tsvSongs.size();i++) {
            TSVSong tsvSong = tsvSongs.get(i);
            String song_name = "song_" + PAKETCompiler.nameToAssemblerLabel(tsvSong.fileName);
            assembler1 += "    dw " + song_name + "\n";
            
            String fileNameWithoutExtension = PAKETCompiler.getTSVSongfileNameWithoutExtension(tsvSong.fileName);
            fileNameWithoutExtension = "data/songs/" + fileNameWithoutExtension;
            Pair<Integer, Integer> sizes = parser.compileAssemblerSong(tsvSong, destinationFolder + "/src/" + fileNameWithoutExtension, notesUsed, game, config);
            if (game.music_buffer_size < sizes.m_a) {
                game.music_buffer_size = sizes.m_a;
            }
            totalUncompressedSize += sizes.m_a;
            totalCompressedSize += sizes.m_b;
            config.info("SONG: " + song_name + " size: " + sizes.m_a + " -> " + sizes.m_b);
            assembler2 += song_name + ":\n";
            assembler2 += "    incbin \""+fileNameWithoutExtension+"."+PAKETConfig.compressorExtension[config.compressor]+"\"\n";
            song_compressed_size += 2 + sizes.m_b;
            song_uncompressed_size += 2 + sizes.m_a;
        }
                
        if (totalUncompressedSize < totalCompressedSize + game.music_buffer_size) {
            // It is not worth compressing the songs!
            assemblerVariables.put("COMPRESS_SONGS", "0");
            assembler2 = assembler2.replace("."+PAKETConfig.compressorExtension[config.compressor]+"\"", ".bin\"");
            game.music_buffer_size = 0;
            game.song_data_size = song_uncompressed_size;
        } else {
             // It is worth compressing the songs!
            assemblerVariables.put("COMPRESS_SONGS", "1");
            game.song_data_size = song_compressed_size;
        }
        
        // Assembler variables:
        assemblerVariables.put("SONG_DATA", assembler1 + assembler2);        
        
        String note_period_table = "";
        for(int idx = 0;idx<notesUsed.size();idx++) {
            int note = notesUsed.get(idx);
            int period = TSVNote.PSGNotePeriod(note, platform.PSGMasterFrequency); 
            if (idx%8 == 0) note_period_table += "\n    db ";
            note_period_table += (period/256) + "," + (period%256);
            if (idx%8 != 7) note_period_table += ",  ";
        }
        if (note_period_table.endsWith(",  ")) {
            note_period_table = note_period_table.substring(0, note_period_table.length()-3);
        }
        note_period_table+="\n";
        assemblerVariables.put("NOTE_PERIOD_TABLE", note_period_table);
    }
        
        
    public void instantiateTSVSongSourceFiles(PAKGame game, String destinationFolder, List<String> dataFolders, Platform platform, PAKETConfig config) throws Exception            
    {
        instantiateAsmPatternFromResources("sound.asm-pattern", destinationFolder+"/src/sound-autogenerated.asm", assemblerVariables, platform);        
    }
    
    
    public static String getTSVSongfileNameWithoutExtension(String fileName) throws Exception {
        String fileNameWithoutExtension = fileName;
        if (fileNameWithoutExtension.endsWith(".tsv")) {
            fileNameWithoutExtension = fileNameWithoutExtension.substring(0, fileNameWithoutExtension.length()-4);
        } else {
            throw new Exception("TSV song extension is not tsv! " + fileName);
        }
        fileNameWithoutExtension = fileNameWithoutExtension.replace("/", "_");
        fileNameWithoutExtension = fileNameWithoutExtension.replace("\\", "_");            
        return fileNameWithoutExtension;
    }
    
    
    public void generateWYZSongData(PAKGame game, String destinationFolder, List<String> dataFolders, Platform platform, PAKETConfig config) throws Exception            
    {        
        String SONG_DATA_INSTRUMENTS = "";   
        String SONG_DATA_TABLE = "";   
        SONG_DATA_INSTRUMENTS += "; ------------------------------------------------\n" +
                     "; WYZ Music instrument data\n";
        SONG_DATA_TABLE += "; WYZ Music data\n";

        PAKET.createFoldersIfNecessary(destinationFolder + "/src/data/songs");
        for(int i = 0;i<game.songs.size();i++) {
            PAKSong song = game.songs.get(i);
            // Copy over files:
            String fileName = song.fileName;
            fileName = fileName.replace("\\", "/");  // make sure it's unx style
            if (fileName.contains("/")) {
                fileName = fileName.substring(fileName.indexOf("/") + 1);
            }
            instantiateAsmPatternFromPath(song.fileName + ".asm", destinationFolder+"/src/data/songs/" + fileName + ".asm", dataFolders, assemblerVariables, platform, config);
            Files.copy(new File(PAKET.getFileName(song.fileName, dataFolders, config)).toPath(),
                       new File(destinationFolder+"/src/data/songs/" + fileName).toPath(), StandardCopyOption.REPLACE_EXISTING);
            
            SONG_DATA_INSTRUMENTS += "    include \"data/songs/" + fileName + ".asm\"\n";
            SONG_DATA_TABLE += "WYZ_SONG_"+i+":\n";
            SONG_DATA_TABLE += "    incbin \"data/songs/" + fileName + "\"\n";
        }
        String SONG_DATA = "song_pointers:\n\n" + SONG_DATA_INSTRUMENTS + SONG_DATA_TABLE + "\nWYZ_SONG_TABLE:\n";
        for(int i = 0;i<game.songs.size();i++) {
            SONG_DATA += "    dw WYZ_SONG_"+i+"\n";
        }

        assemblerVariables.put("MUSIC_TYPE_WYZ", "1");
        assemblerVariables.put("SOUND_CONSTANTS_INCLUDE", "");
        assemblerVariables.put("SONG_DATA", SONG_DATA);
        assemblerVariables.put("SOUND_PLAYER_INCLUDE", "include \"wyzproplay46c.asm\"");
        assemblerVariables.put("INIT_SOUND_CODE",
                "    xor a\n" +
                "    ld (MUSIC_muted), a\n" +
                "    ld (SFX_player_active), a\n" +
                "    call PLAYER_INIT\n"
                
        );
        
    }    
    
    
    public void instantiateWYZSongSourceFiles(PAKGame game, String destinationFolder, List<String> dataFolders, Platform platform, PAKETConfig config) throws Exception            
    {
        initializeAssemblerSourceCode(destinationFolder+"/src", "wyzproplay46c.asm", true, assemblerVariables, platform, game, config);
        initializeAssemblerSourceCode(destinationFolder+"/src", "wyzproplay46c-ram.asm", true, assemblerVariables, platform, game, config);
    }
    
    
    public void generatePlayerScaling(PAKGame game) {
        if (game.playerScalingThresholds == null) {
            assemblerVariables.put("PLAYER_SCALING","0");
            assemblerVariables.put("PLAYER_SCALING_THRESHOLD_75_0","0");
            assemblerVariables.put("PLAYER_SCALING_THRESHOLD_87_5","0");
            assemblerVariables.put("PLAYER_SCALING_THRESHOLD_112_5","0");
        } else {
            assemblerVariables.put("PLAYER_SCALING","1");
            assemblerVariables.put("PLAYER_SCALING_THRESHOLD_75_0",""+game.playerScalingThresholds[0]);
            assemblerVariables.put("PLAYER_SCALING_THRESHOLD_87_5",""+game.playerScalingThresholds[1]);
            assemblerVariables.put("PLAYER_SCALING_THRESHOLD_112_5",""+game.playerScalingThresholds[2]);
        }
    }


    public static void instantiateAsmPatternFromPath(String patternFilename, String outputFilename, List<String> dataFolders, HashMap<String, String> variables, Platform platform, PAKETConfig config) throws Exception
    {
        String inputFilePath = PAKET.getFileName(patternFilename, dataFolders, config);
        BufferedReader br = new BufferedReader(new FileReader(inputFilePath));
        instantiateAsmPatternInternal(br, patternFilename, outputFilename, variables, platform);        
    }
    
    
    public static void instantiateAsmPatternFromResources(String patternFilename, String outputFilename, HashMap<String, String> variables, Platform platform) throws Exception
    {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream is = classLoader.getResourceAsStream("paket/templates/"+platform.targetSystemName+"/asmpatterns/" + patternFilename);
        if (is == null) {
            is = classLoader.getResourceAsStream("paket/templates/common/asmpatterns/" + patternFilename);
            if (is == null) {
                throw new Exception("Cannot find: " + "paket/templates/"+platform.targetSystemName+"/asmpatterns/" + patternFilename);
            }
        }
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);
        instantiateAsmPatternInternal(br, patternFilename, outputFilename, variables, platform);
    }

    
    public static String instantiateAsmLine(String line, String patternFilename, HashMap<String, String> variables, Platform platform) throws Exception
    {
        String newLine = "";
        // there is a variable!
        StringTokenizer st = new StringTokenizer(line, "{}", true);
        while(st.hasMoreTokens()) {
            String token = st.nextToken();
            if (token.equals("{")) {
                String variable = st.nextToken();
                String value = variables.get(variable);
                if (value == null) {
                    throw new Exception("No value for variable " + variable + " in "+patternFilename+"!");
                }
                if (value.contains("\n")) {
                    List<String> lines = new ArrayList<>();
                    for(String l:value.split("\n")) {
                        lines.add(l);
                    }
                    List<String> newLines = instantiateAsmPatternInternalLines(lines, patternFilename, variables, platform);
                    for(String l:newLines) {
                        newLine += l;
                        newLine += "\n";
                    }
                } else {
                    newLine += value;
                }
                st.nextToken(); // skip the  "{"
            } else {
                newLine += token;
            }
        }
        return newLine;
    }

    public static List<String> instantiateAsmPatternInternalLines(List<String> lines, String patternFilename, HashMap<String, String> variables, Platform platform) throws Exception
    {        
        List<String> outputLines = new ArrayList<>();
        for(String line:lines) {
            if (line.contains("{")) {
                String newLine = instantiateAsmLine(line, patternFilename, variables, platform);
                outputLines.add(newLine);
            } else {
                outputLines.add(line);
            }
        }
        return outputLines;
    }
    
    
    public static void instantiateAsmPatternInternal(BufferedReader br, String patternFilename, String outputFilename, HashMap<String, String> variables, Platform platform) throws Exception
    {        
        List<String> lines = new ArrayList<>();
        while(true) {
            String line = br.readLine();
            if (line == null) break;
            lines.add(line);
        }
        FileWriter fw = new FileWriter(outputFilename);
        for(String line:instantiateAsmPatternInternalLines(lines, patternFilename, variables, platform)) {
            fw.write(line);
            fw.write("\n");
        }
        fw.flush();
    }    
    
    
    public static void callMDL(String []args, PAKETConfig config) throws Exception
    {   
        String args2[] = null;
        if (config.mdlLoggerFlag == null) {
            args2 = args;
        } else {
            args2 = new String[args.length+1];
            for(int i = 0;i<args.length;i++) {
                args2[i] = args[i];
                args2[args.length] = config.mdlLoggerFlag;
            }
        }
        cl.Main.main(args2);
    }
    
    
    public static String nameToAssemblerLabel(String name) {
        name =name.replace(" ", "_");
        name = name.replace(".", "_");
        name = name.replace("-", "_");
        name = name.replace("/", "_");
        name = name.replace("\\", "_"); 
        return name;
    }
}
