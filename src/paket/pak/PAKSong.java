/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.pak;

/**
 *
 * @author santi
 */
public class PAKSong {
    public static final int TYPE_NONE = 0;
    public static final int TYPE_TSV = 1;
    public static final int TYPE_WYZ = 2;
    
    public String fileName;
    public int type;


    public PAKSong(String a_fileName, int a_type)
    {
        fileName = a_fileName;
        type = a_type;
    }
    
    
    public static String typeToString(int type)
    {
        switch(type) {
            case TYPE_TSV: return "tsv";
            case TYPE_WYZ: return "wyz";
            default: return "unknown format";
        }
    }
}
