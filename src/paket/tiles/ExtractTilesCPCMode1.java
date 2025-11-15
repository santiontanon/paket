/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.tiles;

import paket.platforms.CPCColors;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import paket.compiler.PAKETConfig;
import paket.pak.PAKRoom;
import paket.platforms.Platform;

/**
 *
 * @author santi
 */
public class ExtractTilesCPCMode1 {
    public static void extractTiles(String inputImagesNames[], String outputImageName, int outputWidth, Platform platform, PAKETConfig config) throws Exception
    {
        extractTilesRectangle(inputImagesNames, outputImageName, outputWidth, -1, -1, -1, -1, false, platform, config);
    }

    
    public static List<int[][]> extractTilesRectangle(String inputImagesNames[], String outputImageName, int outputWidth,
            int x, int y, int width, int height, boolean lookForMirroredTiles, Platform platform, PAKETConfig config) throws Exception
    {
        List<int[]> tiles = new ArrayList<>();
        List<int[][]> nameTables = new ArrayList<>();
        
        // we add an empty tile (in this way, we ensure the empty tile is tile 0)
        int emptyTile[] = new int[PAKRoom.TILE_HEIGHT*platform.TILE_WIDTH];
        for(int i = 0;i<emptyTile.length;i++) emptyTile[i] = 0;
        tiles.add(emptyTile);
        
        for(String iin:inputImagesNames) {
            config.info("Reading: " + iin);
            BufferedImage sourceImage = ImageIO.read(new File(iin));
            List<Integer> indexes = findTiles(sourceImage, tiles, iin, platform, config);
            int x1 = Math.max(0, x);
            int y1 = Math.max(0, y);
            int x2 = sourceImage.getWidth()/platform.TILE_WIDTH;
            int y2 = sourceImage.getHeight()/PAKRoom.TILE_HEIGHT;
            if (width > 0) x2 = x1+width;
            if (height > 0) y2 = y1+height;
            int nameTable[][] = new int[width][height];
            for(int i = y1;i<y2;i++) {
                String str = "";
                for(int j = x1;j<x2;j++) {
                    int idx = (1+indexes.get(j+i*(sourceImage.getWidth()/(platform.TILE_WIDTH))));
                    nameTable[(j-x1)][(i-y1)] = idx;
                    str += idx + ",";
                }
                config.info(str);
            }
            config.info("Tiles so far: " + tiles.size());
            nameTables.add(nameTable);
        }
        
        // Look for mirrored tiles:
        for(int i = 0;i<tiles.size();i++) {
            for(int j = i+1;j<tiles.size();j++) {
                boolean mirrorMatch = true;
                for(int ty = 0;ty<PAKRoom.TILE_HEIGHT;ty++) {
                    for(int tx = 0;tx<platform.TILE_WIDTH;tx++) {
                        if (tiles.get(i)[ty*platform.TILE_WIDTH+tx] != 
                            tiles.get(j)[ty*platform.TILE_WIDTH+(platform.TILE_WIDTH-(tx+1))]) {
                            mirrorMatch = false;
                            break;
                        }
                    }
                }
                if (mirrorMatch) {
                    config.info("Tile " + j + " is " + i + " mirrored.");
                    break;
                }
            }
        }
                
        int tilesPerRow = outputWidth/(platform.TILE_WIDTH);
        int outputHeight = ((tiles.size()+(tilesPerRow-1))/tilesPerRow)*PAKRoom.TILE_HEIGHT;
        BufferedImage img = new BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_ARGB);
        for(int i = 0;i<tiles.size();i++) {
            int tilex = (i%tilesPerRow)*platform.TILE_WIDTH;
            int tiley = (i/tilesPerRow)*PAKRoom.TILE_HEIGHT;
            drawTile(img, tiles.get(i), tilex, tiley, platform);
        }
        ImageIO.write(img, "png", new File(outputImageName));
        return nameTables;
    }
    
    
    public static List<Integer> findTiles(BufferedImage img, List<int[]> tiles, String imageName, Platform platform, PAKETConfig config) 
    {
        List<Integer> indexes = new ArrayList<>();
        for(int i = 0;i<img.getHeight();i+=PAKRoom.TILE_HEIGHT) {
            for(int j = 0;j<img.getWidth();j+=platform.TILE_WIDTH) {
                int tile[] = getTile(img, j, i, imageName, platform, config);
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
                        config.info("    Tiles very similar to each other ("+minDiff+" pixels of difference): " + tiles.size() + " and " + min_diff_index);
                    }
                    found_index = tiles.size();
                    tiles.add(tile);
                }
                indexes.add(found_index);
            }
        }
        return indexes;
    }    


    public static int[] getTile(BufferedImage img, int x, int y, String imageName, Platform platform, PAKETConfig config)
    {
        int tile[] = new int[platform.TILE_WIDTH*PAKRoom.TILE_HEIGHT];
        
        int offs = 0;
        boolean allTransparent = true;
        for(int i = 0;i<PAKRoom.TILE_HEIGHT;i++) {
            for(int j = 0;j<platform.TILE_WIDTH;j++) {
                tile[offs] = CPCColors.getImageColor(img, x+j, y+i, imageName, config);
                if (tile[offs]>=0) allTransparent = false;
                offs++;
            }
        }
        if (allTransparent) return null;
        return tile;
    }
    
    
    public static boolean tileEquals(int t1[], int t2[])
    {
        for(int i = 0;i<t1.length;i++) {
            if (t1[i] != t2[i]) return false;
        }
        return true;
    }


    public static int numberOfDifferentPixels(int t1[], int t2[])
    {
        int differences = 0;
        for(int i = 0;i<t1.length;i++) {
            if (t1[i] != t2[i]) differences++;
        }
        return differences;
    }
    
    
    public static void drawTile(BufferedImage img, int tile[], int x, int y, Platform platform)
    {
        for(int i = 0;i<PAKRoom.TILE_HEIGHT;i++) {
            for(int j = 0;j<platform.TILE_WIDTH;j++) {
                int color = tile[j+i*platform.TILE_WIDTH];
                if (color >= 0) {
                    int r = CPCColors.CPCMode0Palette[color][0];
                    int g = CPCColors.CPCMode0Palette[color][1];
                    int b = CPCColors.CPCMode0Palette[color][2];
                    int colorRGB = b + (g<<8)+(r<<16);
                    img.setRGB(x+j, y+i, colorRGB | 0xff000000);
                }
            }
        }
    }

}
