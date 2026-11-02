package jp.personal.entertainmentguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.TextView;
import android.view.Gravity;

/** Pause accessibility before opening an explicitly registered incompatible app. */
public class CompatibilityLaunchActivity extends Activity {
  private boolean launched;
  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    TextView label=new TextView(this);
    label.setText("対象アプリを準備しています…");
    label.setGravity(Gravity.CENTER);label.setTextSize(18);
    setContentView(label);
    if(!AccessibilityCompatibility.enabled(this)||!AccessibilityCompatibility.ready(this)){finish();return;}
    java.util.List<String> targets=new java.util.ArrayList<>(AccessibilityCompatibility.activeTargets(this));
    java.util.Collections.sort(targets);
    String requested=getIntent().getStringExtra("target_package");
    if(requested!=null){
      if(targets.contains(requested))open(requested);else finish();
    }else if(targets.size()==1)open(targets.get(0));
    else if(targets.isEmpty())finish();
    else{
      String[] names=new String[targets.size()];
      for(int i=0;i<names.length;i++){
        try{names[i]=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(targets.get(i),0)).toString();}
        catch(android.content.pm.PackageManager.NameNotFoundException e){names[i]=targets.get(i);}
      }
      new AlertDialog.Builder(this).setTitle("対象アプリを開く")
        .setItems(names,(dialog,index)->open(targets.get(index)))
        .setOnCancelListener(dialog->finish()).show();
    }
  }
  private void open(String pkg){
    if(!AccessibilityCompatibility.activeTargets(this).contains(pkg)){finish();return;}
    AccessibilityCompatibility.startIfEnabled(this);
    AccessibilityCompatibility.prefs(this).edit().putString("launch_target",pkg)
      .putLong("launch_until_elapsed",SystemClock.elapsedRealtime()+3000).commit();
    new Handler(Looper.getMainLooper()).postDelayed(()->{
      if(isFinishing()||isDestroyed())return;
      if(!AccessibilityCompatibility.activeTargets(this).contains(pkg)){
        AccessibilityCompatibility.restore(this);finish();return;
      }
      Intent intent=getPackageManager().getLaunchIntentForPackage(pkg);
      if(intent==null||(!AccessibilityCompatibility.suspended(this)&&!AccessibilityCompatibility.pause(this))){
        AccessibilityCompatibility.restore(this);finish();return;
      }
      // Recreate the launch screen; never force-stop, clear data or perform transactions.
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
      try{startActivity(intent);launched=true;}
      catch(RuntimeException e){AccessibilityCompatibility.restore(this);}
      finish();
    },150);
  }
  @Override protected void onDestroy(){
    if(!launched)AccessibilityCompatibility.restore(this);
    super.onDestroy();
  }
}
