/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package paket.compiler;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author santi
 */
public class PAKETTokenizer {    
    
    public static final int NO_CHARACTER = -2;
    
    FileReader fr = null;
    List<Token> nextTokens = new ArrayList<>();
    int nextC = NO_CHARACTER;
    String currentFile = null;
    int currentLine = 1;
    
    public PAKETTokenizer(String fileName) throws Exception {
        currentFile = fileName;
        fr = new FileReader(fileName);
    }
    
    
    public int getCurrentLine()
    {
        return currentLine;
    }
    
    
    public int nextCharacter() throws Exception
    {
        int c = fr.read();
        if (c == '\n') currentLine ++;
        return c;
    }
    
    
    public Token nextToken() throws Exception
    {
        if (!nextTokens.isEmpty()) {
            Token t = nextTokens.remove(0);
            return t;
        }
        
        String token = "";
        int c = nextC;
        if (c == NO_CHARACTER) {
            c = nextCharacter();
        } else {
            nextC = NO_CHARACTER;
        }
        // skip spaces:
        while (c == ' ' || c=='\t' || c=='\n' || c=='\r') c = nextCharacter();
        
        if (c == -1) return null;
        if (c == '#') {
            // comments:
            while(c != '\n' && c != -1) c = nextCharacter();
            return nextToken();
        } else if ((c >= 'a' && c <= 'z') ||
            (c >= 'A' && c <= 'Z')) {
            // alphanumeric symbol:
            while ((c >= 'a' && c <= 'z') ||
                   (c >= 'A' && c <= 'Z') ||
                   (c >= '0' && c<='9') ||
                   c == '_' || c=='-' || 
                   c == '{' || c=='}' ||
                   c == '$') {
                token += (char)c;
                c = nextCharacter();
            }
            nextC = c;  // we save the next character for the next time
            return new Token(token, Token.TOKEN_TYPE_ALPHANUMERIC_SYMBOL);
        } else if (c >= '0' && c<='9') {
            // numeric constant:
            while (c >= '0' && c<='9') {
                token += (char)c;
                c = nextCharacter();
            }
            nextC = c;  // we save the next character for the next time
            return new Token(token, Token.TOKEN_TYPE_NUMBER);
        } else if (c == '(' || c == ')' || c ==':' || c == ',' || c== '+') {
            // single character symbol:
            token += (char)c;
            return new Token(token, Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
        } else if (c == '&') {
            c = nextCharacter();
            if (c == '&') {
                return new Token("&&", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
            } else {
                throw new Exception("Cannot parse token starting with &" + (char)c);
            }
        } else if (c == '|') {
            c = nextCharacter();
            if (c == '|') {
                return new Token("||", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
            } else {
                throw new Exception("Cannot parse token starting with |" + (char)c);
            }
        } else if (c == '=') {
            c = nextCharacter();
            if (c == '=') {
                return new Token("==", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
            } else {
                nextC = c;
                return new Token("=", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
            }
        } else if (c == '\"') {
            // quotation:
            int previousC = c;
            c = nextCharacter();
            while((c != '\"') || (c=='\"' && previousC == '\\')) {
                if (c == '\"') {
                    // we remove the escape character
                    token = token.substring(0, token.length()-1) + '\"';
                } else {
                    token += (char)c;
                }
                previousC = c;
                c = nextCharacter();
            }
            Token str = new Token(token, Token.TOKEN_TYPE_STRING);
            Token next = nextToken();
            if (next != null) {
                if (next.type == Token.TOKEN_TYPE_PRIMITIVE_SYMBOL &&
                    next.value.equals("+")) {
                    Token next2 = nextToken();
                    if (next2.type == Token.TOKEN_TYPE_STRING) {
                        str.value += next2.value;
                    } else {
                        unread(next2);
                        unread(next);
                    }
                } else {
                    unread(next);
                }
            }
            return str;
        } else if (c == '$') {
            String accum = "$";
            c = nextCharacter();
            while((c>='a' && c<='z') || (c>='A' && c<='Z')) {
                accum += (char)c;
                c = nextCharacter();
            }
            nextC = c;
            if (accum.equals("$this")) {
                return new Token("$this", Token.TOKEN_TYPE_PRIMITIVE_SYMBOL);
            }
            throw new Exception("Cannot parse token " + accum);
        } 
                
        throw new Exception("Cannot parse token starting with " + (char)c);
    }
    
    
    public boolean hasMoreTokens() throws Exception
    {
        if (!nextTokens.isEmpty()) return true;
        Token t = nextToken();
        if (t != null) nextTokens.add(t);
        return t != null;
    }
    
    
    public void unread(Token t) {
        nextTokens.add(0, t);
    }
}
