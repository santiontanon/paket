/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.pak;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import paket.compiler.PAKETConfig;
import paket.compiler.TileBankSet;
import paket.platforms.Platform;
import paket.text.PAKFont;
import paket.util.Pair;

/**
 *
 * @author santi
 */
public class PAKGame {
    public static final int PATHFINDING_OFF = 0;
    public static final int PATHFINDING_ON_CLICK = 1;
    public static final int PATHFINDING_ON_COLLISION = 2;
    
    // Language:
    public String language = null;  // the target language that the game needs to be compiled to
    public String inputFileLanguage = null; // the default language of the input file
    public HashMap<String, String> translation = null;  // a translation between the language of the input file and the target language
    public List<String> translations_used = new ArrayList<>();  // To report to the user if there are sentences in a localization file that were unused.
    
    public PAKFont font = null;
    public int general_buffer_size = 256;
    public int max_tiles_per_room = 1;
    public int room_tiles_objects_combined_buffer_size = 0;
    public int music_buffer_size = 0;
    public List<String> itemIDs = new ArrayList<>();
    public HashMap<String, PAKItem> itemHash = new HashMap<>();
    public HashMap<String, Pair<Integer,Integer>> textIDHash = new HashMap<>();
    public HashMap<String, PAKObjectType> objectTypesHash = new HashMap<>();
    public List<PAKRoom> rooms = new ArrayList<>();
    public List<PAKDialogue> dialogues = new ArrayList<>();
    public List<PAKSFX> sfxs = new ArrayList<>();
    public List<PAKSong> songs = new ArrayList<>();

    // Global rules:
    public List<PAKRule> itemRules = new ArrayList<>();
    public List<PAKRule> onRoomLoadRules = new ArrayList<>();
    public List<PAKRule> onRoomStartRules = new ArrayList<>();
    public List<Pair<String, PAKRule>> scripts = new ArrayList<>();  // these are named functions that can be called from any other scripts

    public List<PAKCutsceneImage> cutsceneImages = new ArrayList<>();
    public boolean frame_around_subrooms = true;
    
    // default texts:
    public String takeUntakeableErrorMessage = null;
    public String cannotUseErrorMessage = null;
    public String cannotTalkErrorMessage = null;
    public String takeFromInventoryErrorMessage = null;
    public String cannotReachErrorMessage = null;
    
    public String examine_action = null;
    public String pickup_action = null;
    public String use_action = null;
    public String talk_action = null;
    public String exit_action = null;
    public String with_action = null;

    // This contains additional text strings used by different modules, such as the password
    // save-game module:
    public HashMap<String, String> additionalTextStrings = new HashMap<>();
    
    public List<String> gameStateVariableNames = new ArrayList<>();
    public HashMap<String, Integer> gameStateVariableInitialValues = new HashMap<>();
    public HashMap<String, Pair<Integer, Integer>> gameStateVariableRanges = new HashMap<>();
        
    public int pathfinding = PATHFINDING_OFF;
    public int doubleClickOnExit = 0;
    public int pathfinding_max_length = 32;
    public boolean stopEarlyWhenWalking = true;
    public boolean useItemWithItemSymmetric = false;
    public String loadSaveGameScript = null;
    public String saveGameMode = null;
    public List<String> saveGameModeParams = new ArrayList<>();
    
    public String largestBufferRequirementTag = null;
    public int actionTextBufferSize = 0;
    public int maxRoomSpecificOnLoadRulesSize = 0;
    
    public int[] playerScalingThresholds = null;

    public int playerHeightInPixels = 0, playerWidthInPixels = 0;
    public int playerAnimationLengths[] = new int[]{0, 0, 0, 0};
    public Integer resetPlayerStateDirection = null;  // if this is != null, when player stops, its state/direction will be reset to this
    
    public List<String> additionalAssemblerFiles = new ArrayList<>();
    public List<Pair<String, String>> additionalAssemblerIncbin = new ArrayList<>();
    public List<String> assemblerFunctions = new ArrayList<>();
    
    // Sets of tile banks used in the game:
    public List<TileBankSet> bankSets = null;
    public HashMap<String, Integer> roomToBankSet = null;
    
    // Sizes to be used later by the memory allocators:
    public List<Integer> textBankSizes = null;
    public List<Integer> tileBankSizes = null;
    public List<Integer> objectTypeBankSizes = null;
    public List<Integer> roomBankSizes = null;
    public List<Integer> cutsceneSizes = null;
    public int gui_compressed_size = 0;
    public int sfx_data_size = 0;
    public int song_data_size = 0;
    public int tile_data_size = 0;  // this includes the size of the pointers
    
    
    public PAKRoom getRoom(String ID) {
        for(PAKRoom room:rooms) {
            if (room.ID.equals(ID)) {
                return room;
            }
        }
        return null;
    }


    public void generalBufferRequirement(int s, String tag)
    {
        if (s > general_buffer_size) {
            general_buffer_size = s;
            largestBufferRequirementTag = tag;
        }
    }
    
    
    public String translateText(String text, PAKETConfig config) throws Exception
    {
        if (translation != null) {
            if (translation.containsKey(text)) {
                if (!translations_used.contains(text)) {
                    translations_used.add(text);
                }
            } else {
                if (config.pedanticTranslation) {
                    throw new Exception("Text '" + text + "' is not defined in the localization file!");
                } else {
                    return text;
                }
            }                
            return translation.get(text);
        }
        return text;
    }
    
    
    public int getOrCreateGameStateVariableIdx(String variableName)
    {
        if (variableName.contains("{$this}")) return -1;
        int idx = gameStateVariableNames.indexOf(variableName);
        if (idx >= 0) return idx;
        idx = gameStateVariableNames.size();
        gameStateVariableNames.add(variableName);
        return idx;
    }
    
    
    public PAKSFX getOrCreateSFX(String fileName)
    {
        for(PAKSFX sfx:sfxs) {
            if (sfx.fileName.equals(fileName)) {
                return sfx;
            }
        }
        
        String sfx_name = "sfx_" + fileName.replace(" ", "_");
        sfx_name = sfx_name.replace(".", "_");
        sfx_name = sfx_name.replace("-", "_");
        sfx_name = sfx_name.replace("/", "_");
        sfx_name = sfx_name.replace("\\", "_");
        
        PAKSFX sfx = new PAKSFX(sfx_name, fileName, PAKSFX.RAW_PAK_BINARY);
        sfxs.add(sfx);
        return sfx;
    }
    
    
    public boolean songExists(String fileName)
    {
        for(PAKSong song:songs) {
            if (song.fileName.equals(fileName)) {
                return true;
            }
        }
        return false;
    }


    public PAKSong getOrCreateSong(String fileName, int type)
    {
        for(PAKSong song:songs) {
            if (song.fileName.equals(fileName)) {
                return song;
            }
        }
        
        PAKSong song = new PAKSong(fileName, type);
        songs.add(song);
        return song;
    }
    
    
    public List<PAKRule> getAllScriptsAndRules()
    {
        List<PAKRule> l = new ArrayList<>();
        l.addAll(itemRules);
        l.addAll(onRoomLoadRules);
        l.addAll(onRoomStartRules);
        for(Pair<String, PAKRule> tmp:scripts) {
            l.add(tmp.m_b);
        }
        for(PAKRoom r:rooms) {
            for(PAKRule rule:r.rules) {
                l.add(rule);
            }
            for(PAKRule rule:r.onLoadRules) {
                l.add(rule);
            }
        }
        return l;
    }
        
    
    public int getTotalNumberOfRules()
    {
        return getAllScriptsAndRules().size();
    }
    
    
    public int getTotalRulesSize(Platform platform) throws Exception
    {
        int n = 0;
        for(PAKRule r:getAllScriptsAndRules()) {
            n += r.toBytesForAssembler(textIDHash, this, dialogues, frame_around_subrooms, frame_around_subrooms, platform).size();
        }
        /*
        for(PAKRule r:itemRules) {
            n += r.toBytesForAssembler(textIDHash, this, dialogues, frame_around_subrooms, frame_around_subrooms, platform).size();
        }
        for(PAKRule r:onRoomLoadRules) {
            n += r.toBytesForAssembler(textIDHash, this, dialogues, frame_around_subrooms, frame_around_subrooms, platform).size();
        }
        for(PAKRule r:onRoomStartRules) {
            n += r.toBytesForAssembler(textIDHash, this, dialogues, frame_around_subrooms, frame_around_subrooms, platform).size();
        }
        for(Pair<String, PAKRule> tmp:scripts) {
            n += tmp.m_b.toBytesForAssembler(textIDHash, this, dialogues, frame_around_subrooms, frame_around_subrooms, platform).size();
        }
        for(PAKRoom r:rooms) {
            for(PAKRule rule:r.rules) {
                n += rule.toBytesForAssembler(textIDHash, this, dialogues, frame_around_subrooms, frame_around_subrooms, platform).size();
            }
            for(PAKRule rule:r.onLoadRules) {
                n += rule.toBytesForAssembler(textIDHash, this, dialogues, frame_around_subrooms, frame_around_subrooms, platform).size();
            }
        }
        */
        return n;
    }
}
