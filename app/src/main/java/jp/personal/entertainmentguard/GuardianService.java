package jp.personal.entertainmentguard;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.InputMethod;
import android.view.KeyEvent;
import android.view.KeyCharacterMap;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.PowerManager;
import java.util.Date;
import java.util.Locale;
import java.text.SimpleDateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Button;
import java.util.List;
import java.util.HashSet;

public class GuardianService extends AccessibilityService {
  private static final long CHECK_INTERVAL=1000;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private SharedPreferences prefs;
  private String trackingHost=null, trackingCategory=null, trackingPackage=null;
  private boolean trackingStrong=false;
  private long trackingStarted=0, lastSeen=0, lastBlockedAction=0, lastWarning=0, lastHeartbeat=0;
  private final java.util.Set<String> usageGroups=new java.util.HashSet<>();
  private long usageLastTick=0, usageWarningAt=0;
  private boolean closingBlockedTab=false;
  private boolean inspectingPip=false;
  private int lastPipWindow=-1;
  private long lastPipAttempt=0;
  private String activeBrowserUrl=null, activeBrowserPackage=null;
  private final AppSurfaceRules.RemoteDesktopSession remoteDesktopSessions=new AppSurfaceRules.RemoteDesktopSession();
  private final BrowserNavigationState browserNavigation=new BrowserNavigationState();
  private View blockingOverlay;
  private Runnable blockingTicker;
  private WindowManager windowManager;
  private View currentOverlay;
  private final Runnable heartbeat=new Runnable(){
    @Override public void run(){
      long now=SystemClock.elapsedRealtime();
      if(prefs!=null&&(lastHeartbeat==0||now-lastHeartbeat>=60000)){
        prefs.edit().putLong("last_heartbeat",System.currentTimeMillis()).apply();
        lastHeartbeat=now;
      }
      inspectStrictPip();
      inspectVisibleBrowser();
      handler.postDelayed(this,CHECK_INTERVAL);
    }
  };
  @Override public void onServiceConnected(){
    super.onServiceConnected();
    prefs=getSharedPreferences("guard",Context.MODE_PRIVATE);
    if(!prefs.contains("restriction_mode"))
      prefs.edit().putInt("restriction_mode",1).putInt("limit_minutes",10).apply();
    // Clean up one-time prototype counters and the temporary full-screen test penalty.
    if(prefs.getInt("usage_schema_version",0)<2)
      prefs.edit().remove("usage_ms").remove("usage_date")
        .remove("penalty_until_ms")
        .putInt("usage_schema_version",2).apply();
    GroupRules.ensure(prefs);
    prefs.edit().putLong("last_service_start",System.currentTimeMillis()).apply();
    windowManager=(WindowManager)getSystemService(WINDOW_SERVICE);
    long pendingUntil=prefs.getLong("penalty_until_ms",0);
    if(pendingUntil>System.currentTimeMillis())
      showTimedBlock(prefs.getString("last_category","娯楽"),pendingUntil);
    else if(pendingUntil>0)prefs.edit().remove("penalty_until_ms").apply();
    handler.removeCallbacks(heartbeat);
    handler.post(heartbeat);
  }
  @Override public void onAccessibilityEvent(AccessibilityEvent event){
    if(event.getEventType()==AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ||event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)inspectStrictPip();
    if(blockingOverlay!=null || closingBlockedTab || event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)return;
    CharSequence p=event.getPackageName();
    if(p!=null){
      String pkg=p.toString();
      if(!pkg.equals(getPackageName()) && prefs!=null){
        // Home widgets and background notifications can emit events for nonforeground apps.
        AccessibilityNodeInfo active=getRootInActiveWindow();
        if(active==null)return;
        String restrictionPkg;
        try{
          if(active.getPackageName()==null || !pkg.equals(active.getPackageName().toString()))return;
          CharSequence eventClass=event.getClassName();
          if(enforceNewApp(pkg))return;
          restrictionPkg=restrictionPackage(active,pkg,eventClass==null?null:eventClass.toString());
        }finally{active.recycle();}
        String category=AppRules.category(restrictionPkg,prefs);
        if(isBrowser(pkg))inspectVisibleBrowser();
        else if(category!=null)enforceLimit("app:"+restrictionPkg,category,"アプリ");
        else if(AppRules.unsupportedBrowser(pkg)){stopUsage();blockNow("browser:"+pkg,"未対応ブラウザ","抜け道");}
        else stopUsage();
      }
    }
  }
  /** During strict mode inspect window metadata; read only a PiP root's package.
   *  No document text, title, sibling contents or notification data is retrieved. */
  private void inspectStrictPip(){
    if(prefs==null||inspectingPip)return;
    inspectingPip=true;
    try{
      boolean strict=GroupRules.strict(prefs);
      android.accessibilityservice.AccessibilityServiceInfo info=getServiceInfo();
      if(info==null)return;
      int flag=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
      if(((info.flags&flag)!=0)!=strict){
        if(strict){info.flags|=flag;info.eventTypes|=AccessibilityEvent.TYPE_WINDOWS_CHANGED;}
        else{info.flags&=~flag;info.eventTypes&=~AccessibilityEvent.TYPE_WINDOWS_CHANGED;}
        setServiceInfo(info);
      }
      if(!strict){lastPipWindow=-1;return;}
      List<android.view.accessibility.AccessibilityWindowInfo> windows=getWindows();
      if(windows==null)return;
      try{
        for(android.view.accessibility.AccessibilityWindowInfo window:windows){
          if(window==null||window.getType()!=android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION
              ||!window.isInPictureInPictureMode())continue;
          long now=SystemClock.elapsedRealtime();
          if(window.getId()==lastPipWindow&&now-lastPipAttempt<800)continue;
          lastPipWindow=window.getId();lastPipAttempt=now;
          AccessibilityNodeInfo root=window.getRoot();
          if(root==null)continue;
          try{
            CharSequence owner=root.getPackageName();
            if(owner==null||owner.length()==0)continue;
            stopBlockedMedia("app:"+owner.toString());
            // Android supplies this action on the PiP root to close that window.
            if(root.performAction(AccessibilityNodeInfo.ACTION_DISMISS)){
              prefs.edit().putInt("pip_closed_total",prefs.getInt("pip_closed_total",0)+1)
                .putLong("last_pip_close",System.currentTimeMillis()).apply();
              showWarning("⛔ 厳格モード中は小窓再生（PiP）を使えません。");
            }
          }finally{root.recycle();}
        }
      }finally{
        for(android.view.accessibility.AccessibilityWindowInfo window:windows)
          if(window!=null)window.recycle();
      }
    }catch(RuntimeException ignored){
      // A stale or protected PiP window must not stop the existing blocker.
    }finally{inspectingPip=false;}
  }
  @Override public void onInterrupt(){}
  @Override public void onDestroy(){
    handler.removeCallbacks(heartbeat);
    clearTimedBlock();
    clearOverlay();
    super.onDestroy();
  }
  private boolean isBrowser(String p) {
    return p.equals("com.brave.browser")||p.equals("com.android.chrome");
  }
  /** Gemini runs in Google's package. Match native view IDs, never chat/page text.
   *  The activity event covers the initial frame before the view tree is populated;
   *  each heartbeat rechecks the actual tree, so a later Search screen is restricted. */
  private String restrictionPackage(AccessibilityNodeInfo root,String pkg,String activity){
    boolean geminiView=false;
    if(AppSurfaceRules.GOOGLE.equals(pkg)){
      for(String id:AppSurfaceRules.GEMINI_VIEW_IDS){
        List<AccessibilityNodeInfo> found=root.findAccessibilityNodeInfosByViewId(id);
        if(found==null)continue;
        for(AccessibilityNodeInfo node:found){
          if(node!=null){
            if(node.getPackageName()!=null && AppSurfaceRules.GOOGLE.contentEquals(node.getPackageName()))geminiView=true;
            node.recycle();
          }
        }
        if(geminiView)break;
      }
    }
    return AppSurfaceRules.restrictionPackage(pkg,activity,geminiView);
  }
  private String urlFromTree(AccessibilityNodeInfo root,String pkg){
    if(root==null)return null;
    for(String id:new String[]{pkg+":id/ntp_content",pkg+":id/search_box_text"}){
      List<AccessibilityNodeInfo> markers=root.findAccessibilityNodeInfosByViewId(id);
      if(markers==null)continue;
      boolean nativeNtp=false;
      for(AccessibilityNodeInfo marker:markers){
        if(marker!=null){nativeNtp|=pkg.contentEquals(marker.getPackageName());marker.recycle();}
      }
      if(nativeNtp)browserNavigation.observe(pkg,root.getWindowId(),null,false);
    }
    for(String id:new String[]{pkg+":id/url_bar",pkg+":id/location_bar"}){
      List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByViewId(id);
      if(nodes==null)continue;
      String value=null;boolean editing=false,found=false;
      for(AccessibilityNodeInfo node:nodes){
        if(node==null)continue;
        try{
          if(!pkg.contentEquals(node.getPackageName()))continue;
          found=true;editing|=node.isFocused();
          if(node.getText()!=null)value=node.getText().toString();
        }finally{node.recycle();}
      }
      if(found)return browserNavigation.observe(pkg,root.getWindowId(),value,editing);
    }
    return null;
  }
  private boolean remoteDesktopSurface(AccessibilityNodeInfo root,String pkg,String url){
    if(!"com.android.chrome".equals(pkg))return false;
    if(url!=null)return remoteDesktopSessions.matches(root.getWindowId(),pkg,url,false,null);
    boolean webApp=false;
    List<AccessibilityNodeInfo> containers=root.findAccessibilityNodeInfosByViewId(pkg+":id/web_app_content");
    if(containers!=null)for(AccessibilityNodeInfo n:containers){
      if(n!=null){
        webApp|=pkg.contentEquals(n.getPackageName())
          && "android.widget.LinearLayout".contentEquals(n.getClassName());
        n.recycle();
      }
    }
    if(!webApp)return remoteDesktopSessions.matches(root.getWindowId(),pkg,null,false,null);
    if(remoteDesktopBrand(root,pkg))
      return remoteDesktopSessions.matches(root.getWindowId(),pkg,null,true,"Chrome Remote Desktop");
    java.util.ArrayDeque<AccessibilityNodeInfo> nodes=new java.util.ArrayDeque<>();
    nodes.add(AccessibilityNodeInfo.obtain(root));
    int count=0;
    try{
      while(!nodes.isEmpty() && count++<50){
        AccessibilityNodeInfo n=nodes.removeFirst();
        try{
          // The first native WebView is the document root. Never inspect links,
          // iframe titles, headings or other text inside the page for exemption.
          if("android.webkit.WebView".contentEquals(n.getClassName())){
            if(!"rootWebArea".equals(n.getExtras().getString("AccessibilityNodeInfo.chromeRole")))
              return remoteDesktopSessions.matches(root.getWindowId(),pkg,null,true,null);
            CharSequence title=n.getExtras().getCharSequence(
              "androidx.view.accessibility.AccessibilityNodeInfoCompat.SUPPLEMENTAL_DESCRIPTION_KEY");
            if(title==null||title.length()==0)title=n.getText();
            return remoteDesktopSessions.matches(root.getWindowId(),pkg,null,true,title==null?null:title.toString());
          }
          for(int i=0;i<n.getChildCount();i++){
            AccessibilityNodeInfo child=n.getChild(i);
            if(child!=null)nodes.add(child);
          }
        }finally{n.recycle();}
      }
    }finally{while(!nodes.isEmpty())nodes.removeFirst().recycle();}
    return remoteDesktopSessions.matches(root.getWindowId(),pkg,null,true,null);
  }
  /** Inspect only the currently active TWA's native document root. No additional
   * accessibility flags, other windows, browsing history or title logs. */
  private boolean remoteDesktopBrand(AccessibilityNodeInfo root,String pkg){
    // DOM view IDs are virtual nodes, not Android resource names; use the
    // existing active tree rather than findAccessibilityNodeInfosByViewId.
    java.util.ArrayDeque<AccessibilityNodeInfo> nodes=new java.util.ArrayDeque<>();
    java.util.ArrayDeque<Boolean> inGoogleRoot=new java.util.ArrayDeque<>();
    nodes.add(AccessibilityNodeInfo.obtain(root));inGoogleRoot.add(false);
    int count=0;
    try{
      while(!nodes.isEmpty() && count++<250){
        AccessibilityNodeInfo n=nodes.removeFirst();
        boolean inside=inGoogleRoot.removeFirst()||"android.webkit.WebView".contentEquals(n.getClassName());
        try{
          if(inside && pkg.contentEquals(n.getPackageName())){
            if(n.getText()!=null && AppSurfaceRules.remoteDesktopBrand(n.getViewIdResourceName(),n.getText().toString()))return true;
            CharSequence label=n.getContentDescription();
            if(label!=null && AppSurfaceRules.remoteDesktopConnectionControl(label.toString()))return true;
          }
          for(int i=0;i<n.getChildCount();i++){
            AccessibilityNodeInfo child=n.getChild(i);
            if(child!=null){nodes.add(child);inGoogleRoot.add(inside);}
          }
        }finally{n.recycle();}
      }
    }finally{while(!nodes.isEmpty())nodes.removeFirst().recycle();}
    return false;
  }
  private void inspectVisibleBrowser(){
    if(blockingOverlay!=null || closingBlockedTab)return;
    AccessibilityNodeInfo root=null;
    try {
      root=getRootInActiveWindow();
      if(root==null||root.getPackageName()==null){
        resetIfStale();return;
      }
      String pkg=root.getPackageName().toString();
      if(enforceNewApp(pkg))return;
      String restrictionPkg=restrictionPackage(root,pkg,null);
      String appCategory=AppRules.category(restrictionPkg,prefs);
      if(appCategory!=null&&!isBrowser(pkg)){enforceLimit("app:"+restrictionPkg,appCategory,"アプリ");return;}
      if(AppRules.unsupportedBrowser(pkg)){
        stopUsage();blockNow("browser:"+pkg,"未対応ブラウザ","抜け道");return;
      }
      if(!isBrowser(pkg)){stopUsage();resetIfStale();return;}
      String url=urlFromTree(root,pkg);
      if(remoteDesktopSurface(root,pkg,url)){
        activeBrowserUrl=null;activeBrowserPackage=null;trackingHost=null;
        String remoteCategory=AppRules.category(AppSurfaceRules.REMOTE_DESKTOP,prefs);
        // Attribute the shared Chrome surface to Remote Desktop. Explicit
        // Remote Desktop restrictions still apply if the user selects that app.
        String remoteAppKey=remoteCategory!=null?"app:"+AppSurfaceRules.REMOTE_DESKTOP:null;
        String remoteSiteKey=isBlocked("remotedesktop.google.com")?"domain:remotedesktop.google.com":null;
        if(remoteAppKey!=null||remoteSiteKey!=null)enforceBoth(remoteAppKey,remoteSiteKey,"Remote Desktop","アプリ");
        else stopUsage();
        return;
      }
      activeBrowserUrl=url;
      activeBrowserPackage=pkg;
      if(url==null){
        if(appCategory!=null)enforceLimit("app:"+pkg,appCategory,"ブラウザ");
        else stopUsage();
        resetIfStale();return;
      }
      Classifier.Hit hit=Classifier.inspect(url);
      if(hit.host==null){
        if(appCategory!=null)enforceLimit("app:"+pkg,appCategory,"ブラウザ");
        else stopUsage();
        resetIfStale();return;
      }
      long now=SystemClock.elapsedRealtime();
      lastSeen=now;
      if(isBlocked(hit.host)){
        enforceBrowserLimit("domain:"+hit.host,pkg,"指定サイト","Web");
        trackingHost=null;
        return;
      }
      if(!hit.flagged()){
        if(appCategory!=null)enforceLimit("app:"+pkg,appCategory,"ブラウザ");
        else stopUsage();
        trackingHost=null;
        return;
      }
      if(hit.strong) {
        enforceBrowserLimit("domain:"+hit.host,pkg,hit.category,"Web");
        trackingHost=null;
        return;
      }
      if(appCategory!=null)enforceLimit("app:"+pkg,appCategory,"ブラウザ");
      else stopUsage();
      if(!hit.host.equals(trackingHost)||!pkg.equals(trackingPackage)){
        trackingHost=hit.host;trackingPackage=pkg;
        trackingCategory=hit.category;trackingStrong=hit.strong;
        trackingStarted=now;
        if(now-lastWarning>12000){
          lastWarning=now;
          showWarning("⚠ "+hit.category+"関連サイトです。学習の目的を確認してください。");
        }
        }
    }catch(Exception ignored){
      // Failure never touches system settings or unrelated apps.
    }finally{if(root!=null)root.recycle();}
  }
  /** Supports overlapping browser-app and website budgets without suppressing either. */
  private void enforceBrowserLimit(String siteKey,String pkg,String category,String type){
    String appKey=AppRules.category(pkg,prefs)!=null?"app:"+pkg:null;
    enforceBoth(siteKey,appKey,category,type);
  }
  private void enforceLimit(String key,String category,String type){
    enforceBoth(key,null,category,type);
  }
  private void enforceBoth(String firstKey,String secondKey,String category,String type){
    if(prefs==null||blockingOverlay!=null)return;
    java.util.LinkedHashMap<String,String> groups=new java.util.LinkedHashMap<>();
    for(String key:new String[]{firstKey,secondKey}){
      if(key==null)continue;
      String id=GroupRules.DEFAULT;
      if(key.startsWith("app:"))id=GroupRules.appGroup(prefs,key.substring(4));
      else if(key.startsWith("domain:"))id=GroupRules.domainGroup(prefs,key.substring(7));
      if(GroupRules.enabled(prefs,id))groups.putIfAbsent(id,key);
    }
    long now=SystemClock.elapsedRealtime();
    boolean interactive=true;
    PowerManager pm=(PowerManager)getSystemService(Context.POWER_SERVICE);
    if(pm!=null)interactive=pm.isInteractive();
    String today=new SimpleDateFormat("yyyyMMdd",Locale.US).format(new Date());
    if(interactive && usageLastTick>0 && now>=usageLastTick){
      long delta=now-usageLastTick;
      for(String previous:new java.util.HashSet<>(usageGroups)){
        if(!GroupRules.enabled(prefs,previous)||GroupRules.mode(prefs,previous)==GroupRules.BLOCK)
          continue;
        long budget=GroupRules.minutes(prefs,previous)*60000L;
        long spent=GroupRules.todayUsed(prefs,previous,today);
        long updated=UsageLimit.accrue(spent,delta,budget);
        if(updated!=spent)
          prefs.edit().putString(GroupRules.dateKey(previous),today)
            .putLong(GroupRules.usedKey(previous),updated).apply();
      }
    }
    usageGroups.clear();
    if(interactive)usageGroups.addAll(groups.keySet());
    usageLastTick=interactive&&!groups.isEmpty()?now:0;
    long shortestRemaining=Long.MAX_VALUE;
    String shortestName=null, shortestTime=null;
    for(java.util.Map.Entry<String,String> entry:groups.entrySet()){
      String id=entry.getKey();
      String key=entry.getValue();
      if(GroupRules.mode(prefs,id)==GroupRules.BLOCK){
        stopUsage();blockAndCloseIfBrowser(key,category,type);return;
      }
      long budget=GroupRules.minutes(prefs,id)*60000L;
      long spent=GroupRules.todayUsed(prefs,id,today);
      if(UsageLimit.isExhausted(budget,spent)){
        stopUsage();blockAndCloseIfBrowser(key,category,type);return;
      }
      long remaining=UsageLimit.remainingSeconds(budget,spent);
      if(remaining<shortestRemaining){
        shortestRemaining=remaining;
        shortestName=GroupRules.name(prefs,id);
        shortestTime=UsageLimit.formatRemaining(budget,spent);
      }
    }
    if(interactive && shortestName!=null &&
        (usageWarningAt==0||now-usageWarningAt>=3600000L)){
      usageWarningAt=now;
      showWarning("⏱ "+shortestName+"：残り "+shortestTime);
    }
  }
  private void stopUsage(){
    usageGroups.clear();
    usageLastTick=0;
  }

  /** Leave a forbidden URL via the browser's normal HTTPS intent.
      Verify the visible URL changed before imposing the deterrent.
      On failure, fall back to tab switcher / close logic. */
  private boolean redirectBlockedBrowser(String key,String category,String type){
    if(closingBlockedTab || blockingOverlay!=null || activeBrowserPackage==null
        ||activeBrowserUrl==null
        ||!browserBlockKey(key))return false;
    String pkg=activeBrowserPackage, previousUrl=activeBrowserUrl;
    if(!isBrowser(pkg))return false;
    AccessibilityNodeInfo root=null;
    try{
      root=getRootInActiveWindow();
      if(root==null||root.getPackageName()==null||
          !pkg.equals(root.getPackageName().toString())||
          !previousUrl.equals(urlFromTree(root,pkg)))return false;
    }catch(Exception ignored){return false;}
    finally{if(root!=null)root.recycle();}
    closingBlockedTab=true;
    try{
      if(!navigateCurrentBrowserTab(pkg)){
        Intent safe=new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/"));
        safe.setPackage(pkg);
        safe.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(safe);
      }
    }catch(Exception e){
      closingBlockedTab=false;
      return false;
    }
    prefs.edit().putString("tab_close_stage","redirect_requested").apply();
    handler.postDelayed(()->{
      AccessibilityNodeInfo check=null;
      boolean recovered=false;
      try{
        check=getRootInActiveWindow();
        String current=check!=null && check.getPackageName()!=null
            && pkg.equals(check.getPackageName().toString())
              ?urlFromTree(check,pkg):null;
        String host=Classifier.host(current);
        recovered=host!=null && !Classifier.inspect(current).flagged()
          && !isBlocked(host);
      }catch(Exception ignored){}
      finally{if(check!=null)check.recycle();}
      closingBlockedTab=false;
      prefs.edit().putString("tab_close_stage",
        recovered?"redirect_confirmed":"redirect_unconfirmed").apply();
      finishDeniedAccess(key,category,type);
    },2400);
    return true;
  }

  /** Navigate the current tab so its player is unloaded, rather than opening a second tab. */
  private boolean navigateCurrentBrowserTab(String pkg){
    AccessibilityNodeInfo root=getRootInActiveWindow();
    if(root==null)return false;
    boolean focused=false;
    try{
      if(!pkg.contentEquals(root.getPackageName()))return false;
      List<AccessibilityNodeInfo> bars=root.findAccessibilityNodeInfosByViewId(pkg+":id/url_bar");
      if(bars!=null)for(AccessibilityNodeInfo bar:bars){
        try{focused|=bar.performAction(AccessibilityNodeInfo.ACTION_CLICK);}
        finally{bar.recycle();}
      }
    }finally{root.recycle();}
    if(!focused)return false;
    handler.postDelayed(()->{
      AccessibilityNodeInfo active=getRootInActiveWindow();
      if(active==null)return;
      try{
        if(!pkg.contentEquals(active.getPackageName()))return;
        List<AccessibilityNodeInfo> bars=active.findAccessibilityNodeInfosByViewId(pkg+":id/url_bar");
        if(bars==null)return;
        for(AccessibilityNodeInfo bar:bars){
          try{
            android.os.Bundle args=new android.os.Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"https://www.google.com/");
            if(!bar.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args))continue;
            boolean entered=android.os.Build.VERSION.SDK_INT>=30
              &&bar.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
            if(android.os.Build.VERSION.SDK_INT>=33&&!entered){
              InputMethod ime=getInputMethod();
              InputMethod.AccessibilityInputConnection connection=ime==null?null:ime.getCurrentInputConnection();
              if(connection!=null){
                long now=SystemClock.uptimeMillis();
                connection.sendKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER,0));
                connection.sendKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER,0));
              }
            }
          }finally{bar.recycle();}
        }
      }finally{active.recycle();}
    },200);
    return true;
  }

  /** Attempt Chrome's native Ctrl+W shortcut on the active tab via Android accessibility IME. */
  private boolean closeCurrentTabWithShortcut(String key,String category,String type){
    if(android.os.Build.VERSION.SDK_INT<33 || closingBlockedTab || blockingOverlay!=null)
      return false;
    String pkg=activeBrowserPackage, url=activeBrowserUrl;
    if(!"com.android.chrome".equals(pkg) || url==null)return false;
    AccessibilityNodeInfo root=null;
    int before=-1;
    boolean focused=false;
    try{
      InputMethod ime=getInputMethod();
      if(ime==null)return false;
      root=getRootInActiveWindow();
      if(root==null||root.getPackageName()==null||
          !pkg.equals(root.getPackageName().toString())||
          !url.equals(urlFromTree(root,pkg)))return false;
      before=countChromeTabs(pkg,root);
      if(before<=0)return false;
      List<AccessibilityNodeInfo> fields=root.findAccessibilityNodeInfosByViewId(pkg+":id/url_bar");
      if(fields==null||fields.isEmpty())return false;
      focused=fields.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }catch(Exception ignored){return false;}
    finally{if(root!=null)root.recycle();}
    if(!focused)return false;
    closingBlockedTab=true;
    final int previousTabs=before;
    handler.postDelayed(()->{
      boolean sent=false;
      try{
        InputMethod inputMethod=getInputMethod();
        InputMethod.AccessibilityInputConnection conn=inputMethod==null
          ?null:inputMethod.getCurrentInputConnection();
        AccessibilityNodeInfo active=getRootInActiveWindow();
        boolean inChrome=active!=null&&active.getPackageName()!=null&&
          pkg.equals(active.getPackageName().toString());
        if(active!=null)active.recycle();
        if(conn!=null && inChrome){
          long now=SystemClock.uptimeMillis();
          int meta=KeyEvent.META_CTRL_ON|KeyEvent.META_CTRL_LEFT_ON;
          int keyboard=KeyCharacterMap.VIRTUAL_KEYBOARD;
          int flags=KeyEvent.FLAG_SOFT_KEYBOARD;
          conn.sendKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_CTRL_LEFT,0,meta,keyboard,0,flags));
          conn.sendKeyEvent(new KeyEvent(now,now+1,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_W,0,meta,keyboard,0,flags));
          conn.sendKeyEvent(new KeyEvent(now,now+2,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_W,0,meta,keyboard,0,flags));
          conn.sendKeyEvent(new KeyEvent(now,now+3,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_CTRL_LEFT,0,0,keyboard,0,flags));
          sent=true;
        }
      }catch(Exception ignored){}
      prefs.edit().putString("tab_close_shortcut",sent?"sent":"no_connection").apply();
      handler.postDelayed(()->{
        AccessibilityNodeInfo check=null;
        int after=-1;
        try{check=getRootInActiveWindow();after=countChromeTabs(pkg,check);}
        catch(Exception ignored){}finally{if(check!=null)check.recycle();}
        closingBlockedTab=false;
        if(after>=0 && after<previousTabs){
          prefs.edit().putString("tab_close_stage","shortcut_closed").apply();
          if(prefs.getInt("restriction_mode",0)==2)blockNow(key,category,type);
          else timedBlock(key,category,type);
        }else{
          prefs.edit().putString("tab_close_shortcut","not_confirmed").apply();
          closeViaSwitcherAndBlock(key,category,type);
        }
      },450);
    },350);
    return true;
  }

  private int countChromeTabs(String pkg,AccessibilityNodeInfo root){
    if(root==null)return -1;
    List<AccessibilityNodeInfo> b=root.findAccessibilityNodeInfosByViewId(
      pkg+":id/tab_switcher_button");
    if(b==null || b.isEmpty() || b.get(0).getContentDescription()==null)return -1;
    java.util.regex.Matcher m=java.util.regex.Pattern.compile("(\\d+)")
      .matcher(b.get(0).getContentDescription().toString());
    if(!m.find())return -1;
    try{return Integer.parseInt(m.group(1));}catch(Exception ignored){return -1;}
  }

  private boolean browserBlockKey(String key){
    return key.startsWith("domain:")
      ||key.equals("app:"+activeBrowserPackage);
  }
  private void blockAndCloseIfBrowser(String key,String category,String type){
    stopBlockedMedia(key);
    if(redirectBlockedBrowser(key,category,type))return;
    finishDeniedAccess(key,category,type);
  }
  private void finishDeniedAccess(String key,String category,String type){
    if(prefs.getInt("restriction_mode",1)==2)blockNow(key,category,type);
    else timedBlock(key,category,type);
  }
  private void stopBlockedMedia(String key){
    if(!key.startsWith("app:"))return;
    String pkg=key.substring(4);
    int receivers=BlockedMediaStop.request(this,pkg);
    prefs.edit().putInt("last_media_stop_receivers",receivers).apply();
    if(receivers>0){
      handler.postDelayed(()->BlockedMediaStop.request(this,pkg),400);
      handler.postDelayed(()->BlockedMediaStop.request(this,pkg),1500);
    }
  }

  /** Fallback: Chrome switches to a fresh safe tab; others close only when unique. */
  private void closeViaSwitcherAndBlock(String key,String category,String type){
    if(closingBlockedTab || blockingOverlay!=null)return;
    prefs.edit().putString("tab_close_stage","entered").apply();
    String url=activeBrowserUrl, pkg=activeBrowserPackage;
    if(url==null || pkg==null || !isBrowser(pkg) ||
       !key.startsWith("domain:")){
      if(prefs.getInt("restriction_mode",0)==2)blockNow(key,category,type);
      else timedBlock(key,category,type);
      return;
    }
    AccessibilityNodeInfo root=null;
    boolean opened=false;
    try{
      root=getRootInActiveWindow();
      if(root!=null && root.getPackageName()!=null &&
         pkg.equals(root.getPackageName().toString()) &&
         url.equals(urlFromTree(root,pkg))){
        List<AccessibilityNodeInfo> buttons=root.findAccessibilityNodeInfosByViewId(
          pkg+":id/tab_switcher_button");
        if(buttons!=null)for(AccessibilityNodeInfo button:buttons){
          if(button.isEnabled() && button.performAction(AccessibilityNodeInfo.ACTION_CLICK)){
            opened=true;break;
          }
        }
      }
    }catch(Exception ignored){
    }finally{if(root!=null)root.recycle();}
    if(!opened){
      prefs.edit().putString("tab_close_stage","switcher_not_opened").apply();
      if(prefs.getInt("restriction_mode",0)==2)blockNow(key,category,type);
      else timedBlock(key,category,type);
      return;
    }
    closingBlockedTab=true;
    prefs.edit().putString("tab_close_stage","switcher_opened").apply();
    final String targetUrl=url, targetPkg=pkg;
    handler.postDelayed(()->{
      boolean recovered=false;
      try{
        recovered="com.android.chrome".equals(targetPkg)
          ?openSafeChromeTab(targetPkg)
          :closeMatchingBrowserTab(targetPkg,targetUrl);
      }catch(Exception ignored){}
      String status=recovered
        ?("com.android.chrome".equals(targetPkg)?"safe_tab_opened":"closed")
        :"no_tab_closed";
      prefs.edit().putString("tab_close_stage",status).apply();
      closingBlockedTab=false;
      if(prefs.getInt("restriction_mode",0)==2)blockNow(key,category,type);
      else timedBlock(key,category,type);
    },650);
  }

  /** When closing the current Chrome tab is unsupported, use Chrome's own New tab.
      This intentionally leaves the offending tab untouched rather than risking wrong deletion. */
  private boolean openSafeChromeTab(String pkg){
    AccessibilityNodeInfo overview=null;
    try{
      overview=getRootInActiveWindow();
      if(overview==null||overview.getPackageName()==null||
        !pkg.equals(overview.getPackageName().toString()))return false;
      List<AccessibilityNodeInfo> buttons=overview.findAccessibilityNodeInfosByViewId(
        pkg+":id/toolbar_action_button");
      if(buttons==null)return false;
      for(AccessibilityNodeInfo button:buttons){
        CharSequence description=button.getContentDescription();
        if(description!=null && "new tab".equalsIgnoreCase(description.toString().trim())
          && button.isEnabled() && button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
          return true;
      }
    }catch(Exception ignored){}
    finally{if(overview!=null)overview.recycle();}
    return false;
  }

  /** The tab switcher exposes a per-tab Close action. Only click an unambiguous match. */
  private boolean closeMatchingBrowserTab(String pkg,String targetUrl){
    AccessibilityNodeInfo overview=null;
    try{
      overview=getRootInActiveWindow();
      if(overview==null || overview.getPackageName()==null ||
         !pkg.equals(overview.getPackageName().toString()))return false;
      String host=Classifier.host(targetUrl);
      if(host==null)return false;
      List<AccessibilityNodeInfo> cards=overview.findAccessibilityNodeInfosByViewId(
        pkg+":id/content_view");
      if(cards==null)return false;
      prefs.edit().putInt("tab_close_card_count",cards.size()).apply();
      AccessibilityNodeInfo best=null;
      int bestScore=-1, tied=0;
      int titleCount=0,buttonCount=0,matchingTitles=0;
      prefs.edit().putBoolean("tab_close_supported_host",
        !BlockedTabMatcher.siteName(host).isEmpty()).apply();
      for(AccessibilityNodeInfo card:cards){
        if(card==null)continue;
        AccessibilityNodeInfo titleNode=null, actionNode=null;
        for(int i=0;i<card.getChildCount();i++){
          AccessibilityNodeInfo child=card.getChild(i);
          if(child==null)continue;
          String id=child.getViewIdResourceName();
          if((pkg+":id/tab_title").equals(id))titleNode=child;
          else if((pkg+":id/action_button").equals(id))actionNode=child;
        }
        if(titleNode==null||actionNode==null)continue;
        titleCount++;buttonCount++;
        CharSequence t=titleNode.getText();
        CharSequence a=actionNode.getContentDescription();
        if(t==null||a==null)continue;
        String desc=a.toString().toLowerCase(java.util.Locale.ROOT);
        if(!desc.contains("close ") && !desc.contains("閉じ"))continue;
        int score=BlockedTabMatcher.score(targetUrl,host,t.toString());
        if(score<0)continue;
        matchingTitles++;
        if(score>bestScore){
          best=actionNode;bestScore=score;tied=1;
        }else if(score==bestScore){tied++;}
      }
      prefs.edit().putInt("tab_close_title_count",titleCount)
        .putInt("tab_close_match_count",matchingTitles).apply();
      prefs.edit().putInt("tab_close_tied",tied).putInt("tab_close_score",bestScore).apply();
      // Do NOT guess between multiple tabs at the same site. Even a differently
      // titled tab may be the current one, so scoring must not override ambiguity.
      return matchingTitles==1 && tied==1 && best!=null &&
        best.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }catch(Exception ignored){return false;}
    finally{if(overview!=null)overview.recycle();}
  }

  /** Show a full-screen deterrent for the selected duration. This NEVER unlocks the target. */
  private void timedBlock(String key,String category,String type){
    if(blockingOverlay!=null)return;
    int seconds=CooldownSettings.seconds(prefs);
    if(seconds==0){blockNow(key,category,type);return;}
    long now=System.currentTimeMillis();
    long until=CooldownPolicy.deadline(now,seconds);
    prefs.edit()
      .putInt("blocked_total",prefs.getInt("blocked_total",0)+1)
      .putLong("last_block",now)
      .putString("last_category",category)
      .putLong("penalty_until_ms",until)
      .apply();
    clearOverlay();
    stopUsage();
    performGlobalAction(GLOBAL_ACTION_HOME);
    showTimedBlock(category,until);
  }
  private void showTimedBlock(String category,long until){
    clearTimedBlock();
    final LinearLayout panel=new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setGravity(Gravity.CENTER);
    panel.setBackgroundColor(Color.rgb(24,29,39));
    panel.setClickable(true);
    panel.setFocusable(true);
    panel.setOnTouchListener((v,event)->true);
    int pad=(int)(getResources().getDisplayMetrics().density*24);
    panel.setPadding(pad,pad,pad,pad);
    TextView heading=new TextView(this);
    heading.setText("今日はもう使えません。遊んでないで集中しなさい。");
    heading.setTextColor(Color.WHITE);
    heading.setTextSize(26);
    heading.setGravity(Gravity.CENTER);
    panel.addView(heading);
    TextView categoryText=new TextView(this);
    categoryText.setText(category+"は現在使用できません。\n時間が過ぎても使用許可にはなりません。");
    categoryText.setTextColor(Color.LTGRAY);
    categoryText.setTextSize(18);
    categoryText.setGravity(Gravity.CENTER);
    categoryText.setPadding(0,pad,0,pad);
    panel.addView(categoryText);
    TextView timer=new TextView(this);
    timer.setTextColor(Color.WHITE);
    timer.setTextSize(44);
    timer.setTypeface(null,Typeface.BOLD);
    timer.setGravity(Gravity.CENTER);
    panel.addView(timer);
    TextView footnote=new TextView(this);
    footnote.setText("振り返り時間が終わるとホームに戻ります。対象の使用禁止は続きます。");
    footnote.setTextColor(Color.LTGRAY);
    footnote.setTextSize(15);
    footnote.setGravity(Gravity.CENTER);
    panel.addView(footnote);
    WindowManager.LayoutParams params=new WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|
        WindowManager.LayoutParams.FLAG_FULLSCREEN,
      PixelFormat.TRANSLUCENT);
    params.gravity=Gravity.CENTER;
    try{
      windowManager.addView(panel,params);
      blockingOverlay=panel;
      final Runnable tick=new Runnable(){
        @Override public void run(){
          if(blockingOverlay!=panel)return;
          long remaining=CooldownPolicy.remainingSeconds(System.currentTimeMillis(),until);
          if(remaining<=0){
            prefs.edit().remove("penalty_until_ms").apply();
            clearTimedBlock();
            performGlobalAction(GLOBAL_ACTION_HOME);
            return;
          }
          timer.setText(remaining+"秒");
          handler.postDelayed(this,250);
        }
      };
      blockingTicker=tick;
      handler.post(tick);
    }catch(Exception ignored){
      // Overlay failure must never grant access to a denied app.
      blockingOverlay=null;
      prefs.edit().remove("penalty_until_ms").apply();
      performGlobalAction(GLOBAL_ACTION_HOME);
    }
  }
  private void clearTimedBlock(){
    if(blockingTicker!=null)handler.removeCallbacks(blockingTicker);
    blockingTicker=null;
    if(blockingOverlay!=null&&windowManager!=null){
      try{windowManager.removeView(blockingOverlay);}catch(Exception ignored){}
    }
    blockingOverlay=null;
  }
  private void blockNow(String key,String category,String type){
    stopUsage();
    long now=SystemClock.elapsedRealtime();
    if(now-lastBlockedAction>=1600){
      lastBlockedAction=now;
      prefs.edit()
        .putInt("blocked_total",prefs.getInt("blocked_total",0)+1)
        .putLong("last_block",System.currentTimeMillis())
        .putString("last_category",category)
        .apply();
      showWarning("⛔ 今日はもう使えません。遊んでないで集中しなさい。");
    }
    // No waiting screen must still deny every attempt, including rapid reopens.
    performGlobalAction(GLOBAL_ACTION_HOME);
  }
  /** Evaluated before browser exemptions, app exclusions and group settings. */
  private boolean enforceNewApp(String pkg){
    if(!NewAppGuard.blocked(this,prefs,pkg))return false;
    stopBlockedMedia("app:"+pkg);
    stopUsage();
    trackingHost=null;activeBrowserUrl=null;activeBrowserPackage=null;
    long now=SystemClock.elapsedRealtime();
    if(now-lastBlockedAction>=1600){
      lastBlockedAction=now;
      prefs.edit().putInt("blocked_total",prefs.getInt("blocked_total",0)+1)
        .putLong("last_block",System.currentTimeMillis())
        .putString("last_category","新規アプリ").apply();
      showWarning("⛔ 新しくインストールしたアプリは使用できません。集中を続けましょう。");
    }
    // Keep returning Home even during the notification throttle.
    performGlobalAction(GLOBAL_ACTION_HOME);
    return true;
  }
  private void resetIfStale(){
    if(SystemClock.elapsedRealtime()-lastSeen>6000)trackingHost=null;
  }
  private boolean isBlocked(String host){
    String entry=prefs.getString("blocked:"+host,null);
    if(entry!=null)return true;
    // Block subdomains of previously blocked domain.
    String[] parts=host.split("\\.");
    for(int i=1;i<parts.length-1;i++){
      StringBuilder base=new StringBuilder();
      for(int j=i;j<parts.length;j++) {
        if(j>i)base.append('.');
        base.append(parts[j]);
      }
      if(prefs.contains("blocked:"+base))return true;
    }
    return false;
  }
  private void blockLocally(String host,String category){
    if(host==null||!trackingStrong)return;
    prefs.edit().putString("blocked:"+host,category+" | "+System.currentTimeMillis()).apply();
  }
  private void showWarning(String message){
    clearOverlay();
    TextView text=new TextView(this);
    text.setText(message);
    text.setTextColor(Color.WHITE);
    text.setTextSize(17);
    text.setTypeface(null,Typeface.BOLD);
    text.setBackgroundColor(Color.rgb(120,40,36));
    int dp=(int)(getResources().getDisplayMetrics().density*16);
    text.setPadding(dp,dp,dp,dp);
    WindowManager.LayoutParams p=new WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
      PixelFormat.TRANSLUCENT
    );
    p.gravity=Gravity.TOP;
    try{windowManager.addView(text,p);currentOverlay=text;}
    catch(Exception ignored){}
    handler.postDelayed(()->{if(currentOverlay==text)clearOverlay();},5500);
  }
  private void clearOverlay(){
    if(currentOverlay!=null&&windowManager!=null){
      try{windowManager.removeView(currentOverlay);}catch(Exception ignored){}
      currentOverlay=null;
    }
  }
}
