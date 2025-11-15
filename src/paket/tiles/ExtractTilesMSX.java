/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.tiles;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import paket.compiler.PAKETConfig;
import paket.pak.PAKRoom;
import paket.platforms.MSX;
import paket.platforms.MSXColors;

/**
 *
 * @author santi
 */
public class ExtractTilesMSX {
    PAKETConfig config;
    MSX msx;
    
    public ExtractTilesMSX(MSX a_msx, PAKETConfig a_config)
    {
        config = a_config;
        msx = a_msx;
    }
    
    public void extractTiles(String inputImagesNames[], String outputImageName, int outputWidth, int tileWidth) throws Exception
    {        
        List<int[]> tiles = new ArrayList<>();
        
        // we add an empty tile (in this way, we ensure the empty tile is tile 0)
        int emptyTile[] = new int[PAKRoom.TILE_HEIGHT*msx.TILE_WIDTH];
        for(int i = 0;i<emptyTile.length;i++) emptyTile[i] = 0;
        tiles.add(emptyTile);
        
        for(String iin:inputImagesNames) {
            config.info("Reading: " + iin);
            BufferedImage sourceImage = ImageIO.read(new File(iin));
            List<Integer> indexes = findTiles(sourceImage, tileWidth, tiles);
            for(int i = 0;i<sourceImage.getHeight()/PAKRoom.TILE_HEIGHT;i++) {
                String str = "";
                for(int j = 0;j<sourceImage.getWidth()/msx.TILE_WIDTH;j++) {
                    str += (1+indexes.get(j+i*(sourceImage.getWidth()/msx.TILE_WIDTH))) + ",";
                }
                config.info(str);
            }
            config.info("Tiles so far: " + tiles.size());
        }
                
        int tilesPerRow = outputWidth/msx.TILE_WIDTH;
        int outputHeight = ((tiles.size()+(tilesPerRow-1))/tilesPerRow)*PAKRoom.TILE_HEIGHT;
        BufferedImage img = new BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_ARGB);
        for(int i = 0;i<tiles.size();i++) {
            int x = (i%tilesPerRow)*msx.TILE_WIDTH;
            int y = (i/tilesPerRow)*PAKRoom.TILE_HEIGHT;
            drawTile(img, tiles.get(i), x, y, msx.TILE_WIDTH);
        }
        ImageIO.write(img, "png", new File(outputImageName));
    }
        

    public List<Integer> findTiles(BufferedImage img, int tileWidth, List<int[]> tiles) 
    {
        return findTiles(img, 0, 0, img.getWidth(), img.getHeight(), tileWidth, tiles);
    }
    
    
    public List<Integer> findTiles(BufferedImage img, int x0, int y0, int x1, int y1, int tileWidth, List<int[]> tiles) 
    {
        List<Integer> indexes = new ArrayList<>();
        for(int i = y0;i<y1;i+=PAKRoom.TILE_HEIGHT) {
            for(int j = x0;j<x1;j+=tileWidth) {
                int tile[] = getTile(img, j, i, tileWidth);
                if (tile == null) {
                    indexes.add(0);
                    continue;
                }
                int minDiff = -1;
                int min_diff_index = -1;
                int found_index = -1;
                for(int index = 0;index<tiles.size();index++) {
                    int tile2[] = tiles.get(index);
                    int diff = numberOfDifferentPixels(tile, tile2);
                    if (diff == 0) {
                        found_index = index;
                        break;
                    }
                    if (minDiff == -1 || diff < minDiff) {
                        minDiff = diff;
                        min_diff_index = index;
                    }
                }
                if (found_index == -1) {
                    if (minDiff>=0 && minDiff<=5) {
                        config.info("    Tiles very similar to each other: " + tiles.size() + " and " + min_diff_index);
                    }
                    found_index = tiles.size();
                    tiles.add(tile);
                }
                indexes.add(found_index);
            }
        }
        return indexes;
    }    


    public int[] getTile(BufferedImage img, int x, int y, int tileWidth)
    {
        int tile[] = new int[tileWidth*PAKRoom.TILE_HEIGHT];
        
        int offs = 0;
        boolean allTransparent = true;
        for(int i = 0;i<PAKRoom.TILE_HEIGHT;i++) {
            for(int j = 0;j<tileWidth;j++) {
                tile[offs] = MSXColors.getImageColor(img, x+j, y+i, config);
                if (tile[offs]>=0) allTransparent = false;
                offs++;
            }
        }
        if (allTransparent) return null;
        return tile;
    }
    
    
    public boolean tileEquals(int t1[], int t2[])
    {
        for(int i = 0;i<t1.length;i++) {
            if (t1[i] != t2[i]) return false;
        }
        return true;
    }


    public int numberOfDifferentPixels(int t1[], int t2[])
    {
        int differences = 0;
        for(int i = 0;i<t1.length;i++) {
            if (t1[i] != t2[i]) differences++;
        }
        return differences;
    }
    
    
    public void drawTile(BufferedImage img, int tile[], int x, int y, int tileWidth)
    {
        for(int i = 0;i<PAKRoom.TILE_HEIGHT;i++) {
            for(int j = 0;j<tileWidth;j++) {
                int color = tile[j + i * tileWidth];
                if (color >= 0) {
                    int r = MSXColors.MSX1Palette[color][0];
                    int g = MSXColors.MSX1Palette[color][1];
                    int b = MSXColors.MSX1Palette[color][2];
                    int colorRGB = b + (g<<8)+(r<<16);
                    img.setRGB(x+j, y+i, colorRGB | 0xff000000);
                }
            }
        }
    }

}
