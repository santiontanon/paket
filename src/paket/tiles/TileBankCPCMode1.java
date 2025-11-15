/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.tiles;

import paket.platforms.CPCColors;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import paket.compiler.PAKETConfig;
import paket.pak.PAKRoom;
import paket.platforms.Platform;

/**
 *
 * @author santi
 */
public class TileBankCPCMode1 {
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
            int tile[] = ExtractTilesCPCMode1.getTile(img, x*platform.TILE_WIDTH, y*PAKRoom.TILE_HEIGHT, imageName, platform, config);
            if (tile == null) continue;

            for(int k = 0;k<tile.length;k+=4) {
                int idx1 = colorPalette.indexOf(tile[k]);
                int idx2 = colorPalette.indexOf(tile[k+1]);
                int idx3 = colorPalette.indexOf(tile[k+2]);
                int idx4 = colorPalette.indexOf(tile[k+3]);
                int byteToWrite = CPCColors.mode1ColorTranslation[idx1]*8 + 
                                  CPCColors.mode1ColorTranslation[idx2]*4 + 
                                  CPCColors.mode1ColorTranslation[idx3]*2 +
                                  CPCColors.mode1ColorTranslation[idx4];
                data.add(byteToWrite);
            }
            tileIdx++;
        }
        
        return data;
    }
}
