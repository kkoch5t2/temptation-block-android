package jp.personal.entertainmentguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import java.util.HashMap;
import java.util.Map;

/** Only installation identity is stored locally; no app usage or network access. */
public final class NewAppGuard {
  private NewAppGuard(){}
  public static boolean enable(Context context,SharedPreferences prefs){
    if(NewAppRules.enabled(prefs))return true;
    Map<String,Long> installed=new HashMap<>();
    try{
      for(PackageInfo app:context.getPackageManager().getInstalledPackages(0))
        installed.put(app.packageName,app.firstInstallTime);
    }catch(RuntimeException e){return false;}
    if(!installed.containsKey(context.getPackageName()))return false;
    return NewAppRules.enable(prefs,installed);
  }
  public static boolean blocked(Context context,SharedPreferences prefs,String pkg){
    if(!NewAppRules.enabled(prefs)||pkg==null||pkg.equals(context.getPackageName()))return false;
    try{
      PackageInfo app=context.getPackageManager().getPackageInfo(pkg,0);
      boolean system=app.applicationInfo!=null&&(app.applicationInfo.flags&
        (ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0;
      return NewAppRules.blocked(prefs,pkg,app.firstInstallTime,system);
    }catch(PackageManager.NameNotFoundException e){
      return NewAppRules.blocked(prefs,pkg,-1,false);
    }
  }
}
