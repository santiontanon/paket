/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.compiler;

import cl.MDLLogger;
import java.awt.image.BufferedImage;
import java.io.File;
import paket.platforms.CPC;
import paket.platforms.Platform;
import paket.platforms.MSX;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import javax.imageio.ImageIO;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import paket.pak.PAKCutsceneImage;
import paket.pak.PAKDialogue;
import paket.pak.PAKDialogue.PAKDialogueState;
import paket.pak.PAKRoom;
import paket.pak.PAKGame;
import paket.pak.PAKObject;
import paket.pak.PAKObjectType;
import paket.pak.PAKObjectType.PAKObjectState;
import paket.pak.PAKRule;
import paket.pak.PAKRule.PAKScript;
import paket.pak.PAKRule.PAKTrigger;
import paket.pak.PAKSFX;
import paket.pak.PAKSong;
import paket.platforms.CPC6128;
import paket.platforms.CPCColors;
import paket.platforms.MSXColors;
import paket.platforms.MSXMegaROM;
import paket.text.PAKFont;
import paket.util.Pair;
import paket.util.Pletter;
import paket.util.zx0.ZX0;

/**
 *
 * @author santi
 */
public class PAKET {
    int nextPaletteID = 0;
    
    
    public static void main(String args[]) throws Exception {
        PAKETConfig config = new PAKETConfig();

        if (args.length < 4) {
            config.info("PAKET (Point And Klick Engine Tool) Compiler v1.0 beta");
            config.info("Santiago (Popolon) Ontañón (2019)\n");
            config.info("Usage:");
            config.info("  java -cp:PAKET.jar compiler.PAKET [game definition file] [platform] [language] [destination folder] [options]\n");
            config.info("Example:");
            config.info("  java -cp:PAKET.jar compiler.PAKET data/game.pak cpc es output/cpc\n");
            config.info("Supported platforms: cpc|cpc6128|msx|msxmegarom|msx-wt|msxmwgarom-wt (see the documentation for more info on each platform)\n");
            config.info("Options:");
            config.info("    -fb [folder]: fallback data folder. If a file cannot be found on the same folder as the game definition file, fallback folders are searched. "
                    + "This is useful for having a game defined for a platform in one folder, and defining it for another platform on a separate folder, but leaving the "
                    + "files that are the same only in the original folder, to avoid replication.");
            config.info("    -compressor [zx0/pletter]: choose which compressor to use (default zx0).");
            config.info("    -quiet: turns off all console output during compilation of the game.");
            config.info("    -diggest: only prints summary messages during compilation (default).");
            config.info("    -info: prints all messages during compilation.");
            config.info("    -text-opt [number]: sets the number of optimization passes over text banks (default 0). The larger, the better optimization achieved (diminishing returns with more iterations).");
            config.info("    -object-opt [number]: sets the number of optimization passes over object banks (default 0). The larger, the better optimization achieved (diminishing returns with more iterations).");
            config.info("    -room-opt [number]: sets the number of optimization passes over room banks (default 0). If rooms-per-bank == 1, this parameter has no effect. The larger, the better optimization achieved (diminishing returns with more iterations).");
            config.info("    -tile-opt [number]: sets the number of optimization passes over tile banks (default 0). The larger, the better optimization achieved (diminishing returns with more iterations).");
            config.info("    -no-song-opt: deactivates song splitting optimization (see documentation).");
            config.info("    -text-bank-size [number]: size of each text bank (default 512). Larger means better compression, but slower to get text during gameplay. Set the right balance.");
            config.info("    -objects-per-bank [number]: sets the number of object to compress per bank (default 4). Find a tradeoff that minimizes space in your game (4 tends to work well).");
            config.info("    -rooms-per-bank [number]: sets the number of rooms to compress per bank (default 1). Only 1 and 2 are supported values for now (find the best for your game).");
            config.info("    -sfx-player [pak/ayfx]: selects which sfx player to use in the generated binary (default 'pak'). See documentation for differences.");
            config.info("    -partial-localization: use this flag if the localization file for the language you are compiling for only specifies changes for a subset of text strings, instead of all of them. By default, if a text string is missing in the localization file, compilation will stop with an error.");
            config.info("    -page0-data [amount]: [cpc6128/msxmegarom platforms only] In some platforms, by default, the compiler uses RAM/MegaROM page 0 for code only, but there might be space left (it is hard to determine how much before compiling the game, hence no data is allocated there by default). If you set this to > 0, the compiler will try to use that space if there are any blocks that fit.");
            
            System.exit(1);
        }
        
        config.setMinLevelToLog(MDLLogger.DIGGEST);        

        HashMap<String, String> variables = new HashMap<>();
        List<String> dataFolders = new ArrayList<>();
        dataFolders.add(getFilePath(args[0]));
        
        for(int i = 4;i<args.length;i++) {
            if (args[i].equals("-fb") && i+1<args.length) {
                dataFolders.add(args[i+1]);
                i++;
            } else if (args[i].equals("-compressor") && i+1<args.length) {
                switch(args[i+1]) {
                    case "zx0": 
                        config.compressor = PAKETConfig.COMPRESSOR_ZX0;
                        break;
                    case "pletter":
                        config.compressor = PAKETConfig.COMPRESSOR_PLETTER;
                        break;
                    default:
                        config.error("Unsupported compressor " + args[i+1]);
                        System.exit(1);
                }
                i++;
            } else if (args[i].equals("-quiet")) {
                config.logger.setMinLevelToLog(MDLLogger.SILENT);
                config.mdlLoggerFlag = "-quiet";
            } else if (args[i].equals("-diggest")) {
                config.logger.setMinLevelToLog(MDLLogger.DIGGEST);
            } else if (args[i].equals("-info")) {
                config.logger.setMinLevelToLog(MDLLogger.INFO);
                config.mdlLoggerFlag = null;
            } else if (args[i].equals("-text-opt") && i+1<args.length) {
                config.maxTextOptimizationIterations = Integer.parseInt(args[i+1]);
                i++;
            } else if (args[i].equals("-object-opt") && i+1<args.length) {
                config.maxObjectOptimizationIterations = Integer.parseInt(args[i+1]);
                i++;
            } else if (args[i].equals("-room-opt") && i+1<args.length) {
                config.maxRoomOptimizationIterations = Integer.parseInt(args[i+1]);
                i++;
            } else if (args[i].equals("-tile-opt") && i+1<args.length) {
                config.maxTileOptimizationIterations = Integer.parseInt(args[i+1]);
                i++;
            } else if (args[i].equals("-no-song-opt")) {
                config.attemptToSplitSongs = false;
            } else if (args[i].equals("-text-bank-size") && i+1<args.length) {
                config.maxTextBankSize = Integer.parseInt(args[i+1]);
                i++;
            } else if (args[i].equals("-objects-per-bank") && i+1<args.length) {
                config.objectTypesPerBank = Integer.parseInt(args[i+1]);
                i++;
            } else if (args[i].equals("-rooms-per-bank") && i+1<args.length) {
                config.roomsPerBank = Integer.parseInt(args[i+1]);
                if (config.roomsPerBank != 1 && config.roomsPerBank != 2) {
                    throw new Exception("Only 1 and 2 are currently supported values for -rooms-per-bank.");
                }
                i++;
            } else if (args[i].equals("-sfx-player") && i+1<args.length) {
                switch (args[i+1]) {
                    case "pak":
                        config.sfxPlayer = PAKETConfig.SFX_PLAYER_PAK;
                        config.sfxPlayerOverride = true;
                        break;
                    case "ayfx":
                        config.sfxPlayer = PAKETConfig.SFX_PLAYER_AYFX;
                        config.sfxPlayerOverride = true;
                        break;
                    default:
                        throw new Exception("Only pak/ayfx are supported values for -sfx-player.");
                }
                i++;
            } else if (args[i].equals("-partial-localization")) {
                config.pedanticTranslation = false;
            } else if (args[i].equals("-page0-data") && i+1<args.length) {
                config.spaceForDataInPage0 = Integer.parseInt(args[i+1]);
                i++;
            } else {
                throw new Exception("Unrecognized flag " + args[i]);
            }
        }

        Platform platform = getPlatform(args[1], variables, config);        
        if (platform == null) {
            config.info("Unknown platform " + args[1]);
            System.exit(1);
        }

        PAKET paket = new PAKET();
        paket.compileGame(args[0], platform, args[2], args[3], dataFolders, config);
    }
    
    
    public PAKET()
    {
        
    }

    
    public static Platform getPlatform(String platform, HashMap<String, String> variables, PAKETConfig config) {
        if (platform.equals("cpc")) {
            return new CPC(variables, config);
        }
        if (platform.equals("cpc6128")) {
            return new CPC6128(variables, config);
        }
        if (platform.equals("msx")) {
            return new MSX(8, variables, config);
        }
        if (platform.equals("msxmegarom")) {
            return new MSXMegaROM(8, variables, config);
        }
        if (platform.equals("msx-wt")) {
            return new MSX(16, variables, config);
        }
        if (platform.equals("msxmegarom-wt")) {
            return new MSXMegaROM(16, variables, config);
        }
        return null;
    }

    
    public static String getFilePath(String file) {
        if (file.lastIndexOf(File.separator) == -1) return null;
        return file.substring(0, file.lastIndexOf(File.separator));
    }
    

    public static String getFileName(String file, List<String> folders, PAKETConfig config) throws Exception {

        for(String folder:folders) {
            if (file.startsWith(folder + File.separator)) {
                file = file.substring(folder.length()+1);
                break;
            }
        }

        if (new File(file).exists()) {
            return file;
        }
                
        for (String folder : folders) {
            if (new File(folder + File.separator + file).exists()) {
                return folder + File.separator + file;
            }
        }
        config.error("folders: " + folders);
        throw new Exception("Cannot find file '" + file + "'!");
    }

    
    public static BufferedImage getSubImage(BufferedImage img, int x0, int y0, int x1, int y1) throws Exception
    {
        if (x0 == -1) return img;
        BufferedImage img2 = new BufferedImage(x1-x0, y1-y0, BufferedImage.TYPE_INT_ARGB);        
        img2.getGraphics().drawImage(img, 0, 0, x1-x0, y1-y0, x0, y0, x1, y1, null);
        return img2;
    }
    
    
    public void compileGame(String inputFilePath, Platform platform, String language,
            String destinationFolder, List<String> dataFolders, PAKETConfig config) throws Exception {

        config.diggest("PAKETCompiler: inputFilePath: " + inputFilePath);
        config.diggest("PAKETCompiler: platform: " + platform.targetSystemName);
        config.diggest("PAKETCompiler: language: " + language);
        config.diggest("PAKETCompiler: destinationFolder: " + destinationFolder);
        config.diggest("PAKETCompiler: dataFolders: " + dataFolders);
        config.diggest("PAKETCompiler: compressor: " + PAKETConfig.compressorExtension[config.compressor]);

        PAKGame game = new PAKGame();
        game.inputFileLanguage = null;
        game.language = language;
        game.saveGameMode = platform.defaultSaveGameMode;
        HashMap<String, String> assemblerVariables = platform.variables;
        
        createFoldersIfNecessary(destinationFolder);
        createFoldersIfNecessary(destinationFolder + "/src");
        createFoldersIfNecessary(destinationFolder + "/src/data");
        
        processPAKFile(inputFilePath, platform, language, destinationFolder, dataFolders,
                       game, assemblerVariables, config);

        PAKETCompiler compiler = new PAKETCompiler(assemblerVariables);
        compiler.compileGame(game, platform, destinationFolder, dataFolders, config);
        
        // reportUnusedTranslations(game, config);
    }

    
    public void processPAKFile(String inputFilePath, Platform platform, String language,
            String destinationFolder, List<String> dataFolders, PAKGame game, 
            HashMap<String, String> assemblerVariables, PAKETConfig config) throws Exception {
        PAKETTokenizer tokenizer = new PAKETTokenizer(inputFilePath);

        while (tokenizer.hasMoreTokens()) {
            Token t = tokenizer.nextToken();
            if (t.type == Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL) {
                switch (t.value) {
                    case "include": {
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        Token t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

                        String inputFilePath2 = getFileName(t2.value, dataFolders, config);
                        List<String> dataFolders2 = new ArrayList<>();
                        String path = getFilePath(t2.value);
                        config.diggest("processing include " + t2.value);
                        if (path != null) {
                            for(String folder:dataFolders) {
                                dataFolders2.add(folder + File.separator + path);
                            }
                        } else {
                            dataFolders2.addAll(dataFolders);
                        }
                        
                        processPAKFile(inputFilePath2, platform, language,
                                destinationFolder, dataFolders2, game, 
                                assemblerVariables, config);                                
                    }
                    break;
                    case "language":
                        game.inputFileLanguage = parseLanguage(tokenizer, config);                            
                        // Check if we need to load a localization file:
                        if (!game.language.equals(game.inputFileLanguage)) game.translation = loadLocalizationFile(game.language, dataFolders, config);

                        break;
                    case "built-in-texts":
                        parseBuiltInTexts(tokenizer, game, config);
                        break;
                    case "action-names":
                        parseActionNames(tokenizer, game, assemblerVariables, config);
                        break;
                    case "font":
                        game.font = parseFont(tokenizer, dataFolders, config);
                        break;
                    case "loading-screen":
                        addLoadingScreen(tokenizer, dataFolders, destinationFolder, platform, game, config);
                        break;
                    case "pointers":
                        parsePointers(tokenizer, dataFolders, destinationFolder, platform, game, config);
                        break;
                    case "frame-around-subrooms":
                        praseFrameAroundSubrooms(tokenizer, game);
                        break;
                    case "text-area":
                        parseTextArea(tokenizer, dataFolders, destinationFolder, platform, game, assemblerVariables, config);
                        break;
                    case "game-area":
                        parseGameArea(tokenizer, dataFolders, destinationFolder, platform, game, assemblerVariables, config);
                        break;
                    case "gui":
                        parseGui(tokenizer, dataFolders, destinationFolder, platform, game, assemblerVariables, config);
                        break;
                    case "inventory":
                        parseInventory(tokenizer, dataFolders, destinationFolder, platform, game, assemblerVariables, config);
                        break;
                    case "pathfinding":
                        parsePathfinding(tokenizer, game);
                        break;
                    case "double-click-on-exit":
                        parseDoubleClickOnExit(tokenizer, game);
                        break;
                    case "load-save-game-mode":
                        parseSaveGameMode(tokenizer, game, platform);
                        break;
                    case "load-save-game-script":
                        parseLoadSaveGameScript(tokenizer, game);
                        break;
                    case "use-item-with-item-symmetric":
                        parseUseItemWithItemSymmetric(tokenizer, game);
                        break;
                    case "player-scaling-thresholds":
                        parsePlayerScalingThresholds(tokenizer, game);
                        break;
                    case "palette-rgb-tolerance":
                        parsePaletteTolerance(tokenizer);
                        break;
                    case "cpc-rgb-palette":
                        parseCPCRGBPalette(tokenizer, dataFolders, platform);
                        break;
                    case "msx-screen2-rgb-palette":
                        parseMSXScreen2RGBPalette(tokenizer, dataFolders, platform);
                        break;

                    case "item":
                        parseItem(tokenizer, dataFolders, destinationFolder, platform, game, config);
                        break;
                    case "object":
                        parseObject(tokenizer, dataFolders, destinationFolder, platform, game, config);
                        break;
                    case "room":
                        parseRoom(tokenizer, dataFolders, destinationFolder, platform, game, config);
                        break;
                    case "itemRules":
                        {
                            expectSpecificToken(tokenizer, ":", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                            List<PAKRule> rules = parseRules(tokenizer, platform, game, config);
                            game.itemRules = rules;
                        }
                        break;
                    case "onRoomLoadRules":
                        {
                            expectSpecificToken(tokenizer, ":", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                            List<PAKRule> rules = parseRules(tokenizer, platform, game, config);
                            game.onRoomLoadRules = rules;
                        }
                        break;
                    case "onRoomStartRules":
                        {
                            expectSpecificToken(tokenizer, ":", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                            List<PAKRule> rules = parseRules(tokenizer, platform, game, config);
                            game.onRoomStartRules = rules;
                        }
                        break;
                    case "dialogue":
                        parseDialogue(tokenizer, platform, game, config);
                        break;

                    case "init-variable":
                        parseInitVariable(tokenizer, game);
                        break;
                        
                    case "cutscene-image":
                        parseCutsceneImage(tokenizer, dataFolders, platform, game, config);
                        break;
                        
                    case "script":
                        parseScript(tokenizer, platform, game, config);
                        break;
                        
                    case "define-palette":
                        parseDefinePalette(tokenizer, platform, game, config);
                        break;
                        
                    case "base-palette":
                        parseBasePalette(tokenizer, platform, game, config);
                        break;
                        
                    case "additional-assembler-file":
                        parseAdditionalAssemblerFile(tokenizer, game, config);
                        break;

                    case "additional-assembler-incbin":
                        parseAdditionalAssemblerIncbin(tokenizer, game, config);
                        break;
                        
                    case "player-stop-state":
                        parsePlayerStopState(tokenizer, game);
                        break;

                    default:
                        throw new Exception("Unexpected token " + t.value + " in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
                }
            } else {
                throw new Exception("Unrecognized token '" + t.value + "' in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
            }
        }
    }

    
    public Token expectSpecificToken(PAKETTokenizer tokenizer, String value, int type) throws Exception {
        Token t = tokenizer.nextToken();
        if (t == null) {
            throw new Exception("Expected " + Token.typeNames[type] + " but file ended in " + tokenizer.currentFile + " " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        if (t.type != type) {
            throw new Exception("Expected " + Token.typeNames[type] + " but found "+t.value+" in " + tokenizer.currentFile + " " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        if (value != null) {
            if (!t.value.equals(value)) {
                throw new Exception("Expected '" + value + "' in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
            }
        }
        return t;
    }

    
    public Pair<Integer, Boolean> expectNumberOrThis(PAKETTokenizer tokenizer) throws Exception {
        Token t = tokenizer.nextToken();
        if (t.type == Token.TOKEN_TYPE_NUMBER) {
            return new Pair<>(Integer.parseInt(t.value), false);
        } else if (t.value.equals("$this")) {
            return new Pair<>(0, true);
        }
        throw new Exception("Expected number or $this, but found " + t.value + " in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
    }

    
    public String parseLanguage(PAKETTokenizer tokenizer, PAKETConfig config) throws Exception {
        String language;

        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

        Token t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_STRING) {
            throw new Exception("Expected string after ( in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        language = t.value;
        config.info("PAKET Compiler:\n    Input file default language: " + t.value);

        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

        return language;
    }
    
    
    public HashMap<String, String> loadLocalizationFile(String language, List<String> dataFolders, PAKETConfig config) throws Exception
    {
        HashMap<String, String> translation = new HashMap<>();
        // We need to load a localization file!
        String localizationFileName = PAKET.getFileName("localization."+language, dataFolders, config);
        PAKETTokenizer tokenizer = new PAKETTokenizer(localizationFileName);
        while(tokenizer.hasMoreTokens()) {
            Token t = tokenizer.nextToken();
            if (!t.value.equals("line")) throw new Exception("Expected line in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
            t = tokenizer.nextToken();
            if (!t.value.equals(":")) throw new Exception("Expected line in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
            Token source = tokenizer.nextToken();
            if (source.type != Token.TOKEN_TYPE_STRING) throw new Exception("Expected string in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
            Token target = tokenizer.nextToken();
            if (target.type != Token.TOKEN_TYPE_STRING) throw new Exception("Expected string in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
            translation.put(source.value, target.value);
        }            
        return translation;
    }

    
    public void parseBuiltInTexts(PAKETTokenizer tokenizer, PAKGame game, PAKETConfig config) throws Exception {
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

        Token t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_STRING) {
            throw new Exception("Expected string after ( in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        game.takeUntakeableErrorMessage = game.translateText(t.value, config);
        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

        t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_STRING) {
            throw new Exception("Expected string after ( in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        game.cannotUseErrorMessage = game.translateText(t.value, config);
        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

        t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_STRING) {
            throw new Exception("Expected string after ( in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        game.cannotTalkErrorMessage = game.translateText(t.value, config);
        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

        t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_STRING) {
            throw new Exception("Expected string after ( in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        game.takeFromInventoryErrorMessage = game.translateText(t.value, config);
        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

        t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_STRING) {
            throw new Exception("Expected string after ( in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        game.cannotReachErrorMessage = game.translateText(t.value, config);

        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
    }
    
    
    public void parseActionNames(PAKETTokenizer tokenizer, PAKGame game, HashMap<String, String> assemblerVariables, PAKETConfig config) throws Exception
    {
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
        game.examine_action = game.translateText(t.value, config);
        assemblerVariables.put("ACTION_NAME_LOOK", game.font.convertStringToAssemblerString(game.examine_action));

        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
        game.pickup_action = game.translateText(t.value, config);
        assemblerVariables.put("ACTION_NAME_PICKUP", game.font.convertStringToAssemblerString(game.pickup_action));

        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
        game.use_action = game.translateText(t.value, config);
        assemblerVariables.put("ACTION_NAME_USE", game.font.convertStringToAssemblerString(game.use_action));
        
        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
        game.talk_action = game.translateText(t.value, config);
        assemblerVariables.put("ACTION_NAME_TALK", game.font.convertStringToAssemblerString(game.talk_action));
        
        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
        game.exit_action = game.translateText(t.value, config);
        assemblerVariables.put("ACTION_NAME_EXIT", game.font.convertStringToAssemblerString(game.exit_action));
        
        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
        game.with_action = game.translateText(t.value, config);
        assemblerVariables.put("ACTION_WITH", game.font.convertStringToAssemblerString(game.with_action));
        
        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

        assemblerVariables.put("ACTION_DOTDOTDOT", game.font.convertStringToAssemblerString("..."));
        assemblerVariables.put("DIALOGUE_PAUSE_TEXT", game.font.convertStringToAssemblerString("^"));

//        String demo_over = game.translateText("Demo Over");
//        assemblerVariables.put("DEMO_OVER_TEXT", game.font.convertStringToAssemblerString(demo_over));
//        String time_played = game.translateText("Tiempo de partida: ");
//        assemblerVariables.put("TIME_PLAYED_TEXT", game.font.convertStringToAssemblerString(time_played));

        assemblerVariables.put("NUMBERS_AND_COLON", game.font.convertStringToAssemblerString("0123456789:"));
    }

    
    public PAKFont parseFont(PAKETTokenizer tokenizer, List<String> dataFolders, PAKETConfig config) throws Exception 
    {
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

        Token t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_STRING) {
            throw new Exception("Expected string after ( in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        String fontFileName = t.value;
        List<String> characterLines = new ArrayList<>();
        t = tokenizer.nextToken();
        while (!t.value.equals(")")) {
            if (!t.value.equals(",")) {
                throw new Exception("Expected , or ) in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
            }
            t = tokenizer.nextToken();
            if (t.type != Token.TOKEN_TYPE_STRING) {
                throw new Exception("Expected string after , in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
            }
            characterLines.add(t.value);
            t = tokenizer.nextToken();
        }
        if (characterLines.isEmpty()) {
            throw new Exception("Expected at least one character line in the font definition in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        return new PAKFont(getFileName(fontFileName, dataFolders, config), characterLines, config);
    }
    
    
    public void addLoadingScreen(PAKETTokenizer tokenizer, List<String> dataFolders, String destinationFolder, Platform platform, PAKGame game, PAKETConfig config) throws Exception {
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_STRING) {
            throw new Exception("Expected string after ( in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        String loadingScreenFile = t.value;
        int mode_or_screen = -1;  // -1 means "default"
        t = tokenizer.nextToken();
        if (t.value.equals(",")) {
            t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL);
            if (t.value.equalsIgnoreCase("cpc-mode0")) {
                mode_or_screen = 0;
            } else if (t.value.equalsIgnoreCase("cpc-mode1")) {
                mode_or_screen = 1;
            } else {
                throw new Exception("Unrecognized option " + t.value + " in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
            }
            expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);            
        } else if (!t.value.equals(")")) {
            throw new Exception("Expected ',' or ')' after the loading screen file name in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }

        config.info("PAKET Compiler: generating loading screen...");
        
        platform.addLoadingScreen(getFileName(loadingScreenFile, dataFolders, config), mode_or_screen, destinationFolder);
    }
    

    public void parsePointers(PAKETTokenizer tokenizer, List<String> dataFolders, String destinationFolder, Platform platform, PAKGame game, PAKETConfig config) throws Exception {        
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_STRING) {
            throw new Exception("Expected string after ( in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        String pointersFileRaw = t.value;
        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

        String pointersFile = getFileName(pointersFileRaw, dataFolders, config);
        config.info("PAKET Compiler: generating pointers from "+pointersFile+"");
        
        platform.addToBasePalette(pointersFile);        
        int buffer_requirement = platform.extractPointers(pointersFile, destinationFolder + "/src/data/");
        game.generalBufferRequirement(buffer_requirement, "pointers data");
    }
    
    
    public void praseFrameAroundSubrooms(PAKETTokenizer tokenizer, PAKGame game) throws Exception
    {
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL);
        if (t.value.equals("true")) {
            game.frame_around_subrooms = true;
        } else if (t.value.equals("false")) {
            game.frame_around_subrooms = false;
        } else {
            throw new Exception("Expected true or false in frame-around-subrooms!");
        }
        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
    }

    
    public void parseGui(PAKETTokenizer tokenizer, List<String> dataFolders,
                         String destinationFolder, Platform platform, PAKGame game,
                         HashMap<String, String> assemblerVariables, PAKETConfig config) throws Exception {
        
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

        Token t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_NUMBER) {
            throw new Exception("Expected a number after ( in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        int x = Integer.parseInt(t.value);
        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

        t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_NUMBER) {
            throw new Exception("Expected a number after , in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        int y = Integer.parseInt(t.value);
        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        
        t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_STRING) {
            throw new Exception("Expected string after ( in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        String guiFileRaw = t.value;
        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

        String guiFile = getFileName(guiFileRaw, dataFolders, config);
        config.info("PAKET Compiler: generating GUI data from " + guiFile);
        config.guiFileName = guiFileRaw;

        platform.addToBasePalette(guiFile);
        BufferedImage gui_image = ImageIO.read(new File(guiFile));
        int gui_data_size = platform.generateGUIData(gui_image, "tiles-gui.png", destinationFolder + "/src/", guiFile, game);
        game.generalBufferRequirement(gui_data_size, "gui data");

        int gui_width = gui_image.getWidth() / platform.TILE_WIDTH;
        if (gui_width > platform.SCREEN_WIDTH_IN_TILES) {
            throw new Exception("GUI is wider than the screen! " + gui_width + " > " + platform.SCREEN_WIDTH_IN_TILES);
        }
        int gui_height = gui_image.getHeight() / PAKRoom.TILE_HEIGHT;
        assemblerVariables.put("GUI_WIDTH", "" + gui_width);
        assemblerVariables.put("GUI_HEIGHT", "" + gui_height);
        platform.setGuiDimensions(x, y, gui_width, gui_height);
    }
    
    
    public void parseTextArea(PAKETTokenizer tokenizer, List<String> dataFolders,
                              String destinationFolder, Platform platform, PAKGame game,
                              HashMap<String, String> assemblerVariables, PAKETConfig config) throws Exception {
        
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_NUMBER) {
            throw new Exception("Expected a number after ( in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        int textX = Integer.parseInt(t.value);
        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        
        t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_NUMBER) {
            throw new Exception("Expected a number after , in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        int textY = Integer.parseInt(t.value);
        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

        t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_NUMBER) {
            throw new Exception("Expected a number after , in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        int textWidth = Integer.parseInt(t.value);
        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

        t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_NUMBER) {
            throw new Exception("Expected a number after , in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        int textHeight = Integer.parseInt(t.value);
        platform.setTextAreaDimensions(textX, textY, textWidth, textHeight);
        
        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
    }    

    
    public void parseGameArea(PAKETTokenizer tokenizer, List<String> dataFolders,
                              String destinationFolder, Platform platform, PAKGame game,
                              HashMap<String, String> assemblerVariables, PAKETConfig config) throws Exception {
        
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_NUMBER) {
            throw new Exception("Expected a number after ( in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        int x = Integer.parseInt(t.value);
        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        
        t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_NUMBER) {
            throw new Exception("Expected a number after , in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        int y = Integer.parseInt(t.value);
        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

        t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_NUMBER) {
            throw new Exception("Expected a number after , in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        int width = Integer.parseInt(t.value);
        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

        t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_NUMBER) {
            throw new Exception("Expected a number after , in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        int height = Integer.parseInt(t.value);
        platform.setGameAreaDimensions(x, y, width, height);
        
        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
    }    
      
    
    public void parseInventory(PAKETTokenizer tokenizer, List<String> dataFolders,
                               String destinationFolder, Platform platform, PAKGame game,
                               HashMap<String, String> assemblerVariables, PAKETConfig config) throws Exception {
        
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_NUMBER) {
            throw new Exception("Expected a number after ( in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        platform.INVENTORY_ITEMS_PER_LINE = Integer.parseInt(t.value);
        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_NUMBER) {
            throw new Exception("Expected a number after , in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        platform.INVENTORY_ROWS = Integer.parseInt(t.value);
        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
    }    
    
    
    public void parsePathfinding(PAKETTokenizer tokenizer, PAKGame game) throws Exception {
        
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        while(true) {
            Token t = tokenizer.nextToken();
            if (t.type != Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL) {
                throw new Exception("Expected alphanumeric symbol after ( in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
            }
            switch(t.value) {
                case "off":
                    game.pathfinding = PAKGame.PATHFINDING_OFF;
                    break;
                case "on-click":
                    game.pathfinding = PAKGame.PATHFINDING_ON_CLICK;
                    break;
                case "on-collision":
                    game.pathfinding = PAKGame.PATHFINDING_ON_COLLISION;
                    break;
                case "stop-early":
                    game.stopEarlyWhenWalking = true;
                    break;
                case "do-not-stop-early":
                    game.stopEarlyWhenWalking = false;
                    break;
                case "max-path-length":
                {
                    expectSpecificToken(tokenizer, ":", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    t = tokenizer.nextToken();
                    if (t.type == Token.TOKEN_TYPE_NUMBER) {
                        game.pathfinding_max_length = Integer.parseInt(t.value);
                    } else {
                        throw new Exception("Expected number after 'max-path-length:' ( in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine() + "), but found: " + t.value);
                    }
                    break;
                }
                default:
                    throw new Exception("Expected off/on-click/on-collision/stop-early/do-not-stop-early ( in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine() + "), but found: " + t.value);
            }
            t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
            
            if (t.value.equals(")")) {
                break;
            } else if (!t.value.equals(",")) {
                throw new Exception("Expected , or ) after option in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
            }
        }
    }    
    
    
    public void parseDoubleClickOnExit(PAKETTokenizer tokenizer, PAKGame game) throws Exception {
        
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL) {
            throw new Exception("Expected true/false after ( in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        switch(t.value) {
            case "true":
                game.doubleClickOnExit = 1;
                break;
            case "false":
                game.doubleClickOnExit = 0;
                break;
            default:
                throw new Exception("Expected true/false after ( in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
    }        
    
    
    public void parseSaveGameMode(PAKETTokenizer tokenizer, PAKGame game, Platform platform) throws Exception {
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL) {
            throw new Exception("Expected symbol after ( in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        game.saveGameMode = t.value;
        if (!platform.supportedSaveGameModes.contains(game.saveGameMode)) {
            throw new Exception("Savegame mode '"+game.saveGameMode+"' not supported by platform, supported modes are: " + platform.supportedSaveGameModes);
        }
        Token t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        while(t2.value.equals(",")) {
            Token t3 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
            game.saveGameModeParams.add(t3.value);
            t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        }
        if (!t2.value.equals(")")) {
            throw new Exception("Expected ',' or ')' after save game mode name in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
    }
    
    
    public void parseLoadSaveGameScript(PAKETTokenizer tokenizer, PAKGame game) throws Exception {
        
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_STRING) {
            throw new Exception("Expected string after ( in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        game.loadSaveGameScript = t.value;
        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
    }  
    
    
    public void parseUseItemWithItemSymmetric(PAKETTokenizer tokenizer, PAKGame game) throws Exception {
        
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        while(true) {
            Token t = tokenizer.nextToken();
            if (t.type != Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL) {
                throw new Exception("Expected alphanumeric symbol after ( in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
            }
            switch(t.value) {
                case "true":
                    game.useItemWithItemSymmetric = true;
                    break;
                case "false":
                    game.useItemWithItemSymmetric = false;
                    break;
                default:
                    throw new Exception("Expected true/false after ( in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
            }
            t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
            
            if (t.value.equals(")")) {
                break;
            } else if (!t.value.equals(",")) {
                throw new Exception("Expected , or ) after option in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
            }
        }
    }    
    
    
    public void parsePlayerScalingThresholds(PAKETTokenizer tokenizer, PAKGame game) throws Exception {
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t1 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t3 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        int v1 = Integer.parseInt(t1.value);
        int v2 = Integer.parseInt(t2.value);
        int v3 = Integer.parseInt(t3.value);
        game.playerScalingThresholds = new int[]{v1, v2, v3};
    }
    
    public void parsePaletteTolerance(PAKETTokenizer tokenizer) throws Exception {
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_NUMBER) {
            throw new Exception("Expected number after ( in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        CPCColors.tolerance = Integer.parseInt(t.value);
        MSXColors.tolerance = Integer.parseInt(t.value);
    }
    
    
    public void parseCPCRGBPalette(PAKETTokenizer tokenizer, List<String> dataFolders, Platform platform) throws Exception {
        expectSpecificToken(tokenizer, ":", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        for(int i = 0;i<27;i++) {
            Token rtoken = tokenizer.nextToken();
            if (rtoken.type != Token.TOKEN_TYPE_NUMBER) {
                throw new Exception("Expected number in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
            }
            expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
            Token gtoken = tokenizer.nextToken();
            if (gtoken.type != Token.TOKEN_TYPE_NUMBER) {
                throw new Exception("Expected number in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
            }
            expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
            Token btoken = tokenizer.nextToken();
            if (btoken.type != Token.TOKEN_TYPE_NUMBER) {
                throw new Exception("Expected number in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
            }
            CPCColors.CPCMode0Palette[i][0] = Integer.parseInt(rtoken.value);
            CPCColors.CPCMode0Palette[i][1] = Integer.parseInt(gtoken.value);
            CPCColors.CPCMode0Palette[i][2] = Integer.parseInt(btoken.value);
        }
        
//        if (!dataFolders.isEmpty()) {
//            config.info("PAKET Compiler: generating color palette...");
//            platform.generateColorPalette(dataFolders.get(0));        
//        }
    }

    
    public void parseMSXScreen2RGBPalette(PAKETTokenizer tokenizer, List<String> dataFolders, Platform platform) throws Exception {
        expectSpecificToken(tokenizer, ":", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        for(int i = 0;i<16;i++) {
            Token rtoken = tokenizer.nextToken();
            if (rtoken.type != Token.TOKEN_TYPE_NUMBER) {
                throw new Exception("Expected number in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
            }
            expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
            Token gtoken = tokenizer.nextToken();
            if (gtoken.type != Token.TOKEN_TYPE_NUMBER) {
                throw new Exception("Expected number in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
            }
            expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
            Token btoken = tokenizer.nextToken();
            if (btoken.type != Token.TOKEN_TYPE_NUMBER) {
                throw new Exception("Expected number in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
            }
            MSXColors.MSX1Palette[i][0] = Integer.parseInt(rtoken.value);
            MSXColors.MSX1Palette[i][1] = Integer.parseInt(gtoken.value);
            MSXColors.MSX1Palette[i][2] = Integer.parseInt(btoken.value);
        }
        
//        if (!dataFolders.isEmpty()) {
//            config.info("PAKET Compiler: generating color palette...");
//            platform.generateColorPalette(dataFolders.get(0));        
//        }
    }
    
    
    public void parseItem(PAKETTokenizer tokenizer, List<String> dataFolders, String destinationFolder, Platform platform, PAKGame game, PAKETConfig config) throws Exception {
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

        Token t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_NUMBER && t.type != Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL) {
            throw new Exception("Token ID should be either a number of a name, found: " + t.value);
        }
        String itemID = t.value;

        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
        String itemName = game.translateText(t.value, config);

        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
        String itemDescription = game.translateText(t.value, config);

        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        expectSpecificToken(tokenizer, ":", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

        expectSpecificToken(tokenizer, "image", Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL);
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
        String itemImage = t.value;
        int x0 = -1;
        int x1 = -1;
        int y0 = -1;
        int y1 = -1;

        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        if (t.value.equals(",")) {
            t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
            x0 = Integer.parseInt(t.value);
            expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

            t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
            y0 = Integer.parseInt(t.value);
            expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

            t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
            x1 = Integer.parseInt(t.value);
            expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

            t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
            y1 = Integer.parseInt(t.value);
            expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        } else if (!t.value.equals(")")) {
            throw new Exception("Expected , or ) after string in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        
        String defaultUseMessage = null;
        t = tokenizer.nextToken();
        if (t != null && t.value.equals("default-use-message")) {
            expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
            t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
            defaultUseMessage = game.translateText(t.value, config);
            expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        } else {
            if (t != null) tokenizer.unread(t);        
        }

        // process item:
        String fileName = getFileName(itemImage, dataFolders, config);
        platform.addToBasePalette(fileName);
        BufferedImage img = ImageIO.read(new File(fileName));
        platform.addItemData(img, x0, y0, x1, y1, itemID, itemName, 
                itemDescription, defaultUseMessage, game, itemImage);
    }

    
    public void parseRoom(PAKETTokenizer tokenizer, List<String> dataFolders, String destinationFolder, Platform platform, PAKGame game, PAKETConfig config) throws Exception {
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
        String roomID = t.value;

        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
        String roomFile = t.value;

        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
        int start_x = Integer.parseInt(t.value);

        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
        int start_y = Integer.parseInt(t.value);

        boolean isSubRoom = false;
        int playerZoom = 0;  // auto
        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        while(t.value.equals(",")) {
            t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL);
            switch (t.value) {
                case "subroom":
                    isSubRoom = true;
                    break;
                case "lock-player-zoom-75":
                    playerZoom = 1;
                    break;
                case "lock-player-zoom-87":
                    playerZoom = 2;
                    break;
                case "lock-player-zoom-100":
                    playerZoom = 3;
                    break;
                case "lock-player-zoom-112":
                    playerZoom = 4;
                    break;
                default:
                    throw new Exception("Unrecognized flag "+t.value+" in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
            }
            t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        }
        tokenizer.unread(t);

        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        expectSpecificToken(tokenizer, ":", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

        if (game.getRoom(roomID) != null) {
            throw new Exception("Room with ID '" + roomID + "' is defined twice!");
        }
        
        roomFile = getFileName(roomFile, dataFolders, config);
        PAKRoom room = PAKRoom.fromFile(new File(roomFile), getFilePath(roomFile), dataFolders, game.objectTypesHash, game.language, platform, config);
        game.rooms.add(room);
        room.ID = roomID;
        room.screen_position_x = start_x;
        room.screen_position_y = start_y;
        room.isSubRoom = isSubRoom;
        room.playerZoom = playerZoom;
        
        // rules/palette:
        List<Integer> roomPalette = null;
        if (tokenizer.hasMoreTokens()) {
            boolean unreadToken = true;
            t = tokenizer.nextToken();
            while(t.type == Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL) {
                if (t.value.equals("rule")) {
                    PAKRule rule = parseRule(tokenizer, platform, game, config);
                    room.rules.add(rule);

                } else if (t.value.equals("persistent-rule")) {
                    PAKRule rule = parseRule(tokenizer, platform, game, config);
                    if (rule.triggerConnective == PAKRule.CONNECTIVE_OR) {
                        throw new Exception("persistent-rule cannot have an || connective in its trigger in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
                    }
                    
                    List<PAKScript> persistentScripts = new ArrayList<>();
                    t = tokenizer.nextToken();
                    if (t.type == Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL && t.value.equals("persistent-rule-effect")) {
                        expectSpecificToken(tokenizer, ":", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

                        // Parse the persistent part:
                        persistentScripts = parseRuleScripts(tokenizer, platform, game, config);
                    } else {
                        tokenizer.unread(t);
                    }
                                        
                    // Create new variable & modify rule:
                    int variableIdx = game.getOrCreateGameStateVariableIdx("room_"+room.ID+"_persistent_rule_"+room.rules.size()+"_fired");
                    rule.triggers.add(new PAKTrigger(PAKRule.TRIGGER_VARIABLE_EQ, variableIdx, 0));
                    PAKScript s = new PAKScript(PAKRule.SCRIPT_SET_VARIABLE);
                    s.ID = variableIdx;
                    s.value = 1;
                    rule.scripts.add(s);
                    rule.scripts.addAll(persistentScripts);
                    room.rules.add(rule);

                    // Create on-room-load rule:
                    if (!persistentScripts.isEmpty()) {
                        PAKRule rule2 = new PAKRule();
                        rule2.triggers.add(new PAKTrigger(PAKRule.TRIGGER_VARIABLE_EQ, variableIdx, 1));
                        rule2.triggerConnective = PAKRule.CONNECTIVE_AND;
                        rule2.scripts.addAll(persistentScripts);
                        room.onLoadRules.add(rule2);
                    }
                } else if (t.value.equals("repeating-rule")) {
                    PAKRule rule = parseRule(tokenizer, platform, game, config);
                    rule.repeat = true;
                    room.rules.add(rule);
                } else if (t.value.equals("on-room-load-rule")) {
                    PAKRule rule = parseRule(tokenizer, platform, game, config);
                    room.onLoadRules.add(rule);

                } else if (t.value.equals("on-room-start-rule")) {
                    PAKRule rule = parseRule(tokenizer, platform, game, config);
                    room.onStartRules.add(rule);

                } else if (t.value.equals("default-verb")) {
                    expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                    expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    if (t.value.equals("examine")) {
                        room.defaultVerbIsUse = false;
                    } else if (t.value.equals("use")) {
                        room.defaultVerbIsUse = true;
                    } else {
                        throw new Exception("Default verb can only be examine or use in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
                    }
                } else if (t.value.equals("palette")) {
                    // hardcoding the room palette:
                    roomPalette = new ArrayList<>();
                    expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    do{
                        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        roomPalette.add(Integer.parseInt(t.value));
                        t = tokenizer.nextToken();
                    }while(t.value.equals(","));
                    if (!t.value.equals(")")) {
                        throw new Exception("Expected ')' in line " + tokenizer.getCurrentLine());
                    }
                } else {
                    break;
                }
                if (!tokenizer.hasMoreTokens()) {
                    unreadToken = false;
                    break;
                }
                t = tokenizer.nextToken();
            }
            if (unreadToken) tokenizer.unread(t);
        }
        
        // Add rules defined in objects directly:
        for(PAKObject o:room.objects) {
            if (o.type.rules != null) {
                for(PAKRule r:o.type.rules) {
                    PAKRule r2 = r.instantiateWithThis(o.ID, game);
                    // See if there is already a rule with the same triggers:
                    boolean found = false;
                    for(PAKRule r3:room.rules) {
                        if (r2.repeat == r3.repeat &&
                            r2.triggerConnective == r3.triggerConnective &&
                            r2.sameTriggersAs(r3, game)) {
                            // The user defined a rule with the same triggers in the
                            // room, so that one should override the one in this object type.
                            found = true;
                        }
                    }
                    if (!found) {
                        room.rules.add(r2);
                    }
                }
            }
            if (o.type.onRoomLoadRules != null) {
                for(PAKRule r:o.type.onRoomLoadRules) {
                    PAKRule r2 = r.instantiateWithThis(o.ID, game);
                    game.onRoomLoadRules.add(r2);
                }
            }
            if (o.type.onRoomStartRules != null) {
                for(PAKRule r:o.type.onRoomStartRules) {
                    PAKRule r2 = r.instantiateWithThis(o.ID, game);
                    game.onRoomStartRules.add(r2);
                }
            }
        }
        
        // Create palette for this room:
        String roomPaletteName = "room_" + room.ID;
        
        if (roomPalette != null) {
            platform.setPalette(roomPaletteName, roomPalette);
        } else {
            // Find all the objects that appear:
            for(PAKObject o:room.objects) {
                for(PAKObjectState s:o.type.states) {
                    for(BufferedImage img:s.animationFrames) {
                        platform.addToPalette(img, roomPaletteName, o.type.ID+"-"+s.state+"-"+s.direction);
                    }
                }
            }

            // Get all the tiles that are used (not the raw tile set, but just the
            // tiles used by this room):
            BufferedImage background = room.render(platform, true, false, config);
            platform.addToPalette(background, roomPaletteName, "room-"+room.ID+"-background");
        }

        // Display the room palette:
        config.info("Room " + roomPaletteName + " palette: " + platform.getPalette(roomPaletteName));
    }

    
    public List<PAKRule> parseRules(PAKETTokenizer tokenizer, Platform platform, PAKGame game, PAKETConfig config) throws Exception
    {
        List<PAKRule> rules = new ArrayList<>();
        Token t = tokenizer.nextToken();
        while(t.type == Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL) {
            if (t.value.equals("rule")) {
                PAKRule rule = parseRule(tokenizer, platform, game, config);
                rules.add(rule);
                
            } else if (t.value.equals("repeating-rule")) {
                PAKRule rule = parseRule(tokenizer, platform, game, config);
                rule.repeat = true;
                rules.add(rule);
                
            } else {
                break;
            }
            if (!tokenizer.hasMoreTokens()) return rules;
            t = tokenizer.nextToken();
        }
        tokenizer.unread(t); 
        return rules;
    }
    
    
    public PAKRule parseRule(PAKETTokenizer tokenizer, Platform platform, PAKGame game, PAKETConfig config) throws Exception
    {
        PAKRule r = new PAKRule();

        Pair<List<PAKTrigger>,Integer> triggersAndConnective = parseRuleTriggers(tokenizer, game);
        List<PAKScript> scripts = parseRuleScripts(tokenizer, platform, game, config);
        r.triggers.addAll(triggersAndConnective.m_a);
        r.triggerConnective = triggersAndConnective.m_b;
        r.scripts.addAll(scripts);
        return r;
    }


    public Pair<List<PAKTrigger>,Integer> parseRuleTriggers(PAKETTokenizer tokenizer, PAKGame game) throws Exception
    {
        boolean orFound = false;
        boolean andFound = false;
        List<PAKTrigger> triggers = new ArrayList<>();
        
        while(true) {
            Token t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL);        
            switch(t.value) {
                case "true":
                    {
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        triggers.add(new PAKTrigger(PAKRule.TRIGGER_TRUE, 0, 0));
                    }                
                    break;
                
                case "examine-object":
                    {
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        Pair<Integer,Boolean> tmp = expectNumberOrThis(tokenizer);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        triggers.add(new PAKTrigger(PAKRule.TRIGGER_EXAMINE_OBJECT, tmp.m_a, 0, tmp.m_b, false));
                    }                
                    break;

                case "pickup-object":
                    {
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        Pair<Integer,Boolean> tmp = expectNumberOrThis(tokenizer);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        triggers.add(new PAKTrigger(PAKRule.TRIGGER_PICK_UP_OBJECT, tmp.m_a, 0, tmp.m_b, false));
                    }                
                    break;

                case "use-object":
                    {
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        Pair<Integer,Boolean> tmp = expectNumberOrThis(tokenizer);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        triggers.add(new PAKTrigger(PAKRule.TRIGGER_USE_OBJECT, tmp.m_a, 0, tmp.m_b, false));
                        
                    }                
                    break;

                case "talk-to-object":
                    {
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        Pair<Integer,Boolean> tmp = expectNumberOrThis(tokenizer);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        triggers.add(new PAKTrigger(PAKRule.TRIGGER_TALK_TO_OBJECT, tmp.m_a, 0, tmp.m_b, false));
                    }                
                    break;
                    
                case "examine-item":
                    {
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = tokenizer.nextToken();
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        if (!game.itemIDs.contains(t.value)) {
                            throw new Exception("Undefined item with ID " + t.value + " in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine() + "\n" +
                                                "Currently defined items: " + game.itemIDs);
                        }
                        int item_id = game.itemIDs.indexOf(t.value) + 1;
                        triggers.add(new PAKTrigger(PAKRule.TRIGGER_EXAMINE_ITEM, item_id, 0));
                    }                
                    break;
                    
                case "use-item-with-object":
                    {
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = tokenizer.nextToken();
                        if (!game.itemIDs.contains(t.value)) {
                            throw new Exception("Undefined item with ID " + t.value + " in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine() + "\n" +
                                                "Currently defined items: " + game.itemIDs);
                        }
                        int item_id = game.itemIDs.indexOf(t.value) + 1;
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        Pair<Integer,Boolean> tmp = expectNumberOrThis(tokenizer);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        triggers.add(new PAKTrigger(PAKRule.TRIGGER_USE_ITEM_WITH_OBJECT, tmp.m_a, item_id, tmp.m_b, false));
                    }                
                    break;
                    
                case "talk-to-item":
                    {
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = tokenizer.nextToken();
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        if (!game.itemIDs.contains(t.value)) {
                            throw new Exception("Undefined item with ID " + t.value + " in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine() + "\n" +
                                                "Currently defined items: " + game.itemIDs);
                        }
                        int item_id = game.itemIDs.indexOf(t.value) + 1;
                        triggers.add(new PAKTrigger(PAKRule.TRIGGER_TALK_TO_ITEM, item_id, 0));
                    }                
                    break;
                    
                case "click-outside-room-area":
                    {
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        triggers.add(new PAKTrigger(PAKRule.TRIGGER_CLICK_OUTSIDE_ROOM_AREA, 0, 0));
                    }                
                    break;
                    
                case "variable-eq":
                    {
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL);
                        String variable_name = t.value;
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int value = Integer.parseInt(t.value);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        
                        int variableIdx = game.getOrCreateGameStateVariableIdx(variable_name);
                        if (variableIdx >= 0) {
                            triggers.add(new PAKTrigger(PAKRule.TRIGGER_VARIABLE_EQ, variableIdx, value));                            
                        } else {
                            // This means, we couldn't do it (variable might contain a {$this}):
                            // TODO: what to do in this case? is this ok?
                            triggers.add(PAKTrigger.fromVariableName(PAKRule.TRIGGER_VARIABLE_EQ, variable_name, value));                            
                        }                        
                    }                
                    break;
                    
                case "use-item-with-item":
                    {
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = tokenizer.nextToken();
                        if (!game.itemIDs.contains(t.value)) {
                            throw new Exception("Undefined item with ID " + t.value + " in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine() + "\n" +
                                                "Currently defined items: " + game.itemIDs);
                        }
                        int item1_id = game.itemIDs.indexOf(t.value) + 1;
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = tokenizer.nextToken();
                        if (!game.itemIDs.contains(t.value)) {
                            throw new Exception("Undefined item with ID " + t.value + " in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine() + "\n" +
                                                "Currently defined items: " + game.itemIDs);
                        }
                        int item2_id = game.itemIDs.indexOf(t.value) + 1;
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        triggers.add(new PAKTrigger(PAKRule.TRIGGER_USE_ITEM_WITH_ITEM, item2_id, item1_id));
                    }                
                    break;
                    
                case "exit-through-object":
                    {
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        Pair<Integer,Boolean> tmp = expectNumberOrThis(tokenizer);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        triggers.add(new PAKTrigger(PAKRule.TRIGGER_EXIT_THROUGH_OBJECT, tmp.m_a, 0, tmp.m_b, false));
                    }                
                    break;
                    
                case "have-item":
                    {
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = tokenizer.nextToken();
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        if (!game.itemIDs.contains(t.value)) {
                            throw new Exception("Undefined item with ID " + t.value + " in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine() + "\n" +
                                                "Currently defined items: " + game.itemIDs);
                        }
                        int item_id = game.itemIDs.indexOf(t.value) + 1;
                        triggers.add(new PAKTrigger(PAKRule.TRIGGER_HAVE_ITEM, item_id, 0));
                    }                
                    break;
                    
                case "current-room-is":
                    {
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        triggers.add(PAKTrigger.fromRoomName(PAKRule.TRIGGER_CURRENT_ROOM_IS, t.value, 0));
                        
                    }
                    break;
                    
                case "number-object-equals":
                    {
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        Pair<Integer,Boolean> tmp = expectNumberOrThis(tokenizer);
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int value = Integer.parseInt(t.value);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        triggers.add(new PAKTrigger(PAKRule.TRIGGER_NUMBER_OBJECT_EQUALS, tmp.m_a, value, tmp.m_b, false));
                    }                
                    break;

                default:
                    throw new Exception("Unknown rule trigger "+t.value+" in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
            }
            t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);        
            if (t.value.equals(":")) {
                break;
            } else if (t.value.equals("&&")) {
                andFound = true;
            } else if (t.value.equals("||")) {
                orFound = true;
            } else {
                throw new Exception("Expected : or && in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
            }
        }
        if (andFound && orFound) {
            throw new Exception("Mixed && and || expressions are not yet supporrted in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        } else {
            if (orFound) {
                return new Pair<>(triggers, PAKRule.CONNECTIVE_OR);
            } else {
                return new Pair<>(triggers, PAKRule.CONNECTIVE_AND);
            }
        }
    }
    
    
    public List<PAKScript> parseRuleScripts(PAKETTokenizer tokenizer, Platform platform, PAKGame game, PAKETConfig config) throws Exception
    {
        List<PAKScript> scripts = new ArrayList<>();
        
        while(true) {
            if (!tokenizer.hasMoreTokens()) return scripts;
            Token t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL);        
            switch(t.value) {
                case "message":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_MESSAGE);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                        s.text = game.translateText(t.value, config);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        scripts.add(s);
                    }
                    break;

                case "remove-object":
                    {
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_REMOVE_OBJECT);
                        Pair<Integer,Boolean> tmp = expectNumberOrThis(tokenizer);
                        s.ID = tmp.m_a;
                        s.ID_this = tmp.m_b;
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        scripts.add(s);
                    }
                    break;

                case "gain-item":
                    {
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = tokenizer.nextToken();
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_GAIN_ITEM);
                        if (!game.itemIDs.contains(t.value)) {
                            throw new Exception("Undefined item with ID " + t.value + " in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine() + "\n" +
                                                "Currently defined items: " + game.itemIDs);
                        }
                        s.ID = game.itemIDs.indexOf(t.value) + 1;
                        scripts.add(s);
                    }
                    break;
                    
//                case "controlled-gain-item":
//                    {
//                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
//                        t = tokenizer.nextToken();
//                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
//                        PAKScript s = new PAKScript(PAKRule.SCRIPT_GAIN_ITEM);
//                        if (!game.itemIDs.contains(t.value)) {
//                            throw new Exception("Undefined item with ID " + t.value + " in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine() + "\n" +
//                                                "Currently defined items: " + game.itemIDs);
//                        }
//                        s.ID = game.itemIDs.indexOf(t.value) + 1;
//                        s.override_gainitem_check = true;
//                        scripts.add(s);
//                    }
//                    break;                    
                    
                case "lose-item":
                    {
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = tokenizer.nextToken();
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_LOSE_ITEM);
                        if (!game.itemIDs.contains(t.value)) {
                            throw new Exception("Undefined item with ID " + t.value + " in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine() + "\n" +
                                                "Currently defined items: " + game.itemIDs);
                        }
                        s.ID = game.itemIDs.indexOf(t.value) + 1;
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        scripts.add(s);
                    }
                    break;
                    
                case "change-object-description":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_CHANGE_OBJECT_DESCRIPTION);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        Pair<Integer,Boolean> tmp = expectNumberOrThis(tokenizer);
                        s.ID = tmp.m_a;
                        s.ID_this = tmp.m_b;
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                        s.text = game.translateText(t.value, config);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        scripts.add(s);
                    }
                    break;
                    
                case "change-object-name":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_CHANGE_OBJECT_NAME);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        Pair<Integer,Boolean> tmp = expectNumberOrThis(tokenizer);
                        s.ID = tmp.m_a;
                        s.ID_this = tmp.m_b;
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                        s.text = game.translateText(t.value, config);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        scripts.add(s);
                    }
                    break;                    
                    
                case "walk-to":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_WALK_TO);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        s.x = Integer.parseInt(t.value);
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        s.y = Integer.parseInt(t.value);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        scripts.add(s);
                    }
                    break;
                    
                case "go-to-room":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_GO_TO_ROOM);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                        s.room = t.value;
                        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        if (t.value.equals(",")) {
                            t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                            s.x = Integer.parseInt(t.value);
                            expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                            t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                            s.y = Integer.parseInt(t.value);
                            expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        } else if (t.value.equals(")")) {
                            // ok
                        } else {
                            throw new Exception("Expected , or ) in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
                        }
                        scripts.add(s);
                    }
                    break;
                    
                case "change-object-state":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_CHANGE_OBJECT_STATE);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        Pair<Integer,Boolean> tmp = expectNumberOrThis(tokenizer);
                        s.ID = tmp.m_a;
                        s.ID_this = tmp.m_b;
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);

                        boolean found = false;
                        for(int i = 0;i<PAKObjectType.stateNames.length;i++) {
                            if (PAKObjectType.stateNames[i].equals(t.value)) {
                                s.value = i;
                                found = true;
                            }
                        }
                        if (!found) throw new Exception("Unknown state "+t.value+" in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
                        
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        scripts.add(s);
                    }
                    break;

                case "set-variable":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_SET_VARIABLE);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL);
                        String variable_name = t.value;
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        s.value = Integer.parseInt(t.value);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        
                        s.ID = game.getOrCreateGameStateVariableIdx(variable_name);
                        if (s.ID < 0) {
                            // This means, we couldn't do it (variable might contain a {$this}):
                            s.ID_from_variable_name = variable_name;
                        }
                        
                        scripts.add(s);
                    }
                    break;
                    
                case "inc-variable":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_INC_VARIABLE);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL);
                        String variable_name = t.value;
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        
                        s.ID = game.getOrCreateGameStateVariableIdx(variable_name);
                        if (s.ID < 0) {
                            // This means, we couldn't do it (variable might contain a {$this}):
                            s.ID_from_variable_name = variable_name;
                        }
                        
                        scripts.add(s);
                    }
                    break;                    

                case "dec-variable":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_DEC_VARIABLE);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL);
                        String variable_name = t.value;
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        
                        s.ID = game.getOrCreateGameStateVariableIdx(variable_name);
                        if (s.ID < 0) {
                            // This means, we couldn't do it (variable might contain a {$this}):
                            s.ID_from_variable_name = variable_name;
                        }
                        
                        scripts.add(s);
                    }
                    break;                    
                    
//                case "game-complete":
//                    {
//                        PAKScript s = new PAKScript(PAKRule.SCRIPT_GAME_COMPLETE);
//                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
//                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
//                        scripts.add(s);
//                    }
//                    break;
                    
                case "if":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_IF_THEN_ELSE);
                        Pair<List<PAKTrigger>, Integer> triggersAndConnective = parseRuleTriggers(tokenizer, game);
                        s.if_triggers = triggersAndConnective.m_a;
                        if (triggersAndConnective.m_b == PAKRule.CONNECTIVE_OR) {
                            s.type = PAKRule.SCRIPT_IFOR_THEN_ELSE;
                        }
                        s.then_scripts = parseRuleScripts(tokenizer, platform, game, config);
                        s.else_scripts = new ArrayList<>();
                        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL);
                        if (t.value.equals("else")) {
                            expectSpecificToken(tokenizer, ":", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                            s.else_scripts = parseRuleScripts(tokenizer, platform, game, config);
                            t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL);
                        }
                        if (t == null || !t.value.equals("endif")) {
                            throw new Exception("Expected endif but found " +t.value+ " in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
                        }
                        scripts.add(s);
                    }
                    break;
                    
                case "add-dialogue-option":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_ADD_DIALOGUE_OPTION);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = tokenizer.nextToken();
                        if (t.value.equals("exit") && t.type == Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL) {
                            s.ID = 255;
                        } else if (t.type == Token.TOKEN_TYPE_NUMBER) {
                            s.ID = Integer.parseInt(t.value);                            
                        } else {
                            throw new Exception("Expected exit|number in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
                        }
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                        s.text = game.translateText(t.value, config);
                        t = tokenizer.nextToken();
                        if (t.value.equals(")")) {
                            s.text2 = s.text;
                        } else {
                            t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                            s.text2 = game.translateText(t.value, config);
                            expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);                            
                        }
                        scripts.add(s);                            

                        int width = game.font.stringWidthInPixels(s.text);
                        if (width > platform.TEXT_AREA_WIDTH_IN_PIXELS) {
                            throw new Exception("Dialogue option '"+s.text+"' is too long, and would use more than one line of text! " + width + " > " + (platform.TEXT_AREA_WIDTH_IN_PIXELS));
                        }
                    }
                    break;
                    
                case "goto-dialogue-state":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_GOTO_DIALOGUE_STATE);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = tokenizer.nextToken();
                        if (t.value.equals("exit") && t.type == Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL) {
                            s.ID = 255;
                        } else if (t.type == Token.TOKEN_TYPE_NUMBER) {
                            s.ID = Integer.parseInt(t.value);                            
                        } else {
                            throw new Exception("Expected exit|number in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
                        }
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        scripts.add(s);
                    }
                    break;

                case "start-dialogue":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_START_DIALOGUE);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        s.dialogue = t.value;
                        scripts.add(s);
                    }
                    break;
                    
                case "scrolling-message":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_SCROLLING_MESSAGE);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        s.text = game.translateText(t.value, config);
                        s.pause = 0;
                        scripts.add(s);
                    }
                    break;
                    
                case "scrolling-message-pause":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_SCROLLING_MESSAGE);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        s.text = game.translateText(t.value, config);
                        s.pause = 1;
                        scripts.add(s);
                    }
                    break;
                    
                case "increase-number-object":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_INCREASE_NUMBER_OBJECT);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        s.ID = Integer.parseInt(t.value);
                        scripts.add(s);
                    }
                    break;
                    
                case "decrease-number-object":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_DECREASE_NUMBER_OBJECT);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        Pair<Integer,Boolean> tmp = expectNumberOrThis(tokenizer);
                        s.ID = tmp.m_a;
                        s.ID_this = tmp.m_b;
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        scripts.add(s);
                    }
                    break;

                case "set-number-object":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_CHANGE_OBJECT_DIRECTION);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        Pair<Integer,Boolean> tmp = expectNumberOrThis(tokenizer);
                        s.ID = tmp.m_a;
                        s.ID_this = tmp.m_b;
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        s.value = Integer.parseInt(t.value);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        scripts.add(s);
                    }
                    break;

                case "change-object-direction":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_CHANGE_OBJECT_DIRECTION);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        Pair<Integer,Boolean> tmp = expectNumberOrThis(tokenizer);
                        s.ID = tmp.m_a;
                        s.ID_this = tmp.m_b;
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                        s.value = PAKObjectType.directionFromString(t.value);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        scripts.add(s);
                    }
                    break;
                    
                case "wait-for-space":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_WAIT_FOR_SPACE);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        scripts.add(s);
                    }
                    break;                    

                case "call-script":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_CALL_SCRIPT);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        Token t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                        s.scriptToCall = t2.value;
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        scripts.add(s);
                    }
                    break;
                    
                case "clear-screen":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_CLEAR_SCREEN);
                        s.clearScreenType = 0;
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        Token t2 = tokenizer.nextToken();
                        if (t2.type == Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL) {
                            s.clearScreenType = platform.clearScreenType(t2.value);
                            t2 = tokenizer.nextToken();
                        }
                        if (!t2.value.equals(")")) {
                            throw new Exception("Expected ')' after clear-screen in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
                        }
                        scripts.add(s);
                    }
                    break;

                case "clear-text-area":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_CLEAR_TEXT_AREA);
                        s.clearScreenType = 0;
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        scripts.add(s);
                    }
                    break;

                case "clear-room-area":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_CLEAR_ROOM_AREA);
                        s.clearScreenType = 0;
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        scripts.add(s);
                    }
                    break;
                    
                case "redraw-room":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_REDRAW_ROOM);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        scripts.add(s);
                    }
                    break;                    

                case "redraw-dirty":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_REDRAW_DIRTY);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        scripts.add(s);
                    }
                    break;                    

                case "gui-off":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_GUI_OFF);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        scripts.add(s);
                    }
                    break;                    
                    
                case "gui-on":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_GUI_ON);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        scripts.add(s);
                    }
                    break;                    

                case "print":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_PRINT);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        Token t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int x = Integer.parseInt(t2.value);
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int y = Integer.parseInt(t2.value);
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int color = Integer.parseInt(t2.value);
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                        String text = t2.value;
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        s.x = x;
                        s.y = y;
                        s.color = color;
                        s.text = game.translateText(text, config);
                        scripts.add(s);
                    }
                    break; 
                    
                case "print-paragraph":
                    {
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        Token t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int x = Integer.parseInt(t2.value);
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int y = Integer.parseInt(t2.value);
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int color = Integer.parseInt(t2.value);
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                        String text = game.translateText(t2.value, config);
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int width = Integer.parseInt(t2.value);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        
                        // Split the text into sentences:
                        String parts[] = text.split(" ");
                        String current = "";
                        for(int i = 0;i<parts.length;i++) {
                            String next = current;
                            if (!next.isEmpty()) next = next + " ";
                            next = next + parts[i];
                            int nextWidth = game.font.stringWidthInPixels(next);
                            if (nextWidth > width) {
                                // we reached the maximum width:
                                PAKScript s = new PAKScript(PAKRule.SCRIPT_PRINT);
                                s.x = x;
                                s.y = y;
                                s.color = color;
                                s.text = current;
                                s.originalUnsplitText = text;
                                scripts.add(s);
                                y += 8;
                                current = parts[i];
                            } else {
                                current = next;
                            }
                        }
                        // Last string:
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_PRINT);
                        s.x = x;
                        s.y = y;
                        s.color = color;
                        s.text = current;
                        s.originalUnsplitText = text;
                        scripts.add(s);
                    }
                    break;
                    
                case "draw-cutscene-image":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_DRAW_CUTSCENE_IMAGE);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        Token t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int x = Integer.parseInt(t2.value);
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int y = Integer.parseInt(t2.value);
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                        String imageToDraw = t2.value;
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        s.x = x;
                        s.y = y;
                        s.x1 = -1;
                        s.y1 = -1;
                        s.x2 = -1;
                        s.y2 = -1;
                        s.imageToDraw = imageToDraw;
                        scripts.add(s);
                    }
                    break;
                    
                case "draw-cutscene-image-horizontal-section":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_DRAW_CUTSCENE_IMAGE);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        Token t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int x = Integer.parseInt(t2.value);
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int y = Integer.parseInt(t2.value);
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                        String imageToDraw = t2.value;
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int from = Integer.parseInt(t2.value);
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int to = Integer.parseInt(t2.value);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        s.x = x;
                        s.y = y;
                        s.x1 = 0;
                        s.y1 = from;
                        s.x2 = 0;  // short-hand for "whole width"
                        s.y2 = to;
                        s.imageToDraw = imageToDraw;
                        scripts.add(s);
                    }
                    break;
                    
                case "draw-cutscene-image-rectangle":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_DRAW_CUTSCENE_IMAGE);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        Token t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int x = Integer.parseInt(t2.value);
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int y = Integer.parseInt(t2.value);
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                        String imageToDraw = t2.value;
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int x1 = Integer.parseInt(t2.value);
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int y1 = Integer.parseInt(t2.value);
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int x2 = Integer.parseInt(t2.value);
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int y2 = Integer.parseInt(t2.value);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        s.x = x;
                        s.y = y;
                        s.x1 = x1;
                        s.y1 = y1;
                        s.x2 = x2;
                        s.y2 = y2;
                        s.imageToDraw = imageToDraw;
                        scripts.add(s);
                    }
                    break;                    
                    
                case "collision-map-clear":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_COLLISION_MAP_CLEAR);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        Token t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int x1 = Integer.parseInt(t2.value);
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int y1 = Integer.parseInt(t2.value);
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int x2 = Integer.parseInt(t2.value);
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int y2 = Integer.parseInt(t2.value);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        s.x = x1;
                        s.y = y1;
                        s.x2 = (x2 - x1) / platform.OBJECT_MASK_HORIZONTAL_RESOLUTION;
                        s.y2 = (y2 - y1) / platform.OBJECT_MASK_VERTICAL_RESOLUTION;;
                        scripts.add(s);
                    }
                    break;

                case "collision-map-set":
                    {
                        PAKScript s = new PAKScript(PAKRule.SCRIPT_COLLISION_MAP_SET);
                        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        Token t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int x1 = Integer.parseInt(t2.value);
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int y1 = Integer.parseInt(t2.value);
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int x2 = Integer.parseInt(t2.value);
                        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                        int y2 = Integer.parseInt(t2.value);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        s.x = x1;
                        s.y = y1;
                        s.x2 = (x2 - x1) / platform.OBJECT_MASK_HORIZONTAL_RESOLUTION;
                        s.y2 = (y2 - y1) / platform.OBJECT_MASK_VERTICAL_RESOLUTION;;
                        scripts.add(s);
                    }
                    break;

                case "play-sfx":
                {
                    expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    Token t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                    expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    PAKScript s = new PAKScript(PAKRule.SCRIPT_PLAY_SFX);
                    s.sfxToPlay = t2.value;
                    scripts.add(s);
                    PAKSFX sfx = game.getOrCreateSFX(s.sfxToPlay);
                    if (s.sfxToPlay.endsWith(".asm")) {
                        sfx.fileType = PAKSFX.RAW_PAK_ASSEMBLER;
                    } else if (s.sfxToPlay.endsWith(".bin")) {
                        sfx.fileType = PAKSFX.RAW_PAK_BINARY;
                    } else if (s.sfxToPlay.endsWith(".afx")) {
                        sfx.fileType = PAKSFX.AYFX;
                    } else {
                        throw new Exception("Unrecognized file termination for SFX: " + s.sfxToPlay);
                    }
                    break;
                }

                case "pause":
                {
                    expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    Token t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                    expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    PAKScript s = new PAKScript(PAKRule.SCRIPT_PAUSE);
                    s.pause = Integer.parseInt(t2.value);
                    scripts.add(s);
                    break;
                }

                case "skippable-pause":
                {
                    expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    Token t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                    expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    PAKScript s = new PAKScript(PAKRule.SCRIPT_SKIPPABLE_PAUSE);
                    s.pause = Integer.parseInt(t2.value);
                    scripts.add(s);
                    break;
                }
                
                case "pause-redrawing":
                {
                    expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    Token t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                    expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    PAKScript s = new PAKScript(PAKRule.SCRIPT_PAUSE_REDRAWING);
                    s.pause = Integer.parseInt(t2.value);
                    scripts.add(s);
                    break;
                }

                case "skippable-pause-redrawing":
                {
                    expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    Token t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                    expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    PAKScript s = new PAKScript(PAKRule.SCRIPT_SKIPPABLE_PAUSE_REDRAWING);
                    s.pause = Integer.parseInt(t2.value);
                    scripts.add(s);
                    break;
                }
                
                case "play-tsv-song":
                {
                    boolean forceRestart = false;
                    expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    Token t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                    expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    Token t3 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                    Token t4 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    if (t4.value.equals(",")) {
                        expectSpecificToken(tokenizer, "force-restart", Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        forceRestart = true;
                    } else if (!t4.value.equals(")")) {
                        throw new Exception("Expected ',' or ')' in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
                    }
                    PAKScript s = new PAKScript(PAKRule.SCRIPT_PLAY_TSV_SONG);
                    s.songToPlay = t2.value;
                    s.songTempo = Integer.parseInt(t3.value);
                    s.forceSongRestart = forceRestart;
                    scripts.add(s);
                    game.getOrCreateSong(s.songToPlay, PAKSong.TYPE_TSV);
                    break;                    
                }

                case "play-wyz-song":
                {
                    boolean forceRestart = false;
                    expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    Token t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                    Token t4 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    if (t4.value.equals(",")) {
                        expectSpecificToken(tokenizer, "force-restart", Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL);
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        forceRestart = true;
                    } else if (!t4.value.equals(")")) {
                        throw new Exception("Expected ',' or ')' in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
                    }
                    PAKScript s = new PAKScript(PAKRule.SCRIPT_PLAY_WYZ_SONG);
                    s.songToPlay = t2.value;
                    s.forceSongRestart = forceRestart;
                    scripts.add(s);
                    game.getOrCreateSong(s.songToPlay, PAKSong.TYPE_WYZ);
                    break;                    
                }
                
                case "stop-song":
                {
                    expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    PAKScript s = new PAKScript(PAKRule.SCRIPT_STOP_SONG);
                    scripts.add(s);
                    break;                    
                }


                case "set-palette":
                {
                    expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    List<Integer> palette = new ArrayList<>();
                    Token t2 = tokenizer.nextToken();
                    String paletteName = null;
                    if (t2.type == Token.TOKEN_TYPE_STRING) {
                        paletteName = t2.value;
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    } else if (t2.type == Token.TOKEN_TYPE_NUMBER) {
                        tokenizer.unread(t2);
                        do{
                            t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                            palette.add(Integer.parseInt(t2.value));
                            t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                        } while(t2.value.equals(","));
                        if (!t2.value.equals(")")) {
                            throw new Exception("Expected ',' or ')' in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
                        }
                        paletteName = "set-palette-"+nextPaletteID;
                        platform.setPalette(paletteName, palette);
                        nextPaletteID++;
                    } else {
                        throw new Exception("Expected string or numner in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
                    }
                    
                    PAKScript s = new PAKScript(PAKRule.SCRIPT_SET_PALETTE);
                    s.paletteToSet = paletteName;
                    scripts.add(s);
                    break;                    
                }
                
                case "restore-current-room-palette":
                {
                    expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);                    
                    PAKScript s = new PAKScript(PAKRule.SCRIPT_RESTORE_ROOM_PALETTE);
                    scripts.add(s);
                    break;  
                }

                case "draw-fog-over-room-area":
                {
                    expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);                    
                    PAKScript s = new PAKScript(PAKRule.SCRIPT_DRAW_FOG_OVER_ROOM_AREA);
                    scripts.add(s);
                    break;  
                }
                
                case "call-assembler":
                {
                    expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    Token t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                    expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);                    
                    if (!game.assemblerFunctions.contains(t2.value)) {
                        game.assemblerFunctions.add(t2.value);
                    }
                    int ID = game.assemblerFunctions.indexOf(t2.value);
                    PAKScript s = new PAKScript(PAKRule.SCRIPT_CALL_ASSEMBLER);
                    s.assemblerFunctionID = ID;
                    scripts.add(s);
                    break;
                }

                case "custom-assembler-room-draw":
                {
                    expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    Token t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                    if (!game.assemblerFunctions.contains(t2.value)) {
                        game.assemblerFunctions.add(t2.value);
                    }
                    int ID = game.assemblerFunctions.indexOf(t2.value);
                    boolean fullRedraw = false;
                    t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    if (t2.value.equals(",")) {
                        t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL);
                        if (t2.value.equalsIgnoreCase("only-full-redraw")) {
                            fullRedraw = true;
                        } else {
                            throw new Exception("Expected 'only-full-redraw' after ',' in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
                        }
                        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    } else if (!t2.value.equals(")")) {
                        throw new Exception("Expected ',' or ')' after assembler function name in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
                    }
                    PAKScript s = new PAKScript(PAKRule.SCRIPT_CUSTOM_ASSEMBLER_ROOM_DRAW);
                    s.assemblerFunctionID = ID;
                    s.fullRedraw = fullRedraw;
                    scripts.add(s);
                    break;
                }    

                case "clear-custom-assembler-room-draw":
                {
                    expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    PAKScript s = new PAKScript(PAKRule.SCRIPT_CLEAR_CUSTOM_ASSEMBLER_ROOM_DRAW);
                    scripts.add(s);
                    break;
                }    

                case "custom-assembler-on-update":
                {
                    expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    Token t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                    if (!game.assemblerFunctions.contains(t2.value)) {
                        game.assemblerFunctions.add(t2.value);
                    }
                    int ID = game.assemblerFunctions.indexOf(t2.value);
                    expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    PAKScript s = new PAKScript(PAKRule.SCRIPT_CUSTOM_ASSEMBLER_ON_UPDATE);
                    s.assemblerFunctionID = ID;
                    scripts.add(s);
                    break;
                }    
                
                case "clear-custom-assembler-on-update":
                {
                    expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    PAKScript s = new PAKScript(PAKRule.SCRIPT_CLEAR_CUSTOM_ASSEMBLER_ON_UPDATE);
                    scripts.add(s);
                    break;
                }    
                
                case "save-game":
                {
                    PAKScript s = new PAKScript(PAKRule.SCRIPT_SAVE_GAME);
                    expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    scripts.add(s);
                }
                break;                    
                
                case "load-game":
                {
                    PAKScript s = new PAKScript(PAKRule.SCRIPT_LOAD_GAME);
                    expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                    scripts.add(s);
                }
                break;   
                                
                default: 
                    if (scripts.isEmpty()) throw new Exception("Unknown rule script "+t.value+" in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
                    tokenizer.unread(t);
                    return scripts;
            }
        }
    }
    
    
    public BufferedImage parseImage(PAKETTokenizer tokenizer, List<String> dataFolders, PAKETConfig config) throws Exception
    {
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
        String imageFileName = t.value;
        int x0 = -1;
        int x1 = -1;
        int y0 = -1;
        int y1 = -1;
        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        if (t.value.equals(",")) {
            t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
            x0 = Integer.parseInt(t.value);
            expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

            t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
            y0 = Integer.parseInt(t.value);
            expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

            t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
            x1 = Integer.parseInt(t.value);
            expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

            t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
            y1 = Integer.parseInt(t.value);
            expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        } else if (!t.value.equals(")")) {
            throw new Exception("Expected , or ) after string in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }   
        imageFileName = getFileName(imageFileName, dataFolders, config);
        // equivalent to an animation with a single frame:
        return getSubImage(ImageIO.read(new File(imageFileName)), x0, y0, x1, y1);
    }
    
    
    public BufferedImage parseEmptyImage(PAKETTokenizer tokenizer, List<String> dataFolders, PAKETConfig config) throws Exception
    {
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
        int w = Integer.parseInt(t.value);
        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
        int h = Integer.parseInt(t.value);
        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    
    public void parseObject(PAKETTokenizer tokenizer, List<String> dataFolders, String destinationFolder, Platform platform, PAKGame game, PAKETConfig config) throws Exception {
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
        String objectID = t.value;

        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
        String objectName = game.translateText(t.value, config);

        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
        String objectDescription = game.translateText(t.value, config);

        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        expectSpecificToken(tokenizer, ":", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

        PAKObjectType ot = new PAKObjectType(objectID);
        ot.name = objectName;
        ot.description = objectDescription;
        if (game.objectTypesHash.containsKey(objectID)) {
            throw new Exception("Object type '" + objectID + "' is defined twice!");
        }
        game.objectTypesHash.put(objectID, ot);
        
        t = tokenizer.nextToken();
        while (t.type == Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL) {
            if (t.value.equals("state")) {
                expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                String stateName = t.value;

                expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                String direction = t.value;

                expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                expectSpecificToken(tokenizer, ":", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);

                PAKObjectState state = new PAKObjectState(stateName, direction);
                ot.states.add(state);

                // image/animation/selection:
                t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL);
                OUTER:
                while (true) {
                    switch (t.value) {
                        case "image":
                        {
                            BufferedImage img = parseImage(tokenizer, dataFolders, config);
                            // equivalent to an animation with a single frame:
                            state.animationTempo = 1;
                            state.animationFrames.clear();
                            state.animationFrames.add(img);
                            state.animationFrameSequence.clear();
                            state.animationFrameSequence.add(0);
                            break;
                        }
                        case "empty-image":
                        {
                            BufferedImage img = parseEmptyImage(tokenizer, dataFolders, config);
                            state.animationTempo = 1;
                            state.animationFrames.clear();
                            state.animationFrames.add(img);
                            state.animationFrameSequence.clear();
                            state.animationFrameSequence.add(0);
                            break;
                        }
                        case "animation":
                        {
                            List<Integer> differentFrames = new ArrayList<>();
                            expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                            t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                            state.animationTempo = Integer.parseInt(t.value);
                            state.animationFrames.clear();
                            state.animationFrameSequence.clear();                            
                            t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                            while(t.value.equals(",")) {
                                t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
                                int frame = Integer.parseInt(t.value);
                                state.animationFrameSequence.add(frame);
                                if (!differentFrames.contains(frame)) differentFrames.add(frame);
                                t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                            }
                            if (!t.value.equals(")")) {
                                throw new Exception("Expected , or ) in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
                            }
                            expectSpecificToken(tokenizer, ":", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                            for(int i = 0;i<differentFrames.size();i++) {
                                expectSpecificToken(tokenizer, "image", Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL);
                                BufferedImage img = parseImage(tokenizer, dataFolders, config);
                                state.animationFrames.add(img);
                                state.checkImageSizes();
                            }
                            break;
                        }
                        case "selection":
                            expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                            t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL);
                            expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                            if (t.value.equals("pixel")) {
                                state.selection = PAKObjectState.SELECTION_PIXEL;
                            } else if (t.value.equals("box")) {
                                state.selection = PAKObjectState.SELECTION_BOX;
                            } else if (t.value.equals("none")) {
                                state.selection = PAKObjectState.SELECTION_NONE;
                            } else {
                                throw new Exception("Expected true or false in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
                            }   
                            break;
                        case "collisionmask":
                        {
                            // collisionmask("0000", "0000", "0000", "0000", "0000", "0000", "0000", "1111")
                            int w = 0;
                            int h = 0;
                            expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                            String collisionMask = "";
                            t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                            while(true) {
                                if (w == 0) {
                                    w = t.value.length();
                                } else {
                                    if (w != t.value.length()) throw new Exception("Different rows of the collision mask have different length in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
                                }
                                collisionMask += t.value;
                                h++;
                                t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
                                if (t.value.equals(")")) break;
                                if (!t.value.equals(",")) throw new Exception("Expected , or ) in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
                                t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
                            }
                            state.collisionMask = new int[w][h];
                            for(int i = 0, offs = 0;i<collisionMask.length();i++) {
                                int c = collisionMask.charAt(i);
                                if (c == '0') {
                                    state.collisionMask[offs%w][offs/w] = 0;
                                    offs++;
                                } else if (c == '1') {
                                    state.collisionMask[offs%w][offs/w] = 1;
                                    offs++;
                                }
                            }   
                            int statew = state.getWidth();
                            int stateh = state.getHeight();
                            if (statew != w*platform.OBJECT_MASK_HORIZONTAL_RESOLUTION ||
                                stateh != h*platform.OBJECT_MASK_VERTICAL_RESOLUTION) {
                                throw new Exception("Dimensions of image and collision mask do not match ("+statew+"x"+stateh+" vs "+w*platform.OBJECT_MASK_HORIZONTAL_RESOLUTION+"x"+h*platform.OBJECT_MASK_VERTICAL_RESOLUTION+") in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
                            }   
                            break;
                        }
                        default:
                            tokenizer.unread(t);
                            break OUTER;
                    }
                    if (!tokenizer.hasMoreTokens()) return;
                    t = tokenizer.nextToken();
                }
            } else if (t.value.equals("rule")) {
                PAKRule rule = parseRule(tokenizer, platform, game, config);
                ot.rules.add(rule);

            } else if (t.value.equals("repeating-rule")) {
                PAKRule rule = parseRule(tokenizer, platform, game, config);
                rule.repeat = true;
                ot.rules.add(rule);

            } else if (t.value.equals("on-room-load-rule")) {
                PAKRule rule = parseRule(tokenizer, platform, game, config);
                ot.onRoomLoadRules.add(rule);

            } else if (t.value.equals("on-room-start-rule")) {
                PAKRule rule = parseRule(tokenizer, platform, game, config);
                ot.onRoomStartRules.add(rule);
            } else {
                break;
            }
            
            if (!tokenizer.hasMoreTokens()) return;
            t = tokenizer.nextToken();
        }
        tokenizer.unread(t);
    }


    public void parseDialogue(PAKETTokenizer tokenizer, Platform platform, PAKGame game, PAKETConfig config) throws Exception
    {
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
        String dialogueID = t.value;
        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        expectSpecificToken(tokenizer, ":", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        
        PAKDialogue d = new PAKDialogue(dialogueID);
        
        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL);
        while(t.value.equals("state")) {
            t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
            int state_ID = Integer.parseInt(t.value);
            PAKDialogueState s = new PAKDialogueState(state_ID);
            expectSpecificToken(tokenizer, ":", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
            s.scripts = parseRuleScripts(tokenizer, platform, game, config);
            d.states.add(s);
            if (!tokenizer.hasMoreTokens()) {
                game.dialogues.add(d);
                return;
            }
            t = tokenizer.nextToken();
        }
        tokenizer.unread(t);
        
        game.dialogues.add(d);
    }
    
    
    public void parsePlayerStopState(PAKETTokenizer tokenizer, PAKGame game) throws Exception
    {
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
        int state = PAKObjectType.stateFromString(t.value);
        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
        int direction = PAKObjectType.directionFromString(t.value);
        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        game.resetPlayerStateDirection = state + direction * 16;
    }
    
    
    public void parseInitVariable(PAKETTokenizer tokenizer, PAKGame game) throws Exception
    {
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL);
        String variableName = t.value;
        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
        int value = Integer.parseInt(t2.value);
        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        if (t.value.equals(",")) {
            t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
            int min = Integer.parseInt(t2.value);
            expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
            t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
            int max = Integer.parseInt(t2.value);
            expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
            game.getOrCreateGameStateVariableIdx(variableName);
            game.gameStateVariableInitialValues.put(variableName, value);
            game.gameStateVariableRanges.put(variableName, new Pair<>(min, max));
        } else if (t.value.equals(")")) {
            game.getOrCreateGameStateVariableIdx(variableName);
            game.gameStateVariableInitialValues.put(variableName, value);            
        } else {
            throw new Exception("Expected ',' or ')' after initial variable value in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
    }
    
    
    public void parseCutsceneImage(
            PAKETTokenizer tokenizer, List<String> dataFolders,
            Platform platform, PAKGame game, PAKETConfig config) throws Exception
    {
        boolean selfContainedTileSet = false;
        List<String> platformOptions = new ArrayList<>();
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
        String image_name = t.value;
        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
        String tiled_filename = t.value;
        t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        while(t.value.equals(",")) {
            t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL);
            switch(t.value) {
                case "self-contained-tileset":
                    selfContainedTileSet = true;
                    break;
                default:
                {
                    // Check if it's somethiing of the form: key=value
                    Token t2 = tokenizer.nextToken();
                    if (t2 != null && t2.value.equals("=")) {
                        String tmp = t.value + t2.value + tokenizer.nextToken().value;
                        platformOptions.add(tmp);
                    } else {
                        tokenizer.unread(t2);
                        platformOptions.add(t.value);
                    }
                }
            }
            t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        }
        if (!t.value.equals(")")) {
            throw new Exception("Expected ',' or ')' in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        
        // Define the image:
        if (!selfContainedTileSet) {
            throw new Exception("parseCutsceneImage: cutscene images without self-contained-tileset are not yet supported!");
        }
        
        PAKCutsceneImage img = createCutsceneImage(image_name, tiled_filename, dataFolders, selfContainedTileSet, platformOptions, config);
        game.cutsceneImages.add(img);
    }
    
    
    public void parseScript(PAKETTokenizer tokenizer, Platform platform, PAKGame game, PAKETConfig config) throws Exception
    {
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
        String scriptName = t.value;
        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        expectSpecificToken(tokenizer, ":", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        List<PAKScript> scripts = parseRuleScripts(tokenizer, platform, game, config);
        
        // We will store as a rule with no triggers:
        PAKRule r = new PAKRule();
        r.triggers = null;
        r.scripts = scripts;
        game.scripts.add(new Pair<>(scriptName, r));
    }
    

    public void parseDefinePalette(PAKETTokenizer tokenizer, Platform platform, PAKGame game, PAKETConfig config) throws Exception
    {
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
        String paletteName = t.value;
        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        List<Integer> palette = new ArrayList<>();
        Token t2;
        do{
            t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
            palette.add(Integer.parseInt(t2.value));
            t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        } while(t2.value.equals(","));
        if (!t2.value.equals(")")) {
            throw new Exception("Expected ',' or ')' in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        config.info("parseDefinePalette: " + paletteName + " -> " + palette);
        platform.setPalette(paletteName, palette);
    }
    
    
    public void parseBasePalette(PAKETTokenizer tokenizer, Platform platform, PAKGame game, PAKETConfig config) throws Exception
    {
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        List<Integer> palette = new ArrayList<>();
        Token t2;
        do{
            t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_NUMBER);
            palette.add(Integer.parseInt(t2.value));
            t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        } while(t2.value.equals(","));
        if (!t2.value.equals(")")) {
            throw new Exception("Expected ',' or ')' in " + tokenizer.currentFile + " line " + tokenizer.getCurrentLine());
        }
        config.info("base palette override: " + palette);
        List<Integer> p = platform.getBasePalette();
        p.clear();
        p.addAll(palette);
    }    
    
    
    public void parseAdditionalAssemblerFile(PAKETTokenizer tokenizer, PAKGame game, PAKETConfig config) throws Exception
    {
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        game.additionalAssemblerFiles.add(t.value);
    }
    

    public void parseAdditionalAssemblerIncbin(PAKETTokenizer tokenizer, PAKGame game, PAKETConfig config) throws Exception
    {
        expectSpecificToken(tokenizer, "(", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t1 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
        expectSpecificToken(tokenizer, ",", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        Token t2 = expectSpecificToken(tokenizer, null, Token.TOKEN_TYPE_STRING);
        expectSpecificToken(tokenizer, ")", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        game.additionalAssemblerIncbin.add(new Pair<>(t1.value, t2.value));
    }

    
    public PAKCutsceneImage createCutsceneImage(String imageName, String tiled_filename, List<String> dataFolders, boolean selfContainedTileSet, List<String> platformOptions, PAKETConfig config) throws Exception
    {
        tiled_filename = PAKET.getFileName(tiled_filename, dataFolders, config);
        String folder = PAKET.getFilePath(tiled_filename);
        
        Element root = new SAXBuilder().build(tiled_filename).getRootElement();
        int w = Integer.parseInt(root.getAttributeValue("width"));
        int h = Integer.parseInt(root.getAttributeValue("height"));
        int nameTable[][] = new int[w][h];
        
        String tmp = folder + File.separatorChar + root.getChild("tileset").getChild("image").getAttributeValue("source");
        String tilesFileName =  PAKET.getFileName(tmp, dataFolders, config);        
        BufferedImage tiles = ImageIO.read(new File(tilesFileName));
        Element bg_xml = root.getChild("layer");

        String bg_data = bg_xml.getChild("data").getValue();
        StringTokenizer st = new StringTokenizer(bg_data, ",");
        for(int i = 0;i<h;i++) {
            for(int j = 0;j<w;j++) {
                nameTable[j][i] = Integer.parseInt(st.nextToken().trim());
            }
        }
        
        return new PAKCutsceneImage(imageName, w, h, nameTable, tiles, selfContainedTileSet, platformOptions);
    }
         

    public void reportUnusedTranslations(PAKGame game, PAKETConfig config)
    {
        for(String text:game.translation.keySet()) {
            if (!game.translations_used.contains(text)) {
                config.info("Unused translation line: " + text);
            }
        }
    }
    
    
    public static boolean createFoldersIfNecessary(String path) {
        File f = new File(path);
        if (!f.exists()) {
            return f.mkdirs();
        }
        return true;
    }
    
    
    // Returns the compressed size:
    public static int compress(String inputFile, String outputFileWithoutExtension, PAKETConfig config) throws Exception
    {
        switch(config.compressor) {
            case PAKETConfig.COMPRESSOR_ZX0:
                return ZX0.compressFile(inputFile, outputFileWithoutExtension + ".zx0", ZX0.MAX_OFFSET_ZX0);
            case PAKETConfig.COMPRESSOR_PLETTER:
                return Pletter.intMain(new String[]{inputFile, outputFileWithoutExtension + ".plt"});
            default:
                throw new Exception("Unsupported compressor " + config.compressor);
        }
    }


    public static int estimateCompressedSize(List<Integer> data, PAKETConfig config) throws Exception
    {
        return estimateCompressedSize(data, PAKETConfig.compressorExtension[config.compressor]);
    }
    
    
    public static int estimateCompressedSize(List<Integer> data, String compressor) throws Exception
    {
        switch(compressor) {
            case "zx0":
                return ZX0.sizeOfCompressedBuffer(data, ZX0.MAX_OFFSET_ZX0); // slower, but more accurate
//                ZX0.sizeOfCompressedBuffer(data, ZX0.MAX_OFFSET_QUICK); // faster, better for quick estimation
            case "plt":
                return Pletter.sizeOfCompressedBuffer(data);
            default:
                throw new Exception("Unsupported compressor: " + compressor);
        }
    }
    
}
