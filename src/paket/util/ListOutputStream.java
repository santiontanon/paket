/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.util;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author santi
 */
public class ListOutputStream extends OutputStream {
    List<Integer> data = new ArrayList<>();

    @Override
    public void write(int bytevalue) throws IOException {
        data.add(bytevalue&0xff);
    }

    public List<Integer> getData() {
        return data;
    }
}
