package jp.personal.entertainmentguard;

import java.util.LinkedHashMap;
import java.util.Map;

/** Only committed native address-bar values are classified; no search form text. */
public final class BrowserNavigationState {
  private final Map<String,String> committed=new LinkedHashMap<>();
  public String observe(String pkg,int window,String address,boolean editing){
    if(pkg==null||window<0)return null;
    String key=pkg+":"+window;
    if(editing)return committed.get(key);
    if(Classifier.host(address)==null){committed.remove(key);return null;}
    committed.put(key,address);
    if(committed.size()>16)committed.remove(committed.keySet().iterator().next());
    return address;
  }
}
