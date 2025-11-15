/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.pak;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 *
 * @author santi
 */
public class PAKCutsceneImage {
    public String name;
    public String assembler_name;
    public int width, height;
    public int nameTable[][] = null;
    public BufferedImage tiles = null;
    public boolean selfContainedTileSet = true;
    public List<String> platformOptions = null;
    
    
    public PAKCutsceneImage(String a_name, int a_w, int a_h, int a_nameTable[][],
                            BufferedImage a_tiles, boolean a_selfContainedTileSet,
                            List<String> a_platformOptions)
    {
        name = a_name;
        assembler_name = name.replace(" ", "_");
        assembler_name = assembler_name.replace("-", "_");
        width = a_w;
        height = a_h;
        nameTable = a_nameTable;
        tiles = a_tiles;
        selfContainedTileSet = a_selfContainedTileSet;
        platformOptions = a_platformOptions;
    }
}
