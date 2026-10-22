package jp.personal.entertainmentguard;

import android.content.SharedPreferences;
import java.util.Map;

/** An installation snapshot, independent of group budgets and manual app exclusions. */
public final class NewAppRules {
  private NewAppRules(){}
  private static final String ENABLED="new_apps.enabled";
  private static final String BASE="new_apps.existing.";
  public static boolean enabled(SharedPreferences p){return p.getBoolean(ENABLED,false);}
  public static boolean enable(SharedPreferences p,Map<String,Long> installed){
    // Repeated taps, service restarts and app updates must never re-baseline new apps.
    if(enabled(p))return true;
    if(installed==null||installed.isEmpty())return false;
    for(Map.Entry<String,Long> app:installed.entrySet())
      if(app.getKey()==null||app.getKey().isEmpty()||app.getValue()==null||app.getValue()<=0)return false;
    SharedPreferences.Editor edit=p.edit();
    for(String key:p.getAll().keySet())if(key.startsWith(BASE))edit.remove(key);
    for(Map.Entry<String,Long> app:installed.entrySet())edit.putLong(BASE+app.getKey(),app.getValue());
    return edit.putBoolean(ENABLED,true).commit();
  }
  public static boolean disable(SharedPreferences p){
    if(GroupRules.strict(p)||p.getBoolean("settings_locked",false))return false;
    return p.edit().putBoolean(ENABLED,false).commit();
  }
  public static boolean blocked(SharedPreferences p,String pkg,long firstInstall,boolean systemApp){
    if(!enabled(p)||pkg==null||pkg.isEmpty()||systemApp)return false;
    // firstInstallTime survives updates, but changes on an uninstall/reinstall.
    return firstInstall<=0||p.getLong(BASE+pkg,-1)!=firstInstall;
  }
}
