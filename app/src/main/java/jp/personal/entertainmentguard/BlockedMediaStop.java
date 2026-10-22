package jp.personal.entertainmentguard;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.view.KeyEvent;

/** Send idempotent STOP/PAUSE only to the denied app's public media receivers.
 * Never send a global media key or read notifications, titles or playback history. */
public final class BlockedMediaStop {
  private BlockedMediaStop(){}
  public static int request(Context context,String pkg){
    if(pkg==null||pkg.equals(context.getPackageName()))return 0;
    int sent=0;
    Intent query=new Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg);
    for(ResolveInfo result:context.getPackageManager().queryBroadcastReceivers(query,0)){
      ActivityInfo receiver=result.activityInfo;
      if(receiver==null||!pkg.equals(receiver.packageName)||!receiver.enabled||!receiver.exported)continue;
      if(receiver.permission!=null&&context.checkSelfPermission(receiver.permission)!=PackageManager.PERMISSION_GRANTED)continue;
      ComponentName component=new ComponentName(receiver.packageName,receiver.name);
      try{
        for(int code:new int[]{KeyEvent.KEYCODE_MEDIA_STOP,KeyEvent.KEYCODE_MEDIA_PAUSE}){
          long now=SystemClock.uptimeMillis();
          for(int action:new int[]{KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP})
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(component)
              .putExtra(Intent.EXTRA_KEY_EVENT,new KeyEvent(now,now,action,code,0))
              .addFlags(Intent.FLAG_RECEIVER_FOREGROUND));
        }
        sent++;
      }catch(SecurityException ignored){}
    }
    return sent;
  }
}
