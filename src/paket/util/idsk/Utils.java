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

/**
 *
 * @author santi
 */
public class Utils {

    //
    // Returns the name of a file in amsDos format (8+3)
    //
    public static String getAmsdosName(String amsName) {
        // Extract the name (without directory components)
        int lastSlash = amsName.lastIndexOf('/');
        int lastBackslash = amsName.lastIndexOf('\\');
        if (lastSlash > lastBackslash) {
            amsName = amsName.substring(lastSlash + 1);
        } else if (lastSlash < lastBackslash) {
            amsName = amsName.substring(lastBackslash + 1);
        }

        String amsDOSName = "";
        for (int i = 0; i < 8 && i < amsName.length(); i++) {
            if (amsName.charAt(i) == ' ' || amsName.charAt(i) == '.') {
                break;
            }
            amsDOSName += amsName.charAt(i);
        }
        if (amsName.contains(".")) {
            amsName = amsName.substring(amsName.indexOf('.') + 1);
        }
        amsDOSName += ".";
        amsDOSName += amsName.substring(0, 3);
        return amsDOSName;
    }

}
