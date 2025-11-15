/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.tiles;

import paket.compiler.PAKET;
import paket.compiler.PAKETConfig;
import paket.platforms.CPCColors;
import java.awt.image.BufferedImage;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import paket.compiler.PAKETCompiler;
import paket.pak.PAKRoom;
import paket.platforms.Platform;
import paket.util.Z80Assembler;

/**
 *
 * @author santi
 */
public class TileBankCPC {
    public static int generateAndCompressTilesAssemblerFile(
                             BufferedImage img, List<Integer> colorPalette, 
                             int t1, int t2,
                             String path, String fileName,
                             Platform platform, PAKETConfig config,
                             String imageName) throws Exception {
        return generateAndCompressTilesAssemblerFileWithPrefix(null, img, colorPalette, t1, t2, path, fileName, platform, config, imageName);
    }

    
    public static int generateAndCompressTilesAssemblerFileByColumns(
                             BufferedImage img, List<Integer> colorPalette, 
                             int t1, int t2,
                             String path, String fileName,
                             Platform platform, PAKETConfig config,
                             String imageName) throws Exception {
        return generateAndCompressTilesAssemblerFileWithPrefixByColumns(null, img, colorPalette, t1, t2, path, fileName, platform, config, imageName);
    }

    

    public static int generateAndCompressTilesAssemblerFileWithPrefix(
                             String prefix, 
                             BufferedImage img, List<Integer> colorPalette, 
                             int t1, int t2,
                             String path, String fileName,
                             Platform platform, PAKETConfig config,
                             String imageName) throws Exception {
        int tilesPerRow = img.getWidth()/platform.TILE_WIDTH;
        int tileIdx = 0;
        FileWriter fw = new FileWriter(path + fileName + ".asm");
        if (prefix != null) fw.write(prefix);
        for(int i = t1;i<t2;i++) {
            int x = i%tilesPerRow;
            int y = i/tilesPerRow;
            int tile[] = ExtractTilesCPC.getTile(img, x*platform.TILE_WIDTH, y*PAKRoom.TILE_HEIGHT, imageName, platform, config);
            if (tile == null) continue;

            List<Integer> data= new ArrayList<>();
            for(int k = 0;k<tile.length;k+=2) {
                int idx1 = colorPalette.indexOf(tile[k]);
                int idx2 = colorPalette.indexOf(tile[k+1]);
                idx1++; // add 1 to consider transparency
                idx2++; // add 1 to consider transparency
                int byteToWrite = CPCColors.mode0ColorTranslation[idx1]*2 + CPCColors.mode0ColorTranslation[idx2];
                data.add(byteToWrite);
            }
            Z80Assembler.dataBlockToAssembler(data, "tile_" + tileIdx, fw, 16);
            tileIdx++;
        }
        fw.close();
        
        PAKETCompiler.callMDL(new String[]{path + fileName + ".asm", "-bin", path + fileName + ".bin"}, config);
        int size = PAKET.compress(path + fileName + ".bin", path + fileName, config);
        return size;
    }
    
    
    public static int generateAndCompressTilesAssemblerFileWithPrefixByColumns(
                             String prefix, 
                             BufferedImage img, List<Integer> colorPalette, 
                             int t1, int t2,
                             String path, String fileName,
                             Platform platform, PAKETConfig config,
                             String imageName) throws Exception {
        int tilesPerRow = img.getWidth()/platform.TILE_WIDTH;
        int tileIdx = 0;
        FileWriter fw = null;
        for(int i = t1;i<t2;i++) {
            int x = i%tilesPerRow;
            int y = i/tilesPerRow;
            int tile[] = ExtractTilesCPC.getTile(img, x*platform.TILE_WIDTH, y*PAKRoom.TILE_HEIGHT, imageName, platform, config);
            if (tile == null) continue;

            List<Integer> data= new ArrayList<>();
            for(int column = 0;column<platform.TILE_WIDTH;column+=2) {
                for(int row = 0;row<PAKRoom.TILE_HEIGHT;row++) {
                    int idx1 = colorPalette.indexOf(tile[column+row*platform.TILE_WIDTH]);
                    int idx2 = colorPalette.indexOf(tile[column+row*platform.TILE_WIDTH+1]);
                    idx1++; // add 1 to consider transparency
                    idx2++; // add 1 to consider transparency
                    int byteToWrite = CPCColors.mode0ColorTranslation[idx1]*2 + CPCColors.mode0ColorTranslation[idx2];
                    data.add(byteToWrite);
                }
            }
            if (fw == null) {
                fw = new FileWriter(path + fileName + ".asm");
                if (prefix != null) fw.write(prefix);                
            }
            Z80Assembler.dataBlockToAssembler(data, "tile_" + tileIdx, fw, 16);
            tileIdx++;
        }
        if (fw == null) {
            return 0;
        }        
        fw.close();
        
        PAKETCompiler.callMDL(new String[]{path + fileName + ".asm", "-bin", path + fileName + ".bin"}, config);
        int size = PAKET.compress(path + fileName + ".bin", path + fileName, config);
        return size;
    }
    
    
    public static int generateAndCompressTilesAssemblerFile(
                             List<int []> mergedTiles,
                             int t1, int t2,
                             String path, String fileName,
                             PAKETConfig config) throws Exception {
        return generateAndCompressTilesAssemblerFileWithPrefix(null, mergedTiles, t1, t2, path, fileName, config);
    }

    
    public static int generateAndCompressTilesAssemblerFileByColumns(
                             List<int []> mergedTiles, 
                             int t1, int t2,
                             String path, String fileName,
                             Platform platform, PAKETConfig config) throws Exception {
        return generateAndCompressTilesAssemblerFileWithPrefixByColumns(null, mergedTiles, t1, t2, path, fileName, platform, config);
    }
    
    
    public static int generateAndCompressTilesAssemblerFileWithPrefix(
                             String prefix, 
                             List<int []> mergedTiles, 
                             int t1, int t2,
                             String path, String fileName,
                             PAKETConfig config) throws Exception {
        int tileIdx = 0;
        List<Integer> data = new ArrayList<>();
        FileWriter fw = null;
        if (path != null && fileName != null) {
            fw = new FileWriter(path + fileName + ".asm");
        }
        if (prefix != null && fw != null) fw.write(prefix);
        if (prefix != null && fw == null) {
            throw new Exception("prefix != null, but no file path specified in generateAndCompressTilesAssemblerFileWithPrefix!");
        }
        for(int i = t1;i<t2;i++) {
            for(int v:mergedTiles.get(i)) data.add(v);
            tileIdx++;
        }
        if (fw != null) {
            Z80Assembler.dataBlockToAssembler(data, "tile_" + tileIdx, fw, 16);
            fw.close();
        }
        
        if (fw != null) {
            PAKETCompiler.callMDL(new String[]{path + fileName + ".asm", "-bin", path + fileName + ".bin"}, config);
            return PAKET.compress(path + fileName + ".bin", path + fileName, config);
        } else {
            return PAKET.estimateCompressedSize(data, config);
        }
    }
    
    
    public static int generateAndCompressTilesAssemblerFileWithPrefixByColumns(
                             String prefix, 
                             List<int []> mergedTiles, 
                             int t1, int t2,
                             String path, String fileName,
                             Platform platform, PAKETConfig config) throws Exception {
        int tileIdx = 0;
        List<Integer> data = new ArrayList<>();
        for(int i = t1;i<t2;i++) {
            if (i < mergedTiles.size()) {
                for(int column = 0;column<platform.TILE_WIDTH/2;column++) {
                    for(int row = 0;row<PAKRoom.TILE_HEIGHT;row++) {
                        data.add(mergedTiles.get(i)[row*platform.TILE_WIDTH/2 + column]);
                    }
                }
            } else {
                for(int j = 0;j<PAKRoom.TILE_HEIGHT*platform.TILE_WIDTH/2;j++) {
                    data.add(0);
                }
            }
            tileIdx++;
        }
        if (path != null && fileName != null) {
            FileWriter fw = new FileWriter(path + fileName + ".asm");
            if (prefix != null) fw.write(prefix);                
            Z80Assembler.dataBlockToAssembler(data, "tile_" + tileIdx, fw, 16);
            fw.close();        
            PAKETCompiler.callMDL(new String[]{path + fileName + ".asm", "-bin", path + fileName + ".bin"}, config);
            int size = PAKET.compress(path + fileName + ".bin", path + fileName, config);
            return size;
        } else {
            if (prefix != null) throw new Exception("prefix != null but no path specified in generateAndCompressTilesAssemblerFileWithPrefixByColumns");
            return PAKET.estimateCompressedSize(data, config);
        }
    }    
    
    
    public static List<Integer> generateCutSceneTileData(
                             BufferedImage img, List<Integer> colorPalette, 
                             String imageName, Platform platform, PAKETConfig config) throws Exception {
        List<Integer> data = new ArrayList<>();
        int tilesPerRow = img.getWidth()/(platform.TILE_WIDTH);
        int rows = img.getHeight()/PAKRoom.TILE_HEIGHT;
        int tileIdx = 0;
        for(int i = 0;i<tilesPerRow*rows;i++) {
            int x = i%tilesPerRow;
            int y = i/tilesPerRow;
            int tile[] = ExtractTilesCPC.getTile(img, x*platform.TILE_WIDTH, y*PAKRoom.TILE_HEIGHT, imageName, platform, config);
            if (tile == null) continue;

            for(int k = 0;k<tile.length;k+=2) {
                int idx1 = colorPalette.indexOf(tile[k]);
                int idx2 = colorPalette.indexOf(tile[k+1]);
                if (idx1 == -1) {
                    throw new Exception("colorPalette ("+colorPalette+") does not contain color found in "+imageName+": " + tile[k]);
                }
                if (idx2 == -1) {
                    throw new Exception("colorPalette ("+colorPalette+") does not contain color found in "+imageName+": " + tile[k+1]);
                }
                int byteToWrite = CPCColors.mode0ColorTranslation[idx1]*2 + CPCColors.mode0ColorTranslation[idx2];
                data.add(byteToWrite);
            }
            tileIdx++;
        }
        
        return data;
    }    
}
