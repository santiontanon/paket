/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package paket.music;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import paket.compiler.PAKETConfig;
import paket.pak.PAKGame;
import paket.pak.PAKSong;

/**
 *
 * @author santi
 */
public class TSVSong {
    public static final int N_CHANNELS = 3;
    
    public String fileName = null;
    public List<TSVNote> channels[];
    public int loopBackTime = -1;
    public List<String> subSongsPlayed = new ArrayList<>();
    
    
    @SuppressWarnings("unchecked")
    public TSVSong() {
        channels = new List[N_CHANNELS];
        for(int i = 0;i<N_CHANNELS;i++) {
            channels[i] = new ArrayList<>();
        }
    }


    @SuppressWarnings("unchecked")
    public TSVSong(TSVSong s) {
        channels = new List[N_CHANNELS];
        for(int i = 0;i<N_CHANNELS;i++) {
            channels[i] = new ArrayList<>();
            for(TSVNote n:s.channels[i]) {
                channels[i].add(new TSVNote(n));
            }
        }
    }
    
    
    public void addNote(TSVNote note, int channel) {
        channels[channel].add(note);
    }
    
    
    public int getDuration() {
        int duration = 0;
        for(int c = 0;c<channels.length;c++) {
            duration = Math.max(duration, channelLength(c));
        }
        return duration;
    }
    
    
    public int channelLength(int channel) {
        int l = 0;
        for(TSVNote n:channels[channel]) {
            l+=n.duration;
        }
        return l;
    }
    
    
    public int getNextIndex(int channel) {
        return channels[channel].size();
    }
    
    
    public void transpose(int shift) {
        for(int i = 0;i<3;i++) {
            for(TSVNote n:channels[i]) {
                if (n.absoluteNote >= 0) n.absoluteNote+=shift;
            }
        }
    }
    
    
    public void convertToAssembler(String songName, List<Integer> notesUsed, PrintStream w, TSVMusicParser parser, PAKGame game, PAKETConfig config) throws Exception
    {
        convertToAssembler(songName, notesUsed, "  include \"../constants.asm\"", w, parser, game, config);
    }
    

    public void convertToAssembler(String songName, List<Integer> notesUsed, String constantsInclude, PrintStream w, TSVMusicParser parser, PAKGame game, PAKETConfig config) throws Exception
    {
        String instrumentName[] = new String[N_CHANNELS];
        int instrumentSpeed[] = new int[N_CHANNELS];
        int index[] = new int[N_CHANNELS];
        int channelTime[] = new int[N_CHANNELS];
        int currentTime = 0;
        boolean loopBackPrinted = false;
        List<String> lines = new ArrayList<>();
        
        for(int i = 0;i<N_CHANNELS;i++) {
            instrumentName[i] = TSVMusicParser.SQUARE_WAVE;
            index[i] = 0;
            channelTime[i] = 0;
            instrumentSpeed[i] = 0;
        }
        currentTime = 0;
        
        lines.add(constantsInclude);
        lines.add("  org #0000");
        lines.add(songName + ":");
        lines.add("  db 7,184");   // set all three channels to wave
        
        boolean repeatStart = false;
        while(true) {
            boolean done = true;
            boolean advanceTime = true;
            if (currentTime == loopBackTime && !loopBackPrinted) {
                lines.add(songName + "_loop:");
                loopBackPrinted = true;
            }
            for(int i = 0;i<N_CHANNELS;i++) {
                if (index[i]<channels[i].size()) {
                    done = false;
                    if (currentTime >= channelTime[i]) {
                        // channel note:
                        TSVNote note = channels[i].get(index[i]);
                        index[i]++;
                        
                        if (note.absoluteNote == TSVNote.NOTHING) {
                            // do nothing
                            channelTime[i] += note.duration;
                        } else if (note.absoluteNote==-1) {
                            // silence:
                            if (!instrumentName[i].equals(TSVMusicParser.SQUARE_WAVE)) {
                                lines.add("  db MUSIC_CMD_SET_INSTRUMENT, MUSIC_INSTRUMENT_SQUARE_WAVE, " + i);
                                instrumentName[i] = TSVMusicParser.SQUARE_WAVE;
                            }
                            lines.add("  db " + (8+i) + ", 0");  // zero out the volume of that channel
                            channelTime[i] += note.duration;
                        } else if (note.absoluteNote == TSVNote.SFX) {
                            if (parser.sfxs.containsKey(note.sfx)) {
                                lines.add("  db MUSIC_CMD_PLAY_SFX_" + note.sfx.toUpperCase());
                            } else {
                                throw new Exception("Undefined SFX!: " + note.sfx);
                            }
                            channelTime[i] += note.duration;
                        } else if (note.absoluteNote == TSVNote.START_REPEAT) {
                            lines.add("  db MUSIC_CMD_REPEAT, " + note.volume);
                            advanceTime = false;
                            break;
                        } else if (note.absoluteNote == TSVNote.END_REPEAT) {
                            lines.add("  db MUSIC_CMD_END_REPEAT");
                            advanceTime = false;
                            repeatStart = true;
                            break;
                        } else if (note.absoluteNote == TSVNote.CLEAR_TRANSPOSE) {
                            throw new Exception("Transpose commands not yet supported!");
//                            lines.add("  db MUSIC_CMD_CLEAR_TRANSPOSE");
//                            advanceTime = false;
                        } else if (note.absoluteNote == TSVNote.TRANSPOSE_UP) {
                            throw new Exception("Transpose commands not yet supported!");
//                            lines.add("  db MUSIC_CMD_TRANSPOSE_UP");
//                            advanceTime = false;
                        } else if (note.absoluteNote == TSVNote.PLAY_SONG) {
                            PAKSong song = game.getOrCreateSong(note.sfx, PAKSong.TYPE_TSV);
                            int offset = game.songs.indexOf(song);
                            lines.add("  db MUSIC_CMD_PLAY_SONG, " + note.duration +", " + offset * 2);
                        } else if (note.absoluteNote == TSVNote.SET_VOLUME) {
                            lines.add("  db MUSIC_CMD_SET_VOLUME" + (i+1) + ", " + (15 - note.volume));
                            advanceTime = false;
                        } else {
                            // We also include the instruments in repeat starts, just in case:
                            if (!instrumentName[i].equals(note.instrumentName) || repeatStart) {
                                if (!parser.instruments.containsKey(note.instrumentName)) {
                                    throw new Exception("Undefined instrument " + note.instrumentName);
                                }
                                lines.add("  db MUSIC_CMD_SET_INSTRUMENT, MUSIC_INSTRUMENT_" + note.instrumentName.toUpperCase() + ", " + i);
                                instrumentName[i] = note.instrumentName;
                                // change speed if necessary:
                                TSVInstrument instrument = parser.instruments.get(note.instrumentName);
                                if (instrument.speed != instrumentSpeed[i]) {
                                    lines.add("  db MUSIC_CMD_INSTRUMENT_SPEED" + (i+1) +", " + (instrument.speed + 1));
                                    instrumentSpeed[i] = instrument.speed;
                                }
                            }  
//                            int period = PSGNote.PSGNotePeriod(note.absoluteNote); 
//                            lines.add("  db MUSIC_CMD_PLAY_INSTRUMENT_CH" + (i+1) + ", " + (period/256) + ", " + (period%256));
                            lines.add("  db MUSIC_CMD_PLAY_INSTRUMENT_CH" + (i+1) + ", " + notesUsed.indexOf(note.absoluteNote));
                            channelTime[i] += note.duration;
                            if (note.duration == 0) {
                                advanceTime = false;
                            }
                        }
                    }
                } else {
                    if (currentTime < channelTime[i]) done = false;
                }
            }
            if (done) break;
            if (advanceTime) {
                lines.add("  db MUSIC_CMD_SKIP");
                currentTime++;
                repeatStart = false;
            }
        }
        if (loopBackTime==-1) {
            lines.add("  db MUSIC_CMD_END");
        } else {
            lines.add("  db MUSIC_CMD_GOTO");
            lines.add("  dw (" + songName + "_loop - " + songName + ")");
        }
        
        // optimize lines:
        boolean done = false;
        for(int i = 0;i<lines.size();i++) {
            if (lines.get(i).equals("  db MUSIC_CMD_SKIP")) {
                String previous = lines.get(i - 1);
                if (previous.startsWith("  db MUSIC_CMD_PLAY_SFX_") && 
                    !previous.contains("MUSIC_CMD_TIME_STEP_FLAG")) {
                    lines.set(i - 1, previous + " + MUSIC_CMD_TIME_STEP_FLAG");
                    lines.remove(i);
                    i--;
                } else if (previous.startsWith("  db MUSIC_CMD_PLAY_INSTRUMENT_CH") &&
                           !previous.contains("MUSIC_CMD_TIME_STEP_FLAG")) {
                    int idx = previous.indexOf(", ");
                    String newLine = previous.substring(0, idx) + " + MUSIC_CMD_TIME_STEP_FLAG" + previous.substring(idx);
                    lines.set(i - 1, newLine);
                    lines.remove(i);
                    i--;
                }
            }
            if (done) {
                lines.remove(i);
                i--;
            } else {
                if (lines.get(i).startsWith("  db MUSIC_CMD_PLAY_SONG")) {
                    done = true;
                }
            }
        }
        
        // write lines:
        for(String line:lines) w.println(line);
        
//        config.info("Channel times: " + channelTime[0] + ", " + channelTime[1] + ", " + channelTime[2]);
    }    
    
    
    public void findNotesUsedBySong(int transposeRange, List<Integer> notes)
    {
        for(int c = 0;c<3;c++) {
            for(TSVNote n:channels[c]) {
                if (n.absoluteNote >= 0) {
                    for(int i = 0;i<=transposeRange;i++) {
                        if (!notes.contains(n.absoluteNote+i)) notes.add(n.absoluteNote+i);
                    }
                }
            }
        }
    }
    
    
    public boolean usesSetVolume()
    {
        for(int c = 0;c<3;c++) {
            for(TSVNote n:channels[c]) {
                if (n.absoluteNote == TSVNote.SET_VOLUME) {
                    return true;
                }
            }
        }
        return false;
    }
    
    
    public TSVSong generateSubSong(int start, int end, String nextSongName, int nextSongTempo) {
        TSVSong s = new TSVSong();
        for(int i = 0;i<N_CHANNELS;i++) {
            int repeats = 0;
            int time = 0;
            for(TSVNote n:channels[i]) {
                if (time >= start && time < end) {
                    if (s.channels[i].isEmpty()) {
                        // first note:
                        if (repeats > 0) {
                            // Cannot break in the middle of a repeat:
                            return null;
                        }
                        if (time > start) {
                            // insert an empty note to fill in the time:
                            TSVNote note = new TSVNote(time - start);  // creates a "silence" note
                            note.absoluteNote = TSVNote.NOTHING;  // overwrites it to be "nothing"
                            s.channels[i].add(note);
                        }
                    }
                    TSVNote n2 = new TSVNote(n);
                    if (time + n2.duration > end) {
                        n2.duration = end - time;
                    }
                    s.channels[i].add(n2);
                }
                if (n.absoluteNote == TSVNote.START_REPEAT) {
                    repeats += 1;
                } else if (n.absoluteNote == TSVNote.END_REPEAT) {
                    repeats -= 1;
                }                    
                if (time < end && time + n.duration >= end) {
                    if (repeats > 0) {
                        // Cannot break in the middle of a repeat:
                        return null;
                    }
                }
                time += n.duration;
            }
        }
        if (nextSongName != null) {
//            int timeToCut = s.channelLength(N_CHANNELS - 1) - (end - start);
//            System.out.println("timeToCut: " + start + " -> " + end + ": " + timeToCut);
            TSVNote n = new TSVNote(nextSongName, nextSongTempo);
            n.absoluteNote = TSVNote.PLAY_SONG;
            for(int i = 0;i<N_CHANNELS;i++) {
                if (!s.channels[i].isEmpty()) {
                    TSVNote lastNote = s.channels[i].get(s.channels[i].size()-1);
                    if (lastNote.duration > 0) {
                        lastNote.duration -= 1;  // to make sure the playSong command does not mess up song tempo
                    }
                }
            }
            s.channels[N_CHANNELS - 1].add(n);
        }
        return s;
    }
    
    
    public boolean halveTempoIfPossible()
    {
        for(int i = 0;i<N_CHANNELS;i++) {
            for(TSVNote n:channels[i]) {
                if ((n.duration % 2) != 0) {
                    return false;
                }
            }
        }

        for(int i = 0;i<N_CHANNELS;i++) {
            for(TSVNote n:channels[i]) {
                n.duration /= 2;
            }
        }
        
        return true;
    }
}
