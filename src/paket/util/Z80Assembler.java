/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.util;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.List;

/**
 *
 * @author santi
 */
public class Z80Assembler {
    
    public static String toHex8bit(int value, boolean hash) throws Exception {
        char table[] = {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'};
        if (value > 255) throw new Exception("trying to convert value " + value + " to a 8bit Hex!");
        return (hash ? "#":"") + table[value/16] + table[value%16];
    }

    
    public static String toHex16bit(int value, boolean hash) throws Exception {
        char table[] = {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'};
        if (value > 65535) throw new Exception("trying to convert value " + value + " to a 16bit Hex!");
        return (hash ? "#":"") + table[value/(16*16*16)] + table[(value/(16*16))%16] + table[(value/16)%16] + table[value%16];
    }
    
    
    public static Integer parseHex(String token)
    {
        int value = 0;
        int startIndex = 0;
        String allowed = "0123456789abcdef";
        
        if (token.charAt(0) == '#' || token.charAt(0) == '$' || token.charAt(0) == '&') startIndex = 1;
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
    
    
    public static void dataBlockToAssembler(List<Integer> data, String name, FileWriter fw, int bytesPerLine) throws Exception
    {
        fw.write("; size in bytes: " + data.size() + "\n");
        fw.write(name+":\n");
        for(int i = 0;i<data.size();i++) {
            if (i%bytesPerLine == 0) fw.write("    db ");
            fw.write(toHex8bit(data.get(i) & 0xff, true));
            if (i%bytesPerLine != (bytesPerLine-1) && i<data.size()-1) {
                fw.write(", ");
            } else {
                fw.write("\n");
            }
        }
        fw.write("end_"+name+":\n");
    }        


    public static String dataBlockToAssemblerString(List<Integer> data) throws Exception
    {
        String assembler = "";
        for(int i = 0;i<data.size();i++) {
            assembler += toHex8bit(data.get(i), true);
            if (i<data.size()-1) {
                assembler += ", ";
            }
        }
        return assembler;
    }
    
    
    public static String dataBlockToAssemblerString(List<Integer> data, int bytesPerLine) throws Exception
    {
        String assembler = "";
        for(int i = 0;i<data.size();i++) {
            if (i%bytesPerLine == 0) assembler += ("    db ");
            assembler += toHex8bit(data.get(i) & 0xff, true);
            if (i%bytesPerLine != (bytesPerLine-1) && i<data.size()-1) {
                assembler += ", ";
            } else {
                assembler += "\n";
            }            
        }
        return assembler;
    }         


    public static String dataBlockArrayToAssemblerString(byte data[], int bytesPerLine) throws Exception
    {
        String assembler = "";
        for(int i = 0;i<data.length;i++) {
            if (i%bytesPerLine == 0) assembler += ("    db ");
            assembler += toHex8bit(data[i] & 0xff, true);
            if (i%bytesPerLine != (bytesPerLine-1) && i<data.length-1) {
                assembler += ", ";
            } else {
                assembler += "\n";
            }            
        }
        return assembler;
    }


    public static void dataToBinary(List<Integer> data, String fileName) throws Exception
    {
        FileOutputStream fos = new FileOutputStream(fileName);
        for(Integer v: data) {
            fos.write(v);
        }
        fos.flush();
        fos.close();
    }    
}
