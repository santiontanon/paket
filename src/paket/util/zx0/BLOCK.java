/*
 * This code is a translation from the ZX0 compressor by Einar Saukas to Java.
 * The original source code by Einar Saukas can be found here:
 * https://github.com/einar-saukas/ZX0
 * Translation by Santiago Ontañón
 */
package paket.util.zx0;

/**
 *
 * @author santi
 */
public class BLOCK {
    public BLOCK chain = null, ghost_chain = null;
    public int bits, index, offset, length, references;
    
    
    public String toString()
    {
        return "BLOCK(" + bits + ", " + index + ", " + offset + ", " + length + ", " + references + ", chain" + 
                (chain == null ? "=null":"!=null") + ", ghost_chain" + (ghost_chain == null ? "=null":"!=null") + ")";
    }
}
