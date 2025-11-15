/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.music;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author santi
 */
public class TSVInstrument {
    public int speed = 0;
    public int repeat = -1;
    public List<Integer> volume = new ArrayList<>();
    
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TSVInstrument)) return false;
        TSVInstrument i2 = (TSVInstrument)o;
        if (speed != i2.speed) return false;
        return volume.equals(i2.volume);
    }
}
