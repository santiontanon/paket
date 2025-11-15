/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import paket.pak.PAKGame;
import paket.pak.PAKObject;
import paket.pak.PAKRule;
import paket.pak.PAKRule.PAKScript;
import paket.pak.PAKRule.PAKTrigger;
import paket.platforms.Platform;
import paket.util.Pair;

/**
 *
 * @author santi
 */
public class PasswordGameSaves {
    public int bits_per_value = 6;
    public int checksum2_and_mask = 0x3f;
    public int player_x_bits = 8;
    public int player_y_bits = 7;
    public int n_bits_for_room = 8;  // overwritten later
    public int n_bits_per_inventory_item = 8;  // overwritten later
    public int n_bits_for_song = 8;  // overwritten later
    public int n_bits_for_tempo = 8;  // overwritten later
    public List<Integer> min_variable_value = new ArrayList<>();
    public List<Integer> bits_per_variable = new ArrayList<>();
    
    public int xorMask[] = {0x5a, 0x13, 0xf0, 0x46};

    PAKGame game;
    Platform platform;
    PAKETConfig config;
    HashMap<String, Integer> bitsForVariable = new HashMap<>();

    
    public PasswordGameSaves(PAKGame a_game, Platform a_platform, PAKETConfig a_config)
    {
        game = a_game;
        platform = a_platform;
        config = a_config;
    }

    
    public void init() throws Exception
    {
        if (game.saveGameModeParams.size() != 6) {
            throw new Exception("load-save-game-mode requires exactly 6 additional parameters when using 'password': save text, load text, accept, cancel, error message, and character set.");
        }
        
        int l = game.saveGameModeParams.get(5).length();
        if (l == 16) {
            bits_per_value = 4;
            checksum2_and_mask = 0x0f;
        } else if (l == 32) {
            bits_per_value = 5;
            checksum2_and_mask = 0x1f;
        } else if (l == 64) {
            bits_per_value = 6;
            checksum2_and_mask = 0x3f;
        } else {
            throw new Exception("The character set used for saving passwords, specified in 'load-save-game-mode' must be 16, 32 or 64 characters long, but it is: " + l);
        }
        
        
        game.additionalTextStrings.put("PASSWORD_SAVE_TEXT", game.saveGameModeParams.get(0));
        game.additionalTextStrings.put("PASSWORD_LOAD_TEXT", game.saveGameModeParams.get(1));
        game.additionalTextStrings.put("PASSWORD_ACCEPT_TEXT", game.saveGameModeParams.get(2));
        game.additionalTextStrings.put("PASSWORD_CANCEL_TEXT", game.saveGameModeParams.get(3));
        game.additionalTextStrings.put("PASSWORD_ERROR_TEXT", game.saveGameModeParams.get(4));
        game.additionalTextStrings.put("PASSWORD_CHARACTERSET", game.saveGameModeParams.get(5));
    }
    
    
    public void fillAssemblerVariables(
            HashMap<String, String> assemblerVariables) throws Exception
    {
        Pair<Integer, Integer> idx = game.textIDHash.get(game.saveGameModeParams.get(0));
        assemblerVariables.put("PASSWORD_SAVE_TEXT_BANK", "" + idx.m_a);
        assemblerVariables.put("PASSWORD_SAVE_TEXT_IDX", "" + idx.m_b);
        idx = game.textIDHash.get(game.saveGameModeParams.get(1));
        assemblerVariables.put("PASSWORD_LOAD_TEXT_BANK", "" + idx.m_a);
        assemblerVariables.put("PASSWORD_LOAD_TEXT_IDX", "" + idx.m_b);
        idx = game.textIDHash.get(game.saveGameModeParams.get(2));
        assemblerVariables.put("PASSWORD_LOAD_ACCEPT_TEXT_BANK", "" + idx.m_a);
        assemblerVariables.put("PASSWORD_LOAD_ACCEPT_TEXT_IDX", "" + idx.m_b);
        idx = game.textIDHash.get(game.saveGameModeParams.get(3));
        assemblerVariables.put("PASSWORD_LOAD_CANCEL_TEXT_BANK", "" + idx.m_a);
        assemblerVariables.put("PASSWORD_LOAD_CANCEL_TEXT_IDX", "" + idx.m_b);
        idx = game.textIDHash.get(game.saveGameModeParams.get(4));
        assemblerVariables.put("PASSWORD_LOAD_INCORRECT_TEXT_BANK", "" + idx.m_a);
        assemblerVariables.put("PASSWORD_LOAD_INCORRECT_TEXT_IDX", "" + idx.m_b);
        idx = game.textIDHash.get(game.saveGameModeParams.get(5));
        assemblerVariables.put("PASSWORD_CHARACTERS_BANK", "" + idx.m_a);
        assemblerVariables.put("PASSWORD_CHARACTERS_IDX", "" + idx.m_b);

        int sizeInBits = determineSizeInBits();
        int sizeInBytes = (sizeInBits + 7) / 8;
        int sizeInCharacters = 2 + (sizeInBits + bits_per_value - 1) / bits_per_value;  // We add 2 for the 2 checksums
        assemblerVariables.put("PASSWORD_SAVEGAME_SIZE_IN_BITS", "" + sizeInBits);
        assemblerVariables.put("PASSWORD_SAVEGAME_SIZE_IN_BYTES", "" + sizeInBytes);
        assemblerVariables.put("PASSWORD_SAVEGAME_SIZE_IN_CHARACTERS", "" + (sizeInCharacters));
        assemblerVariables.put("PASSWORD_BITS_PER_CHARACTER", "" + bits_per_value);
        assemblerVariables.put("CHECKSUM2_AND_MASK", "" + checksum2_and_mask);
        assemblerVariables.put("PASSWORD_ROOM_N_BITS", "" + n_bits_for_room);
        assemblerVariables.put("PASSWORD_PLAYER_X_N_BITS", "" + player_x_bits);
        assemblerVariables.put("PASSWORD_PLAYER_Y_N_BITS", "" + player_y_bits);
        assemblerVariables.put("PASSWORD_INVENTORY_ITEM_N_BITS", "" + n_bits_per_inventory_item);
        assemblerVariables.put("PASSWORD_SONG_N_BITS", "" + n_bits_for_song);
        assemblerVariables.put("PASSWORD_TEMPO_N_BITS", "" + n_bits_for_tempo);
        String variable_data_str = "";
        for(int i = 0;i<bits_per_variable.size();i++) {
            if (i != 0) {
                variable_data_str += ", ";
            }
            variable_data_str += "" + min_variable_value.get(i);
            variable_data_str += ", " + bits_per_variable.get(i);
        }
        assemblerVariables.put("PASSWORD_VARIABLE_DATA", variable_data_str);
        String characters = game.additionalTextStrings.get("PASSWORD_CHARACTERSET");
        assemblerVariables.put("PASSWORD_CHARACTER_TABLE", "\"" + characters + "\"");
    }
    
    
    public int determineSizeInBits() throws Exception
    {
        int n_bits = player_x_bits + player_y_bits;
        
        // Current room:
        n_bits_for_room = nBitsForValue(game.rooms.size());
        n_bits += n_bits_for_room;
        
        // inventory:
        int n_items = game.itemIDs.size();
        n_bits_per_inventory_item = nBitsForValue(n_items + 1);
        int n_items_bits = n_bits_per_inventory_item * platform.inventorySize();
        n_bits += n_items_bits;
        
        // game state variables:
        int n_bits_for_variables = 0;
        for(String variable:game.gameStateVariableNames) {
            if (game.gameStateVariableRanges.containsKey(variable)) {
                Pair<Integer, Integer> rangePair = game.gameStateVariableRanges.get(variable);
//                config.info("Variable '"+ variable + "' possible value range: " + rangePair.m_a + " - " + rangePair.m_b);
                int range = (rangePair.m_b - rangePair.m_a) + 1;
                if (range > 256) {
                    throw new Exception("Variable '" + variable + "' can take more than 256 possible values!");
                }
                bitsForVariable.put(variable, nBitsForValue(range));
                n_bits_for_variables += nBitsForValue(range);
                min_variable_value.add(rangePair.m_a);
                bits_per_variable.add(nBitsForValue(range));
            } else {
                int idx = game.gameStateVariableNames.indexOf(variable);
                List<Integer> possibleValues = new ArrayList<>();
                if (game.gameStateVariableInitialValues.containsKey(variable)) {
                    possibleValues.add(game.gameStateVariableInitialValues.get(variable));
                } else {
                    possibleValues.add(0);
                }
                // Iterate through all game scripts, trying to find variable possible values:
                for(PAKRule r:game.getAllScriptsAndRules()) {
                    if (r.triggers != null) {
                        for(PAKTrigger t:r.triggers) {
                            if (t.type == PAKRule.TRIGGER_VARIABLE_EQ &&
                                t.value1 == idx) {
                                if (!possibleValues.contains(t.value2)) {
                                    possibleValues.add(t.value2);
                                }
                            }
                        }
                    }
                    for(PAKScript s:r.scripts) {
                        if (s.type == PAKRule.SCRIPT_SET_VARIABLE &&
                            s.ID == idx) {
                            if (!possibleValues.contains(s.value)) {
                                possibleValues.add(s.value);
                            }
                        } else if ((s.type == PAKRule.SCRIPT_INC_VARIABLE ||
                                    s.type == PAKRule.SCRIPT_DEC_VARIABLE) &&
                                   s.ID == idx) {
                            throw new Exception("Variable '" + variable + "' needs its range defined with a 'variable-range' command.");
                        }
                    }
                }    
//                config.info("Variable '"+ variable + "' possible values: " + possibleValues);
                int min_value = possibleValues.get(0);
                int max_value = possibleValues.get(1);
                for(int v: possibleValues) {
                    if (v < min_value) min_value = v;
                    if (v > max_value) max_value = v;
                }
                int range = (max_value - min_value) + 1;
                if (range > 256) {
                    throw new Exception("Variable '" + variable + "' can take more than 256 possible values!");
                }
                bitsForVariable.put(variable, nBitsForValue(range));
                n_bits_for_variables += nBitsForValue(range);
                min_variable_value.add(min_value);
                bits_per_variable.add(nBitsForValue(range));
            }
        }
        n_bits += n_bits_for_variables;
        
        // Current song/tempo:
        int n_songs = game.songs.size();
        int max_tempo = 1;
        for(PAKRule r:game.getAllScriptsAndRules()) {
            for(PAKScript s:r.scripts) {
                if (s.type == PAKRule.SCRIPT_PLAY_TSV_SONG) {
                    if (s.songTempo > max_tempo) max_tempo = s.songTempo;
                }
            }
        }
        n_bits_for_song = nBitsForValue(n_songs + 1);
        n_bits_for_tempo = nBitsForValue(max_tempo);
        n_bits += n_bits_for_song + n_bits_for_tempo;
        
        config.info("PasswordGameSaves.determineSizeInBits: " + n_bits +
                    " (player position: " + (player_x_bits + player_y_bits) +
                    ", rooms: " + n_bits_for_room +
                    ", inventory: " + n_items_bits +
                    ", game state variables: " + n_bits_for_variables + 
                    ", song/tempo: " + (n_bits_for_song + n_bits_for_tempo) +
                    ")");
        return n_bits;
    }
    
    
    int nBitsForValue(int n_values)
    {
        int n_bits = 1;
        while(n_values > 2) {
            n_bits++;
            if ((n_values / 2) * 2 != n_values) {
                n_values = (n_values / 2) + 1;
            } else {
                n_values /= 2;                
            }
        }
        return n_bits;
    }
    
    public void testPasswordSystem() throws Exception
    {
        determineSizeInBits();  // This initializes a bunch of variables
                
        // Generate password for the initial state:
        int currentRoom = 0;
        int player_x = 0, player_y = 0;
        PAKObject player = game.rooms.get(currentRoom).getObject(63);
        if (player != null) {
            player_x = player.x;
            player_y = player.y;
        }
        int inventory[] = new int[platform.inventorySize()];
        for(int i = 0;i<inventory.length;i++) {
            inventory[i] = 0;
        }
        List<Pair<String, Integer>> variables = new ArrayList<>();
        for(String variable:game.gameStateVariableNames) {
            if (game.gameStateVariableInitialValues.containsKey(variable)) {
                variables.add(new Pair<>(variable, game.gameStateVariableInitialValues.get(variable)));
            } else {
                variables.add(new Pair<>(variable, 0));
            }
        }
        String password = generatePasswordForState(player_x, player_y, currentRoom, inventory, variables, 0, 0);
        config.info("Initial state password: " + password);
    }
    
    
    void addValueToBits(int value, int nBits, List<Integer> bits) throws Exception {
        if (value < 0) throw new Exception("Values for savegame cannot be negative!");
        int insertPosition = bits.size();
        for(int i = 0;i<nBits;i++) {
            bits.add(insertPosition, value % 2);
            value /= 2;
        }
    }
    
    
    public List<Integer> applyXorMask(List<Integer> bits) throws Exception
    {
        List<Integer> newBits = new ArrayList<>();
        int xorMaskIndex = 0;
        int value = 0;
        int bits_in_this_value = 0;
        for(int i = 0;i<bits.size();i++) {
            value *= 2;
            value += bits.get(i);
            bits_in_this_value++;
            if (bits_in_this_value >= 8 || i == bits.size() - 1) {
                // The tail might not have a full byte:
                int n_bit_to_shift_back = 0;
                while(bits_in_this_value < 8) {
                    value *= 2;
                    bits_in_this_value++;
                    n_bit_to_shift_back++;
                }
                value = value ^ xorMask[xorMaskIndex % xorMask.length];
                xorMaskIndex++;
                
                while(n_bit_to_shift_back > 0) {
                    value /= 2;
                    bits_in_this_value--;
                    n_bit_to_shift_back--;
                }
                                
                addValueToBits(value, bits_in_this_value, newBits);
                value = 0;
                bits_in_this_value = 0;
            }
        }
        return newBits;
    }
    
    
    public Pair<Integer, Integer> computeChecksums(List<Integer> bits)  throws Exception
    {
        // Add checksum:
        // - checksum1: and "number of 1s & checksum2_and_mask"
        // - checksum2: "sum & checksum2_and_mask", 
        int checksum1 = 0;
        int checksum2 = 0;
        {
            int value = 0;
            int bits_in_this_value = 0;
            for(int i = 0;i<bits.size();i++) {
                value *= 2;
                value += bits.get(i);
                bits_in_this_value++;
                if (bits_in_this_value >= bits_per_value || i == bits.size() - 1) {
                   checksum2 += value;
                   value = 0;
                   bits_in_this_value = 0;
                }
                if (bits.get(i) == 1) checksum1 ++;
            }
            checksum1 = checksum1 & checksum2_and_mask;
            checksum2 = checksum2 & checksum2_and_mask;
//            addValueToBits(checksum2, bits_per_value, bits);
//            // Checksum1 is prepended:
//            List<Integer> prefixBits = new ArrayList<>();
//            addValueToBits(checksum1, bits_per_value, prefixBits);
//            bits.addAll(0, prefixBits);
            config.info("computeChecksums: checksum: " + checksum1 + ", " + checksum2);
        }
        return new Pair<>(checksum1, checksum2);
    }

    
    String encodeBitListIntoPassword(List<Integer> bits) throws Exception
    {
        String characters = game.additionalTextStrings.get("PASSWORD_CHARACTERSET");
        String password = "";

        bits = applyXorMask(bits);
        config.info("encodeBitListIntoPassword: bits with xor mask: " + bits);

        Pair<Integer, Integer> checksums = computeChecksums(bits);
        int checksum1 = checksums.m_a;
        int checksum2 = checksums.m_b;

        password += characters.charAt(checksum1 & checksum2_and_mask);
        int value = 0;
        int bits_in_this_value = 0;
        for(int i = 0;i<bits.size();i++) {
            value *= 2;
            value += bits.get(i);
            bits_in_this_value++;
            if (bits_in_this_value >= bits_per_value || i == bits.size() - 1) {
                password += characters.charAt((value + checksum1) & checksum2_and_mask);
                value = 0;
                bits_in_this_value = 0;
            }
        }
        password += characters.charAt(checksum2 & checksum2_and_mask);
        return password;
    }

    
    String generatePasswordForState(
            int player_x, int player_y,
            int currentRoom,
            int inventory[],
            List<Pair<String, Integer>> variables,
            int currentSong, int currentTempo) throws Exception
    {
        List<Integer> bits = new ArrayList<>();
        
        // Add all the bits:
        addValueToBits(currentRoom, nBitsForValue(game.rooms.size()), bits);
        for(int ID:inventory) {
            addValueToBits(ID, nBitsForValue(game.itemIDs.size() + 1), bits);
        }
        for(Pair<String, Integer> variable:variables) {
            addValueToBits(variable.m_b, bitsForVariable.get(variable.m_a), bits);
        }
        addValueToBits(player_x, player_x_bits, bits);
        addValueToBits(player_y, player_y_bits, bits);
        addValueToBits(currentSong, n_bits_for_song, bits);
        addValueToBits(currentTempo, n_bits_for_tempo, bits);
        
        config.info("generatePasswordForState: bits: " + bits);
        
        return encodeBitListIntoPassword(bits);
    }
    
}
