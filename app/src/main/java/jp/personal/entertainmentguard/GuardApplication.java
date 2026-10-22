package jp.personal.entertainmentguard;

import android.app.Application;
import android.os.SystemClock;
import android.provider.Settings;
import android.os.Build;
import java.time.DateTimeException;
import android.util.Log;

/** Shared clock provider for the app and its accessibility service. */
public class GuardApplication extends Application {
  @Override public void onCreate(){
    super.onCreate();
    GroupRules.TimeSource source=new GroupRules.TimeSource(){
      @Override public long elapsedMs(){return SystemClock.elapsedRealtime();}
      @Override public int bootCount(){
        try{return Settings.Global.getInt(getContentResolver(),Settings.Global.BOOT_COUNT,-1);}
        catch(SecurityException e){return -1;}
      }
      @Override public long networkMs(){
        if(Build.VERSION.SDK_INT<33)return -1;
        try{return SystemClock.currentNetworkTimeClock().millis();}
        catch(DateTimeException|SecurityException e){return -1;}
      }
      @Override public long wallMs(){return System.currentTimeMillis();}
    };
    GroupRules.setTimeSource(source);
    Log.i("GuardClock","Boot identity accessible="+(source.bootCount()>=0)+
      ", independently synchronized time available="+(source.networkMs()>0));
  }
}
