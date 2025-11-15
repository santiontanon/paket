/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.pak;

import paket.platforms.Platform;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import paket.compiler.PAKETConfig;

/**
 *
 * @author santi
 */
public class PAKObject {
    public int ID = 0;
    public String name = null;
    public int x, y, depth;
    public int state = PAKObjectType.STATE_NONE;
    public int direction = PAKObjectType.DIRECTION_FRONT;
    public PAKObjectType type = null;
    
    
    public PAKObject(int a_ID, String a_name, int a_x, int a_y, int a_depth, int a_state, int a_direction, PAKObjectType a_type)
    {
        ID = a_ID;
        name = a_name;
        x = a_x;
        y = a_y;
        depth = a_depth;
        state = a_state;
        direction = a_direction;
        type = a_type;
    }
    
    
    public void draw(BufferedImage img, Platform targetSystem, PAKETConfig config)
    {
        Graphics g = img.getGraphics();
        BufferedImage object_img = type.getFirstFrameImage(state, direction, config);
        if (object_img != null) {
            g.drawImage(object_img, x, y, null);
        }
    }
}
