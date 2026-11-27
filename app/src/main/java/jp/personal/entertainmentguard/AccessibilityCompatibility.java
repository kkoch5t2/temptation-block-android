package jp.personal.entertainmentguard;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

/** All settings writes are restricted to this app's own accessibility component. */
public final class AccessibilityCompatibility {
  private AccessibilityCompatibility(){}
  static SharedPreferences prefs(Context c){return c.getSharedPreferences("payment_compat",Context.MODE_PRIVATE);}
  static String component(Context c){return new ComponentName(c,GuardianService.class).flattenToString();}
  private static final String DEFAULT_PACKAGE="jp.ne.paypay.android.app";
  public static java.util.Set<String> targets(Context c){
    return new java.util.HashSet<>(prefs(c).getStringSet("targets",
      java.util.Collections.singleton(DEFAULT_PACKAGE)));
  }
  public static boolean allowedTarget(Context c,String pkg){
    if(pkg==null||pkg.isEmpty()||pkg.equals(c.getPackageName())
        ||AppRules.supportedBrowser(pkg)||AppRules.unsupportedBrowser(pkg))return false;
    SharedPreferences guard=c.getSharedPreferences("guard",Context.MODE_PRIVATE);
    if(RestrictionTargets.app(guard,pkg)||NewAppGuard.blocked(c,guard,pkg))return false;
    return c.getPackageManager().getLaunchIntentForPackage(pkg)!=null;
  }
  public static java.util.Set<String> activeTargets(Context c){
    java.util.Set<String> result=targets(c);
    result.removeIf(pkg->!allowedTarget(c,pkg));
    return result;
  }
  public static boolean setTargets(Context c,java.util.Set<String> values){
    java.util.Set<String> old=targets(c);
    // Recheck newly added entries at save time; removing an entry is always safe.
    for(String pkg:values)if(!old.contains(pkg)&&!allowedTarget(c,pkg))return false;
    return prefs(c).edit().putStringSet("targets",new java.util.HashSet<>(values)).commit();
  }
  public static boolean enabled(Context c){return prefs(c).getBoolean("enabled",false);}
  public static boolean suspended(Context c){return prefs(c).getBoolean("suspended",false);}
  static boolean restoreFailed(Context c){
    return "restore_failed".equals(prefs(c).getString("status",""));
  }
  static boolean startFailed(Context c){
    return "start_failed".equals(prefs(c).getString("status",""));
  }
  public static boolean ownEnabled(Context c){
    return CompatibilitySwitchPolicy.contains(Settings.Secure.getString(c.getContentResolver(),
      Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),component(c));
  }
  public static boolean securePermission(Context c){
    return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)==PackageManager.PERMISSION_GRANTED;
  }
  public static boolean usagePermission(Context c){
    AppOpsManager ops=(AppOpsManager)c.getSystemService(Context.APP_OPS_SERVICE);
    if(ops==null)return false;
    int mode=Build.VERSION.SDK_INT>=29
      ?ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),c.getPackageName())
      :ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),c.getPackageName());
    return mode==AppOpsManager.MODE_ALLOWED;
  }
  public static boolean ready(Context c){return securePermission(c)&&usagePermission(c);}
  static synchronized boolean ownEnabled(Context c,boolean enabled){
    if(!securePermission(c))return false;
    try{
      String current=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
      String updated=CompatibilitySwitchPolicy.editServices(current,component(c),enabled);
      if(!Settings.Secure.putString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,updated))return false;
      if(enabled&&!Settings.Secure.putInt(c.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,1))return false;
      return ownEnabled(c)==enabled;
    }catch(SecurityException e){return false;}
  }
  /** Write the recovery marker before disabling, never after. */
  static synchronized boolean pause(Context c){
    if(!ownEnabled(c)||suspended(c))return false;
    if(!prefs(c).edit().putBoolean("suspended",true)
        .putString("status","paused")
        .putLong("pause_started_elapsed",android.os.SystemClock.elapsedRealtime()).commit())return false;
    if(ownEnabled(c,false))return true;
    // A failed or partial settings write must attempt recovery.
    restore(c);
    return false;
  }
  static synchronized boolean restore(Context c){
    if(!suspended(c))return true;
    if(!ownEnabled(c)&&!ownEnabled(c,true)){
      prefs(c).edit().putString("status","restore_failed").apply();
      return false;
    }
    return prefs(c).edit().putBoolean("suspended",false).remove("pause_started_elapsed").putString("status","monitoring").commit();
  }
  public static boolean setEnabled(Context c,boolean value){
    if(value&&!ready(c))return false;
    if(!value&&!restore(c))return false;
    if(!prefs(c).edit().putBoolean("enabled",value).commit())return false;
    if(value)startIfEnabled(c);
    else c.stopService(new Intent(c,CompatibilityService.class));
    return true;
  }
  public static void startIfEnabled(Context c){
    if(!enabled(c)){restore(c);return;}
    try{c.startForegroundService(new Intent(c,CompatibilityService.class));}
    catch(RuntimeException e){
      if(restore(c))prefs(c).edit().putString("status","start_failed").apply();
    }
  }
}
