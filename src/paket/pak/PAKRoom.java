/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.pak;

import paket.compiler.PAKET;
import paket.platforms.Platform;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import javax.imageio.ImageIO;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import paket.compiler.PAKETConfig;
import paket.util.Pair;
import paket.util.Z80Assembler;

/**
 *
 * @author santi
 */
public class PAKRoom {
    /*
    - Since collision is specified in 2x4 blocks, object graphics are multiples of 2x4 pixels.
    - Collision masks are only used on the Java side, however. So, once the collision mask is calculated, 
      sprites can be tightened to be just as big as they need to be and not a pixel more.
    - Setting this to "true" will perform such cropping before generating the assembler data.
    */
    public static final boolean CROP_TRANSPARENT_SPRITE_ROWS = true;
    
//    public static final int TILE_WIDTH = 8;
    public static final int TILE_HEIGHT = 8;

    public static final int FLIPPED_TILE_MASK = 0x80;
    
    public int screen_position_x = 0;
    public int screen_position_y = 0;
    public String ID = null;
    public boolean isSubRoom = false;
    public boolean defaultVerbIsUse = false;
    public int playerZoom = 0;  // default to "autozoom"
    public int width = 16;
    public int height = 8;
    public int background[][] = null;
    public int originalBackground[][] = null;  // Before we unify all the room tiles (this one can be used to refer to the "tiles" image)
    public int collisionBackground[][] = null;
    public Integer paletteID = null;  // If the target platform can specify palettes, this will be
                                      // the index of the palette to use in this room.
    public List<PAKObject> objects = new ArrayList<>();
    public List<PAKRule> rules = new ArrayList<>();
    public List<PAKRule> onLoadRules = new ArrayList<>();
    public List<PAKRule> onStartRules = new ArrayList<>();
    public String tilesFileName = null;
    public String collisionTilesFileName = null;
    public BufferedImage tiles = null;
    
    
    public PAKRoom(String a_ID, int a_w, int a_h)
    {
        ID = a_ID;
        width = a_w;
        height = a_h;
        background = new int[width][height];
        originalBackground = new int[width][height];
    }

    
    public PAKObject getObject(int ID)
    {
        for(PAKObject o:objects) {
            if (o.ID == ID) return o;
        }
        return null;
    }
    

    public BufferedImage render(Platform targetSystem, PAKETConfig config)
    {
        return renderUpToDepthAndObject(Integer.MAX_VALUE, null, true, true, targetSystem, config);
    }
    
    
    public BufferedImage render(Platform targetSystem, boolean renderBackground, boolean renderObjects, PAKETConfig config)
    {
        return renderUpToDepthAndObject(Integer.MAX_VALUE, null, renderBackground, renderObjects, targetSystem, config);
    }


    public BufferedImage renderUpToDepthAndObject(int depth, PAKObject obj, 
            boolean renderBackground, boolean renderObjects, Platform targetSystem, PAKETConfig config)
    {
        int tw = targetSystem.TILE_WIDTH;
        BufferedImage img = new BufferedImage(width*tw, height*TILE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics g = img.getGraphics();
        
        // render the background:
        if (renderBackground) {
            for(int i = 0;i<height;i++) {
                for(int j = 0;j<width;j++) {
                    int tile = originalBackground[j][i];
                    if (tile > 0) {
                        int tx = (tile-1)%(tiles.getWidth()/tw);
                        int ty = (tile-1)/(tiles.getWidth()/tw);
                        g.drawImage(tiles,
                                    j*tw, i*TILE_HEIGHT, (j+1)*tw, (i+1)*TILE_HEIGHT, 
                                    tx*tw, ty*TILE_HEIGHT, (tx+1)*tw, (ty+1)*TILE_HEIGHT, null);
                    }
                }
            }
        }
        
        // render the objects (by depth):
        if (renderObjects) {
            List<PAKObject> sortedObjects = new ArrayList<>();
            sortedObjects.addAll(objects);
            Collections.sort(sortedObjects, new Comparator<PAKObject>() {
                public int compare(PAKObject o1, PAKObject o2) {
                    if (o1.depth == o2.depth) {
                        return Integer.compare(o2.y+o2.type.getPixelHeight(o2.state, o2.direction),
                                               o1.y+o1.type.getPixelHeight(o1.state, o1.direction));
                    } else {
                        return Integer.compare(o1.depth, o2.depth);
                    }
                }
            });

            for(PAKObject o:sortedObjects) {
                if (o.depth<depth) {
                    o.draw(img, targetSystem, config);
                } else if (o.depth == depth) {
                    if (o == obj) break;
                    o.draw(img, targetSystem, config);
                }
            }
        }
        
        return img;
    }
    
    
    public List<Integer> toBytesForAssembler(List<PAKObjectType> objectTypes, int screen_width, 
                                             PAKGame game, 
                                             List<PAKDialogue> dialogues, 
                                             boolean log,
                                             Platform platform,
                                             PAKETConfig config) throws Exception
    {
        List<Integer> bytes = new ArrayList<>();
        
        if (log) config.info("toBytesForAssembler for room " + ID);

        // Also, check that there are no repeated IDs:
        if (!isSubRoom) {
            List<Integer> usedIDs = new ArrayList<>();
            for(PAKObject o:objects) {
                if (usedIDs.contains(o.ID)) {
                    throw new Exception("Two objects with ID " + o.ID + " found in room " + ID);
                }
                usedIDs.add(o.ID);
            }
        }
        
        // Sort objects according to the order in which they need to be drawn:
        Collections.sort(objects, (PAKObject po1, PAKObject po2) -> Integer.compare(po1.depth, po2.depth));
        
        int flags = 0;
        if (isSubRoom) flags |= 1;
        if (defaultVerbIsUse) flags |= 2;
        flags |= playerZoom * 4;
        platform.roomVideoMemoryStartAddress(this, bytes);
        bytes.add(flags);
        bytes.add(((screen_position_x + platform.GAME_AREA_DIMENSIONS[0] - platform.SCREEN_LEFT_MOST_COORDINATE)*platform.TILE_WIDTH));
        bytes.add(screen_position_y*8);
        bytes.add(width);
        bytes.add(height);
        if (paletteID != null) {
            bytes.add(paletteID);
        }
        bytes.add(objects.size());
        if (game.bankSets.size() > 1) {
            // If there is more than one bank set, we need to store which bank
            // set does this room use:
            bytes.add(game.roomToBankSet.get(ID));
        }
        if (platform.saveByColumns) {
            for(int j = 0;j<width;j++) {
                for(int i = 0;i<height;i++) {
                    bytes.add(background[j][i]-1);
                }
            }
        } else {
            for(int i = 0;i<height;i++) {
                for(int j = 0;j<width;j++) {
                    bytes.add(background[j][i]-1);
                }
            }
        }
        
        // collision mask:
        int collisionMask[] = calculateCollisionMask(log, platform, config);
        for(int j = 0;j<collisionMask.length;j++) {
            bytes.add(collisionMask[j]);
        }        

        // rules:
        if (log) config.info("Room Rules: " + rules.size() + " (using: " + sizeOfRulesData(rules, game.textIDHash, game, dialogues, platform) + " bytes)");
        int rules_size = sizeOfRulesData(rules, game.textIDHash, game, dialogues, platform);
        bytes.add(rules_size%256);
        bytes.add(rules_size/256);
        bytes.add(rules.size());
        for(PAKRule r:rules) {
            r.checkValidity(this, objectTypes, game.textIDHash, true);
            List<Integer> r_bytes = r.toBytesForAssembler(game.textIDHash, game, dialogues, true, true, platform);
            if (log) config.info("    Room rule size: " + r_bytes.size() + " -> " + r_bytes);
            bytes.addAll(r_bytes);
        }        
        
        for(int v:bytes) {
            if (v<0 || v>255) {
                throw new Exception("byte out of range before on room load/start rules!!! " + v);
            }
        }
        
        // on room load/start rules:
        {
            int on_load_rules_size = sizeOfRulesData(onLoadRules, game.textIDHash, game, dialogues, platform);
            int on_start_rules_size = sizeOfRulesData(onStartRules, game.textIDHash, game, dialogues, platform);
            int total_size = on_load_rules_size + on_start_rules_size;
            if (log) config.info("On room load rules: " + onLoadRules.size() + " (using: " + on_load_rules_size + " bytes)");
            bytes.add(total_size%256);
            bytes.add(total_size/256);
            bytes.add(onLoadRules.size());
            for(PAKRule r:onLoadRules) {
                r.checkValidity(this, objectTypes, game.textIDHash, true);
                List<Integer> r_bytes = r.toBytesForAssembler(game.textIDHash, game, dialogues, true, true, platform);
                if (log) config.info("    On room load rule size: " + r_bytes.size() + " -> " + r_bytes);
                bytes.addAll(r_bytes);
            }
            if (log) config.info("On room start rules: " + onStartRules.size() + " (using: " + on_start_rules_size + " bytes)");
            bytes.add(onStartRules.size());
            for(PAKRule r:onStartRules) {
                r.checkValidity(this, objectTypes, game.textIDHash, true);
                List<Integer> r_bytes = r.toBytesForAssembler(game.textIDHash, game, dialogues, true, true, platform);
                if (log) config.info("    On room start rule size: " + r_bytes.size() + " -> " + r_bytes);
                bytes.addAll(r_bytes);
            }
        }
                
        // objects:
        for(PAKObject o:objects) {
            bytes.add(o.x+o.type.getPixelWidth(o.state, o.direction));
            bytes.add(o.x);
            if (CROP_TRANSPARENT_SPRITE_ROWS) {
                Pair<Integer, Integer> crop = o.type.getCropCoordinates();
                int h = o.type.getPixelHeight(o.state, o.direction);
                if (crop.m_b - crop.m_a<h) h = crop.m_b - crop.m_a;
                bytes.add(o.y+crop.m_a);
                bytes.add(o.y+crop.m_a+h);
            } else {
                bytes.add(o.y);
                bytes.add(o.y+o.type.getPixelHeight(o.state, o.direction));                
            }
            bytes.add(o.ID);
            int type_index = objectTypes.indexOf(o.type);
            if (type_index == -1) {
                throw new Exception("Object type not found '" + o.type.name + "' when generating object bytes! This is probably an engine bug!");
            }
            bytes.add(type_index);
            bytes.add(0);   // this is a buffer byte, since we will later replace the index by a pointer
            String name = o.type.getInGameName();
            Pair<Integer, Integer> idx = game.textIDHash.get(name);
            if (idx == null) {
                throw new Exception("Object name '"+name+"' not found in the textIDHash!");
            }
            bytes.add(idx.m_a);   // these two values are to be replaced by the pointer to the object name
            bytes.add(idx.m_b);
            bytes.add(o.depth);
            bytes.add(o.state + o.direction*16);
            bytes.add(0);  // animation step
            bytes.add(((o.ID*4)+(o.x/8))%64);  // animation timer (we do not put them all to 0, so animations are staggered)
            Pair<Integer, Integer> description_idx = game.textIDHash.get(o.type.getDescription());
            bytes.add(description_idx.m_a);   
            bytes.add(description_idx.m_b);
        }
        
        return bytes;
    }
    
    
    public int sizeOfRulesData(List<PAKRule> l, HashMap<String, Pair<Integer,Integer>> textIDHash, PAKGame game, List<PAKDialogue> dialogues, Platform platform) throws Exception
    {
        int size = 1;   // # of rules
        for(PAKRule r:l) {
            List<Integer> r_bytes = r.toBytesForAssembler(textIDHash, game, dialogues, true, true, platform);
            size += r_bytes.size();
        }
        return size;
    }
    
    
    public int[] calculateCollisionMask(boolean log, Platform platform, PAKETConfig config) throws Exception 
    {
        if (platform.OBJECT_MASK_VERTICAL_RESOLUTION != TILE_HEIGHT / 2) {
            throw new Exception("calculateCollisionMask: the engine assumes that OBJECT_MASK_VERTICAL_RESOLUTION = TILE_HEIGHT / 2. But we have: " + platform.OBJECT_MASK_VERTICAL_RESOLUTION + " != " + TILE_HEIGHT + " / 2 ");
        }
        int mask_width = width;
        int collision_tile_multiplier = 1;
        if (platform.OBJECT_MASK_HORIZONTAL_RESOLUTION == platform.TILE_WIDTH / 4) {
            mask_width = width;
            collision_tile_multiplier = 1;
        } else if (platform.OBJECT_MASK_HORIZONTAL_RESOLUTION == platform.TILE_WIDTH / 2) {
            mask_width = (width + 1) / 2;
            collision_tile_multiplier = 2;
        } else {
            throw new Exception("calculateCollisionMask: the engine assumes that each tile is either 2 or 4 collision tiles in width, but we have OBJECT_MASK_HORIZONTAL_RESOLUTION = " + platform.OBJECT_MASK_HORIZONTAL_RESOLUTION + " and TILE_WIDTH = " + platform.TILE_WIDTH);
        }
        int mask[] = new int[mask_width*height];
        
        if (collisionBackground != null && collisionTilesFileName != null) {
            BufferedImage collisionTiles = ImageIO.read(new File(collisionTilesFileName));
            if (collisionTiles == null) {
                throw new Exception("calculateCollisionMask: Could not load collision tiles: " + collisionTilesFileName);
            }
            if (collisionTiles.getHeight() != 8) {
                throw new Exception("calculateCollisionMask: The PAKET compiler assumes that the collision masks file has all the tiles in a single row: " + collisionTilesFileName);
            }
            
            for(int j = 0;j<mask_width;j++) {
                for(int i = 0;i<height;i++) {
                    for(int x=0,bit=0; x<platform.TILE_WIDTH*collision_tile_multiplier; x+=platform.OBJECT_MASK_HORIZONTAL_RESOLUTION,bit++) {
                        int tileIdx = collisionBackground[j * collision_tile_multiplier + (x / platform.TILE_WIDTH)][i];
                        for(int y=0; y<8; y+=platform.OBJECT_MASK_VERTICAL_RESOLUTION) {
                            // Check if the pixel is white:
//                            if (tileIdx * platform.TILE_WIDTH + x > collisionTiles.getWidth()) {
//                                System.out.println("!!!");
//                            }
                            int color = collisionTiles.getRGB(tileIdx * platform.TILE_WIDTH + (x % platform.TILE_WIDTH), y);
                            if (color == 0xffffffff) {
                                int bit_to_set = (y & 0x04) + (bit&0x03);
                                int bit_mask = 1 << bit_to_set;
                                mask[i + j*height] |= bit_mask;
                            }
                        }
                    }
                }
            }
        }

        // add objects:
        for(PAKObject o:objects) {
            PAKObjectType ot = o.type;
            int o_mask[][] = ot.getCollisionMask(o.state, o.direction);
            if (o_mask != null) {
                for(int i = 0;i<o_mask[0].length;i++) {
                    for(int j = 0;j<o_mask.length;j++) {
                        if (o_mask[j][i] != 0) {
                            int x = o.x+j*platform.OBJECT_MASK_HORIZONTAL_RESOLUTION;
                            int y = o.y+i*platform.OBJECT_MASK_VERTICAL_RESOLUTION;
                            int offset = (y/TILE_HEIGHT) + (x/(platform.OBJECT_MASK_HORIZONTAL_RESOLUTION * 4))*height;
                            int bit_to_set = (y & 0x04) + ((x/platform.OBJECT_MASK_HORIZONTAL_RESOLUTION)&0x03);
                            int bit_mask = 1 << bit_to_set;
                            mask[offset] |= bit_mask;
                        }
                    }
                }
            }
        }
        
        // show the collision mask:
        if (log) {
            config.info("Collision mask:");
            for(int i = 0;i<height;i++) {
                String str = "";
                for(int j = 0;j<mask_width;j++) {
                    str += Z80Assembler.toHex8bit(mask[i+j*height], false) + " ";
                }
                config.info(str);
            }
        }

        return mask;
    }
    
    
    public List<Integer> getUsedTiles()
    {
        List<Integer> roomTiles = new ArrayList<>();
        for(int j = 0;j<width;j++) {
            for(int i = 0;i<height;i++) {
                if (!roomTiles.contains(background[j][i])) roomTiles.add(background[j][i]);
            }
        }
        return roomTiles;
    }    
    

    public static PAKRoom fromFile(File file, String folder, List<String> dataFolders, HashMap<String, PAKObjectType> objectTypes, String language, Platform platform, PAKETConfig config) throws Exception
    {
        Element root = new SAXBuilder().build(file).getRootElement();
        int w = Integer.parseInt(root.getAttributeValue("width"));
        int h = Integer.parseInt(root.getAttributeValue("height"));
        int collisionFirstTileID = 0;
        PAKRoom r = new PAKRoom(null, w, h);

        for(Object tileset_object:root.getChildren("tileset")) {
            Element tileset_xml = (Element)tileset_object;
            if (tileset_xml.getAttributeValue("name").contains("collision")) {
                String tmp = folder + File.separatorChar + tileset_xml.getChild("image").getAttributeValue("source");
                r.collisionTilesFileName =  PAKET.getFileName(tmp, dataFolders, config);        
                collisionFirstTileID = Integer.parseInt(tileset_xml.getAttributeValue("firstgid"));
            } else {
                String tmp = folder + File.separatorChar + tileset_xml.getChild("image").getAttributeValue("source");
                r.tilesFileName =  PAKET.getFileName(tmp, dataFolders, config);        
            }
        }
                
        File f = new File(r.tilesFileName);
        config.info("Room " + file.getCanonicalPath() + " tiles file: " + f.getAbsolutePath());
        r.tiles = ImageIO.read(f);
        Element bg_xml = null;
        Element collision_xml = null;
        for(Object layer_object:root.getChildren("layer")) {
            Element layer_xml = (Element)layer_object;
            if (layer_xml.getAttributeValue("name").equals("background")) {
                bg_xml = layer_xml;
            } else if (layer_xml.getAttributeValue("name").equals("collision")) {
                collision_xml = layer_xml;
            }
        }
        
        if (bg_xml != null) {
            String bg_data = bg_xml.getChild("data").getValue();
            StringTokenizer st = new StringTokenizer(bg_data, ",");
            for(int i = 0;i<h;i++) {
                for(int j = 0;j<w;j++) {
                    r.background[j][i] = Integer.parseInt(st.nextToken().trim());
                    r.originalBackground[j][i] = r.background[j][i];
                }
            }
        } else {
            throw new Exception("Could not find a tile layer called 'background' when loading Tiled room: " + file.getName());
        }
        
        // If there is a collision layer definition, use it to define the collision mask!
        if (collision_xml != null) {
            r.collisionBackground = new int[w][h];
            String collision_data = collision_xml.getChild("data").getValue();
            StringTokenizer st = new StringTokenizer(collision_data, ",");
            for(int i = 0;i<h;i++) {
                for(int j = 0;j<w;j++) {
                    r.collisionBackground[j][i] = Integer.parseInt(st.nextToken().trim()) - collisionFirstTileID;
                    if (r.collisionBackground[j][i] < 0) r.collisionBackground[j][i] = 0;
                }
            }
        }
        
        Element fg_xml = root.getChild("objectgroup");
        if (fg_xml != null) {
            for(Object o:fg_xml.getChildren("object")) {
                Element e = (Element)o;
                int ID = Integer.parseInt(e.getAttributeValue("id"));
                if (ID>=64) throw new Exception("Object ID in room is larger than 63! That will cause problems with 'current_action_text_id'");
                String name = e.getAttributeValue("name");
                int ox = Integer.parseInt(e.getAttributeValue("x"));
                int oy = Integer.parseInt(e.getAttributeValue("y"));
                int ow = Integer.parseInt(e.getAttributeValue("width"));
                int oh = Integer.parseInt(e.getAttributeValue("height"));
                int depth = 0;
                int state = PAKObjectType.STATE_NONE;
                int direction = PAKObjectType.DIRECTION_FRONT;
                if (e.getChild("properties") != null) {
                    for(Object o2:e.getChild("properties").getChildren("property")) {
                        Element e2 = (Element)o2;
                        if (e2.getAttributeValue("name").equals("depth")) {
                            depth = Integer.parseInt(e2.getAttributeValue("value"));
                        }
                        if (e2.getAttributeValue("name").equals("state")) {
                            String stateStr = e2.getAttributeValue("value");
                            for(int i = 0;i<PAKObjectType.stateNames.length;i++) {
                                if (PAKObjectType.stateNames[i] != null) {
                                    if (PAKObjectType.stateNames[i].equals(stateStr)) {
                                        state = i;
                                    }
                                }
                            }
                        }
                        if (e2.getAttributeValue("name").equals("direction")) {
                            if (e2.getAttributeValue("value").equals("back")) direction = PAKObjectType.DIRECTION_BACK;
                            else if (e2.getAttributeValue("value").equals("up")) direction = PAKObjectType.DIRECTION_BACK;
                            else if (e2.getAttributeValue("value").equals("right")) direction = PAKObjectType.DIRECTION_RIGHT;
                            else if (e2.getAttributeValue("value").equals("front")) direction = PAKObjectType.DIRECTION_FRONT;
                            else if (e2.getAttributeValue("value").equals("down")) direction = PAKObjectType.DIRECTION_FRONT;
                            else if (e2.getAttributeValue("value").equals("left")) direction = PAKObjectType.DIRECTION_LEFT;
                            else if (e2.getAttributeValue("value").equals("0")) direction = 0;
                            else if (e2.getAttributeValue("value").equals("1")) direction = 1;
                            else if (e2.getAttributeValue("value").equals("2")) direction = 2;
                            else if (e2.getAttributeValue("value").equals("3")) direction = 3;
                            else if (e2.getAttributeValue("value").equals("4")) direction = 4;
                            else if (e2.getAttributeValue("value").equals("5")) direction = 5;
                            else if (e2.getAttributeValue("value").equals("6")) direction = 6;
                            else if (e2.getAttributeValue("value").equals("7")) direction = 7;
                            else if (e2.getAttributeValue("value").equals("8")) direction = 8;
                            else if (e2.getAttributeValue("value").equals("9")) direction = 9;
                            else if (e2.getAttributeValue("value").equals("10")) direction = 10;
                            else if (e2.getAttributeValue("value").equals("11")) direction = 11;
                            else {
                                throw new Exception("Undefined state " + e2.getAttributeValue("value") + " when parsing " + file.getName());
                            }
                        }
                        if (e2.getAttributeValue("name").equals("number")) {
                            direction = Integer.parseInt(e2.getAttributeValue("value"));
                        }
                    }
                }
                PAKObjectType type = objectTypes.get(name);
                if (type == null) {
                    throw new Exception("Unknown object type '" + name + "' parsing "+file.getName()+"!");
                }
                // Make sure we have all the necessary states:
                if (type.getFirstFrameImage(state, direction, config) == null) {
                    config.info("   autogenerating state direction...");
                    if (!type.autoGenerateDirection(state, direction)) {
                        throw new Exception("Cannot auto generate state " + PAKObjectType.stateNames[state] + " " + direction + " of " + name + " (ID: "+ID+") in room " + file.getName());
                    }
                }

                if (ID == 63) {
                    // Player object, here we want to set as size the largest of the animation frames:
                    if (ow != type.getPixelMaximumWidthConsideringCropping() &&
                        ow != type.getPixelWidth(state, direction)) {
                        throw new Exception("Object " + ID + " in " + file.getName() + " has width " + ow + ", but should be either " + type.getPixelMaximumWidthConsideringCropping() + " (max player width) or " + type.getPixelWidth(state, direction) + " for state " + PAKObjectType.stateNames[state] + ", direction " + PAKObjectType.directionNames[direction]);
                    }
                    if (oh != type.getPixelHeight(state, direction)) {
                        throw new Exception("Object " + ID + " in "+file.getName()+" has height " + oh + ", but should be " + type.getPixelHeight(state, direction));
                    }
                } else {
                    if (ow != type.getPixelWidth(state, direction)) {
                        throw new Exception("Object " + ID + " in " + file.getName() + " has width " + ow + ", but should be " + type.getPixelWidth(state, direction) + " for state " + PAKObjectType.stateNames[state] + ", direction " + PAKObjectType.directionNames[direction]);
                    }
                    if (oh != type.getPixelHeight(state, direction)) {
                        throw new Exception("Object " + ID + " in "+file.getName()+" has height " + oh + ", but should be " + type.getPixelHeight(state, direction));
                    }
                }
                r.objects.add(new PAKObject(ID, name, ox, oy, depth, state, direction, type));
            }
        }

        return r;
    }
        
}
