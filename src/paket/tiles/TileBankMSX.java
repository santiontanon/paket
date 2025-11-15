/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.tiles;

import paket.compiler.PAKET;
import paket.compiler.PAKETConfig;
import java.awt.image.BufferedImage;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import paket.compiler.PAKETCompiler;
import paket.pak.PAKRoom;
import paket.platforms.MSX;
import paket.platforms.Platform;
import paket.util.Z80Assembler;

/**
 *
 * @author santi
 */
public class TileBankMSX {
    public static int generateAndCompressTilesAssemblerFile(
                             BufferedImage img, 
                             int t1, int t2,
                             String path, String fileName,
                             boolean writeNTiles,
                             boolean collateAttributes,
                             MSX msxPlatform,
                             PAKETConfig config) throws Exception {
        return generateAndCompressTilesAssemblerFileWithPrefix(null, img, t1, t2, path, fileName, writeNTiles, collateAttributes, msxPlatform, config);
    }
    
    
    public static int generateAndCompressTilesAssemblerFileWithPrefix(
                             String prefix, 
                             BufferedImage img,
                             int t1, int t2,
                             String path, String fileName,
                             boolean writeNTiles,
                             boolean collateAttributes,
                             MSX msxPlatform,
                             PAKETConfig config) throws Exception {
        int tilesPerRow = img.getWidth()/msxPlatform.TILE_WIDTH;
        FileWriter fw = new FileWriter(path + fileName + ".asm");
        boolean anyTile = false;
        if (prefix != null) fw.write(prefix);
        if (writeNTiles) {
            List<Integer> data = new ArrayList<>();
            data.add(t2-t1);
            Z80Assembler.dataBlockToAssembler(data, "n_tiles", fw, 16);
        }
        if (collateAttributes) {
            // patterns && attributes collated 2 by 2:
            for(int i = t1;i<t2;i+=2) {
                int x = i%tilesPerRow;
                int y = i/tilesPerRow;
                int tile1[] = msxPlatform.tileExtractor.getTile(img, x*msxPlatform.TILE_WIDTH, y*PAKRoom.TILE_HEIGHT, msxPlatform.TILE_WIDTH);
                int tile2[] = msxPlatform.tileExtractor.getTile(img, (x+1)*msxPlatform.TILE_WIDTH, y*PAKRoom.TILE_HEIGHT, msxPlatform.TILE_WIDTH);
                if (tile1 == null || tile2 == null) continue;

                List<Integer> data = new ArrayList<>();
                int bytes1[] = patternBytes(tile1);
                int bytes2[] = patternBytes(tile2);
                for(int j = 0; j < 8 ; j++) data.add(bytes1[j]);
                for(int j = 0; j < 8 ; j++) data.add(bytes2[j]);
                for(int j = 0; j < 8 ; j++) data.add(bytes1[j+8]);
                for(int j = 0; j < 8 ; j++) data.add(bytes2[j+8]);
                Z80Assembler.dataBlockToAssembler(data, "pattern_" + i, fw, 16);
                anyTile = true;
            }
        } else {
            // patterns:
            for(int i = t1;i<t2;i++) {
                int x = i%tilesPerRow;
                int y = i/tilesPerRow;
                int tile[] = msxPlatform.tileExtractor.getTile(img, x*msxPlatform.TILE_WIDTH, y*PAKRoom.TILE_HEIGHT, msxPlatform.TILE_WIDTH);
                if (tile == null) continue;

                List<Integer> data = new ArrayList<>();
                int bytes[] = patternBytes(tile);
                for(int j = 0; j < bytes.length/2 ; j++) {
                    data.add(bytes[j]);
                }
                Z80Assembler.dataBlockToAssembler(data, "pattern_" + i, fw, 16);
                anyTile = true;
            }
            // attributes:
            for(int i = t1;i<t2;i++) {
                int x = i%tilesPerRow;
                int y = i/tilesPerRow;
                int tile[] = msxPlatform.tileExtractor.getTile(img, x*msxPlatform.TILE_WIDTH, y*PAKRoom.TILE_HEIGHT, msxPlatform.TILE_WIDTH);
                if (tile == null) continue;

                List<Integer> data = new ArrayList<>();
                int bytes[] = patternBytes(tile);
                for(int j = 0; j < bytes.length/2 ; j++) {
                    data.add(bytes[j+bytes.length/2]);
                }
                Z80Assembler.dataBlockToAssembler(data, "attributes_" + i, fw, 16);
                anyTile = true;
            }
        }
        fw.close();
        
        if (anyTile) {
            PAKETCompiler.callMDL(new String[]{path + fileName + ".asm", "-bin", path + fileName + ".bin"}, config);
            int size = PAKET.compress(path + fileName + ".bin", path + fileName, config);
            return size;
        } else {
            return 0;
        }
    }
    
    
    public static int generateAndCompressTilesAssemblerFile(
                             List<int []> tileData, 
                             int t1, int t2,
                             String path, String fileName,
                             boolean writeNTiles,
                             boolean collateAttributes,
                             PAKETConfig config) throws Exception {
        return generateAndCompressTilesAssemblerFileWithPrefix(null, tileData, t1, t2, path, fileName, writeNTiles, collateAttributes, config);
    }    
    
    
    public static int generateAndCompressTilesAssemblerFileWithPrefix(
                             String prefix, 
                             List<int []> tileData,
                             int t1, int t2,
                             String path, String fileName,
                             boolean writeNTiles,
                             boolean collateAttributes,
                             PAKETConfig config) throws Exception {
        List<Integer> data = new ArrayList<>();
        FileWriter fw = null;
        if (path != null && fileName != null) {
            fw = new FileWriter(path + fileName + ".asm");
        }
        boolean anyTile = false;
        if (fw != null && prefix != null) fw.write(prefix);
        if (prefix != null && fw == null) {
            throw new Exception("prefix != null, but no file path specified in generateAndCompressTilesAssemblerFileWithPrefix!");
        }
        if (writeNTiles) {
            data.add(t2-t1);
        }
        if (collateAttributes) {
            // patterns && attributes collated 2 by 2:
            for(int i = t1;i<t2 && i<tileData.size();i++) {
                int tile[] = tileData.get(i);
                // first 16 bytes are patterns, second 16 bytes are attributes:
                for(int j = 0;j<tile.length;j++) data.add(tile[j]);
                anyTile = true;
            }
        } else {
            // patterns:
            for(int i = t1;i<t2;i++) {
                int tile[] = tileData.get(i);
                // first 16 bytes are patterns, second 16 bytes are attributes:
                for(int j = 0;j<8*2;j++) data.add(tile[j]);
                anyTile = true;
            }
            // attributes:
            for(int i = t1;i<t2;i++) {
                int tile[] = tileData.get(i);
                // first 16 bytes are patterns, second 16 bytes are attributes:
                for(int j = 8*2;j<8*4;j++) data.add(tile[j]);
                anyTile = true;
            }
        }
        if (fw != null) {
            Z80Assembler.dataBlockToAssembler(data, "tile_data", fw, 16);
            fw.close();
        }        
        
        if (anyTile) {
            if (fw != null) {
                PAKETCompiler.callMDL(new String[]{path + fileName + ".asm", "-bin", path + fileName + ".bin"}, config);
                return PAKET.compress(path + fileName + ".bin", path + fileName, config);
            } else {
                return PAKET.estimateCompressedSize(data, config);
            }
        } else {
            return 0;
        }
    }    
    
    
    public static int[] patternBytes(int pixels[]) throws Exception
    {
        if ((pixels.length % 8*8) != 0) throw new Exception("Expected an array of (k*8)x8 pixels");
        int width = pixels.length / 8;
        int width_in_bytes = width / 8;
        int bytes[] = new int[2 * width_in_bytes * 8];
        for(int i = 0;i<width_in_bytes;i++) {
            for(int j = 0;j<8;j++) {
                int rowbytes[] = pixelRowBytes(pixels, (i + j*width_in_bytes)*8);
                bytes[j + i*8] = rowbytes[0];  // pattern
                bytes[j + i*8 + width] = rowbytes[1];  // attribute
            }
        }
        
        return bytes;
    } 


    // returns the pattern and attribute byte for a row of 8 pixels
    public static int[] pixelRowBytes(int pixels[], int offset) throws Exception
    {
        List<Integer> colors = new ArrayList<>();
        for(int k = 0;k<8;k++) {
            if (!colors.contains(pixels[offset+k])) colors.add(pixels[offset+k]);
        }
        while(colors.size()<2) colors.add(0);
        if (colors.size()>2) throw new Exception("more than 2 colors in an 8x1 block!!! " + colors);
        Collections.sort(colors);
        int colorbyte = colors.get(0) + colors.get(1)*16;                    
        int patternbyte = 0;
        int mask = 1;
        for(int k = 0;k<8;k++) {
            if (colors.get(0) != pixels[offset+(7-k)]) patternbyte += mask;
            mask *= 2;
        }        
        
        return new int[]{patternbyte, colorbyte};
    }
    
    
    public static List<Integer> generateCutSceneTileData(
                             BufferedImage img,
                             ExtractTilesMSX tileExtractor,
                             String imageName, Platform platform, PAKETConfig config) throws Exception {
        List<Integer> data = new ArrayList<>();
        int tilesPerRow = img.getWidth()/(platform.TILE_WIDTH);
        int rows = img.getHeight()/PAKRoom.TILE_HEIGHT;
        int tileIdx = 0;
        for(int i = 0;i<tilesPerRow*rows;i++) {
            int x = i%tilesPerRow;
            int y = i/tilesPerRow;
            int tile[] = tileExtractor.getTile(img, x*platform.TILE_WIDTH, y*PAKRoom.TILE_HEIGHT, platform.TILE_WIDTH);
            if (tile == null) continue;

            // add tile bytes:
            int bytes[] = patternBytes(tile);
            for(int j = 0; j < bytes.length ; j++) {
                data.add(bytes[j]);
            }
            tileIdx++;
        }
        
        return data;
    }    
    
}
