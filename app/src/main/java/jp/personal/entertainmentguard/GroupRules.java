package jp.personal.entertainmentguard;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Date;
import java.text.SimpleDateFormat;

/** One mixed app/website budget per group. Strict mode permits only stronger policies. */
public final class GroupRules {
  private GroupRules(){}
  public static final String DEFAULT="default";
  public static final int BLOCK=0;
  public static final int DAILY=1;

  public static void ensure(SharedPreferences p){
    if(p.getInt("group_schema",0)>=1)return;
    int minutes=p.getInt("limit_minutes",10);
    if(!UsageLimit.validMinutes(minutes))minutes=10;
    int previousMode=p.getInt("restriction_mode",1);
    p.edit().putString("group.ids",DEFAULT)
      .putString("group.name."+DEFAULT,"基本グループ")
      .putInt("group.mode."+DEFAULT,previousMode==1?DAILY:BLOCK)
      .putInt("group.minutes."+DEFAULT,minutes)
      .putInt("group.next_id",1).putInt("group_schema",1).commit();
  }
  /** Time information; platform implementation is initialized in GuardApplication. */
  public interface TimeSource {
    long elapsedMs();
    int bootCount(); // -1 if not available; never assume same boot in that case.
    long networkMs(); // -1 unless Android's independently obtained network time is available.
    long wallMs(); // for display and calendar inputs; NEVER sufficient to unlock.
  }
  private static TimeSource timeSource;
  public static void setTimeSource(TimeSource source){timeSource=source;}
  private static TimeSource clock(){
    if(timeSource==null)
      throw new IllegalStateException("Strict clock not initialized");
    return timeSource;
  }
  private static final String STRICT_UNTIL="strict_until_ms";
  private static final String STRICT_START="strict_started_elapsed";
  private static final String STRICT_DURATION="strict_duration_elapsed";
  private static final String STRICT_BOOT="strict_boot_count";
  private static final long MAX_DURATION_MS=32000000000000L;

  /** Unlock only after an unadjustable interval within one boot, or independent network
   *  time confirmation after reboot. Device wall-clock changes NEVER directly unlock. */
  public static boolean strict(SharedPreferences p){
    if(!p.getBoolean("strict_mode",false))return false;
    long until=p.getLong(STRICT_UNTIL,0);
    if(until<=0)return true; // Old indefinite lock (v1.2.0).
    TimeSource ts=clock();
    long duration=p.getLong(STRICT_DURATION,0);
    long start=p.getLong(STRICT_START,-1);
    int boot=p.getInt(STRICT_BOOT,-1);
    int currentBoot=ts.bootCount();
    boolean sameBoot=duration>0 && start>=0 && boot>=0 && boot==currentBoot
      && ts.elapsedMs()>=start;
    boolean expired=false;
    if(sameBoot){
      // Elapsed realtime counts deep sleep. Ignore manually changed wall-clock/network time.
      expired=ts.elapsedMs()-start>=duration;
    }else{
      // Cross-boot (or legacy deadline) cannot be proved with local wall time.
      // The system's network-time clock does not use user's manual clock.
      long remote=ts.networkMs();
      expired=remote>0 && remote>=until;
    }
    if(expired){
      p.edit().putBoolean("strict_mode",false).remove(STRICT_UNTIL)
        .remove(STRICT_START).remove(STRICT_DURATION).remove(STRICT_BOOT)
        .remove("strict_duration_label").commit();
      return false;
    }
    return true;
  }
  public static long strictUntil(SharedPreferences p){
    return strict(p)?p.getLong(STRICT_UNTIL,0):0;
  }
  /** -1 means restart/network verification is needed, 0 means unlocked. */
  public static long strictRemainingMs(SharedPreferences p){
    if(!strict(p))return 0;
    TimeSource ts=clock();
    int boot=p.getInt(STRICT_BOOT,-1);
    long start=p.getLong(STRICT_START,-1);
    long duration=p.getLong(STRICT_DURATION,0);
    if(boot>=0 && boot==ts.bootCount() && start>=0 && duration>0
        && ts.elapsedMs()>=start)
      return Math.max(0,duration-(ts.elapsedMs()-start));
    long network=ts.networkMs();
    long until=p.getLong(STRICT_UNTIL,0);
    return until>0 && network>0?Math.max(0,until-network):-1;
  }
  /** Persist a monotonic time interval alongside a wall-clock display deadline.
   *  Caller fixes the interval BEFORE any final confirmation dialog. */
  public static boolean enableStrictForDuration(SharedPreferences p,long duration,long until,String label){
    ensure(p);
    if(strict(p)||duration<=0||duration>MAX_DURATION_MS||until<=0||
        label==null||label.isEmpty())return false;
    TimeSource ts=clock();
    int boot=ts.bootCount();
    // Never start a lock which cannot be measured or safely verified.
    if(boot<0)return false;
    long elapsed=ts.elapsedMs();
    return p.edit().putLong(STRICT_UNTIL,until)
      .putLong(STRICT_START,elapsed).putLong(STRICT_DURATION,duration)
      .putInt(STRICT_BOOT,boot)
      .putString("strict_duration_label",label)
      .putBoolean("strict_mode",true).commit();
  }
  public static List<String> ids(SharedPreferences p){
    List<String> list=new ArrayList<>();list.add(DEFAULT);
    for(String id:p.getString("group.ids",DEFAULT).split(",")){
      if(id.matches("g[0-9]+")&&!list.contains(id))list.add(id);
    }
    return list;
  }
  public static boolean exists(SharedPreferences p,String id){return id!=null&&ids(p).contains(id);}
  public static String name(SharedPreferences p,String id){
    return p.getString("group.name."+id,DEFAULT.equals(id)?"基本グループ":"制限グループ");
  }
  public static int mode(SharedPreferences p,String id){return p.getInt("group.mode."+id,DAILY);}
  public static int minutes(SharedPreferences p,String id){
    int m=p.getInt("group.minutes."+id,10);
    return m>=1&&m<=1440?m:10;
  }
  public static boolean enabled(SharedPreferences p,String id){
    return exists(p,id)&&p.getBoolean("group.enabled."+id,true);
  }
  public static boolean setEnabled(SharedPreferences p,String id,boolean enabled){
    if(!exists(p,id))return false;
    if(strict(p) && !enabled && enabled(p,id))return false;
    if(strict(p) && !enabled)return false;
    return p.edit().putBoolean("group.enabled."+id,enabled).commit();
  }
  public static String appGroup(SharedPreferences p,String pkg){
    String id=p.getString("group.app."+pkg,DEFAULT);
    return exists(p,id)?id:DEFAULT;
  }
  public static String domainGroup(SharedPreferences p,String host){
    if(host==null)return DEFAULT;
    String h=host.toLowerCase(Locale.ROOT);
    while(h.contains(".")){
      String match=p.getString("group.domain."+h,null);
      if(exists(p,match))return match;
      h=h.substring(h.indexOf('.')+1);
    }
    return DEFAULT;
  }
  /** AppBlock-style keyword restrictions, scoped to a chosen group. */
  public static String keywordGroup(SharedPreferences p,String query){
    if(query==null||query.isEmpty())return null;
    String lower=query.toLowerCase(Locale.ROOT);
    for(String key:p.getAll().keySet()){
      if(!key.startsWith("group.keyword."))continue;
      String word=key.substring("group.keyword.".length()).toLowerCase(Locale.ROOT);
      if(word.isEmpty()||!lower.contains(word))continue;
      String id=p.getString(key,null);
      if(exists(p,id))return id;
    }
    return null;
  }
  public static boolean assignKeyword(SharedPreferences p,String id,String word){
    if(!exists(p,id)||word==null)return false;
    String w=word.trim().toLowerCase(Locale.ROOT);
    if(!w.matches("[a-z0-9]{2,48}"))return false;
    String previous=p.getString("group.keyword."+w,null);
    if(strict(p)&&previous!=null && !atLeastAsStrict(p,id,previous))return false;
    return p.edit().putString("group.keyword."+w,id).commit();
  }
  public static boolean removeKeyword(SharedPreferences p,String word){
    if(strict(p)||word==null)return false;
    return p.edit().remove("group.keyword."+word.toLowerCase(Locale.ROOT)).commit();
  }
  public static boolean explicitDomain(SharedPreferences p,String host){return p.contains("group.domain."+host);}
  public static String dateKey(String id){return DEFAULT.equals(id)?"usage_date":"group.date."+id;}
  public static String usedKey(String id){return DEFAULT.equals(id)?"usage_ms":"group.used."+id;}
  public static long todayUsed(SharedPreferences p,String id,String day){
    return day.equals(p.getString(dateKey(id),""))?p.getLong(usedKey(id),0):0;
  }
  /** True when the destination allows no more access than the source. */
  public static boolean atLeastAsStrict(SharedPreferences p,String target,String source){
    if(!exists(p,target)||!exists(p,source))return false;
    if(!enabled(p,source))return true;
    if(!enabled(p,target))return false;
    if(mode(p,target)==BLOCK)return true;
    if(mode(p,source)==BLOCK)return false;
    return minutes(p,target)<=minutes(p,source);
  }
  public static String create(SharedPreferences p,String name,int mode,int minutes){
    ensure(p);
    if(name==null||name.trim().isEmpty()||(mode!=BLOCK&&mode!=DAILY)
        ||minutes<1||minutes>1440)return null;
    int next=p.getInt("group.next_id",1);
    String id="g"+next;
    List<String> all=ids(p);all.add(id);
    boolean success=p.edit().putString("group.ids",String.join(",",all))
      .putString("group.name."+id,name.trim())
      .putInt("group.mode."+id,mode).putInt("group.minutes."+id,minutes)
      .putInt("group.next_id",next+1).commit();
    return success?id:null;
  }
  public static boolean configure(SharedPreferences p,String id,String name,int mode,int minutes){
    if(!exists(p,id)||name==null||name.trim().isEmpty()
        ||(mode!=BLOCK&&mode!=DAILY)||minutes<1||minutes>1440)return false;
    if(strict(p) && (mode(p,id)==BLOCK && mode!=BLOCK ||
       mode(p,id)==DAILY && mode!=BLOCK && minutes>minutes(p,id)))return false;
    return p.edit().putString("group.name."+id,name.trim())
      .putInt("group.mode."+id,mode).putInt("group.minutes."+id,minutes).commit();
  }
  /** Preserve consumption when transferring a restriction. */
  private static void carryToday(SharedPreferences p,String from,String to){
    if(from.equals(to))return;
    String today=new SimpleDateFormat("yyyyMMdd",Locale.US).format(new Date());
    long used=Math.max(todayUsed(p,from,today),todayUsed(p,to,today));
    p.edit().putString(dateKey(to),today).putLong(usedKey(to),used).commit();
  }
  public static boolean assignApp(SharedPreferences p,String id,String pkg){
    if(!exists(p,id)||pkg==null||pkg.isEmpty())return false;
    String from=appGroup(p,pkg);
    // A previously disabled app is being newly restricted, not weakened.
    boolean selected=RestrictionTargets.app(p,pkg);
    if(strict(p)&&selected&&!atLeastAsStrict(p,id,from))return false;
    carryToday(p,from,id);
    return p.edit().putString("group.app."+pkg,id).putBoolean("app:"+pkg,true).commit();
  }
  public static boolean assignDomain(SharedPreferences p,String id,String host){
    if(!exists(p,id)||host==null||host.isEmpty())return false;
    String from=domainGroup(p,host);
    boolean protectedAlready=RestrictionTargets.domain(p,host);
    if(strict(p)&&protectedAlready&&!atLeastAsStrict(p,id,from))return false;
    carryToday(p,from,id);
    return p.edit().putString("group.domain."+host,id)
      .putString("blocked:"+host,"グループ登録").commit();
  }
  public static boolean removeAppFromGroup(SharedPreferences p,String id,String pkg){
    if(strict(p)||DEFAULT.equals(id)||!id.equals(appGroup(p,pkg)))return false;
    carryToday(p,id,DEFAULT);
    return p.edit().remove("group.app."+pkg).commit();
  }
  public static boolean disableApp(SharedPreferences p,String pkg){
    if(strict(p))return false;
    return p.edit().putBoolean("app:"+pkg,false).remove("group.app."+pkg).commit();
  }
  public static boolean removeDomainFromGroup(SharedPreferences p,String id,String host){
    if(strict(p)||DEFAULT.equals(id)||!id.equals(domainGroup(p,host)))return false;
    carryToday(p,id,DEFAULT);
    return p.edit().remove("group.domain."+host).commit();
  }
  public static boolean removeDomain(SharedPreferences p,String host){
    if(strict(p))return false;
    return p.edit().remove("group.domain."+host).remove("blocked:"+host).commit();
  }
  public static boolean delete(SharedPreferences p,String id){
    if(strict(p)||DEFAULT.equals(id)||!exists(p,id))return false;
    carryToday(p,id,DEFAULT);
    List<String> all=ids(p);all.remove(id);
    SharedPreferences.Editor edit=p.edit();
    edit.putString("group.ids",String.join(",",all));
    edit.remove("group.name."+id).remove("group.mode."+id)
      .remove("group.minutes."+id).remove("group.enabled."+id);
    for(String k:p.getAll().keySet()){
      if(k.startsWith("group.app.")||k.startsWith("group.domain.")){
        if(id.equals(p.getAll().get(k)))edit.remove(k);
      }
    }
    return edit.commit();
  }
}
