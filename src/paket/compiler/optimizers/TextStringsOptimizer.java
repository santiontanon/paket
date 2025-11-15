/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package paket.compiler.optimizers;

import java.util.ArrayList;
import java.util.List;
import paket.compiler.PAKETConfig;
import paket.text.EncodeText;
import paket.text.PAKFont;

/**
 *
 * @author santi
 */
public class TextStringsOptimizer extends PAKETOptimizer {
    
    PAKFont font = null;
    List<String> new_order = new ArrayList<>();
    
    public TextStringsOptimizer(String a_name, int a_randomSeed, PAKFont a_font)
    {
        super(a_name, a_randomSeed, false);
        font = a_font;
    }


    @Override
    public int extimateSizeWithOrder(List<Integer> order, List<? extends Object> objects, String compressor, PAKETConfig config) throws Exception
    {
        new_order.clear();
        for(int idx:order) {
            new_order.add((String)objects.get(idx));
        }
        return EncodeText.estimateSizeOfAllTextBanks(new_order, font, config.maxTextBankSize, compressor, config);
    }

    
    @Override
    public int uncompressedSize(List<? extends Object> objects, PAKETConfig config) throws Exception
    {
        int size = 0;
        for(Object o: objects) {
            size += ((String)o).length() + 1;
        }
        
        return size;
    }
    
    
    @Override
    public String IDFromObject(Object object) {
        return (String)object;
    }
    
    
    @Override
    public List<Integer> uncompressedBytes(Object object, PAKETConfig config) throws Exception
    {
        String s = (String)object;
        List<Integer> bytes = new ArrayList<>();
        for(int i = 0;i<s.length();i++) {
            bytes.add((int)s.charAt(i));
        }
        return bytes;
    }
}
