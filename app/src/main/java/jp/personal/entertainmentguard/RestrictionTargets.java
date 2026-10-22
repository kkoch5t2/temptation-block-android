package jp.personal.entertainmentguard;

import android.content.SharedPreferences;

/** Only explicit selections authorize enforcement. Built-in categories are UI hints. */
public final class RestrictionTargets {
  private RestrictionTargets(){}
  public static boolean app(SharedPreferences p,String pkg){
    if(pkg==null||pkg.isEmpty())return false;
    if(p.contains("app:"+pkg))return p.getBoolean("app:"+pkg,false);
    String group=p.getString("group.app."+pkg,null);
    return GroupRules.exists(p,group);
  }
  public static boolean domain(SharedPreferences p,String input){
    String host=Classifier.host(input);
    if(host==null)return false;
    while(host.contains(".")){
      if(GroupRules.exists(p,p.getString("group.domain."+host,null))
          ||p.contains("blocked:"+host))return true;
      host=host.substring(host.indexOf('.')+1);
    }
    return false;
  }
  public static boolean activeKey(SharedPreferences p,String key){
    if(key==null)return false;
    if(key.startsWith("app:")){
      String pkg=key.substring(4);
      return app(p,pkg)&&GroupRules.enabled(p,GroupRules.appGroup(p,pkg));
    }
    if(key.startsWith("domain:")){
      String host=Classifier.host(key.substring(7));
      return host!=null&&domain(p,host)&&GroupRules.enabled(p,GroupRules.domainGroup(p,host));
    }
    return false;
  }
  public static boolean sameWindow(String owner,int window,String activeOwner,int activeWindow){
    return owner!=null&&window>=0&&owner.equals(activeOwner)&&window==activeWindow;
  }
}
