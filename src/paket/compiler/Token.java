/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.compiler;

/**
 *
 * @author santi
 */
public class Token {
    public static final int TOKEN_TYPE_PRIMITIVE_SYMBOL = 0;
    public static final int TOKEN_TYPE_ALPHANUMERIC_SYMBOL = 1;
    public static final int TOKEN_TYPE_STRING = 2;
    public static final int TOKEN_TYPE_NUMBER = 3;
    public static String typeNames[] = {"basic symbol", "alphanumeric constant", "string", "number"};


    public String value;
    public int type;


    public Token(String v, int t)
    {
        value = v;
        type = t;
    }


    @Override
    public String toString()
    {
        return "('" + value + "', "+type+")";
    }
}