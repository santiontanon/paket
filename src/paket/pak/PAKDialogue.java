/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.pak;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import paket.pak.PAKRule.PAKScript;
import paket.platforms.Platform;
import paket.util.Pair;

/**
 *
 * @author santi
 */
public class PAKDialogue {
    public static class PAKDialogueState {
        public int ID;
        public List<PAKScript> scripts = new ArrayList<>();


        public PAKDialogueState(int a_ID)
        {
            ID = a_ID;
        }
        

        public List<String> getTextLines()
        {
            List<String> lines = new ArrayList<>();
            for(PAKScript s:scripts) {
                getTextLinesInternal(s, lines);
            }
            return lines;
        }


        public void getTextLinesInternal(PAKScript s, List<String> lines)
        {
            if (s.text != null) lines.add(s.text);
            if (s.text2 != null) lines.add(s.text2);
            if (s.then_scripts != null) {
                for(PAKScript s2:s.then_scripts) {
                    getTextLinesInternal(s2, lines);
                }
            }
            if (s.else_scripts != null) {
                for(PAKScript s2:s.else_scripts) {
                    getTextLinesInternal(s2, lines);
                }
            }
        }
        
        
        public List<Integer> toBytesForAssembler(HashMap<String, Pair<Integer,Integer>> textIDHash, PAKGame game, List<PAKDialogue> dialogues, Platform platform) throws Exception
        {
            List<Integer> bytes = new ArrayList<>();
            PAKRule.toBytesForAssemblerBody(bytes, scripts, textIDHash, game, dialogues, true, PAKRule.CONNECTIVE_AND, false, platform);
            
            bytes.add(0, ID);  // add the state ID#:

            return bytes;
        }

        /*
        public static PACDialogueState fromxml(Element xml, String language) throws Exception
        {
            PACDialogueState s = new PACDialogueState();
            
            s.ID = Integer.parseInt(xml.getAttributeValue("id"));
            for(Object o:xml.getChildren("script")) {
                PAKScript script = PAKScript.fromxml((Element)o, language);
                if (script != null) s.scripts.add(script);
            }
            
            return s;
        }
        */
    }
    
    
    public String ID = null;
    public List<PAKDialogueState> states = new ArrayList<>();

    
    public PAKDialogue(String a_ID)
    {
        ID = a_ID;
    }
    
    
    public List<String> getTextLines()
    {
        List<String> lines = new ArrayList<>();
        for(PAKDialogueState s:states) {
            for(String line:s.getTextLines()) {
                if (!lines.contains(line)) lines.add(line);
            }
        }
        return lines;
    }

    
    public List<Integer> toBytesForAssembler(HashMap<String, Pair<Integer,Integer>> textIDHash, PAKGame game, List<PAKDialogue> dialogues, Platform platform) throws Exception
    {
        List<Integer> bytes = new ArrayList<>();
        
        for(PAKDialogueState s:states) {
            bytes.addAll(s.toBytesForAssembler(textIDHash, game, dialogues, platform));
        }
        
        return bytes;
    }
    
    /*
    public static PAKDialogue fromxml(Element xml, String language) throws Exception
    {
        PAKDialogue d = new PAKDialogue();
        
        d.ID = xml.getAttributeValue("id");
        for(Object o:xml.getChildren("state")) {
            PACDialogueState s = PACDialogueState.fromxml((Element)o, language);
            d.states.add(s);
        }
        
        return d;
    }
*/
}
