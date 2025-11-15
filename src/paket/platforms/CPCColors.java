/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.platforms;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import paket.compiler.PAKETConfig;


/**
 *
 * @author santi
 */
public class CPCColors {
    
    public static int tolerance = 32;
    public static int CPCMode0Palette[][] = {{0,0,0},
                                      {0,0,85},
                                      {0,0,255},
                                      {109,0,0},
                                      {109,0,85},
                                      {109,0,255},  // 5: violeta
                                      {255,0,0},
                                      {255,0,85},
                                      {255,0,255},  // 8: pink
                                      
                                      {0,109,0},  // 9
                                      {0,109,85},  // 10: teal
                                      {0,109,255},
                                      {109,109,0},
                                      {109,109,85},  // 13: gray
                                      {109,109,255},
                                      {255,109,0},
                                      {255,109,85},  // 16: carne
                                      {255,109,255},  // 17: pink
                                      
                                      {0,255,0},  // 18
                                      {0,255,85},  // 19: 
                                      {0,255,255},
                                      {109,255,0},
                                      {109,255,85},
                                      {109,255,255},  // 23: turquoise
                                      {255,255,0},
                                      {255,255,85},  // 25: 
                                      {255,255,255}}; // 26: white    
    public static int CPCHardwareColorCodes[] = {0x54, 0x44, 0x55, 0x5c, 0x58, 0x5d, 0x4c, 0x45, 0x4d,
                                                 0x56, 0x46, 0x57, 0x5e, 0x40, 0x5f, 0x4e, 0x47, 0x4f,
                                                 0x52, 0x42, 0x53, 0x5a, 0x59, 0x5b, 0x4a, 0x43, 0x4b};
    public static int mode0ColorTranslation[] = {0x00,0x40,0x04,0x44,
                                                 0x10,0x50,0x14,0x54,
                                                 0x01,0x41,0x05,0x45,
                                                 0x11,0x51,0x15,0x55,};
    public static int mode1ColorTranslation[] = {0x00, 0x10, 0x01, 0x11};
    
    
    public static int getImageColor(BufferedImage sourceImage, int x, int y, String imageName, PAKETConfig config)
    {
        int colorRGB = sourceImage.getRGB(x, y);
        int r = (colorRGB & 0xff0000)>>16;
        int g = (colorRGB & 0x00ff00)>>8;
        int b = colorRGB & 0x0000ff;
        int a = (colorRGB & 0xff000000)>>24;
        if (a == 0) return -1;  // -1 is transparent
        int color = findColor(r, g, b, x, y, imageName, config);
        if (color == -1) {
            config.error("Color not found in "+imageName+" at " + x + ", " + y);
        }
        return color;
    }
    

    public static int getImageColorIndex(BufferedImage sourceImage, int x, int y, List<Integer> colorPalette, String imageName, PAKETConfig config)
    {
        int colorRGB = sourceImage.getRGB(x, y);
        int r = (colorRGB & 0xff0000)>>16;
        int g = (colorRGB & 0x00ff00)>>8;
        int b = colorRGB & 0x0000ff;
        int color = findColor(r, g, b, x, y, imageName, config);
        int idx = colorPalette.indexOf(color);
        return idx;
    }
    
        
    public static List<Integer> findColors(BufferedImage image, String imageName, PAKETConfig config)
    {
        List<Integer> palette = new ArrayList<>();
        
        for(int i = 0;i<image.getHeight();i++) {
            for(int j = 0;j<image.getWidth();j++) {
                int color = image.getRGB(j, i);
                int r = (color & 0xff0000)>>16;
                int g = (color & 0x00ff00)>>8;
                int b = color & 0x0000ff;
                int a = (color & 0xff000000)>>24;
                if (a != 0) {
                    int idx = findColor(r, g, b, j, i, imageName, config);
                    if (idx == -1) {
                        config.error("CPCColors.findColors: Color not found in "+imageName+"!! " + r +"," + g + "," + b + " at " + j + ", " + i);
                    } else {
                        if (!palette.contains(idx)) palette.add(idx);
                    }
                }
            }
        }
        
        Collections.sort(palette);
        
        return palette;
    }
      

    public static int findColor(int r, int g, int b, int x, int y, String imageName, PAKETConfig config) {
        for(int i = 0;i<CPCMode0Palette.length;i++) {
            if (Math.abs(r-CPCMode0Palette[i][0]) < tolerance &&
                Math.abs(g-CPCMode0Palette[i][1]) < tolerance &&
                Math.abs(b-CPCMode0Palette[i][2]) < tolerance) {
                return i;
            }
        }
        config.error("CPCColors.findColor: Color not found in "+imageName+"!! " + r +"," + g + "," + b + "  at " + x + ", " + y);
        return -1;
    }
    
    
    public static int Mode02ColorBlockByte(int color1, int color2, List<Integer> palette) throws Exception
    {
        int idx1 = palette.indexOf(color1);
        int idx2 = palette.indexOf(color2);
        if (idx1 == -1 && color1 != -1) {
            // ERROR!
            throw new Exception("color1 " + color1 + " not found in palette " + palette);
        }
        if (idx2 == -1 && color2 != -1) {
            // ERROR!
            throw new Exception("color2 " + color2 + " not found in palette " + palette);
        }
        idx1++; // add 1 to consider transparency
        idx2++; // add 1 to consider transparency
        int byteToWrite = CPCColors.mode0ColorTranslation[idx1]*2 + CPCColors.mode0ColorTranslation[idx2];
        return byteToWrite;
    }
    

    public static List<Integer> paletteFromImagesInFolder(String folder, PAKETConfig config) throws Exception
    {
        List<Integer> palette =  paletteFromImagesInFolder(folder, new ArrayList<>(), config);
        Collections.sort(palette);
        if (palette.size()>15) throw new Exception("Too many colors in the source images ("+palette.size()+")!");
        while(palette.size()<15) palette.add(0);
        return palette;
    }   
        
    
    public static List<Integer> paletteFromImagesInFolder(String folder,
                                                          List<Integer> palette,
                                                          PAKETConfig config) throws Exception
    {
        File f = new File(folder);
        if (!f.isDirectory()) throw new Exception(folder + " is not a directory!");
        for(File f2:f.listFiles()) {
            if (f2.getName().endsWith(".png")) {
                BufferedImage img = ImageIO.read(f2);
                List<Integer> palette2 = findColors(img, f2.getName(), config);
                for(int color:palette2) {
                    if (!palette.contains(color)) {
                        palette.add(color);
                        config.info("        " + color);
                    }
                }
                config.info("    " + f2.getName() + " contains " + palette2.size() + " colors ("+palette2+"), total: " + palette.size());
            }
            if (f2.isDirectory()) {
                paletteFromImagesInFolder(folder + File.separator + f2.getName(), palette, config);
            }
        }
                
        return palette;
    }


    public static List<Integer> paletteFromImages(List<String> images, PAKETConfig config) throws Exception
    {
        List<Integer> palette =  paletteFromImageFileNames(images, new ArrayList<>(), config);
        Collections.sort(palette);
        if (palette.size()>15) throw new Exception("Too many colors in the source images ("+palette.size()+")!");
        while(palette.size()<15) palette.add(0);
        return palette;
    }   


    public static List<Integer> paletteFromImagesUnlimited(List<String> images, PAKETConfig config) throws Exception
    {
        List<Integer> palette =  paletteFromImageFileNames(images, new ArrayList<>(), config);
        Collections.sort(palette);
        while(palette.size()<16) palette.add(0);
        return palette;
    }   
    
    
    public static List<Integer> paletteFromImageFileNames(List<String> images,
                                                  List<Integer> palette,
                                                  PAKETConfig config) throws Exception
    {
        for(String fileName:images) {
            BufferedImage img = ImageIO.read(new File(fileName));
            List<Integer> palette2 = findColors(img, fileName, config);
            config.info("palette from " + fileName + ": " + palette2);
            for(int color:palette2) {
                if (!palette.contains(color)) {
                    palette.add(color);
                }
            }
        }
                
        return palette;
    }
    
    
    public static List<Integer> paletteFromImages(List<BufferedImage> images,
                                                  List<Integer> palette,
                                                  String imageName, 
                                                  PAKETConfig config) throws Exception
    {
        for(BufferedImage img:images) {
            List<Integer> palette2 = findColors(img, imageName, config);
            for(int color:palette2) {
                if (!palette.contains(color)) {
                    palette.add(color);
                }
            }
        }
                
        return palette;
    }    
}
