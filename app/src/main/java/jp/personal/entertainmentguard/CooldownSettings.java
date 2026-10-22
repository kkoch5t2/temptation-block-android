package jp.personal.entertainmentguard;

import android.content.SharedPreferences;

/** Shared persistence gate for the block-screen duration. */
public final class CooldownSettings {
  private CooldownSettings(){}
  private static final String KEY="cooldown_seconds";
  public static int seconds(SharedPreferences prefs){
    int value=prefs.getInt(KEY,CooldownPolicy.DEFAULT_SECONDS);
    return CooldownPolicy.validSeconds(value)?value:CooldownPolicy.DEFAULT_SECONDS;
  }
  public static boolean locked(SharedPreferences prefs){
    return prefs.getBoolean("settings_locked",false)||GroupRules.strict(prefs);
  }
  public static boolean setSeconds(SharedPreferences prefs,int value){
    // Recheck at save time, including a dialog opened before strict mode began.
    if(!CooldownPolicy.validSeconds(value)||locked(prefs))return false;
    return prefs.edit().putInt(KEY,value).commit();
  }
}
