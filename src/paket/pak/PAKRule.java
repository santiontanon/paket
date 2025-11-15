/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.pak;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import paket.platforms.Platform;
import paket.util.Pair;

/**
 *
 * @author santi
 */
public class PAKRule {
    public static final int TRIGGER_NONE = 0;

    // 1 byte triggers:
    public static final int TRIGGER_CLICK_OUTSIDE_ROOM_AREA = 1;
    public static final int TRIGGER_TRUE = 2;  // not event
    
    // 2 byte triggers:
    public static final int TRIGGER_EXAMINE_OBJECT = 3;
    public static final int TRIGGER_PICK_UP_OBJECT = 4;
    public static final int TRIGGER_USE_OBJECT = 5;
    public static final int TRIGGER_TALK_TO_OBJECT = 6;
    public static final int TRIGGER_EXAMINE_ITEM = 7;
    public static final int TRIGGER_TALK_TO_ITEM = 8;
    public static final int TRIGGER_EXIT_THROUGH_OBJECT = 9;
    public static final int TRIGGER_HAVE_ITEM = 10;  // not event
    public static final int TRIGGER_CURRENT_ROOM_IS = 11;  // not event

    // 3 byte triggers:
    public static final int TRIGGER_USE_ITEM_WITH_OBJECT = 12;
    public static final int TRIGGER_VARIABLE_EQ = 13;            // not event
    public static final int TRIGGER_USE_ITEM_WITH_ITEM = 14;
    public static final int TRIGGER_NUMBER_OBJECT_EQUALS = 15;   // not event

    public static final int FIRST_2BYTE_TRIGGER = TRIGGER_EXAMINE_OBJECT;
    public static final int FIRST_3BYTE_TRIGGER = TRIGGER_USE_ITEM_WITH_OBJECT;    

    // scripts:
    public static final int SCRIPT_NONE = 0;  // (1 byte)
    public static final int SCRIPT_DO_NOT_DELETE_RULE = 1;  // (1 byte)
    public static final int SCRIPT_WAIT_FOR_SPACE = 2;  // 1 byte
    public static final int SCRIPT_REDRAW_ROOM = 3;  // 1 byte
    public static final int SCRIPT_REDRAW_DIRTY = 4;  // 1 byte
    public static final int SCRIPT_CLEAR_TEXT_AREA = 5;  // 1 byte
    public static final int SCRIPT_GUI_OFF = 6;  // 1 byte
    public static final int SCRIPT_GUI_ON = 7;  // 1 byte
    public static final int SCRIPT_SAVE_GAME = 8;  // 1 byte
    public static final int SCRIPT_LOAD_GAME = 9;  // 1 byte
    public static final int SCRIPT_STOP_SONG = 10;  // 1 byte
    public static final int SCRIPT_RESTORE_ROOM_PALETTE = 11; // 1 byte
    public static final int SCRIPT_DRAW_FOG_OVER_ROOM_AREA = 12; // 1 byte
    public static final int SCRIPT_CLEAR_ROOM_AREA = 13;  // 1 byte
    public static final int SCRIPT_CLEAR_CUSTOM_ASSEMBLER_ROOM_DRAW = 14;  // 1 byte
    public static final int SCRIPT_CLEAR_CUSTOM_ASSEMBLER_ON_UPDATE = 15;  // 1 byte
    
    public static final int SCRIPT_REMOVE_OBJECT = 16;  // 2 bytes
    public static final int SCRIPT_GAIN_ITEM = 17;  // 2 bytes
    public static final int SCRIPT_LOSE_ITEM = 18;  // 2 bytes
    public static final int SCRIPT_GOTO_DIALOGUE_STATE = 19;  // 2 bytes
    public static final int SCRIPT_START_DIALOGUE = 20;  // 2 bytes
    public static final int SCRIPT_INCREASE_NUMBER_OBJECT = 21;  // 2 bytes
    public static final int SCRIPT_DECREASE_NUMBER_OBJECT = 22;  // 2 bytes
    public static final int SCRIPT_CALL_SCRIPT = 23;  // 2 bytes
    public static final int SCRIPT_CLEAR_SCREEN = 24;  // 2 bytes
    public static final int SCRIPT_PLAY_SFX = 25;  // 2 bytes
    public static final int SCRIPT_SET_PALETTE = 26;  // 2 bytes
    public static final int SCRIPT_CALL_ASSEMBLER = 27;  // 2 bytes
    public static final int SCRIPT_CUSTOM_ASSEMBLER_ON_UPDATE = 28;  // 2 bytes    
    
    public static final int SCRIPT_CUSTOM_ASSEMBLER_ROOM_DRAW = 29;  // 3 bytes    
    public static final int SCRIPT_INC_VARIABLE = 30;  // 3 bytes
    public static final int SCRIPT_DEC_VARIABLE = 31;  // 3 bytes
    public static final int SCRIPT_MESSAGE = 32;  // 3 bytes
    public static final int SCRIPT_CHANGE_OBJECT_STATE = 33;  // 3 bytes
    public static final int SCRIPT_SET_VARIABLE = 34;  // 3 bytes
    public static final int SCRIPT_SKIPPABLE_PAUSE = 35;  // 3 bytes
    public static final int SCRIPT_CHANGE_OBJECT_DIRECTION = 36;  // 3 bytes
    public static final int SCRIPT_PAUSE = 37;  // 3 bytes
    public static final int SCRIPT_PAUSE_REDRAWING = 38;  // 3 bytes
    public static final int SCRIPT_SKIPPABLE_PAUSE_REDRAWING = 39;  // 3 bytes
    public static final int SCRIPT_WALK_TO = 40;  // 3 bytes
    public static final int SCRIPT_PLAY_WYZ_SONG = 41;  // 3 bytes

    public static final int SCRIPT_CHANGE_OBJECT_DESCRIPTION = 42;  // 4 bytes
    public static final int SCRIPT_GO_TO_ROOM = 43;  // 4 bytes
    public static final int SCRIPT_SCROLLING_MESSAGE = 44;  // 4 bytes
    public static final int SCRIPT_CHANGE_OBJECT_NAME = 45;  // 4 bytes
    public static final int SCRIPT_PLAY_TSV_SONG = 46;  // 4 bytes

    public static final int SCRIPT_COLLISION_MAP_CLEAR = 47;  // 5 bytes
    public static final int SCRIPT_COLLISION_MAP_SET = 48;  // 5 bytes

    public static final int SCRIPT_ADD_DIALOGUE_OPTION = 49;  // 6 bytes
    
    public static final int SCRIPT_PRINT = 50;  // 2 + platform dependent (3 bytes for CPC/MSX)
    public static final int SCRIPT_DRAW_CUTSCENE_IMAGE = 51;  // 3 + platform dependent (7 bytes for CPC, 6 bytes for MSX)

    public static final int SCRIPT_IF_THEN_ELSE = 52;  // special
    public static final int SCRIPT_IFOR_THEN_ELSE = 53;  // special
    
    public static final int FIRST_2BYTE_SCRIPT = SCRIPT_REMOVE_OBJECT;
    public static final int FIRST_3BYTE_SCRIPT = SCRIPT_CUSTOM_ASSEMBLER_ROOM_DRAW;    
    public static final int FIRST_4BYTE_SCRIPT = SCRIPT_CHANGE_OBJECT_DESCRIPTION;    
    public static final int FIRST_5BYTE_SCRIPT = SCRIPT_COLLISION_MAP_CLEAR;    
    public static final int FIRST_6BYTE_SCRIPT = SCRIPT_ADD_DIALOGUE_OPTION;    
    public static final int LAST_4BYTE_SCRIPT = SCRIPT_PLAY_TSV_SONG;    
    
    public static final int CONNECTIVE_AND = 0;
    public static final int CONNECTIVE_OR = 1;

    
    public static final String scriptUseVariableNames[] = {
        // 1 byte scripts:
        "NONE",  // 0
        "SCRIPT_DO_NOT_DELETE_RULE_USED",
        "SCRIPT_WAIT_FOR_SPACE_USED",
        "SCRIPT_REDRAW_ROOM_USED",
        "SCRIPT_REDRAW_DIRTY_USED",
        "SCRIPT_CLEAR_TEXT_AREA_USED",
        "SCRIPT_GUI_OFF_USED",
        "SCRIPT_GUI_ON_USED",
        "SCRIPT_SAVE_GAME_USED",
        "SCRIPT_LOAD_GAME_USED",
        "SCRIPT_STOP_SONG_USED",  // 10
        "SCRIPT_RESTORE_ROOM_PALETTE_USED",
        "SCRIPT_DRAW_FOG_OVER_ROOM_AREA_USED",
        "SCRIPT_CLEAR_ROOM_AREA_USED",
        "SCRIPT_CLEAR_CUSTOM_ASSEMBLER_ROOM_DRAW_USED",
        "SCRIPT_CLEAR_CUSTOM_ASSEMBLER_ON_UPDATE_USED",
        // 2 byte scripts:
        "SCRIPT_REMOVE_OBJECT_USED",  // 16
        "SCRIPT_GAIN_ITEM_USED",
        "SCRIPT_LOSE_ITEM_USED",
        "SCRIPT_GOTO_DIALOGUE_STATE_USED",
        "SCRIPT_START_DIALOGUE_USED",
        "SCRIPT_INCREASE_NUMBER_OBJECT_USED",
        "SCRIPT_DECREASE_NUMBER_OBJECT_USED",
        "SCRIPT_CALL_SCRIPT_USED",
        "SCRIPT_CLEAR_SCREEN_USED",
        "SCRIPT_PLAY_SFX_USED",
        "SCRIPT_SET_PALETTE_USED",
        "SCRIPT_CALL_ASSEMBLER_USED",  // 27
        "SCRIPT_CUSTOM_ASSEMBLER_ON_UPDATE_USED",  // 28
        // 3 byte scripts        
        "SCRIPT_CUSTOM_ASSEMBLER_ROOM_DRAW_USED",  // 29
        "SCRIPT_INC_VARIABLE_USED",
        "SCRIPT_DEC_VARIABLE_USED",
        "SCRIPT_MESSAGE_USED",  // 32
        "SCRIPT_CHANGE_OBJECT_STATE_USED",
        "SCRIPT_SET_VARIABLE_USED",
        "SCRIPT_SKIPPABLE_PAUSE_USED",
        "SCRIPT_CHANGE_OBJECT_DIRECTION_USED",
        "SCRIPT_PAUSE_USED",
        "SCRIPT_PAUSE_REDRAWING_USED",
        "SCRIPT_SKIPPABLE_PAUSE_REDRAWING_USED",
        "SCRIPT_WALK_TO_USED",  // 40
        "SCRIPT_PLAY_WYZ_SONG_USED",  // 41
        // 4 byte scripts
        "SCRIPT_CHANGE_OBJECT_DESCRIPTION_USED",  // 42
        "SCRIPT_GO_TO_ROOM_USED",
        "SCRIPT_SCROLLING_MESSAGE_USED",
        "SCRIPT_CHANGE_OBJECT_NAME_USED",
        "SCRIPT_PLAY_TSV_SONG_USED",  // 46
        // 5 byte scripts
        "SCRIPT_COLLISION_MAP_CLEAR_USED",
        "SCRIPT_COLLISION_MAP_SET_USED",
        // 6 byte scripts
        "SCRIPT_ADD_DIALOGUE_OPTION_USED",
        // Platform dependent size:        
        "SCRIPT_PRINT_USED",  // 50
        "SCRIPT_DRAW_CUTSCENE_IMAGE_USED",  // 51
        // Variable size:
        "SCRIPT_IF_THEN_ELSE_USED",  // 52
        "SCRIPT_IFOR_THEN_ELSE_USED",  // 53
        };

                
    public static class PAKTrigger {
        public int type;
        public int value1 = 0, value2 = 0;
        public String value1_from_variableName = null;
        public String value1_from_roomName = null;
        
        // These variables are used to define rule patterns, using "$this",
        // which would replace each of the values with the ID of the current
        // object.
        boolean value1_this = false, value2_this = false;
    
        
        public PAKTrigger(int a_t, int a_v1, int a_v2)
        {
            type = a_t;
            value1 = a_v1;
            value2 = a_v2;
        }

//        public PAKTrigger(int a_t, String a_v1, int a_v2)
//        {
//            type = a_t;
//            value1_from_variableName = a_v1;
//            value2 = a_v2;
//        }        
        
        public PAKTrigger(int a_t, int a_v1, int a_v2, boolean a_v1_this, boolean a_v2_this)
        {
            type = a_t;
            value1 = a_v1;
            value2 = a_v2;
            value1_this = a_v1_this;
            value2_this = a_v2_this;
        }

        
        public static PAKTrigger fromVariableName(int a_t, String a_v1, int a_v2)
        {
            PAKTrigger t = new PAKTrigger(a_t, 0, a_v2);
            t.value1_from_variableName = a_v1;
            return t;
        }


        public static PAKTrigger fromRoomName(int a_t, String a_v1, int a_v2)
        {
            PAKTrigger t = new PAKTrigger(a_t, 0, a_v2);
            t.value1_from_roomName = a_v1;
            return t;
        }
        
        
        public String instantiateStringWithThis(String str, int thisID)
        {
            if (str == null) return str;
            while(str.contains("{$this}")) {
                str = str.replace("{$this}", ""+thisID);
            }
            return str;
        }
        

        public PAKTrigger instantiateWithThis(int thisID, PAKGame game) throws Exception
        {
            PAKTrigger t = new PAKTrigger(type, value1, value2);
            if (value1_from_variableName != null) {
                String tmp = instantiateStringWithThis(value1_from_variableName, thisID);
                t.value1 = game.getOrCreateGameStateVariableIdx(tmp);
            }            
            if (value1_this) t.value1 = thisID;
            if (value2_this) t.value2 = thisID;
            return t;
        }
        
        
        public List<Integer> toBytesForAssembler(boolean last, PAKGame game) throws Exception
        {
            List<Integer> bytes = new ArrayList<>();
                        
            if (value1_from_roomName != null) {
                value1 = -1;
                for(int idx = 0;idx<game.rooms.size();idx++) {
                    PAKRoom r = game.rooms.get(idx);
                    if (r.ID.equals(value1_from_roomName)) value1 = idx;
                }
                if (value1 == -1) throw new Exception("Cannot find room with ID: " + value1_from_roomName);
                value1_from_roomName = null;
            }            
            
            // They all have 0x80, except the last one that does not have that bit
            bytes.add(type | (last ? 0:0x80));
            if (type >= FIRST_2BYTE_TRIGGER) {
                bytes.add(value1);
                if (type >= FIRST_3BYTE_TRIGGER) {
                    bytes.add(value2);
                }
            }
            
            if (value1_from_variableName != null) throw new Exception("Trigger is still expecting value1 from $this variableName instantiation!");;
            if (value1_from_roomName != null) throw new Exception("Trigger is still expecting value1 from a room name!");;
            
            return bytes;
        }
        
        
        public boolean isEvent() {
            if (type == TRIGGER_VARIABLE_EQ ||
                type == TRIGGER_HAVE_ITEM ||
                type == TRIGGER_CURRENT_ROOM_IS || 
                type == TRIGGER_NUMBER_OBJECT_EQUALS) {
                return false;
            }
            return true;
        }
        
        
        public void checkValidity() throws Exception {
            if (value1_this || value2_this) throw new Exception("Trigger is still expecting value from $this!");
            if (value1_from_variableName != null) throw new Exception("Trigger is still expecting value1 from $this variableName instantiation!");;
            if (value1_from_roomName != null) throw new Exception("Trigger is still expecting value1 from a room name!");;
        }
    }

    
    public static class PAKScript {
        public int type;
        public int ID;
        public String ID_from_variable_name = null;
        public int value;
        public int x,y;
        public int x1,y1, x2,y2;  // used to define rectangle areas
        public String text = null, text2 = null, originalUnsplitText = null;
        public int pause = 0;
        public String room = null;
        public String dialogue = null;
        public String scriptToCall = null;
        public String imageToDraw = null;
        public String sfxToPlay = null;
        public String songToPlay = null;
        public int songTempo = 10;
        public boolean forceSongRestart = false;
        public boolean fullRedraw = false;
        public int clearScreenType;
        public int color;
        public String paletteToSet = null;
        public int assemblerFunctionID;
        
        public List<PAKTrigger> if_triggers = null;
        public List<PAKScript> then_scripts = null;
        public List<PAKScript> else_scripts = null;

//        public boolean override_gainitem_check = false;
        
        // These variables are used to define rule patterns, using "$this",
        // which would replace each of the values with the ID of the current
        // object.
        public boolean ID_this = false;

        
        public PAKScript(int a_t)
        {
            type = a_t;
        }
        
        
        public String instantiateStringWithThis(String str, int thisID)
        {
            if (str == null) return str;
            while(str.contains("{$this}")) {
                str = str.replace("{$this}", ""+thisID);
            }
            return str;
        }
        
        
        public PAKScript instantiateWithThis(int thisID, PAKGame game) throws Exception
        {
            PAKScript s = new PAKScript(type);
            if (ID_from_variable_name == null) {
                s.ID = ID_this ? thisID:ID;
            } else {
                String tmp = instantiateStringWithThis(ID_from_variable_name, thisID);;
                s.ID = game.getOrCreateGameStateVariableIdx(tmp);
            }
            s.value = value;
            s.x = x;
            s.y = y;
            s.x1 = x1;
            s.y1 = y1;
            s.x2 = x2;
            s.y2 = y2;
            s.text = instantiateStringWithThis(text, thisID);
            s.text2 = instantiateStringWithThis(text2, thisID);
            s.pause = pause;
            s.room = instantiateStringWithThis(room, thisID);
            s.dialogue = instantiateStringWithThis(dialogue, thisID);
            s.scriptToCall = instantiateStringWithThis(scriptToCall, thisID);
            s.imageToDraw = instantiateStringWithThis(imageToDraw, thisID);
            s.sfxToPlay = instantiateStringWithThis(sfxToPlay, thisID);
            s.songToPlay = instantiateStringWithThis(songToPlay, thisID);
            s.songTempo = songTempo;
            s.forceSongRestart = forceSongRestart;
            s.clearScreenType = clearScreenType;
            s.color = color;
            s.paletteToSet = paletteToSet;
            s.assemblerFunctionID = assemblerFunctionID;
            if (if_triggers != null) {
                s.if_triggers = new ArrayList<>();
                for(PAKTrigger t:if_triggers) {
                    s.if_triggers.add(t.instantiateWithThis(thisID, game));
                }
            }
            if (then_scripts != null) {
                s.then_scripts = new ArrayList<>();
                for(PAKScript t:then_scripts) {
                    s.then_scripts.add(t.instantiateWithThis(thisID, game));
                }
            }
            if (else_scripts != null) {
                s.else_scripts = new ArrayList<>();
                for(PAKScript t:else_scripts) {
                    s.else_scripts.add(t.instantiateWithThis(thisID, game));
                }
            }
            
            return s;
        }        
                
        
        public List<Integer> toBytesForAssembler(HashMap<String, Pair<Integer,Integer>> textIDHash, PAKGame game, List<PAKDialogue> dialogues, List<List<Integer>> additionalBlocks, Platform platform) throws Exception
        {
            List<PAKRoom> rooms = game.rooms;
            List<Integer> bytes = new ArrayList<>();
            
            bytes.add(type);
            switch(type) {
                case SCRIPT_MESSAGE:
                    {
                        Pair<Integer, Integer> idx = textIDHash.get(text);
                        if (idx == null) {
                            throw new Exception("INTERNAL ENGINE BUG: text string '" + text+ "' is unaccounted for! (please report this bug)");
                        }
                        bytes.add(idx.m_a);
                        bytes.add(idx.m_b);
                        break;
                    }
                case SCRIPT_REMOVE_OBJECT:
                case SCRIPT_GAIN_ITEM:
                case SCRIPT_LOSE_ITEM:
                    bytes.add(ID);
                    break;
                case SCRIPT_CHANGE_OBJECT_DESCRIPTION:
                case SCRIPT_CHANGE_OBJECT_NAME:
                    {
                        Pair<Integer, Integer> idx = textIDHash.get(text);
                        bytes.add(ID);
                        bytes.add(idx.m_a);
                        bytes.add(idx.m_b);
                        break;
                    }
                case SCRIPT_GO_TO_ROOM:
                    {
                        int roomID = -1;
                        for(int idx = 0;idx<rooms.size();idx++) {
                            PAKRoom r = rooms.get(idx);
                            if (r.ID.equals(room)) roomID = idx;
                        }
                        if (roomID == -1) throw new Exception("Cannot find room with ID: " + room);
                        bytes.add(roomID);
                        bytes.add(x);
                        bytes.add(Math.max(0, y - game.playerHeightInPixels));
                    }
                    break;
                    
                case SCRIPT_CHANGE_OBJECT_STATE:
                case SCRIPT_SET_VARIABLE:
                    bytes.add(ID);
                    bytes.add(value);
                    break;

                case SCRIPT_INC_VARIABLE:
                case SCRIPT_DEC_VARIABLE:
                    bytes.add(ID);
                    break;
                
                case SCRIPT_WAIT_FOR_SPACE:
                case SCRIPT_REDRAW_ROOM:
                case SCRIPT_REDRAW_DIRTY:
                case SCRIPT_GUI_OFF:
                case SCRIPT_GUI_ON:
                case SCRIPT_SAVE_GAME:
                case SCRIPT_LOAD_GAME:
                case SCRIPT_CLEAR_CUSTOM_ASSEMBLER_ON_UPDATE:
                case SCRIPT_CLEAR_CUSTOM_ASSEMBLER_ROOM_DRAW:
                    break;
                    
                case SCRIPT_IF_THEN_ELSE:
                case SCRIPT_IFOR_THEN_ELSE:
                    bytes.add(0);  // temporary data that will be replaced later with the pointers to if, then and else
                    bytes.add(0);
                    bytes.add(0);
                    
                    // additional blocks:
                    List<Integer> if_block_bytes = new ArrayList<>();
                    List<Integer> then_block_bytes = new ArrayList<>();
                    List<Integer> else_block_bytes = new ArrayList<>();
                    
                    for(PAKTrigger t:if_triggers) {
                        if_block_bytes.addAll(t.toBytesForAssembler(if_triggers.indexOf(t) == if_triggers.size() - 1, game));
                    }
//                    if_block_bytes.add(0);
                    if (then_scripts != null) {
                        for(int i = 0;i<then_scripts.size();i++) {
                            PAKScript s = then_scripts.get(i);
                            int tmp = additionalBlocks.size();
                            List<Integer> scriptBytes = s.toBytesForAssembler(textIDHash, game, dialogues, additionalBlocks, platform);
                            if (i == then_scripts.size() - 1) {
                                // Indicate end of rule:
                                scriptBytes.set(0, scriptBytes.get(0) | 0x80);
                            }
                            then_block_bytes.addAll(scriptBytes);
                            if (tmp != additionalBlocks.size()) {
                                throw new Exception("Nested if-then-else scripts is not yet supported!");
                            }
                        }
                    } else {
                        throw new Exception("'then' part of an if-then-else statement cannot be empty!");
                    }
                    if (else_scripts != null) {
                        for(int i = 0;i<else_scripts.size();i++) {
                            PAKScript s = else_scripts.get(i);
                            int tmp = additionalBlocks.size();
                            List<Integer> scriptBytes = s.toBytesForAssembler(textIDHash, game, dialogues, additionalBlocks, platform);
                            if (i == else_scripts.size() - 1) {
                                // Indicate end of rule:
                                scriptBytes.set(0, scriptBytes.get(0) | 0x80);
                            }
                            else_block_bytes.addAll(scriptBytes);
                            if (tmp != additionalBlocks.size()) {
                                throw new Exception("Nested if-then-else scripts is not yet supported!");
                            }
                        }
                    }
                    additionalBlocks.add(if_block_bytes);
                    additionalBlocks.add(then_block_bytes);
                    if (!else_block_bytes.isEmpty()) {
                        additionalBlocks.add(else_block_bytes);
                    }
                    break;
                    
                case SCRIPT_ADD_DIALOGUE_OPTION:
                    {
                        Pair<Integer, Integer> idx = textIDHash.get(text);
                        bytes.add(idx.m_a);
                        bytes.add(idx.m_b);
                        Pair<Integer, Integer> idx2 = textIDHash.get(text2);
                        bytes.add(idx2.m_a);
                        bytes.add(idx2.m_b);
                        bytes.add(ID);
                        break;
                    }
                                        
                case SCRIPT_GOTO_DIALOGUE_STATE:
                    bytes.add(ID);
                    break;
                    
                case SCRIPT_START_DIALOGUE:
                    {
                        int dialogueID = -1;
                        for(int idx = 0;idx<dialogues.size();idx++) {
                            PAKDialogue d = dialogues.get(idx);
                            if (d.ID.equals(dialogue)) dialogueID = idx;
                        }
                        if (dialogueID == -1) throw new Exception("Cannot find dialogue with ID: " + dialogue);
                        bytes.add(dialogueID);
                    }
                    break;        
                    
                case SCRIPT_SCROLLING_MESSAGE:
                    {
                        Pair<Integer, Integer> idx = textIDHash.get(text);
                        bytes.add(idx.m_a);
                        bytes.add(idx.m_b);
                        bytes.add(pause);
                        break;
                    }
                    
                case SCRIPT_INCREASE_NUMBER_OBJECT:
                case SCRIPT_DECREASE_NUMBER_OBJECT:
                    bytes.add(ID);
                    break;
                    
                case SCRIPT_CHANGE_OBJECT_DIRECTION:
                    bytes.add(ID);
                    bytes.add(value*16);
                    break;
                    
                case SCRIPT_CALL_SCRIPT:
                {
                    int found = -1;
                    for(int i = 0;i<game.scripts.size();i++) {
                        Pair<String, PAKRule> script = game.scripts.get(i);
                        if (script.m_a.equals(scriptToCall)) {
                            found = i;
                            break;
                        }
                    }
                    if (found == -1) {
                        throw new Exception("Calling undefined script " + scriptToCall + " from call-script!");
                    }
                    bytes.add(found*2);
                    break;
                }
                
                case SCRIPT_CLEAR_SCREEN:
                    bytes.add(clearScreenType);
                    break;
                    
                case SCRIPT_CLEAR_TEXT_AREA:
                case SCRIPT_CLEAR_ROOM_AREA:
                    break;
                    
                case SCRIPT_PRINT:
                    Pair<Integer, Integer> idx = textIDHash.get(text);
                    bytes.add(idx.m_a);
                    bytes.add(idx.m_b);
                    platform.printScriptArguments(this, game, bytes);
                    break;
                    
                case SCRIPT_DRAW_CUTSCENE_IMAGE:
                {
                    int found = -1;
                    for(int i = 0;i<game.cutsceneImages.size();i++) {
                        PAKCutsceneImage image = game.cutsceneImages.get(i);
                        if (image.name.equals(imageToDraw)) {
                            found = i;
                            break;
                        }
                    }
                    if (found == -1) {
                        throw new Exception("Undefined cutscene image " + imageToDraw + " in draw-cutscene-image script.");
                    }
                    bytes.add(found * 2);
                    platform.addCutsceneImageMetaData(x, y, game.cutsceneImages.get(found), bytes);
                    if (x2 > 0 || y2 > 0) {
                        if (x2 == 0) {
                            PAKCutsceneImage image = game.cutsceneImages.get(found);
                            x2 = image.width;
                        }
                        bytes.add(y1);
                        bytes.add(y2);
                        bytes.add(x1);
                        bytes.add(x2);
                    } else {
                        PAKCutsceneImage image = game.cutsceneImages.get(found);
                        bytes.add(0);
                        bytes.add(image.height);
                        bytes.add(0);
                        bytes.add(image.width);
                    }
                    break;
                }
                
                case SCRIPT_PLAY_SFX:
                {
                    // We multiply by two, since this will be the offset into the SFX pointers table:
                    bytes.add(game.sfxs.indexOf(game.getOrCreateSFX(sfxToPlay))*2);
                    break;
                }
                    
                case SCRIPT_PAUSE:
                case SCRIPT_PAUSE_REDRAWING:
                case SCRIPT_SKIPPABLE_PAUSE:
                case SCRIPT_SKIPPABLE_PAUSE_REDRAWING:
                {
                    bytes.add(pause%256);
                    bytes.add(pause/256);
                    break;
                }
                
                case SCRIPT_PLAY_TSV_SONG:
                {
                    // We multiply by two, since this will be the offset into the Song pointers table:
                    if (!game.songExists(songToPlay)) {
                        String existingSongNames = "";
                        for(PAKSong s: game.songs) {
                            existingSongNames += s.fileName + ", ";
                        }
                        throw new Exception("Cannot find song: " + songToPlay + ", existing ones are: " + existingSongNames);
                    }
                    int songIndex = game.songs.indexOf(game.getOrCreateSong(songToPlay, PAKSong.TYPE_TSV));
                    bytes.add(songIndex*2);
                    if (forceSongRestart) {
                        bytes.add(1);
                    } else {
                        bytes.add(0);
                    }
                    bytes.add(songTempo);
                    break;
                }

                case SCRIPT_PLAY_WYZ_SONG:
                {
                    if (!game.songExists(songToPlay)) {
                        String existingSongNames = "";
                        for(PAKSong s: game.songs) {
                            existingSongNames += s.fileName + ", ";
                        }
                        throw new Exception("Cannot find song: " + songToPlay + ", existing ones are: " + existingSongNames);
                    }
                    int songIndex = game.songs.indexOf(game.getOrCreateSong(songToPlay, PAKSong.TYPE_WYZ));
                    bytes.add(songIndex);
                    if (forceSongRestart) {
                        bytes.add(1);
                    } else {
                        bytes.add(0);
                    }
                    break;
                }
                                
                case SCRIPT_STOP_SONG:
                {
                    break;
                }
                
                case SCRIPT_WALK_TO:
                {
                    bytes.add(x);
                    bytes.add(y);
                    break;
                }
                
                case SCRIPT_RESTORE_ROOM_PALETTE:
                case SCRIPT_DRAW_FOG_OVER_ROOM_AREA:
                {
                    break;
                }
                
                case SCRIPT_SET_PALETTE:
                {
                    int paletteID = platform.getPaletteID(paletteToSet);
                    if (paletteID < 0) {
                        throw new Exception("Error getting palette ID for palette " + paletteToSet);
                    }
                    bytes.add(paletteID);
                    break;
                }
                
                case SCRIPT_COLLISION_MAP_CLEAR:
                case SCRIPT_COLLISION_MAP_SET:
                    bytes.add(x);
                    bytes.add(y);
                    bytes.add(x2);
                    bytes.add(y2);
                    break;
                
                case SCRIPT_CALL_ASSEMBLER:
                {
                    bytes.add(assemblerFunctionID);
                    break;
                }

                case SCRIPT_CUSTOM_ASSEMBLER_ROOM_DRAW:
                {
                    bytes.add(assemblerFunctionID);
                    if (fullRedraw) {
                        bytes.add(1);
                    } else {
                        bytes.add(0);
                    }
                    break;
                }

                case SCRIPT_CUSTOM_ASSEMBLER_ON_UPDATE:
                {
                    bytes.add(assemblerFunctionID);
                    break;
                }
                
                default:
                    throw new Exception("Unknown script type " + type);
            }
                                    
            return bytes;
        }

        
        public void checkValidity(PAKRoom room, List<PAKObjectType> objectTypes, HashMap<String, Pair<Integer, Integer>> textIDHash) throws Exception 
        {
            if (ID_from_variable_name != null) {
                throw new Exception("script is still waiting for instantiation of the {$this} value!");
            }

            if (if_triggers != null) {
                for(PAKTrigger t:if_triggers) {
                    t.checkValidity();
                }
            }
            if (else_scripts != null) {
                for(PAKScript s:else_scripts) {
                    s.checkValidity(room, objectTypes, textIDHash);
                }
            }
            if (then_scripts != null) { 
                for(PAKScript s:then_scripts) {
                    s.checkValidity(room, objectTypes, textIDHash);
                }
            }
            
            if (type == SCRIPT_CHANGE_OBJECT_NAME) {
                // The new name must be smaller or equal to the old name:
                String newName = text;
                PAKObject o = room.getObject(ID);
                if (o == null) throw new Exception("Changing the name of an inexisting object " + ID + " in room " + room.ID);
                if (o.type.name.length() < newName.length()) {
                    throw new Exception("change-object-name will change an object name to a larger one, which is not allowed! '" + o.type.name + "' ->> '" + newName + "'");
                }
            }
        }
    }
    
    
    public boolean repeat = false;
    public List<PAKTrigger> triggers = new ArrayList<>();
    public List<PAKScript> scripts = new ArrayList<>();
    public int triggerConnective = CONNECTIVE_AND;
    
    
    public PAKRule instantiateWithThis(int thisID, PAKGame game) throws Exception
    {
        PAKRule r = new PAKRule();
        r.repeat = repeat;
        for(PAKTrigger t:triggers) {
            r.triggers.add(t.instantiateWithThis(thisID, game));
        }
        for(PAKScript s:scripts) {
            r.scripts.add(s.instantiateWithThis(thisID, game));
        }
        r.triggerConnective = triggerConnective;
        return r;
    }
    
    
    public boolean sameTriggersAs(PAKRule r2, PAKGame game) throws Exception
    {
        if (r2.triggers.size() != triggers.size()) return false;
        for(int i = 0;i<triggers.size();i++) {
            PAKTrigger t = triggers.get(i);
            PAKTrigger t2 = r2.triggers.get(i);
            List<Integer> bytes1 = t.toBytesForAssembler(i == triggers.size() - 1, game);
            List<Integer> bytes2 = t2.toBytesForAssembler(i == triggers.size() - 1, game);
            if (bytes1.size() != bytes2.size()) return false;
            for(int j = 0;j<bytes1.size();j++) {
                if (!bytes1.get(j).equals(bytes2.get(j))) {
                    return false;
                }
            }
        }
        return true;
    }
    
    
    public List<String> getTextLines(boolean forLocalization)
    {
        List<String> lines = new ArrayList<>();
        for(PAKScript s:scripts) {
            getTextLinesInternal(s, forLocalization, lines);
        }
        return lines;
    }
    
    
    public void getTextLinesInternal(PAKScript s, boolean forLocalization, List<String> lines)
    {
        if (s.text != null) {
            if (forLocalization && s.originalUnsplitText != null) {
                lines.add(s.originalUnsplitText);
            } else {
                lines.add(s.text);
            }
        }
        if (s.text2 != null) {
            lines.add(s.text2);
        }
        if (s.then_scripts != null) {
            for(PAKScript s2:s.then_scripts) {
                getTextLinesInternal(s2, forLocalization, lines);
            }
        }
        if (s.else_scripts != null) {
            for(PAKScript s2:s.else_scripts) {
                getTextLinesInternal(s2, forLocalization, lines);
            }
        }
    }
    
    
    public static void toBytesForAssemblerBody(List<Integer> bytes, List<PAKScript> scripts, HashMap<String, Pair<Integer,Integer>> textIDHash, PAKGame game, List<PAKDialogue> dialogues, boolean addSize, int triggerConnectiveUsed, boolean markAsRepeating, Platform platform) throws Exception
    {
        List<Integer> pointerLocations = new ArrayList<>();
        List<List<Integer>> additionalBlocks = new ArrayList<>();
                
        for(int i = 0;i<scripts.size();i++) {
            PAKScript s = scripts.get(i);
            int tmp = additionalBlocks.size();
            List<Integer> scriptBytes = s.toBytesForAssembler(textIDHash, game, dialogues, additionalBlocks, platform);
            if (i == scripts.size() - 1) {
                // Indicate end of rule:
                if (markAsRepeating) {
                    scriptBytes.set(0, scriptBytes.get(0) | 0xc0);
                } else {
                    scriptBytes.set(0, scriptBytes.get(0) | 0x80);
                }
            }
            bytes.addAll(scriptBytes);
            if (additionalBlocks.size() != tmp) {
                if (tmp != additionalBlocks.size() - 3 &&
                    tmp != additionalBlocks.size() - 2) throw new Exception("Script has additional blocks, but is not 2 or 3 of them (for 'if-then' or 'if-then-else' statements)!");
                // if-then-else, we need to add pointerLocations:
                for(int j = 0;j<additionalBlocks.size() - tmp;j++) {
                    pointerLocations.add(bytes.size() - 3 + j);
                }
            }
        }
                
        // Additional blocks:
        for(int i = 0;i<pointerLocations.size();i++) {
            bytes.set(pointerLocations.get(i), bytes.size() - pointerLocations.get(i));
            bytes.addAll(additionalBlocks.get(i));
        }
        
        // add the size of the rule:
        int size = bytes.size() + 1;
        if (size > 127) {
            throw new Exception("Rule is larger ("+size+" bytes) than the maximum size allowed of 127 bytes!");
        }
        if (triggerConnectiveUsed == CONNECTIVE_OR) {
            size |= 0x80;
        }
        if (addSize) bytes.add(0, size);        
    }
    
    
    public List<Integer> toBytesForAssembler(HashMap<String, Pair<Integer,Integer>> textIDHash, PAKGame game, List<PAKDialogue> dialogues, boolean firstMustBeEvent, boolean addSize, Platform platform) throws Exception
    {
        List<Integer> bytes = new ArrayList<>();
        
        // number of bytes - trigger - trigger - script - script - ... - #00
        // the 7th bit of "number of bytes" is set to 1 if the connective is an ||
        if (triggers != null) {
            for(PAKTrigger t:triggers) {
                bytes.addAll(t.toBytesForAssembler(triggers.indexOf(t) == triggers.size()-1, game));
                if (triggerConnective == CONNECTIVE_OR) {
                    // Make sure all triggers are events:
                    if (firstMustBeEvent && !t.isEvent()) {
                        throw new Exception("When a rule trigger uses the || connective, all conditions must be events!");
                    }
                }
            }
        }

        PAKRule.toBytesForAssemblerBody(bytes, scripts, textIDHash, game, dialogues, addSize, triggerConnective, repeat, platform);
        
        return bytes;
    }
    
    
    public void checkValidity(PAKRoom room, List<PAKObjectType> objectTypes, HashMap<String, Pair<Integer, Integer>> textIDHash, 
                       boolean roomRule) throws Exception 
    {
//        if (roomRule) {
//            // Check that if this is a pick-up room, the pickup script should be
//            // the first script:
//            boolean gainItem = false;
//            for(PAKScript s:scripts) {
//                if (s.containsGainItem()) {
//                    gainItem = true;
//                    break;
//                }
//            }
//            if (gainItem && scripts.get(0).type != SCRIPT_GAIN_ITEM) {
//                throw new Exception("gain-item script is not the first script in a rule in room " + room.ID + " (check documentation for why this is necessary, and what to do about it).");
//            }
//        }
        for(PAKScript s:scripts) {
            s.checkValidity(room, objectTypes, textIDHash);
        }
    }
    

    public static String generateConstantDefinitionAssembler() {
        String asm = "; triggers\n";
        asm += "TRIGGER_NONE: equ " + TRIGGER_NONE + "\n";
        asm += "TRIGGER_CLICK_OUTSIDE_ROOM_AREA: equ " + TRIGGER_CLICK_OUTSIDE_ROOM_AREA + "\n";
        asm += "TRIGGER_TRUE: equ " + TRIGGER_TRUE + "\n";
        asm += "TRIGGER_EXAMINE_OBJECT: equ " + TRIGGER_EXAMINE_OBJECT + "\n";
        asm += "TRIGGER_PICK_UP_OBJECT: equ " + TRIGGER_PICK_UP_OBJECT + "\n";
        asm += "TRIGGER_USE_OBJECT: equ " + TRIGGER_USE_OBJECT + "\n";
        asm += "TRIGGER_TALK_TO_OBJECT: equ " + TRIGGER_TALK_TO_OBJECT + "\n";
        asm += "TRIGGER_EXAMINE_ITEM: equ " + TRIGGER_EXAMINE_ITEM + "\n";
        asm += "TRIGGER_TALK_TO_ITEM: equ " + TRIGGER_TALK_TO_ITEM + "\n";
        asm += "TRIGGER_EXIT_THROUGH_OBJECT: equ " + TRIGGER_EXIT_THROUGH_OBJECT + "\n";
        asm += "TRIGGER_HAVE_ITEM: equ " + TRIGGER_HAVE_ITEM + "\n";
        asm += "TRIGGER_CURRENT_ROOM_IS: equ " + TRIGGER_CURRENT_ROOM_IS + "\n";
        asm += "\n";
        asm += "TRIGGER_USE_ITEM_WITH_OBJECT: equ " + TRIGGER_USE_ITEM_WITH_OBJECT + "\n";
        asm += "TRIGGER_VARIABLE_EQ: equ " + TRIGGER_VARIABLE_EQ + "\n";
        asm += "TRIGGER_USE_ITEM_WITH_ITEM: equ " + TRIGGER_USE_ITEM_WITH_ITEM + "\n";
        asm += "TRIGGER_NUMBER_OBJECT_EQUALS: equ " + TRIGGER_NUMBER_OBJECT_EQUALS + "\n";
        asm += "\n";
        asm += "TRIGGER_WALK: equ #7f\n";
        asm += "\n";
        asm += "FIRST_2BYTE_TRIGGER: equ " + FIRST_2BYTE_TRIGGER + "\n";
        asm += "FIRST_3BYTE_TRIGGER: equ " + FIRST_3BYTE_TRIGGER + "\n";
        asm += "\n";
        asm += "; scripts:\n";
        asm += "SCRIPT_NONE: equ " + SCRIPT_NONE + "\n";
        asm += "SCRIPT_MESSAGE: equ " + SCRIPT_MESSAGE + "\n";
        asm += "SCRIPT_REMOVE_OBJECT: equ " + SCRIPT_REMOVE_OBJECT + "\n";
        asm += "SCRIPT_GAIN_ITEM: equ " + SCRIPT_GAIN_ITEM + "\n";
        asm += "SCRIPT_LOSE_ITEM: equ " + SCRIPT_LOSE_ITEM + "\n";
        asm += "SCRIPT_CHANGE_OBJECT_DESCRIPTION: equ " + SCRIPT_CHANGE_OBJECT_DESCRIPTION + "\n";
        asm += "SCRIPT_GO_TO_ROOM: equ " + SCRIPT_GO_TO_ROOM + "\n";
        asm += "SCRIPT_DO_NOT_DELETE_RULE: equ " + SCRIPT_DO_NOT_DELETE_RULE + "\n";
        asm += "SCRIPT_CHANGE_OBJECT_STATE: equ " + SCRIPT_CHANGE_OBJECT_STATE + "\n";
        asm += "SCRIPT_SET_VARIABLE: equ " + SCRIPT_SET_VARIABLE + "\n";
        asm += "SCRIPT_INC_VARIABLE: equ " + SCRIPT_INC_VARIABLE + "\n";
        asm += "SCRIPT_DEC_VARIABLE: equ " + SCRIPT_DEC_VARIABLE + "\n";
        asm += "SCRIPT_SKIPPABLE_PAUSE: equ " + SCRIPT_SKIPPABLE_PAUSE + "\n";
        asm += "SCRIPT_IF_THEN_ELSE: equ " + SCRIPT_IF_THEN_ELSE + "\n";
        asm += "SCRIPT_ADD_DIALOGUE_OPTION: equ " + SCRIPT_ADD_DIALOGUE_OPTION + "\n";
        asm += "SCRIPT_GOTO_DIALOGUE_STATE: equ " + SCRIPT_GOTO_DIALOGUE_STATE + "\n";
        asm += "SCRIPT_START_DIALOGUE: equ " + SCRIPT_START_DIALOGUE + "\n";
        asm += "SCRIPT_SCROLLING_MESSAGE: equ " + SCRIPT_SCROLLING_MESSAGE + "\n";
        asm += "SCRIPT_INCREASE_NUMBER_OBJECT: equ " + SCRIPT_INCREASE_NUMBER_OBJECT + "\n";
        asm += "SCRIPT_DECREASE_NUMBER_OBJECT: equ " + SCRIPT_DECREASE_NUMBER_OBJECT + "\n";
        asm += "SCRIPT_CHANGE_OBJECT_DIRECTION: equ " + SCRIPT_CHANGE_OBJECT_DIRECTION + "\n";
        asm += "SCRIPT_IFOR_THEN_ELSE: equ " + SCRIPT_IFOR_THEN_ELSE + "\n";
        asm += "SCRIPT_CHANGE_OBJECT_NAME: equ " + SCRIPT_CHANGE_OBJECT_NAME + "\n";
        asm += "SCRIPT_WAIT_FOR_SPACE: equ " + SCRIPT_WAIT_FOR_SPACE + "\n";
        asm += "SCRIPT_CALL_SCRIPT: equ " + SCRIPT_CALL_SCRIPT + "\n";
        asm += "SCRIPT_CLEAR_SCREEN: equ " + SCRIPT_CLEAR_SCREEN + "\n";
        asm += "SCRIPT_REDRAW_ROOM: equ " + SCRIPT_REDRAW_ROOM + "\n";
        asm += "SCRIPT_PRINT: equ " + SCRIPT_PRINT + "\n";
        asm += "SCRIPT_DRAW_CUTSCENE_IMAGE: equ " + SCRIPT_DRAW_CUTSCENE_IMAGE + "\n";
        asm += "SCRIPT_PLAY_SFX: equ " + SCRIPT_PLAY_SFX + "\n";
        asm += "SCRIPT_PAUSE: equ " + SCRIPT_PAUSE + "\n";
        asm += "SCRIPT_PLAY_WYZ_SONG: equ " + SCRIPT_PLAY_WYZ_SONG + "\n";
        asm += "SCRIPT_PLAY_TSV_SONG: equ " + SCRIPT_PLAY_TSV_SONG + "\n";
        asm += "SCRIPT_COLLISION_MAP_CLEAR: equ " + SCRIPT_COLLISION_MAP_CLEAR + "\n";
        asm += "SCRIPT_COLLISION_MAP_SET: equ " + SCRIPT_COLLISION_MAP_SET + "\n";
        asm += "SCRIPT_STOP_SONG: equ " + SCRIPT_STOP_SONG + "\n";
        asm += "SCRIPT_REDRAW_DIRTY: equ " + SCRIPT_REDRAW_DIRTY + "\n";
        asm += "SCRIPT_PAUSE_REDRAWING: equ " + SCRIPT_PAUSE_REDRAWING + "\n";
        asm += "SCRIPT_WALK_TO: equ " + SCRIPT_WALK_TO + "\n";
        asm += "SCRIPT_SET_PALETTE: equ " + SCRIPT_SET_PALETTE + "\n";
        asm += "SCRIPT_RESTORE_ROOM_PALETTE: equ " + SCRIPT_RESTORE_ROOM_PALETTE + "\n";
        asm += "SCRIPT_DRAW_FOG_OVER_ROOM_AREA: equ " + SCRIPT_DRAW_FOG_OVER_ROOM_AREA + "\n";
        asm += "SCRIPT_CALL_ASSEMBLER: equ " + SCRIPT_CALL_ASSEMBLER + "\n";
        asm += "SCRIPT_CUSTOM_ASSEMBLER_ON_UPDATE: equ " + SCRIPT_CUSTOM_ASSEMBLER_ON_UPDATE + "\n";
        asm += "SCRIPT_CUSTOM_ASSEMBLER_ROOM_DRAW: equ " + SCRIPT_CUSTOM_ASSEMBLER_ROOM_DRAW + "\n";
        asm += "SCRIPT_CLEAR_CUSTOM_ASSEMBLER_ON_UPDATE: equ " + SCRIPT_CLEAR_CUSTOM_ASSEMBLER_ON_UPDATE + "\n";
        asm += "SCRIPT_CLEAR_CUSTOM_ASSEMBLER_ROOM_DRAW: equ " + SCRIPT_CLEAR_CUSTOM_ASSEMBLER_ROOM_DRAW + "\n";
        asm += "SCRIPT_CLEAR_TEXT_AREA: equ " + SCRIPT_CLEAR_TEXT_AREA + "\n";
        asm += "SCRIPT_CLEAR_ROOM_AREA: equ " + SCRIPT_CLEAR_ROOM_AREA + "\n";
        asm += "SCRIPT_GUI_OFF: equ " + SCRIPT_GUI_OFF + "\n";
        asm += "SCRIPT_GUI_ON: equ " + SCRIPT_GUI_ON + "\n";
        asm += "SCRIPT_SAVE_GAME: equ " + SCRIPT_SAVE_GAME + "\n";
        asm += "SCRIPT_LOAD_GAME: equ " + SCRIPT_LOAD_GAME + "\n";
        asm += "\n";
        asm += "FIRST_2BYTE_SCRIPT: equ " + FIRST_2BYTE_SCRIPT + "\n";
        asm += "FIRST_3BYTE_SCRIPT: equ " + FIRST_3BYTE_SCRIPT + "\n";
        asm += "FIRST_4BYTE_SCRIPT: equ " + FIRST_4BYTE_SCRIPT + "\n";
        asm += "FIRST_5BYTE_SCRIPT: equ " + FIRST_5BYTE_SCRIPT + "\n";
        asm += "FIRST_6BYTE_SCRIPT: equ " + FIRST_6BYTE_SCRIPT + "\n";
        asm += "LAST_4BYTE_SCRIPT: equ " + LAST_4BYTE_SCRIPT + "\n";
        
        return asm;
    }
}
