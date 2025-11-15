/*
 * This code is a translation from the original cpc2cdt tool by:
 * - cpcemu - Marco Vieth
 * - manageDSK - Ludovic Deplanque
 * - iDSK - Sid from IMPACT / PulkoMandy from the Shinra Team
 * The original source code that I used for translation can be found here:
 *  - https://github.com/jeromelesaux/idsk
 * Translation by Santiago Ontañón
 */
package paket.util.idsk;

// - rewritten in Java by Santiago Ontañón (May 9, 2024):
//   - Only those flags and functionalities needed by the PAKET engine were
//     translated.

import java.util.ArrayList;
import java.util.List;
import paket.compiler.PAKETConfig;

/**
 *
 * @author santi
 */
public class IDsk {

    public static void main(String argv[], PAKETConfig config) throws Exception
    {
        boolean ModeImportFile = false;
        boolean ModeNewDsk = false;
        boolean Read_only = false;
        boolean System_file = false;

        String DskFile = null;
        List<String> AmsdosFileList = new ArrayList<>();

        int exeAdress = 0, loadAdress = 0, AmsdosType = 1, UserNumber = 0;

        DSK MyDsk = new DSK(config);

        DskFile = argv[0];
        if (DskFile == null || DskFile.isBlank()) {
            throw new Exception("You did not select a DSK file to work with!");
        }
        for(int i = 1;i<argv.length;i++) {
            if (argv[i].equalsIgnoreCase("-i")) {
                ModeImportFile = true;
                i++;
                AmsdosFileList.add(argv[i]);
            } else if (argv[i].equalsIgnoreCase("-n")) {
                ModeNewDsk = true;
            } else if (argv[i].equalsIgnoreCase("-e")) {
                i++;
                exeAdress = parseHex(argv[i]);
            } else if (argv[i].equalsIgnoreCase("-c")) {
                i++;
                loadAdress = parseHex(argv[i]);
            } else if (argv[i].equalsIgnoreCase("-t")) {
                i++;
                AmsdosType = Integer.parseInt(argv[i]);
            } else {
                throw new Exception("Unsupported flag: " + argv[i]);
            }
        }
        
        config.info("DSK: " + DskFile);

        if (ModeNewDsk) {
            MyDsk.formatDsk(9, 42);
            if (!MyDsk.writeDsk(DskFile)) {
                throw new Exception("Error writing file " + DskFile);
            }
        }

        if (ModeImportFile)
        {
            if (!MyDsk.readDsk(DskFile)) {
                throw new Exception("Error reading file (" + DskFile + ").");
            }

            for(String amsdosfile: AmsdosFileList) {
                config.info("Amsdos file: " + amsdosfile);

                MyDsk.putFileInDsk(amsdosfile, AmsdosType, loadAdress, exeAdress, UserNumber, System_file, Read_only);
            }
            if (!MyDsk.writeDsk(DskFile)) {
                throw new Exception("Error writing file : " + DskFile);
            }
        }
    }

    
    public static Integer parseHex(String token)
    {
        int value = 0;
        int startIndex = 0;
        String allowed = "0123456789abcdef";
        
        if (token.charAt(0) == '#' || token.charAt(0) == '$' || token.charAt(0) == '&') {
            startIndex = 1;
        } else if (token.startsWith("0x")) {
            startIndex = 2;
        }
        for(int i = startIndex;i<token.length();i++) {
            char c = (char)token.charAt(i);
            c = Character.toLowerCase(c);
            int idx = allowed.indexOf(c);
            if (idx == -1) {
                if (i == token.length()-1 && c == 'h') return value;
                return null;
            }
            value = value * 16 + idx;
        }
        return value;
    }
}
