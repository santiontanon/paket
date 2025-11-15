/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.text;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.imageio.ImageIO;
import paket.compiler.PAKETConfig;

/**
 *
 * @author santi
 */
public class PAKFont {
    public static List<String> characterLines = null;
    public static List<Character> characters = null;
    public static HashMap<Character, Integer> characterIndexes = null;
    public static List<Integer> indexes = null;
    public static List<Integer> widths = null;

    List<Integer> bytesForAssembler = null;

    public PAKFont(String a_fontFile, List<String> a_characters, PAKETConfig config) throws Exception
    {        
        int index_resolution = 3;
     
        characterIndexes = new HashMap<>();
        characterLines = a_characters;
        characters = new ArrayList<>();
        for(String line:characterLines) {
            for(int i = 0;i<line.length();i++) {
                characters.add(line.charAt(i));
            }
        }
        List<List<Integer>> data = extractFontCharacters(a_fontFile);
        indexes = new ArrayList<>();
        widths = new ArrayList<>();
        bytesForAssembler = new ArrayList<>();
        int charIndex = 0;
        for(List<Integer> characterData:data) {
            int index = bytesForAssembler.size();
            while(index%index_resolution != 0) {
                bytesForAssembler.add(bytesForAssembler.get(index-1));
                index++;
            }
            if (index/index_resolution >= 256) throw new Exception("Font is too large!");
            indexes.add(index/index_resolution);
            characterIndexes.put(characters.get(charIndex), index/index_resolution);
            widths.add(characterData.size());
            bytesForAssembler.add(characterData.size());
            bytesForAssembler.addAll(characterData);
            charIndex++;
        }
        config.info("PAKFont from: " + a_fontFile);
        config.info("    Flattened data size: " + bytesForAssembler.size());
        config.info("    Indexes: " + indexes);
        config.info("    Character widths: " + widths);
    }    
    
    
    public void saveToAssemblerBinary(String fileName) throws Exception
    {
        FileOutputStream fos = new FileOutputStream(fileName);
        for(Integer value:bytesForAssembler) {
            fos.write(value);
        }
        fos.close();
    }
    
    
    public String convertStringToAssemblerString(String sentence) throws Exception
    {
        List<Integer> data = convertStringToAssembler(sentence);
        String str = "";
        for(int i = 0;i<data.size();i++) {
            if (i!=0) str += ", ";
            str += data.get(i);
        }
        return str;
    }
    
    
    public List<Integer> convertStringToAssembler(String sentence) throws Exception
    {
        List<Integer> data = new ArrayList<>();
        
        // the first byte is the sentence length
        data.add(sentence.length());
        
        for(int i = 0;i<sentence.length();i++) {
            Integer index = characterIndexes.get(sentence.charAt(i));
            if (index == null) {
                throw new Exception("Character '"+sentence.charAt(i)+"' does not exist in the font!");
            }
            data.add(index);
        }
        return data;
    }
    
    
    public int stringWidthInPixels(String sentence) throws Exception
    {
        int width = 0;
        for(int i = 0;i<sentence.length();i++) {
            boolean found = false;
            int index = 0;
            for(int j = 0;j<characterLines.size();j++) {
                int index_line = characterLines.get(j).indexOf(sentence.charAt(i));
                if (index_line >=0) {
                    found = true;
                    width += widths.get(index+index_line);
                    break;
                }
                index += characterLines.get(j).length();
            }  
            if (!found) throw new Exception("character '"+sentence.charAt(i)+"' in '"+sentence+"' not found!");
        }
        return width;
    }
    
    
    public final List<List<Integer>> extractFontCharacters(String fontImageFileName) throws Exception
    {
        int dy = 8;
        BufferedImage img = ImageIO.read(new File(fontImageFileName));
        List<List<Integer>> data = new ArrayList<>();
             
        for(int i = 0;i<characterLines.size();i++) {
            int current_x = 0;
            for(int j = 0;j<characterLines.get(i).length();j++) {
                List<Integer> characterData = new ArrayList<>();
                boolean anyNonEmpty = false;
                while(current_x<img.getWidth()) {
                    int bitmap[] = extractColumnBitmap(img, current_x, i*dy, dy);
                    current_x++;
                    if (bitmap == null) {
                        // column that separates characters:
                        if (!characterData.isEmpty()) {
                            break;
                        }
                    } else {
                        int value = 0;
                        int mask = 1;
                        for(int l = 0;l<dy;l++) {
                            if (bitmap[l]!=0) value += mask;
                            mask *= 2;
                        }
                        characterData.add(value);                    
                        if (value == 0) {
                            if (anyNonEmpty) break;
                        } else {
                            anyNonEmpty = true;
                        }
                    }
                }
                if (!characterData.isEmpty()) {
                    data.add(characterData);
                }                
            }
        }
        
        return data;
    }
    
    
    public static int[] extractColumnBitmap(BufferedImage img, int x, int y, int h) throws Exception
    {
        int bitmap[] = new int[h];
        
        for(int i = 0;i<h;i++) {
            int color = img.getRGB(x, y+i);
            int r = (color & 0xff0000)>>16;
            int g = (color & 0x00ff00)>>8;
            int b = color & 0x0000ff;
            int a = (color & 0xff000000)>>24;
            if (r>0 && g==0 && b==0 &&a!=0) return null;
            if (a!=0 && r > 0 && g > 0 && b > 0) {
                bitmap[i] = 1;
            } else {
                bitmap[i] = 0;
            }
        }
        return bitmap;
    }
        
}
