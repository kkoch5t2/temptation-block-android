package jp.personal.entertainmentguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Resume only a pause recorded by us; never turn on a manually disabled service. */
public class CompatibilityRecoveryReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context context,Intent intent){
    if(intent==null||(!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
        &&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())))return;
    AccessibilityCompatibility.restore(context);
    AccessibilityCompatibility.startIfEnabled(context);
  }
}
