/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author santi
 * 
 * Tile layers of rooms can only specify up to 128 different tiles (msb is 
 * reserved as the "mirrored" flag). But it is likely that large games have more
 * than 128 tiles. So, we divide tiles into "Tile bank sets", and each room
 * specify which tile bank set it uses. It is possible that tiles are replicated
 * among tile bank sets, but this class attempts to create tile bank sets by
 * creating as few of them as possible, and minimizing tile replication.
 * 
 */
public class TileBankSetAllocator {
    
    /*
        - roomTiles: a list of lists. Each list of integers, corresponds to one
            room, and contains the tile indexes used in that room.
        - tilesPerBank: how many tiles go in each bank.
    */
    public List<TileBankSet> distributeTilesInBanks(
            List<List<Integer>> roomTiles, int tilesPerBank) {
        List<TileBankSet> banks = new ArrayList<>();

        // Generate an initial naive groupping:
        for(int room_idx = 0;room_idx<roomTiles.size();room_idx++) {
            List<Integer> room = roomTiles.get(room_idx);
            TileBankSet bestBank = null;
            int best_newTiles = 0;
            for(TileBankSet bank:banks) {
                int n_newTiles = newTiles(bank.tiles, room);
                if ((bestBank == null || n_newTiles < best_newTiles) &&
                     bank.tiles.size() + n_newTiles <= tilesPerBank) {
                    bestBank = bank;
                    best_newTiles = n_newTiles;
                }
            }
            if (bestBank == null) {
                bestBank = new TileBankSet();
                banks.add(bestBank);
            }
            bestBank.rooms.add(room_idx);
            for(Integer tile:room) {
                if (!bestBank.tiles.contains(tile)) {
                    bestBank.tiles.add(tile);
                }
            }
        }
        
        // Optimize assignments:
        while(true) {
            List<TileBankSet> newBanks = null;
            for(int bank_idx1 = 0;bank_idx1<banks.size();bank_idx1++) {
                for(int bank_idx2 = 0;bank_idx2<banks.size();bank_idx2++) {
                    if (bank_idx1 == bank_idx2) continue;
                    TileBankSet bank1 = banks.get(bank_idx1);
                    TileBankSet bank2 = banks.get(bank_idx2);
                    for(Integer room1: bank1.rooms) {
                        // Try to move it to room2:
                        newBanks = attemptSwap(room1, -1, bank_idx1, bank_idx2, banks, roomTiles, tilesPerBank);
                        if (newBanks == null) {
                            for(Integer room2: bank2.rooms) {
                                newBanks = attemptSwap(room1, room2, bank_idx1, bank_idx2, banks, roomTiles, tilesPerBank);
                                if (newBanks != null) break;
                            }
                        }
                        if (newBanks != null) break;
                    }
                    if (newBanks != null) break;
                }
                if (newBanks != null) break;
            }
            if (newBanks != null) {
                banks = newBanks;
            } else {
                break;
            }
        }
        
        return banks;
    }
    
    
    int newTiles(List<Integer> bank, List<Integer> room)
    {
        int n = 0;
        for(Integer tile:room) {
            if (!bank.contains(tile)) {
                n += 1;
            }
        }
        return n;
    }
    
    
    List<TileBankSet> reconstructBanksFromRoomAssignment(List<List<Integer>> roomAssignment,
                                                         List<List<Integer>> roomTiles)
    {
        List<TileBankSet> banks = new ArrayList<>();
        
        for(List<Integer> bankRooms: roomAssignment) {
            TileBankSet bank = new TileBankSet();
            bank.rooms.addAll(bankRooms);
            for(Integer room:bankRooms) {
                for(Integer tile:roomTiles.get(room)) {
                    if (!bank.tiles.contains(tile)) {
                        bank.tiles.add(tile);
                    }
                }
            }
            banks.add(bank);
        }
        return banks;
    }
    
    
    int banksSize(List<TileBankSet> banks)
    {
        int size = 0;
        for(TileBankSet bank:banks) {
            size += bank.tiles.size();
        }
        return size;
    }
    
    
    boolean respectsBounds(List<TileBankSet> banks, int tilesPerBank)
    {
        for(TileBankSet bank:banks) {
            if (bank.tiles.size() > tilesPerBank) {
                return false;
            }
        }
        return true;
    }
    
    
    List<TileBankSet> attemptSwap(int room1, int room2, int bank1_idx, int bank2_idx,
                                                         List<TileBankSet> banks,
                                                         List<List<Integer>> roomTiles, int tilesPerBank)
    {
        List<List<Integer>> roomAssignment = new ArrayList<>();
        for(TileBankSet bank:banks) {
            roomAssignment.add(new ArrayList<>(bank.rooms));
        }
        roomAssignment.get(bank1_idx).remove((Integer)room1);
        roomAssignment.get(bank2_idx).add(room1);
        if (room2 >= 0) {
            roomAssignment.get(bank1_idx).add(room2);
            roomAssignment.get(bank2_idx).remove((Integer)room2);
        }
        
        List<TileBankSet> newBanks = reconstructBanksFromRoomAssignment(roomAssignment, roomTiles);
        if (banksSize(newBanks) < banksSize(banks) &&
            respectsBounds(newBanks, tilesPerBank)) {
//            System.out.println("new best: " + banksSize(banks) + " -> " + banksSize(newBanks));
            return newBanks;
        }
        return null;
    }
    
    
    /*
    // Test case: should optimize to use only 20 tiles total
    public static void main(String args[]) throws Exception {
        TileBankDistributor d = new TileBankDistributor();
        List<List<Integer>> roomTiles = new ArrayList<>();
        roomTiles.add(Arrays.asList(new Integer[]{0,1,2,3,4,5,6,7}));
        roomTiles.add(Arrays.asList(new Integer[]{0,11,12,13,14,15,16,17}));
        roomTiles.add(Arrays.asList(new Integer[]{0,1,2,3,8,9,10,11}));
        List<Pair<List<Integer>, List<Integer>>> banks = d.distributeTilesInBanks(roomTiles, 16);
        int totalTiles = 0;
        for(Pair<List<Integer>, List<Integer>> bank:banks) {
            totalTiles += bank.m_a.size();
            System.out.println("rooms: " + bank.m_b + ", # tiles: " + bank.m_a.size() + " (" + bank.m_a + ")");
        }
        System.out.println("total size: " + totalTiles);
    }
    */
}
