/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.pak;

/**
 *
 * @author santi
 */
public class PAKItem {
//    public int ID = -1;
    public String ID = null;
    public String inGameNameInLanguage = null;
    public String descriptionInLanguage = null;
    public String defaultUseMessage = null;
    
    
    public PAKItem(String a_ID)
    {
        ID = a_ID;
    }
    
    
    public String getInGameName() throws Exception
    {
        if (inGameNameInLanguage == null) throw new Exception("Item "+ID+" does not have a name defined for the target language!");
        return inGameNameInLanguage;
    }


    public String getDescription() throws Exception
    {
        if (descriptionInLanguage == null) throw new Exception("Item "+ID+" does not have a description defined for the target language!");
        return descriptionInLanguage;
    }    
}
