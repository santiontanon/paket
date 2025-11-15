/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.compiler;

import cl.MDLLogger;

/**
 *
 * @author santi
 */
public class PAKETConfig {
    public static final String compressorExtension[] = {"zx0", "plt"};
    public static final int COMPRESSOR_ZX0 = 0;
    public static final int COMPRESSOR_PLETTER = 1;
    
    public static final int SFX_PLAYER_PAK = 0;
    public static final int SFX_PLAYER_AYFX = 1;
    
    public MDLLogger logger = new MDLLogger(MDLLogger.INFO);

    public int compressor = COMPRESSOR_ZX0;
    public boolean run_mdl_optimizers = true;
//    public boolean run_mdl_optimizers = false;
    public String mdlLoggerFlag = "-diggest";    
    
    public int sfxChannel = 2;
    public int sfxPlayer = SFX_PLAYER_PAK;
    public boolean sfxPlayerOverride = false;  // This is set to true, if the user specifically specifies which sfx player to use
                                               // If it is set to false, some settings (like using WYZ player), will override the default.
    
//    public int maxTextBankSize = 512;
    public int maxTextBankSize = 768;
    public int maxLinesPerTextBank = 64;
    public int objectTypesPerBank = 4;
    public int maxTextOptimizationIterations = 0;
    public int maxObjectOptimizationIterations = 0;
    public int maxRoomOptimizationIterations = 0;
    public int maxTileOptimizationIterations = 0;
    public int roomsPerBank = 1;
    public boolean attemptToSplitSongs = true;
    
    public int playerObjectId = 63;
    public String guiFileName = "gui.png";
    
    public boolean pedanticTranslation = true;  // If this is "true" ALL sentences in the original must appear in the translation file.

    // Platform specific options:
    public int spaceForDataInPage0 = 0;
    
    
    public PAKETConfig()
    {
        logger.INFO_PREFIX = "";
        logger.DIGGEST_PREFIX = "";
    }
    
    
    public void setMinLevelToLog(int minLevelToLog)
    {
        logger.setMinLevelToLog(minLevelToLog);
    }    


    public void trace(String message) {
        logger.log(MDLLogger.TRACE, message);
    }


    public void debug(String message) {
        logger.log(MDLLogger.DEBUG, message);
    }


    public void info(String message) {
        logger.log(MDLLogger.INFO, message);
    }


    public void diggest(String message) {
        logger.log(MDLLogger.DIGGEST, message);
    }

    
    public void warn(String message) {
        logger.log(MDLLogger.WARNING, message);
    }


    public void error(String message) {
        logger.log(MDLLogger.ERROR, message);
    }

}
