/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.text;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import paket.compiler.PAKET;
import paket.compiler.PAKETCompiler;
import paket.compiler.PAKETConfig;
import paket.compiler.optimizers.PAKETOptimizer;
import paket.util.Pair;
import paket.util.Z80Assembler;

/**
 *
 * @author santi
 */
public class EncodeText {    
    // maxBankSize is in bytes of text per bank
    @SuppressWarnings("unchecked")
    public static List<Integer> encodeTextInBanks(List<String> lines, PAKFont font, int maxBankSize, String outputFolder, HashMap<String, Pair<Integer, Integer>> ids, 
            PAKETOptimizer optimization_textState,
            PAKETConfig config) throws Exception
    {
        if (config.maxTextOptimizationIterations == 0) {
            lines = (List<String>) optimization_textState.heuristicCompressionOrder(lines, config);            
        } else {
            for(int i = 0;i<config.maxTextOptimizationIterations;i++) {
                lines = (List<String>) optimization_textState.optimizeCompressionOrder(lines, config);
            }
        }
        
        config.info("encodeTextInBanks: with " + lines.size() + " lines.");
        List<Integer> sizes = new ArrayList<>();
        int bank = 0;
        int bankSize = 0;
        int totalCompressedSize = 0;
        List<String> bankLines = new ArrayList<>();
        for(String line:lines) {
            if (bankSize + 1 + line.length() > maxBankSize ||
                bankLines.size()>=config.maxLinesPerTextBank) {
                // new bank:
                Pair<Integer, Integer> size = compressTextLines(bankLines, font, outputFolder, "textBank" + bank, PAKETConfig.compressorExtension[config.compressor], config);
                config.info("   text bank size: " + size.m_a + " / " + size.m_b);
                totalCompressedSize += size.m_b; // compressed
                sizes.add(size.m_a);
                bankSize = 0;
                bankLines.clear();
                bank++;
            }
            ids.put(line, new Pair<>(bank, bankLines.size()));
            bankLines.add(line);   
            bankSize += 1 + line.length();
        }

        if (!bankLines.isEmpty()) {
            Pair<Integer, Integer> size = compressTextLines(bankLines, font, outputFolder, "textBank" + bank, PAKETConfig.compressorExtension[config.compressor], config);
            config.info("   text bank size: " + size.m_a + " / " + size.m_b);
            totalCompressedSize += size.m_b;
            sizes.add(size.m_a);
        }
        
        config.info("   compressed total text bank size: " + totalCompressedSize);
        
        return sizes;
    }
    
    
    // returns the size of the data before / after compression
    public static Pair<Integer, Integer> compressTextLines(List<String> lines, PAKFont font, String outputFolder, String fileName, String compressor, PAKETConfig config) throws Exception
    {
        List<Integer> data = new ArrayList<>();
        FileWriter fw = new FileWriter(outputFolder + "data/" + fileName + ".asm");

        for(String line:lines) {
            List<Integer> line_data;
            line_data = font.convertStringToAssembler(line);
            if (line_data.size() > 255) throw new Exception("Line of text longer than 255 characters!");
            // data.add(line_data.size());
            data.addAll(line_data);
            fw.write("; line: '" + line + "'\n");
            Z80Assembler.dataBlockToAssembler(line_data, "line_"+lines.indexOf(line), fw, 16);
        }
        
        fw.close();
        PAKETCompiler.callMDL(new String[]{outputFolder + "data/" + fileName + ".asm", "-bin", outputFolder + "data/" + fileName + ".bin"}, config);
        int compressedSize = PAKET.compress(outputFolder + "data/" + fileName + ".bin", outputFolder + "data/" + fileName, config);
        return new Pair<>(data.size(), compressedSize);
    }
    
    
    // maxBankSize is in bytes of text per bank
    public static int estimateSizeOfAllTextBanks(List<String> lines, PAKFont font, int maxBankSize, String compressor, PAKETConfig config) throws Exception
    {
        int total_size = 0;
        int bankSize = 0;
        List<String> bankLines = new ArrayList<>();
        for(String line:lines) {
            if (bankSize + 1 + line.length() > maxBankSize ||
                bankLines.size()>=config.maxLinesPerTextBank) {
                // new bank:
                total_size += estimateSizeOfCompressedTextBank(bankLines, font, compressor);
                bankSize = 0;
                bankLines.clear();
            }
            bankLines.add(line);   
            bankSize += 1 + line.length();
        }
        total_size += estimateSizeOfCompressedTextBank(bankLines, font, compressor);
        return total_size;
    }   


    public static int estimateSizeOfCompressedTextBank(List<String> lines, PAKFont font, String compressor) throws Exception
    {
        List<Integer> data = new ArrayList<>();

        for(String line:lines) {
            List<Integer> line_data;
            line_data = font.convertStringToAssembler(line);
            for(Integer v:line_data) {
                if (v == null) {
                    throw new Exception("Error converting '"+line+"' to data!");
                }
            }
            if (line_data.size() > 255) throw new Exception("Line of text longer than 255 characters!");
            data.addAll(line_data);
        }
        
        // make sure buffer is not too small for pletter:
        while(data.size()<16) data.add(0);
        return PAKET.estimateCompressedSize(data, compressor);
    }    

}
