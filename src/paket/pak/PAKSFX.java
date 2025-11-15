/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.pak;

import cl.MDLConfig;
import code.CodeBase;
import paket.compiler.PAKET;
import paket.compiler.PAKETConfig;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import paket.platforms.Platform;
import workers.BinaryGenerator;
import paket.util.ListOutputStream;

/**
 *
 * @author santi
 */
public class PAKSFX {
    public final static int RAW_PAK_BINARY = 0;
    public final static int RAW_PAK_ASSEMBLER = 1;
    public final static int AYFX = 2;
    
    public int fileType = RAW_PAK_BINARY;
    public String name = null;
    public String fileName = null;

    int MUSIC_CMD_FLAG = 0x80;
    int MUSIC_CMD_TIME_STEP_FLAG = 0x40;
    int MUSIC_CMD_SKIP = 0x00+MUSIC_CMD_FLAG;
    int SFX_CMD_END = 0x09+MUSIC_CMD_FLAG;
    int MUSIC_CMD_TIME_STEP_MASK = MUSIC_CMD_TIME_STEP_FLAG ^ 0xff;
    
    
    public PAKSFX(String a_name, String a_fileName, int a_filetype)
    {
        name = a_name;
        fileName = a_fileName;
        fileType = a_filetype;
    }
    
    
    public List<Integer> toAssemblerBytes(List<String> dataFolders, Platform platform, PAKETConfig config) throws Exception
    {
        switch(fileType) {
            case RAW_PAK_BINARY:
            {
                String filePath = PAKET.getFileName(fileName, dataFolders, config);
                FileInputStream fis = new FileInputStream(filePath);
                List<Integer> data = new ArrayList<>();
                while(fis.available() > 0) {
                    data.add(fis.read());
                }
                if (config.sfxPlayer == PAKETConfig.SFX_PLAYER_PAK) {
                    return register7SafetyCheck(data, platform);
                }
                return convertToAYFX(data, platform, config);
            }

            case RAW_PAK_ASSEMBLER:
            {
                String filePath = PAKET.getFileName(fileName, dataFolders, config);
                // Use MDL to assembler the file into a list of bytes:
                MDLConfig mdlConfig = new MDLConfig();
                CodeBase code = new CodeBase(mdlConfig);
                mdlConfig.parseArgs(filePath);
                if (!mdlConfig.codeBaseParser.parseMainSourceFiles(mdlConfig.inputFiles, code)) {
                    throw new Exception("Cannot parse SFX assembler file: " + fileName);
                }
                BinaryGenerator bg = new BinaryGenerator(mdlConfig);
                ListOutputStream out = new ListOutputStream();
                if (!bg.writeBytes(code.outputs.get(0).main, code, out, 0, true)) {
                    throw new Exception("Cannot parse SFX assembler file: " + fileName);
                }
                List<Integer> data = out.getData();
                if (config.sfxPlayer == PAKETConfig.SFX_PLAYER_PAK) {
                    return register7SafetyCheck(data, platform);
                }
                return convertToAYFX(data, platform, config);                
            }

            case AYFX:
            {
                String filePath = PAKET.getFileName(fileName, dataFolders, config);
                FileInputStream fis = new FileInputStream(filePath);
                List<Integer> ayfxData = new ArrayList<>();
                while(fis.available() > 0) {
                    ayfxData.add(fis.read());
                }
                
                if (config.sfxPlayer == PAKETConfig.SFX_PLAYER_AYFX) return ayfxData;
                return convertFromAYFX(ayfxData, platform, config);
            }
        }
        return null;
    }
    
    
    public List<Integer> register7SafetyCheck(List<Integer> pakData, Platform platform) throws Exception
    {
        // Checks that no unsafe values are being sent to register 7 (since SFX
        // might have been written for a different platform):
        for(int i = 0;i<pakData.size();i++) {
            int v = pakData.get(i);
            v &= MUSIC_CMD_TIME_STEP_MASK;
            if ((v & MUSIC_CMD_FLAG) != 0) {
                // command:
                if (v == MUSIC_CMD_SKIP) {
                } else if (v == SFX_CMD_END) {
                    break;
                } else {
                    throw new Exception("Unsupported command when converting to AYFX: " + v);
                }
            } else {
                // raw register write:
                v &= MUSIC_CMD_TIME_STEP_MASK;
                if (v == 7) {
                    // register 7 write:
                    i++;
                    v = pakData.get(i);
                    v = v & 0x3f;
                    v |= (platform.basePSGReg7Value & 0xc0);
                    pakData.set(i, v);
                } else {
                    i++;
                }
            }
        }
        return pakData;
    }
    
    
    public List<Integer> convertFromAYFX(List<Integer> ayfxData, Platform platform, PAKETConfig config) throws Exception
    {
        /*
        SFX format (taken form the ayfxedit documentation, but with fixed
        typos and grammar):
        Every frame is encoded with a flag byte and a number of bytes, 
        which varies depending from value change flags.
        - bit0..3  Volume
        - bit4     Disable T
        - bit5     Change Tone
        - bit6     Change Noise
        - bit7     Disable N
        
        Cases:
        - When none of the bits is set, next flags byte will follow.
        - when the bit5 is set: two bytes with tone period will follow; 
        - when the bit6 set: a single byte with noise period will follow;
        - when both bits are set: the first two bytes are the tone period, 
          then a single byte with noise period will follow. 
        End of the effect is marked with byte sequence #D0, #20. Player should
        detect it before outputting it to the AY registers, by checking if the
        noise period value is equal to #20.
        */

        int channel = config.sfxChannel;
        List<Integer> data = new ArrayList<>();
        
        if (ayfxData.get(ayfxData.size()-2) != 0xd0 ||
            ayfxData.get(ayfxData.size()-1) != 0x20) {
            throw new Exception("Data is an invalid AYFX!");
        }
                
        // base values:
        // - 0x38 for CPC
        // - 0xb8 for MSX
        
        int volumeRegister = 8 + channel;
        int periodRegister = 0 + channel*2;
        int toneReg7Bit = 0x01 << channel;
        int noiseReg7Bit = 0x08 << channel;
        int currentVolume = 0;
        int currentReg7Value = 0; 
        int currentPeriodR1 = -1;
        int currentPeriodR2 = -1;
        int currentNoiseTone = -1;
        
        for(int i = 0;i<ayfxData.size()-2;i++) {
            // This list contains an alternative way to encode the effect,
            // which could be shorter. At the end of the frame, we will 
            // decide which is shorter:
//            List<Integer> alternativeEncoding = new ArrayList<>();
//            boolean alternativeEncodingValid = true;
            int previousDataSize = data.size();
            int commandIdx = -1;
            
            int v = ayfxData.get(i);
            int newVolume = v & 0xf;
            boolean disableTone = (v & 0x10) != 0;
            boolean disableNoise = (v & 0x80) != 0;
            int newReg7Value = platform.basePSGReg7Value;
            int newNoiseTone = currentNoiseTone;
//            String changed = "";
            
            if (disableTone) {
                newReg7Value |= toneReg7Bit;
            } else {
                newReg7Value &= toneReg7Bit^0xff;
            }
            if (disableNoise) {
                newReg7Value |= noiseReg7Bit;
            } else {
                newReg7Value &= noiseReg7Bit^0xff;
            }
            
            if (i == 0) {
                data.add(7);
                data.add(newReg7Value);
                commandIdx = data.size();
                data.add(volumeRegister);
                data.add(newVolume);

//                alternativeEncoding.add(SFX_CMD_SFX_AYFX_FULL_FRAME);
//                alternativeEncoding.add(newReg7Value);
//                alternativeEncoding.add(newVolume);
//                changed += "(R7:"+newReg7Value+")(volume)";
            } else {
                if (newReg7Value != currentReg7Value) {
                    commandIdx = data.size();
                    data.add(7);
                    data.add(newReg7Value);
//                    alternativeEncoding.add(SFX_CMD_SFX_AYFX_FULL_FRAME);
//                    alternativeEncoding.add(newReg7Value);
//                    changed += "(R7:"+newReg7Value+")";
                } else {
//                    alternativeEncoding.add(SFX_CMD_SFX_AYFX_FRAME_WO_R7);
                }
                if (newVolume != currentVolume) {
                    commandIdx = data.size();
                    data.add(volumeRegister);
                    data.add(newVolume);  
//                    changed += "(volume)";
                }
//                alternativeEncoding.add(newVolume);
            }
            
            if ((v & 0x20) != 0) {
                // bit5 is set:
                i++;
                int v1 = ayfxData.get(i);
                i++;
                int v2 = ayfxData.get(i);
                int period = (v1&0xff)+(v2&0xff)*256;
                period = convertFrequency(period, platform);
                v1 = period & 0xff;
                v2 = (period >> 8) & 0xff;
                if (v1 != currentPeriodR1) {
                    commandIdx = data.size();
                    data.add(periodRegister);
                    data.add(v1&0xff);
                    currentPeriodR1 = v1;
                }
                if (v2 != currentPeriodR2) {
                    commandIdx = data.size();
                    data.add(periodRegister+1);
                    data.add(v2);
                    currentPeriodR2 = v2;
                }
//                alternativeEncoding.add(v1);
//                alternativeEncoding.add(v2);
//                changed += "(tone)";
            } else {
//                alternativeEncodingValid = false;
            }
            if ((v & 0x40) != 0) {
                // bit6 is set:
                i++;
                int v1 = ayfxData.get(i);
                int period = (v1&0xff);
                period = convertFrequency(period, platform);
                newNoiseTone = period&0xff;
                if (newNoiseTone != currentNoiseTone) {
                    commandIdx = data.size();
                    data.add(6);
                    data.add(v1);
                }
//                alternativeEncoding.add(v1);
//                changed += "(noise)";
            } else {
//                if (alternativeEncoding.get(0).equals(SFX_CMD_SFX_AYFX_FRAME_WO_R7)) {
//                    alternativeEncoding.set(0, SFX_CMD_SFX_AYFX_FRAME_WO_R7_NOISE);
//                } else {
//                    alternativeEncodingValid = false;
//                }
            }
            
//            if (alternativeEncodingValid && 
//                (data.size() - previousDataSize) > alternativeEncoding.size()) {
//                // int savings = (data.size() - previousDataSize) - alternativeEncoding.size();
//                while(data.size() > previousDataSize) data.remove(data.size()-1);
//                commandIdx = data.size();
//                data.addAll(alternativeEncoding);
//            }
            
            if (previousDataSize == data.size()) {
                // no bytes in this cycle:
                data.add(MUSIC_CMD_SKIP);
            } else {
                data.set(commandIdx, data.get(commandIdx) | MUSIC_CMD_TIME_STEP_FLAG);
            }
            
//            System.out.println("   sfx frame changes: " + changed);
            
            currentReg7Value = newReg7Value;
            currentVolume = newVolume;
            currentNoiseTone = newNoiseTone;            
        }
                
        // Final state should be tone on, noise off, volume 0:
        if (platform.basePSGReg7Value != currentReg7Value) {
            data.add(7);
            data.add(platform.basePSGReg7Value);                    
        }
        if (0 != currentVolume) {
            data.add(volumeRegister);
            data.add(0);                    
        }
        
        data.add(SFX_CMD_END);
        
        config.info("AYFX conversion: " + ayfxData.size() + " -> " + data.size());
        
        return data;
    }
    
    
    public List<Integer> convertToAYFX(List<Integer> pakData, Platform platform, PAKETConfig config) throws Exception
    {        
        int channel = config.sfxChannel;
        List<Integer> data = new ArrayList<>();
        boolean toneActive = false;
        boolean noiseActive = false;
        int frameVolume = 0;
        int frameTonePeriodLSB = 0;
        int frameTonePeriodMSB = 0;
        int frameNoisePeriod = 0;
        
        int previous_frameTonePeriodLSB = -1;
        int previous_frameTonePeriodMSB = -1;
        int previous_frameNoisePeriod = -1;
                
        for(int i = 0;i<pakData.size();i++) {
            boolean endFrame = false;
            int v = pakData.get(i);
            if ((v & MUSIC_CMD_TIME_STEP_FLAG) != 0) endFrame = true; 
            v &= MUSIC_CMD_TIME_STEP_MASK;
            if ((v & MUSIC_CMD_FLAG) != 0) {
                // command:
                if (v == MUSIC_CMD_SKIP) {
                    endFrame = true;
                } else if (v == SFX_CMD_END) {
                    break;
                } else {
                    throw new Exception("Unsupported command when converting to AYFX: " + v);
                }
            } else {
                // raw register write:
                v &= MUSIC_CMD_TIME_STEP_MASK;
                if (v == 7) {
                    // register 7 write:
                    i++;
                    v = pakData.get(i);
                    if ((v & (0x01 << channel)) == 0) {
                        toneActive = true;
                    } else {
                        toneActive = false;
                    }
                    if ((v & (0x01 << (channel+3))) == 0) {
                        noiseActive = true;
                    } else {
                        noiseActive = false;
                    }
                } else if (v == channel*2) {
                    // lsb of tone period:
                    i++;
                    frameTonePeriodLSB = pakData.get(i);
                } else if (v == channel*2 + 1) {
                    // msb of tone period:
                    i++;
                    frameTonePeriodMSB = pakData.get(i);
                } else if (v == channel + 8) {
                    // volume:
                    i++;
                    frameVolume = pakData.get(i);
                } else if (v == 6) {
                    // noise period:
                    i++;
                    frameNoisePeriod = pakData.get(i);
                } else {
                    throw new Exception("Unexpected register " + v + " when converting a sound effect ao AYFX!");
                }
            }
            if (endFrame) {
                // generate an AYFX frame:
                // - bit0..3  Volume
                // - bit4     Disable T
                // - bit5     Change Tone
                // - bit6     Change Noise
                // - bit7     Disable N
                int controlByte = frameVolume;
                if (!noiseActive) controlByte |= 0x80; 
                if (!toneActive) controlByte |= 0x10; 
                if (frameTonePeriodLSB != previous_frameTonePeriodLSB ||
                    frameTonePeriodMSB != previous_frameTonePeriodMSB) {
                    controlByte |= 0x20;
                }
                if (frameNoisePeriod != previous_frameNoisePeriod) {
                    controlByte |= 0x40;
                }
                
                data.add(controlByte);
                if (frameTonePeriodLSB != previous_frameTonePeriodLSB ||
                    frameTonePeriodMSB != previous_frameTonePeriodMSB) {
                    data.add(frameTonePeriodLSB);
                    data.add(frameTonePeriodMSB);
                }
                if (frameNoisePeriod != previous_frameNoisePeriod) {
                    data.add(frameNoisePeriod);
                }
                
                previous_frameTonePeriodLSB = frameTonePeriodLSB;
                previous_frameTonePeriodMSB = frameTonePeriodMSB;
                previous_frameNoisePeriod = frameNoisePeriod;
            }
        }
        // End SFX:
        data.add(0xd0);
        data.add(0x20);
        
        return data;
    }   
    
    
    int convertFrequency(int period, Platform platform)
    {
        double periodFactor = 111861 / platform.PSGMasterFrequency;
        period /= periodFactor;
        return period;
    }
}
