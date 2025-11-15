/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.platforms;

import java.awt.image.BufferedImage;
import paket.compiler.PAKETConfig;


/**
 *
 * @author santi
 */
public class MSXColors {
    
    public static int ALPHA_MASK = 0xff000000;
    public static int R_MASK = 0x00ff0000;
    public static int G_MASK = 0x0000ff00;
    public static int B_MASK = 0x000000ff;
    
    public static int ALPHA_SHIFT = 24;
    public static int R_SHIFT = 16;
    public static int G_SHIFT = 8;
    public static int B_SHIFT = 0;
    
    public static int tolerance = 32;
    
    /*
    public static int MSX1Palette[][] = {
                                {0,0,0},
                                {0,0,0},
                                {43,221,81},
                                {100,255,118},
                                {81,81,255},
                                {118,118,255},
                                {221,81,81},
                                {81,255,255},
                                {255,81,81},
                                {255,118,118},
                                {255,221,81},
                                {255,255,160},
                                {43,187,43},
                                {221,81,187},
                                {221,221,221},
                                {255,255,255}};     
    */
    
    public static int MSX1Palette[][]={
                                {0,0,0},              // Transparent
                                {0,0,0},              // Black
                                {36,219,36},          // Medium Green
                                {109,255,109},        // Light Green
                                {36,36,255},          // Dark Blue
                                {73,109,255},         // Light Blue
                                {182,36,36},          // Dark Red
                                {73,219,255},         // Cyan
                                {255,36,36},          // Medium Red
                                {255,109,109},        // Light Red
                                {219,219,36},         // Dark Yellow
                                {219,219,146},        // Light Yellow
                                {36,146,36},          // Dark Green
                                {219,73,182},         // Magenta
                                {182,182,182},        // Grey
                                {255,255,255}};       // White        
    
    public static final int COLOR_WHITE = 15;
    
    public static int getImageColor(BufferedImage sourceImage, int x, int y, PAKETConfig config)
    {
        if (x < 0 || x >= sourceImage.getWidth() || 
            y < 0 || y >= sourceImage.getHeight()) {
            config.error("MSXColors.getImageColor: Coordinates out of bounds " + x + ", " + y + ", and image is " + sourceImage.getWidth() + " * " + sourceImage.getHeight());
        }
        int colorRGB = sourceImage.getRGB(x, y);
        int r = (colorRGB & 0xff0000)>>16;
        int g = (colorRGB & 0x00ff00)>>8;
        int b = colorRGB & 0x0000ff;
        int a = (colorRGB & 0xff000000)>>24;
        if (a == 0) return -1;  // -1 is transparent
        int color = findColor(r, g, b, x, y, config);
        if (color == -1) {
            config.error("MSXColors.getImageColor: Color not found at " + x + ", " + y);
        }
        return color;
    }
    
    
    public static int findColor(int r, int g, int b, int x, int y, PAKETConfig config) {
        for(int i = 0;i<MSX1Palette.length;i++) {
            if (Math.abs(r-MSX1Palette[i][0]) < tolerance &&
                Math.abs(g-MSX1Palette[i][1]) < tolerance &&
                Math.abs(b-MSX1Palette[i][2]) < tolerance) {
                return i;
            }
        }
        config.error("Color not found!! " + r +"," + g + "," + b + "  at " + x + ", " + y);
        return -1;
    }    
}
