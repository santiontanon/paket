/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.pak;

import paket.platforms.Platform;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import paket.compiler.PAKETConfig;
import paket.util.Pair;

/**
 *
 * @author santi
 */
public class PAKObjectType {

    public static final int DIRECTION_BACK = 0;
    public static final int DIRECTION_RIGHT = 1;
    public static final int DIRECTION_FRONT = 2;
    public static final int DIRECTION_LEFT = 3;

    public static final int STATE_WALKING_1 = 0;
    public static final int STATE_WALKING_2 = 1;
    public static final int STATE_WALKING_3 = 2;
    public static final int STATE_WALKING_4 = 3;
    public static final int STATE_NONE = 4;
    public static final int STATE_OPEN = 5;
    public static final int STATE_CLOSED = 6;
    public static final int STATE_EXIT = 7;
    public static final int STATE_NUMBER = 8;
    public static final int STATE_HIDDEN = 9;

    public static final String stateNames[] = {
        "walking-1",
        "walking-2",
        "walking-3",
        "walking-4",
        "idle",
        "open",
        "closed",
        "exit",
        "number",
        "hidden",};

    public static final String directionNames[] = {
        "back/0",
        "right/1",
        "front/2",
        "left/3",
        "4", "5", "6", "7", "8", "9", "10", "11"
    };

    public static int stateFromString(String name) throws Exception {
        for (int i = 0; i < stateNames.length; i++) {
            if (stateNames[i].equals(name)) {
                return i;
            }
        }
        throw new Exception("Unknown state name '" + name + "'");
    }

    public static int directionFromString(String a_direction) throws Exception {
        switch (a_direction) {
            case "right":
                return DIRECTION_RIGHT;
            case "left":
                return DIRECTION_LEFT;
            case "front":
            case "down":
                return DIRECTION_FRONT;
            case "back":
            case "up":
                return DIRECTION_BACK;
            case "0":
                return 0;
            case "1":
                return 1;
            case "2":
                return 2;
            case "3":
                return 3;
            case "4":
                return 4;
            case "5":
                return 5;
            case "6":
                return 6;
            case "7":
                return 7;
            case "8":
                return 8;
            case "9":
                return 9;
            case "10":
                return 10;
            case "11":
                return 11;
            default:
                throw new Exception("Unknown direction name '" + a_direction + "'");
        }
    }

    public static class PAKObjectState {

        public static final int SELECTION_NONE = 0;
        public static final int SELECTION_PIXEL = 1;
        public static final int SELECTION_BOX = 2;

        public int state = STATE_NONE;
        public int direction = DIRECTION_FRONT;
        public int collisionMask[][] = null;

        public int selection = SELECTION_PIXEL;

        public int animationTempo = 1;
        public List<Integer> animationFrameSequence = new ArrayList<>();
        public List<BufferedImage> animationFrames = new ArrayList<>();

        public PAKObjectState(int a_state, int a_direction) {
            state = a_state;
            direction = a_direction;
        }

        public void checkImageSizes() throws Exception {
            Integer w = null, h = null;

            for (BufferedImage img : animationFrames) {
                if (w == null) {
                    w = img.getWidth();
                    h = img.getHeight();
                } else {
                    if (w != img.getWidth() || h != img.getHeight()) {
                        throw new Exception("Animation frames have different dimensions!");
                    }
                }
            }
        }

        public int getWidth() {
            return animationFrames.get(0).getWidth();
        }

        public int getHeight() {
            return animationFrames.get(0).getHeight();
        }

        public int getMaxWidth() {
            int width = 0;
            for (BufferedImage frame : animationFrames) {
                width = Math.max(width, frame.getWidth());
            }
            return width;
        }

        public int getMaxHeight() {
            int height = 0;
            for (BufferedImage frame : animationFrames) {
                height = Math.max(height, frame.getHeight());
            }
            return height;
        }

        public PAKObjectState(String a_state, String a_direction) throws Exception {
            state = stateFromString(a_state);
            direction = directionFromString(a_direction);
        }

        public List<Integer> toBytesForAssembler(int crop_y1, int crop_y2, Platform targetSystem, PAKObjectType type, List<PAKRoom> rooms) throws Exception {
            /*
            - state+direction: 1 byte
            - state length: 2 bytes
            - # animation frames: 1 byte
            - animation tempo: 1 byte
            - animation frame offsets: n*2 bytes
            - for each frame:
              - image size: 2 bytes
              - image: n bytes: selection mask, width, height, image data
             */

            List<Integer> bytes = new ArrayList<>();
            bytes.add(state + direction * 16);
            bytes.add(0);  // place holders for the size
            bytes.add(0);
            bytes.add(animationFrameSequence.size());
            bytes.add(animationTempo);
            int imageOffsetStartIdx = bytes.size();
            for (int i = 0; i < animationFrameSequence.size(); i++) {
                bytes.add(0);  // place holders for size
                bytes.add(0);
            }
            int imageIdx = 0;
            for (BufferedImage original : animationFrames) {
                BufferedImage img = original;
                if (PAKRoom.CROP_TRANSPARENT_SPRITE_ROWS) {
                    if ((crop_y1 != 0 || crop_y2 != original.getHeight())) {
                        img = new BufferedImage(original.getWidth(), crop_y2 - crop_y1, BufferedImage.TYPE_INT_ARGB);
                        for (int i = 0; i < img.getHeight(); i++) {
                            for (int j = 0; j < img.getWidth(); j++) {
                                int c = 0;
                                if (i + crop_y1 < original.getHeight()) {
                                    c = original.getRGB(j, i + crop_y1);
                                }
                                img.setRGB(j, i, c);
                            }
                        }
                    }
                }
                boolean foundAny = false;
                for (int i = 0; i < animationFrameSequence.size(); i++) {
                    if (animationFrameSequence.get(i).equals(imageIdx)) {
                        int startOffset = imageOffsetStartIdx + i * 2 + 1;
                        int targetOffset = bytes.size();
                        bytes.set(startOffset - 1, (targetOffset - startOffset) % 256);
                        bytes.set(startOffset, (targetOffset - startOffset) / 256);
                        foundAny = true;
                    }
                }
                if (!foundAny) {
                    throw new Exception("Animation frame " + imageIdx + " was never used! " + animationFrameSequence);
                }
                List<Integer> imageBytes = targetSystem.convertObjectStateImageToBytes(img, selection, type, 0, crop_y1, rooms, type.ID + "-" + state + "-" + direction);
                bytes.addAll(imageBytes);

                imageIdx++;
            }

            int state_len = bytes.size() - 3;
            bytes.set(1, state_len % 256);
            bytes.set(2, state_len / 256);
            return bytes;
        }
    }

    public String ID = null;
    public List<PAKObjectState> states = new ArrayList<>();
    public String name = null;
    public String description = null;

    // These are rules that if an object of this type is present in a room,
    // we will instantiate in that room automatically:
    public List<PAKRule> rules = new ArrayList<>();

    // These are rules that if an object of this type is present in a room,
    // a corresponding rule will be spawned in the on-room-load-rules:
    public List<PAKRule> onRoomLoadRules = new ArrayList<>();
    // Same, but these will be checked after the room has loaded, and drawn
    // (while the previous are just before the room is drawn the first time)
    public List<PAKRule> onRoomStartRules = new ArrayList<>();

    public PAKObjectType(String a_ID) {
        ID = a_ID;
    }

    public int getPixelWidth(int state, int direction) {
        for (PAKObjectState s : states) {
            if (s.state == state && s.direction == direction) {
                return s.getWidth();
            }
        }
        return 0;
    }

    public int getPixelHeight(int state, int direction) {
        for (PAKObjectState s : states) {
            if (s.state == state && s.direction == direction) {
                return s.getHeight();
            }
        }
        return 0;
    }

    public int getPixelHeightConsideringCropping(int state, int direction) {
        for (PAKObjectState s : states) {
            if (s.state == state && s.direction == direction) {
                if (!s.animationFrames.isEmpty()) {
                    Pair<Integer, Integer> crop = getCropCoordinates();
                    if (PAKRoom.CROP_TRANSPARENT_SPRITE_ROWS) {
                        return crop.m_b - crop.m_a;
                    } else {
                        return s.getHeight();
                    }
                }
            }
        }
        return 0;
    }

    public int getPixelMaximumHeightConsideringCropping() {
        int maxHeight = 0;
        for (PAKObjectState s : states) {
            if (!s.animationFrames.isEmpty()) {
                Pair<Integer, Integer> crop = getCropCoordinates();
                if (PAKRoom.CROP_TRANSPARENT_SPRITE_ROWS) {
                    maxHeight = Math.max(maxHeight, crop.m_b - crop.m_a);
                } else {
                    maxHeight = Math.max(maxHeight, s.getMaxHeight());
                }
            }
        }
        return maxHeight;
    }

    public int getPixelMaximumWidthConsideringCropping() {
        int maxWidth = 0;
        for (PAKObjectState s : states) {
            if (!s.animationFrames.isEmpty()) {
                maxWidth = Math.max(maxWidth, s.getMaxWidth());
            }
        }
        return maxWidth;
    }

    public int getAnimationLength(int direction) {
        int frames = 0;
        for (PAKObjectState s : states) {
            if (s.direction != direction) {
                continue;
            }
            if (s.state == STATE_WALKING_1
                    || s.state == STATE_WALKING_2
                    || s.state == STATE_WALKING_3
                    || s.state == STATE_WALKING_4) {
                frames += 1;
            }
        }
        return frames;
    }

    public int[][] getCollisionMask(int state, int direction) {
        for (PAKObjectState s : states) {
            if (s.state == state && s.direction == direction) {
                return s.collisionMask;
            }
        }
        return null;
    }

    public BufferedImage getFirstFrameImage(int state, int direction, PAKETConfig config) {
        for (PAKObjectState s : states) {
            if (s.state == state) {
                if (s.direction == direction) {
                    if (s.animationFrames.isEmpty()) {
                        return null;
                    }
                    return s.animationFrames.get(0);
                }
                /*
                if ((s.direction == DIRECTION_LEFT && direction == DIRECTION_RIGHT) ||
                    (s.direction == DIRECTION_RIGHT && direction == DIRECTION_LEFT)) {
                    BufferedImage img = s.img;
                    AffineTransform tx = AffineTransform.getScaleInstance(-1, 1);
                    tx.translate(-img.getWidth(null), 0);
                    AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
                    BufferedImage newImage = op.filter(img, null);
                    return newImage;
                }
                 */
            }
        }
        config.error("Cannot find image for object " + ID + " state " + stateNames[state] + " direction " + directionNames[direction]);
        return null;
    }

    public boolean autoGenerateDirection(int state, int direction) throws Exception {
        PAKObjectState s2 = null;
        for (PAKObjectState s : states) {
            if (s.state == state) {
                if ((s.direction == DIRECTION_LEFT && direction == DIRECTION_RIGHT)
                        || (s.direction == DIRECTION_RIGHT && direction == DIRECTION_LEFT)) {
                    s2 = new PAKObjectState(state, direction);
                    s2.selection = s.selection;
                    s2.collisionMask = new int[s.collisionMask.length][s.collisionMask[0].length];
                    for (int i = 0; i < s.collisionMask[0].length; i++) {
                        for (int j = 0; j < s.collisionMask.length; j++) {
                            s2.collisionMask[s.collisionMask.length - (1 + j)][i] = s.collisionMask[j][i];
                        }
                    }
                    s2.animationTempo = s.animationTempo;
                    s2.animationFrameSequence.addAll(s.animationFrameSequence);
                    for (BufferedImage img : s.animationFrames) {
                        AffineTransform tx = AffineTransform.getScaleInstance(-1, 1);
                        tx.translate(-img.getWidth(null), 0);
                        AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
                        BufferedImage newImage = op.filter(img, null);
                        if (newImage.getWidth() != img.getWidth()
                                || newImage.getHeight() != img.getHeight()) {
                            throw new Exception("Flipped image is of different size than the original!");
                        }

                        s2.animationFrames.add(newImage);
                    }
                    break;
                }
            }
        }
        if (s2 != null) {
            states.add(s2);
            return true;
        }
        return false;
    }

    public List<Integer> toBytesForAssembler(Platform targetSystem, List<PAKRoom> rooms, boolean log, PAKETConfig config) throws Exception {
        List<Integer> bytes = new ArrayList<>();
        Pair<Integer, Integer> crop = getCropCoordinates();

        for (PAKObjectState s : states) {
            List<Integer> state_bytes = s.toBytesForAssembler(crop.m_a, crop.m_b, targetSystem, this, rooms);
            bytes.addAll(state_bytes);
        }

        if (log) {
            config.info("    objectType " + this.ID + ": " + states.size() + " states, " + bytes.size() + " bytes");
            if (PAKRoom.CROP_TRANSPARENT_SPRITE_ROWS) {
                config.info("        crop: " + crop.m_a + ", " + crop.m_b + " (original: 0 - " + this.states.get(0).getHeight() + ")");
            }
        }

        return bytes;
    }

    public String getInGameName() throws Exception {
        if (name == null) {
            throw new Exception("Object type " + ID + " does not have a name defined for the target language!");
        }
        return name;
    }

    public String getDescription() throws Exception {
        if (description == null) {
            throw new Exception("Object type " + ID + " does not have a description defined for the target language!");
        }
        return description;
    }

    // Checks if there are transparent rows of pixels on top and below all the sprites of an object
    // that can be potentially cropped.
    public Pair<Integer, Integer> getCropCoordinates() {
        int firstNonTransparentRow = states.get(0).getHeight();
        int lastNonTransparentRow = 0;
        for (PAKObjectState state : states) {
            for (int i = 0; i < state.getHeight(); i++) {
                boolean transparent = true;
                for (int j = 0; j < state.getWidth(); j++) {
                    for (BufferedImage img : state.animationFrames) {
                        int color = img.getRGB(j, i);
                        int a = (color & 0xff000000) >> 24;
                        if (a != 0) {
                            transparent = false;
                            break;
                        }
                    }
                    if (!transparent) {
                        break;
                    }
                }
                if (!transparent) {
                    if (i < firstNonTransparentRow) {
                        firstNonTransparentRow = i;
                    }
                    if (i > lastNonTransparentRow) {
                        lastNonTransparentRow = i;
                    }
                }
            }
        }

        // Special case for an "all transparent object":
        if (lastNonTransparentRow == 0 && firstNonTransparentRow > 0) {
            firstNonTransparentRow = 0;
            lastNonTransparentRow = states.get(0).getHeight() - 1;
        }

        return new Pair<>(firstNonTransparentRow, lastNonTransparentRow + 1);
    }
}
