/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.music;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import paket.compiler.PAKETConfig;
import paket.pak.PAKGame;
import paket.util.Pair;

/**
 *
 * @author santi
 */
public class TSVSongSplitOptimizer {
    public static class SongSplit {
        TSVSong song;
        int start, end;
        int uncompressedSize;
        int compressedSize;
        int checksum;
        
        public SongSplit(TSVSong a_song, 
                         int a_start, int a_end,
                         int a_uncompressedSize, int a_compressedSize,
                         int a_checksum) {
            song = a_song;
            start = a_start;
            end = a_end;
            uncompressedSize = a_uncompressedSize;
            compressedSize = a_compressedSize;
            checksum = a_checksum;
        }
    }
    
    
    public List<Integer> notesUsed;
    public String destinationFolder;
    public TSVMusicParser parser;
    public PAKGame game;
    public PAKETConfig config;
   
    
    public TSVSongSplitOptimizer(
            List<Integer> a_notesUsed,
            String a_destinationFolder, TSVMusicParser a_parser, PAKGame a_game,
            PAKETConfig a_config) {
        notesUsed = a_notesUsed;
        destinationFolder = a_destinationFolder;
        parser = a_parser;
        game = a_game;
        config = a_config;
    }

    
    public List<TSVSong> optimallySplitSongs(List<TSVSong> songs) throws Exception
    {
        List<TSVSong> toOptimize = new ArrayList<>();
        List<TSVSong> alreadyOptimized = new ArrayList<>();
        
        // Only try to optimize those songs that do not have subsongs:
        for(TSVSong s:songs) {
            if (s.subSongsPlayed.isEmpty()) {
                toOptimize.add(s);
            } else {
                alreadyOptimized.add(s);
            }
        }
        
        config.info("SongSplitOptimizer: " + toOptimize.size() + " optimizable songs, and " + alreadyOptimized.size() + " non optimizable songs.");
        
        // Calculate the music buffer size we would have with the songs we
        // cannot optimize:
        int max_buffer_size = 0;
        for(TSVSong s:alreadyOptimized) {
            Pair<Integer, Integer> sizes = parser.compileAssemblerSong(s, destinationFolder + "/src/data/songs/PKT_tmp", notesUsed, game, config);
            if (max_buffer_size < sizes.m_a) {
                max_buffer_size = sizes.m_a;
            }
        }
        config.info("SongSplitOptimizer: max_buffer_size for non optimizable songs: " + max_buffer_size);
        
        // Optimize song by song individually (this is greedy, as we could
        // optimize all songs at the same time globally, but it would be much
        // slower).
        // At least we sort them and optimize the largest first:
        List<SongSplit> toOptimizeWithSizes = new ArrayList<>();
        for(TSVSong s:toOptimize) {
            Pair<Integer, Integer> sizes = parser.compileAssemblerSong(s, destinationFolder + "/src/data/songs/PKT_tmp", notesUsed, game, config);
            toOptimizeWithSizes.add(new SongSplit(s, 0, s.getDuration(), sizes.m_a, sizes.m_b, getSong16bitChecksum(destinationFolder + "/src/data/songs/PKT_tmp.bin")));
        }
        Collections.sort(toOptimizeWithSizes, (o1, o2) -> {
            return -Integer.compare(o1.uncompressedSize, o2.uncompressedSize);
        });
        
        List<TSVSong> fromPreviousState = applyPreviousOptimizations(max_buffer_size, toOptimizeWithSizes);
        if (fromPreviousState != null) return fromPreviousState;
        
        int initial_max_buffer_size = max_buffer_size;
        List<Pair<SongSplit, List<SongSplit>>> optimizationResult = new ArrayList<>();
        for(SongSplit sws:toOptimizeWithSizes) {
            TSVSong s = sws.song;
            if (s.halveTempoIfPossible()) {
                config.info("SongSplitOptimizer: tempo of song " + s.fileName + " halved!");
            } else {
                config.info("SongSplitOptimizer: tempo of song " + s.fileName + " could not be halved!");
            }

            int duration = s.getDuration();
            config.info("SongSplitOptimizer: song duration " + duration);
                                                
            // Iterative strategy for 1 split:
            String fileNameWithoutExtension = s.fileName.substring(0, s.fileName.length()-4);
            
            List<SongSplit> splits = optimizeSplittingRecursively(
                    s, fileNameWithoutExtension,
                    fileNameWithoutExtension + ".tsv",
                    0, duration, duration,
                    max_buffer_size);
            config.info("Best split had " + splits.size() + " parts.");
            for(SongSplit ss:splits) {
                alreadyOptimized.add(ss.song);
                max_buffer_size = Math.max(max_buffer_size, ss.uncompressedSize);
            }
            optimizationResult.add(new Pair<>(sws, splits));
        }
        
        // Save optimizer state:
        // - starting max_buffer_size
        // - songs to optimize: names, duration, checksum, resulting splits
        saveOptimizerState(initial_max_buffer_size, optimizationResult);
        
        return alreadyOptimized;
    }
    
    
    public void saveOptimizerState(int initial_max_buffer_size,
                                   List<Pair<SongSplit, List<SongSplit>>> optimizationResult) throws Exception
    {
        String fileName = destinationFolder + "/PAKETOptimizerState-songs.txt";
        FileWriter fw = new FileWriter(fileName);
        fw.write(""+initial_max_buffer_size+"\n");
        fw.write(""+optimizationResult.size() + "\n");
        for(Pair<SongSplit, List<SongSplit>> p:optimizationResult) {
            SongSplit s = p.m_a;
            List<SongSplit> result = p.m_b;
            fw.write(s.song.fileName + "\n");
            fw.write(s.end + "\t" + s.checksum + "\t" + result.size());
            for(SongSplit ss:result) {
                fw.write("\t" + ss.start + ":" + ss.end);
            }
            fw.write("\n");
        }
        fw.flush();
        fw.close();        
    }
    
    
    public List<TSVSong> applyPreviousOptimizations(
            int initial_max_buffer_size,
            List<SongSplit> toOptimizeWithSizes) throws Exception
    {
        // Load optimizer state and see if it matches:
        String fileName = destinationFolder + "/PAKETOptimizerState-songs.txt";
        File f = new File(fileName);
        if (!f.exists()) return null;
        BufferedReader br = new BufferedReader(new FileReader(fileName));
        String line = br.readLine();
        int previous_initial_max_buffer_size = Integer.parseInt(line.strip());
        if (previous_initial_max_buffer_size != initial_max_buffer_size) return null;
        line = br.readLine();
        int previous_n_songs = Integer.parseInt(line.strip());
        if (previous_n_songs != toOptimizeWithSizes.size()) return null;
        List<List<Pair<Integer, Integer>>> previousSplits = new ArrayList<>();
        for(SongSplit ss:toOptimizeWithSizes) {
            line = br.readLine().strip();
            if (!ss.song.fileName.equals(line)) return null;
            line = br.readLine().strip();
            String cells[] = line.split("\t");
            if (Integer.parseInt(cells[0]) != ss.end) return null;
            if (Integer.parseInt(cells[1]) != ss.checksum) return null;
            int nSplits = Integer.parseInt(cells[2]);
            List<Pair<Integer, Integer>> splits = new ArrayList<>();
            for(int i = 0;i<nSplits;i++) {
                String subcells[] = cells[3+i].split(":");
                splits.add(new Pair<>(Integer.parseInt(subcells[0]), Integer.parseInt(subcells[1])));
            }
            previousSplits.add(splits);
        }
        
        // Apply previous optimizer state:
        List<TSVSong> optimized = new ArrayList<>();
        for(int i = 0;i<toOptimizeWithSizes.size();i++) {
            SongSplit ss = toOptimizeWithSizes.get(i);
            TSVSong s = ss.song;
            if (s.halveTempoIfPossible()) {
                config.info("SongSplitOptimizer: tempo of song " + s.fileName + " halved!");
                ss.end /= 2;
            } else {
                config.info("SongSplitOptimizer: tempo of song " + s.fileName + " could not be halved!");
            }            
            List<Pair<Integer, Integer>> splits = previousSplits.get(i);
            String fileNameWithoutExtension = s.fileName.substring(0, s.fileName.length()-4);
            
            for(Pair<Integer, Integer> split:splits) {
                TSVSong piece;
                if (split.m_b == ss.end) {
                    piece = s.generateSubSong(split.m_a, split.m_b, fileNameWithoutExtension + ".tsv", 0);
                    piece.fileName = (split.m_a == 0 ? fileNameWithoutExtension + ".tsv" : fileNameWithoutExtension + "-"+split.m_a+".tsv");
                } else {
                    piece = s.generateSubSong(split.m_a, split.m_b, fileNameWithoutExtension + "-"+split.m_b+".tsv", 0);
                    piece.fileName = (split.m_a == 0 ? fileNameWithoutExtension + ".tsv" : fileNameWithoutExtension + "-"+split.m_a+".tsv");                
                }
                if (piece == null) {
                    throw new Exception("Cannot split song " + s.fileName + " from " + split.m_a + " to " + split.m_b);
                }
                optimized.add(piece);
            }
        }
        return optimized;
    }
    
    
    public Pair<SongSplit, SongSplit> bestBinarySplit(TSVSong s, String fileNameWithoutExtension, String nextPartName, int start, int end, int duration, int max_buffer_size) throws Exception
    {
        config.info("SongSplitOptimizer.bestBinarySplit: from " + start + " to " + end + " (max_buffer_size = " + max_buffer_size + ")");
        // Default best is not to split:
        int best_split = 0;
        int best = 0;
        String best_split_msg = null;
        Pair<SongSplit, SongSplit> best_parts = new Pair<>(null, null);
        
        for(int split = start + 1;split<end-1;split++) {
            String part1name = (start == 0 ? fileNameWithoutExtension + ".tsv" : fileNameWithoutExtension + "-"+start+".tsv");
            TSVSong part1 = s.generateSubSong(start, split, fileNameWithoutExtension + "-"+split+".tsv", 0);
            TSVSong part2 = s.generateSubSong(split, end, nextPartName, 0);
            if (part1 == null || part2 == null) {
                // Cannot split the song here
                continue;
            }
            part1.fileName = part1name;
            part2.fileName = fileNameWithoutExtension + "-"+split+".tsv";
//                config.info("SongSplitOptimizer: split at " + split);
            Pair<Integer, Integer> sizes1 = parser.compileAssemblerSong(part1, destinationFolder + "/src/data/songs/PKT_tmp", notesUsed, game, config);
            int checksum1 = getSong16bitChecksum(destinationFolder + "/src/data/songs/PKT_tmp.bin");
            Pair<Integer, Integer> sizes2 = parser.compileAssemblerSong(part2, destinationFolder + "/src/data/songs/PKT_tmp", notesUsed, game, config);
            int checksum2 = getSong16bitChecksum(destinationFolder + "/src/data/songs/PKT_tmp.bin");
            int compressedSize = sizes1.m_b + sizes2.m_b;
            int largestBufferSize = Math.max(sizes1.m_a, sizes2.m_a);
            int extraBufferSize = (max_buffer_size >= largestBufferSize ? 0 : largestBufferSize - max_buffer_size);
            if (best_split == 0 || best > compressedSize + extraBufferSize) {
                best_parts.m_a = new SongSplit(part1, start, split, sizes1.m_a, sizes1.m_b, checksum1);
                best_parts.m_b = new SongSplit(part2, split, end, sizes2.m_a, sizes2.m_b, checksum2);
                best = compressedSize + extraBufferSize;
                best_split = split;
                best_split_msg = "SongSplitOptimizer: best split [" + start + ", " + best_split + ", " + end + "] -> " + best + " (" + largestBufferSize + ", " + sizes1.m_b + ", " + sizes2.m_b + ")";
            }
        }
        if (best_split_msg != null) {
            config.info(best_split_msg);
        }
        if (best_parts.m_a == null) return null;
        return best_parts;
    }
    
    
    public List<SongSplit> optimizeSplittingRecursively(TSVSong s, String fileNameWithoutExtension, String nextPartName, int start, int end, int duration,
            int max_buffer_size) throws Exception
    {
        SongSplit noSplitSong;
        if (start == 0 && end == duration) {
            Pair<Integer, Integer> sizes = parser.compileAssemblerSong(s, destinationFolder + "/src/data/songs/PKT_tmp", notesUsed, game, config);
            noSplitSong = new SongSplit(s, start, end, sizes.m_a, sizes.m_b, getSong16bitChecksum(destinationFolder + "/src/data/songs/PKT_tmp.bin"));
        } else {
            TSVSong tmp = s.generateSubSong(start, end, fileNameWithoutExtension + "-"+end+".tsv", 0);
            tmp.fileName = (start == 0 ? fileNameWithoutExtension + ".tsv" : fileNameWithoutExtension + "-"+start+".tsv");
            Pair<Integer, Integer> sizes = parser.compileAssemblerSong(tmp, destinationFolder + "/src/data/songs/PKT_tmp", notesUsed, game, config);
            noSplitSong = new SongSplit(s, start, end, sizes.m_a, sizes.m_b, getSong16bitChecksum(destinationFolder + "/src/data/songs/PKT_tmp.bin"));
        }        
        Pair<SongSplit, SongSplit> bestSplit = bestBinarySplit(s, fileNameWithoutExtension, nextPartName, start, end, duration, max_buffer_size);
        if (bestSplit == null) {
            // We could not split the song, we are done!
            List<SongSplit> best_split = new ArrayList<>();
            best_split.add(noSplitSong);
            return best_split;
        }
        int best_split_point = bestSplit.m_a.end;

        Pair<SongSplit, SongSplit> bestSplit1 = bestBinarySplit(s, fileNameWithoutExtension, fileNameWithoutExtension + "-"+best_split_point+".tsv", start, best_split_point, duration, max_buffer_size);
        Pair<SongSplit, SongSplit> bestSplit2 = bestBinarySplit(s, fileNameWithoutExtension, nextPartName, best_split_point, end, duration, max_buffer_size);
        
        // Choose the best between:
        // - noSplitSong
        // - bestSplit
        // - bestSplit1 + bestSplit.m_b
        // - bestSplit.m_a + bestSplit2
        // - bestSplit1 + bestSplit2
        
        int best = spaceUsed(new SongSplit[]{noSplitSong}, max_buffer_size);
        List<SongSplit> best_parts = new ArrayList<>();
        best_parts.add(noSplitSong);
        int bestSplitSpace = spaceUsed(new SongSplit[]{bestSplit.m_a, bestSplit.m_b}, max_buffer_size);
        if (bestSplitSpace < best) {
            best_parts.clear();
            best_parts.add(bestSplit.m_a);
            best_parts.add(bestSplit.m_b);
            best = bestSplitSpace;
        }
        int bestSplit1Space = spaceUsed(new SongSplit[]{bestSplit1.m_a, bestSplit1.m_b, bestSplit.m_b}, max_buffer_size);
        if (bestSplit1Space < best) {
            best_parts.clear();
            best_parts.add(bestSplit1.m_a);
            best_parts.add(bestSplit1.m_b);
            best_parts.add(bestSplit.m_b);
            best = bestSplit1Space;
        }
        int bestSplit2Space = spaceUsed(new SongSplit[]{bestSplit.m_a, bestSplit2.m_a, bestSplit2.m_b}, max_buffer_size);
        if (bestSplit1Space < best) {
            best_parts.clear();
            best_parts.add(bestSplit.m_a);
            best_parts.add(bestSplit2.m_a);
            best_parts.add(bestSplit2.m_b);
            best = bestSplit2Space;
        }
        int bestSplit12Space = spaceUsed(new SongSplit[]{bestSplit1.m_a, bestSplit1.m_b, bestSplit2.m_a, bestSplit2.m_b}, max_buffer_size);        
        if (bestSplit12Space < best) {
            best_parts.clear();
            best_parts.add(bestSplit1.m_a);
            best_parts.add(bestSplit1.m_b);
            best_parts.add(bestSplit2.m_a);
            best_parts.add(bestSplit2.m_b);
            // best = bestSplit12Space;
        }

        // Keep dividing recursively:
        // ...

        return best_parts;
    }   
    
    
    public int spaceUsed(SongSplit splits[], int max_buffer_size)
    {
        int size = 0;
        int max_uncompressed_size = 0;
        for(SongSplit s:splits) {
            size += s.compressedSize;
            max_uncompressed_size = Math.max(max_uncompressed_size, s.uncompressedSize);
        }
        if (max_buffer_size < max_uncompressed_size) {
            size += max_uncompressed_size - max_buffer_size;
        }
        return size;
    }
    
    
    public int getSong16bitChecksum(String path) throws Exception {
        File f = new File(path);
        int binarySize = (int)f.length();
        
        InputStream is = new FileInputStream(path);
        final byte[] buffer = new byte[binarySize];
        is.read(buffer);
        
        int checksum = 0;
        for(byte b:buffer) {
            checksum = (checksum + b)%65536;
        }
        return checksum;
    }
}
