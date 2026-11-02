package jp.personal.entertainmentguard;

import android.app.*;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.os.*;
import java.util.Objects;

/** A visible foreground watchdog continues running while accessibility is disabled. */
public class CompatibilityService extends Service {
  private static final String CHANNEL="payment_compat";
  private static final int NOTIFICATION=610;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private final CompatibilitySwitchPolicy.Foreground foreground=new CompatibilitySwitchPolicy.Foreground();
  private CompatibilitySwitchPolicy policy;
  private long lastQuery,pauseStart=-1,lastHeartbeat;
  private String notificationText;
  private final Runnable tick=new Runnable(){
    @Override public void run(){
      try{inspect();}catch(RuntimeException e){
        foreground.clear();
        if(AccessibilityCompatibility.restore(CompatibilityService.this)){
          pauseStart=-1;
          notifyState("判定を確認できないため、通常のブロックに戻しました");
        }else notifyState("監視の復帰に失敗しました。ユーザー補助の設定を確認してください");
      }
      handler.postDelayed(this,500);
    }
  };
  @Override public void onCreate(){
    super.onCreate();
    NotificationManager manager=getSystemService(NotificationManager.class);
    manager.createNotificationChannel(new NotificationChannel(CHANNEL,"ユーザー補助との互換モード",NotificationManager.IMPORTANCE_LOW));
    startForeground(NOTIFICATION,notification("自動切り替えを準備しています"));
    boolean recovered=AccessibilityCompatibility.suspended(this);
    policy=new CompatibilitySwitchPolicy(recovered,AccessibilityCompatibility.activeTargets(this));
    AccessibilityCompatibility.restore(this);
    if(recovered)AccessibilityCompatibility.prefs(this).edit().remove("launch_until_elapsed").apply();
  }
  @Override public int onStartCommand(Intent intent,int flags,int id){
    if(!AccessibilityCompatibility.enabled(this)){
      AccessibilityCompatibility.restore(this);stopSelf();return START_NOT_STICKY;
    }
    handler.removeCallbacks(tick);
    handler.post(tick);
    return START_STICKY;
  }
  private void inspect(){
    long elapsed=SystemClock.elapsedRealtime();
    if(elapsed-lastHeartbeat>=10000||lastHeartbeat==0){
      AccessibilityCompatibility.prefs(this).edit().putLong("heartbeat_elapsed",elapsed).apply();
      lastHeartbeat=elapsed;
    }
    PowerManager power=getSystemService(PowerManager.class);
    KeyguardManager keyguard=getSystemService(KeyguardManager.class);
    boolean interactive=power!=null&&power.isInteractive()&&keyguard!=null&&!keyguard.isKeyguardLocked();
    boolean ready=AccessibilityCompatibility.ready(this);
    long now=System.currentTimeMillis();
    if(interactive&&ready){
      if(lastQuery==0||now<lastQuery||now-lastQuery>60000){foreground.clear();lastQuery=now-30000;}
      UsageEvents events=getSystemService(UsageStatsManager.class).queryEvents(lastQuery-1000,now+1);
      if(events==null){ready=false;foreground.clear();}
      else{
        UsageEvents.Event event=new UsageEvents.Event();
        while(events.hasNextEvent()){
          events.getNextEvent(event);
          if(event.getEventType()==UsageEvents.Event.ACTIVITY_RESUMED)
            foreground.resume(event.getPackageName(),event.getClassName());
          else if(event.getEventType()==UsageEvents.Event.ACTIVITY_PAUSED)
            foreground.pause(event.getPackageName(),event.getClassName());
        }
      }
      lastQuery=now;
    }else{foreground.clear();lastQuery=0;}
    boolean suspended=AccessibilityCompatibility.suspended(this);
    pauseStart=suspended?AccessibilityCompatibility.prefs(this).getLong("pause_started_elapsed",-1):-1;
    long launchUntil=AccessibilityCompatibility.prefs(this).getLong("launch_until_elapsed",0);
    String visible=foreground.current();
    java.util.Set<String> targets=AccessibilityCompatibility.activeTargets(this);
    policy.setTargets(targets);
    String launchTarget=AccessibilityCompatibility.prefs(this).getString("launch_target",null);
    String permittedForeground=CompatibilitySwitchPolicy.foregroundDuringLaunch(interactive,visible,
      foreground.activity(),getPackageName(),elapsed,launchUntil,launchTarget,targets);
    if(launchUntil>0&&elapsed>=launchUntil){
      AccessibilityCompatibility.prefs(this).edit().remove("launch_until_elapsed").apply();
      if(!suspended)policy.waitForExit();
    }
    if(suspended&&targets.contains(visible)&&launchUntil>0)
      AccessibilityCompatibility.prefs(this).edit().remove("launch_until_elapsed").apply();
    CompatibilitySwitchPolicy.Action action=policy.decide(AccessibilityCompatibility.enabled(this),ready,interactive,
      permittedForeground,AccessibilityCompatibility.ownEnabled(this),suspended,elapsed,pauseStart);
    if(action==CompatibilitySwitchPolicy.Action.PAUSE&&launchUntil<=elapsed){
      AccessibilityCompatibility.prefs(this).edit().putLong("launch_until_elapsed",elapsed+3000).putString("launch_target",visible).commit();
      try{
        // While accessibility is still bound, ask Android to show our visible launch step.
        // If background launch is restricted, the notification offers the same user action.
        startActivity(new Intent(this,CompatibilityLaunchActivity.class).putExtra("target_package",visible).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      }catch(RuntimeException e){
        AccessibilityCompatibility.prefs(this).edit().remove("launch_until_elapsed").apply();
        policy.waitForExit();
      }
    }else if(action==CompatibilitySwitchPolicy.Action.RESTORE){
      if(AccessibilityCompatibility.restore(this)){pauseStart=-1;suspended=false;}
    }
    if(AccessibilityCompatibility.suspended(this)){
      notifyState(!ready?"監視を復帰できません。ユーザー補助の設定を確認してください":
        "対象アプリ利用中：ブロックを一時停止（最大5分）");
    }else notifyState(AccessibilityCompatibility.ownEnabled(this)
      ?"通常のブロック中 · 対象アプリを離れると自動復帰"
      :"ユーザー補助がOFFです。設定で有効にしてください");
  }
  private Notification notification(String text){
    Intent intent=new Intent(this,MainActivity.class).putExtra("page","settings");
    PendingIntent pending=PendingIntent.getActivity(this,NOTIFICATION,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    PendingIntent openPayment=PendingIntent.getActivity(this,NOTIFICATION+1,
      new Intent(this,CompatibilityLaunchActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    return new Notification.Builder(this,CHANNEL)
      .addAction(new Notification.Action.Builder(null,"対象アプリを開く",openPayment).build())
      .setSmallIcon(jp.personal.entertainmentguard.R.drawable.ic_status_block)
      .setContentTitle("誘惑ブロック · 互換モード")
      .setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
      .setContentIntent(pending).setOngoing(true).setOnlyAlertOnce(true).build();
  }
  private void notifyState(String text){
    if(Objects.equals(notificationText,text))return;
    notificationText=text;
    getSystemService(NotificationManager.class).notify(NOTIFICATION,notification(text));
  }
  @Override public void onDestroy(){
    handler.removeCallbacks(tick);
    AccessibilityCompatibility.restore(this);
    super.onDestroy();
  }
  @Override public IBinder onBind(Intent intent){return null;}
}
