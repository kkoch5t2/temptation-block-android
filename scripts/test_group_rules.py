#!/usr/bin/env python3
"""Run GroupRules logic against an in-memory SharedPreferences stub (no device changes)."""
from pathlib import Path
import subprocess, tempfile, textwrap
root=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix="guard-groups-") as directory:
    tmp=Path(directory)
    (tmp/"android/content").mkdir(parents=True)
    (tmp/"jp/personal/entertainmentguard").mkdir(parents=True)
    (tmp/"android/content/SharedPreferences.java").write_text("""
package android.content;
import java.util.Map;
public interface SharedPreferences {
  String getString(String key,String def);
  int getInt(String key,int def);
  long getLong(String key,long def);
  boolean getBoolean(String key,boolean def);
  boolean contains(String key);
  Map<String,?> getAll();
  Editor edit();
  interface Editor{
    Editor putString(String k,String v);
    Editor putInt(String k,int v);
    Editor putLong(String k,long v);
    Editor putBoolean(String k,boolean v);
    Editor remove(String k);
    boolean commit();
    void apply();
  }
}
""")
    (tmp/"jp/personal/entertainmentguard/GroupRulesTest.java").write_text("""
package jp.personal.entertainmentguard;
import android.content.SharedPreferences;
import java.util.*;
import java.text.SimpleDateFormat;
public class GroupRulesTest {
  static class Memory implements SharedPreferences{
    final Map<String,Object> map=new HashMap<>();
    public String getString(String k,String d){return (String)map.getOrDefault(k,d);}
    public int getInt(String k,int d){return (Integer)map.getOrDefault(k,d);}
    public long getLong(String k,long d){return (Long)map.getOrDefault(k,d);}
    public boolean getBoolean(String k,boolean d){return (Boolean)map.getOrDefault(k,d);}
    public boolean contains(String k){return map.containsKey(k);}
    public Map<String,?> getAll(){return map;}
    public Editor edit(){return new Editor(){
      final Map<String,Object> updates=new HashMap<>();
      final Set<String> removals=new HashSet<>();
      public Editor putString(String k,String v){updates.put(k,v);return this;}
      public Editor putInt(String k,int v){updates.put(k,v);return this;}
      public Editor putLong(String k,long v){updates.put(k,v);return this;}
      public Editor putBoolean(String k,boolean v){updates.put(k,v);return this;}
      public Editor remove(String k){removals.add(k);return this;}
      public boolean commit(){removals.forEach(map::remove);map.putAll(updates);return true;}
      public void apply(){commit();}
    };}
  }
  static class FakeClock implements GroupRules.TimeSource {
    long elapsed=1_000_000;
    int boot=35;
    long network=-1;
    long wall=System.currentTimeMillis();
    public long elapsedMs(){return elapsed;}
    public int bootCount(){return boot;}
    public long networkMs(){return network;}
    public long wallMs(){return wall;}
  }
  private static void eq(Object actual,Object expect,String description){
    if(!Objects.equals(actual,expect))throw new AssertionError(description+": "+actual+" != "+expect);
    System.out.println("PASS "+description);
  }
  public static void main(String[] a){
    Memory n=new Memory();
    GroupRules.setTimeSource(new FakeClock());
    Memory explicit=new Memory();GroupRules.ensure(explicit);
    for(String pkg:new String[]{"com.example.notes","com.google.android.youtube","com.miui.videoplayer","org.mozilla.firefox","com.android.chrome","com.google.android.googlequicksearchbox","com.google.android.apps.bard"}){
      eq(AppRules.category(pkg,explicit),null,"unselected app is allowed: "+pkg);
      eq(RestrictionTargets.activeKey(explicit,"app:"+pkg),false,"no default group enforcement: "+pkg);
    }
    eq(RestrictionTargets.domain(explicit,"youtube.com"),false,"known domain is not implicitly selected");
    eq(RestrictionTargets.activeKey(explicit,"domain:youtube.com"),false,"unselected entertainment site allowed");
    GroupRules.assignApp(explicit,GroupRules.DEFAULT,"com.example.selected");
    eq(RestrictionTargets.activeKey(explicit,"app:com.example.selected"),true,"explicit app selected");
    explicit.edit().putBoolean("app:com.example.selected",false).commit();
    eq(RestrictionTargets.activeKey(explicit,"app:com.example.selected"),false,"explicit exclusion overrides old group");
    explicit.edit().remove("app:com.example.selected").commit();
    eq(RestrictionTargets.activeKey(explicit,"app:com.example.selected"),true,"group-only imported selection retained");
    GroupRules.assignDomain(explicit,GroupRules.DEFAULT,"example.com");
    eq(RestrictionTargets.activeKey(explicit,"domain:video.example.com"),true,"registered site covers subdomains");
    eq(RestrictionTargets.activeKey(explicit,"domain:example.com.evil.test"),false,"site suffix spoofing never selects");
    eq(RestrictionTargets.activeKey(explicit,"domain:example.org"),false,"other site not selected");
    explicit.edit().putString("blocked:legacy.example","manual import").commit();
    eq(RestrictionTargets.activeKey(explicit,"domain:sub.legacy.example"),true,"legacy explicit domain retained");
    eq(RestrictionTargets.activeKey(explicit,"browser:org.mozilla.firefox"),false,"unsupported browser cannot authorize denial");
    eq(RestrictionTargets.sameWindow("browser",7,"notes",7),false,"delayed block cancelled on app change");
    eq(RestrictionTargets.sameWindow("browser",7,"browser",8),false,"delayed block cancelled on window change");
    eq(RestrictionTargets.sameWindow("browser",-1,"browser",-1),false,"unknown windows cannot authorize delayed block");
    eq(RestrictionTargets.sameWindow("browser",7,"browser",7),true,"original target window remains eligible");
    GroupRules.setEnabled(explicit,GroupRules.DEFAULT,false);
    eq(RestrictionTargets.activeKey(explicit,"app:com.example.selected"),false,"inactive app group cannot block");
    eq(RestrictionTargets.activeKey(explicit,"domain:example.com"),false,"inactive site group cannot block");

    eq(NewAppRules.enabled(n),false,"new apps default off");
    eq(NewAppRules.enable(n,Map.of()),false,"incomplete baseline rejected");
    eq(NewAppRules.enable(n,Map.of("existing",100L)),true,"snapshot activation");
    eq(NewAppRules.blocked(n,"existing",100L,false),false,"existing installation preserved");
    eq(NewAppRules.blocked(n,"fresh",200L,false),true,"new installation denied");
    eq(NewAppRules.blocked(n,"existing",201L,false),true,"reinstallation denied");
    eq(NewAppRules.blocked(n,"fresh",-1L,false),true,"unknown identity denied");
    eq(NewAppRules.blocked(n,"system",201L,true),false,"system components preserved");
    n.edit().putBoolean("app:fresh",false).putBoolean("group.enabled.default",false).commit();
    eq(NewAppRules.blocked(n,"fresh",200L,false),true,"app exclusion and inactive group cannot bypass");
    eq(NewAppRules.enable(n,Map.of("fresh",200L)),true,"repeat activation harmless");
    eq(NewAppRules.blocked(n,"fresh",200L,false),true,"repeat activation never resets baseline");
    n.edit().putBoolean("strict_mode",true).commit();
    eq(NewAppRules.disable(n),false,"strict mode prevents disabling");
    eq(NewAppRules.blocked(n,"fresh",200L,false),true,"strict denial persists");
    n.edit().putBoolean("strict_mode",false).putBoolean("settings_locked",true).commit();
    eq(NewAppRules.disable(n),false,"permanent settings lock prevents disabling");
    n.edit().putBoolean("settings_locked",false).commit();
    eq(NewAppRules.disable(n),true,"unlocked disable allowed");
    eq(NewAppRules.blocked(n,"fresh",200L,false),false,"disabled feature permits new app");
    n.edit().putBoolean("strict_mode",true).commit();
    eq(NewAppRules.enable(n,Map.of("fresh",200L)),true,"strengthening allowed in strict mode");
    eq(NewAppRules.blocked(n,"fresh",200L,false),false,"new activation snapshots current installs");
    eq(n.contains("new_apps.existing.existing"),false,"old baseline removed on new activation");
    Memory cooldown=new Memory();
    FakeClock cooldownClock=new FakeClock();
    GroupRules.setTimeSource(cooldownClock);
    eq(CooldownSettings.seconds(cooldown),30,"existing installs retain 30 seconds");
    for(int seconds:CooldownPolicy.secondOptions()){
      eq(CooldownSettings.setSeconds(cooldown,seconds),true,"select cooldown "+seconds);
      long deadline=CooldownPolicy.deadline(100000,seconds);
      eq(deadline,100000+seconds*1000L,"selected penalty deadline "+seconds);
      eq(CooldownPolicy.remainingSeconds(deadline-1,deadline),1,"last millisecond rounds up "+seconds);
      eq(CooldownPolicy.waiting(deadline,deadline),false,"penalty ends at selected boundary "+seconds);
    }
    Memory restored=new Memory();restored.map.putAll(cooldown.map);
    eq(CooldownSettings.seconds(restored),180,"cooldown persists across preferences reload");
    eq(CooldownSettings.setSeconds(cooldown,0),true,"no-wait cooldown accepted");
    eq(CooldownPolicy.waiting(100000,CooldownPolicy.deadline(100000,0)),false,"no-wait deadline is immediately over");
    eq(CooldownPolicy.remainingSeconds(100000,CooldownPolicy.deadline(100000,0)),0,"no-wait has zero countdown");
    eq(CooldownPolicy.label(0),"なし","no-wait label");
    eq(CooldownSettings.setSeconds(cooldown,181),false,"above maximum rejected");
    eq(CooldownSettings.setSeconds(cooldown,-1),false,"negative cooldown rejected");
    eq(CooldownSettings.setSeconds(cooldown,7),false,"unsupported cooldown rejected");
    cooldown.edit().putInt("cooldown_seconds",7).commit();
    eq(CooldownSettings.seconds(cooldown),30,"invalid stored cooldown safely defaults");
    eq(CooldownSettings.setSeconds(cooldown,30),true,"restore default cooldown");
    cooldown.edit().putLong("group.used.default",123000L).commit();
    eq(GroupRules.enableStrictForDuration(cooldown,1000,cooldownClock.wall+1000,"test"),true,"start strict after dialog could open");
    eq(CooldownSettings.locked(cooldown),true,"strict duration selector disabled");
    eq(CooldownSettings.setSeconds(cooldown,0),false,"strict rejects switching to no-wait");
    eq(CooldownSettings.setSeconds(cooldown,5),false,"strict rejects shorter penalty");
    eq(CooldownSettings.setSeconds(cooldown,180),false,"strict rejects longer penalty");
    eq(CooldownSettings.seconds(cooldown),30,"failed strict save preserves duration");
    cooldownClock.elapsed+=1000;
    eq(CooldownSettings.setSeconds(cooldown,10),true,"duration editable after strict expires");
    eq(cooldown.getLong("group.used.default",0),123000L,"duration changes preserve usage");
    cooldown.edit().putBoolean("settings_locked",true).commit();
    eq(CooldownSettings.setSeconds(cooldown,30),false,"permanent settings lock rejects cooldown edits");
    eq(CooldownSettings.seconds(cooldown),10,"permanent lock retains selected duration");
    Memory p=new Memory();
    FakeClock clock=new FakeClock();
    GroupRules.setTimeSource(clock);
    String today=new SimpleDateFormat("yyyyMMdd",Locale.US).format(new Date());
    p.edit().putString("usage_date",today).putLong("usage_ms",123000L)
      .putInt("restriction_mode",1).putInt("limit_minutes",10).commit();
    GroupRules.ensure(p);
    eq(GroupRules.todayUsed(p,GroupRules.DEFAULT,today),123000L,"legacy time preserved");
    eq(GroupRules.mode(p,GroupRules.DEFAULT),GroupRules.DAILY,"legacy daily policy");
    eq(GroupRules.minutes(p,GroupRules.DEFAULT),10,"legacy ten minutes");
    String a1=GroupRules.create(p,"漫画・動画",GroupRules.DAILY,3);
    String a2=GroupRules.create(p,"ゲーム禁止",GroupRules.BLOCK,10);
    GroupRules.assignApp(p,a1,"com.example.manga");
    eq(GroupRules.appGroup(p,"com.example.manga"),a1,"app group assignment");
    eq(GroupRules.todayUsed(p,a1,today),123000L,"move cannot reset usage");
    GroupRules.assignDomain(p,a1,"youtube.com");
    eq(GroupRules.domainGroup(p,"m.youtube.com"),a1,"site subdomains grouped");
    eq(GroupRules.domainGroup(p,"notyoutube.com"),GroupRules.DEFAULT,"spoofed host excluded");
    eq(p.contains("blocked:youtube.com"),true,"group domain protected");
    GroupRules.assignApp(p,a2,"com.example.game");
    eq(GroupRules.mode(p,a2),GroupRules.BLOCK,"always block policy");
    GroupRules.assignDomain(p,a2,"youtube.com");
    eq(GroupRules.domainGroup(p,"m.youtube.com"),a2,"site reassigned not duplicated");
    eq(GroupRules.todayUsed(p,a2,today),123000L,"time carries between groups");
    GroupRules.removeAppFromGroup(p,a1,"com.example.manga");
    eq(GroupRules.appGroup(p,"com.example.manga"),GroupRules.DEFAULT,"app moves back");
    GroupRules.delete(p,a2);
    eq(GroupRules.ids(p).size(),2,"group deletion");
    eq(GroupRules.domainGroup(p,"m.youtube.com"),GroupRules.DEFAULT,"site falls back on deletion");
    eq(p.contains("group.used."+a2),true,"usage retained on deletion");
    eq(GroupRules.todayUsed(p,GroupRules.DEFAULT,today),123000L,"default usage retained");
    eq(GroupRules.strict(p),false,"strict mode defaults off");
    eq(AppRules.knownCategory("com.google.android.youtube"),"動画","YouTube classified as video");
    eq(AppRules.knownCategory("com.google.android.apps.youtube.music"),"音楽・ラジオ","YouTube Music classified as music");
    eq(AppRules.knownCategory("com.google.android.videos"),"動画","Google TV classified as video");
    eq(AppRules.knownCategory("com.miui.videoplayer"),"動画","Mi Video classified as entertainment");
    eq(AppRules.knownCategory("com.miui.player"),"音楽・ラジオ","Mi Music classified as entertainment");
    eq(AppRules.knownCategory("com.miui.fm"),"音楽・ラジオ","Mi FM classified as entertainment");
    eq(AppRules.knownCategory("jp.hamitv.hamiand1"),"動画","HamiTV classified as video");
    eq(AppRules.unsupportedBrowser("com.mi.globalbrowser"),true,"Mi Browser unsupported bypass is detected");
    String restrictedBrowser=GroupRules.create(p,"Redmiブラウザ禁止",GroupRules.BLOCK,10);
    eq(GroupRules.assignApp(p,restrictedBrowser,"com.mi.globalbrowser"),true,"assign Mi Browser to no-access group");
    eq(AppRules.category("com.mi.globalbrowser",p),"指定アプリ","Mi Browser assignment detectable");
    eq(GroupRules.mode(p,GroupRules.appGroup(p,"com.mi.globalbrowser")),GroupRules.BLOCK,"Mi Browser mapped to always block");
    String youtubeBlock=GroupRules.create(p,"絶対使わせないアプリ",GroupRules.BLOCK,10);
    eq(GroupRules.assignApp(p,youtubeBlock,"com.google.android.youtube"),true,"YouTube app assigned to permanent-block group");
    eq(GroupRules.assignApp(p,youtubeBlock,"com.google.android.apps.youtube.music"),true,"YouTube Music assigned to permanent-block group");
    eq(GroupRules.assignApp(p,youtubeBlock,"com.google.android.videos"),true,"Google TV assigned to permanent-block group");
    eq(GroupRules.setEnabled(p,youtubeBlock,false),true,"disabled group does not enforce");
    eq(GroupRules.enabled(p,GroupRules.appGroup(p,"com.google.android.youtube")),false,"reproduces YouTube bypass from paused group");
    eq(GroupRules.setEnabled(p,youtubeBlock,true),true,"reactivate YouTube permanent-block group");
    for(String video:new String[]{"com.google.android.youtube","com.google.android.apps.youtube.music","com.google.android.videos"}){
      String group=GroupRules.appGroup(p,video);
      eq(GroupRules.enabled(p,group)&&GroupRules.mode(p,group)==GroupRules.BLOCK,true,"permanent block active: "+video);
    }
    String importedSites=GroupRules.create(p,"娯楽系サイト",GroupRules.BLOCK,10);
    eq(GroupRules.assignDomain(p,importedSites,"youtube.com"),true,"import YouTube domain");
    eq(GroupRules.assignKeyword(p,importedSites,"manga"),true,"import manga keyword");
    eq(GroupRules.setEnabled(p,importedSites,false),true,"imported sites are initially paused");
    eq(GroupRules.assignDomain(p,GroupRules.DEFAULT,"youtube.com"),true,"YouTube domain moved to shared entertainment budget");
    eq(GroupRules.assignKeyword(p,GroupRules.DEFAULT,"manga"),true,"manga keyword moved to shared entertainment budget");
    eq(GroupRules.domainGroup(p,"music.youtube.com"),GroupRules.DEFAULT,"YouTube Music website shares default daily budget");
    eq(GroupRules.keywordGroup(p,"manga ranking"),GroupRules.DEFAULT,"entertainment search shares default daily budget");
    eq(GroupRules.todayUsed(p,GroupRules.DEFAULT,today),123000L,"shared entertainment usage retained during reassignment");
    String weak=GroupRules.create(p,"自由時間",GroupRules.DAILY,30);
    String paused=GroupRules.create(p,"一時停止",GroupRules.DAILY,10);
    eq(GroupRules.setEnabled(p,paused,false),true,"can disable group before strict");
    eq(GroupRules.enabled(p,paused),false,"paused group inactive");
    eq(GroupRules.configure(p,a1,"漫画・動画",GroupRules.DAILY,15),true,"can relax before strict");
    long strictStart=clock.wall;
    eq(GroupRules.enableStrictForDuration(p,-1000L,strictStart-1000L,"期限切れ"),false,"cannot start invalid strict duration");
    eq(GroupRules.enableStrictForDuration(p,3600000L,strictStart+3600000L,"1時間"),true,"can enable 1 hour strict mode");
    eq(GroupRules.strictUntil(p)>strictStart,true,"expiry timestamp persisted");
    eq(GroupRules.enableStrictForDuration(p,7200000L,strictStart+7200000L,"2時間"),false,"cannot restart active strict window");
    eq(GroupRules.strictRemainingMs(p),3600000L,"monotonic remaining time");
    clock.wall+=86400000L*365L; // manually advance device clock by 1 year
    eq(GroupRules.strict(p),true,"clock forward does not unlock");
    clock.wall-=86400000L*365L;
    eq(GroupRules.strict(p),true,"clock backward does not unlock");
    clock.elapsed+=123000L;
    eq(GroupRules.strictRemainingMs(p),3477000L,"monotonic countdown advances");
    clock.elapsed-=123000L;
    eq(GroupRules.strict(p),true,"strict mode enabled");
    eq(GroupRules.setEnabled(p,GroupRules.DEFAULT,false),false,"cannot disable default under strict");
    eq(GroupRules.setEnabled(p,a1,false),false,"cannot disable active group");
    eq(GroupRules.setEnabled(p,paused,true),true,"can activate paused group");
    eq(GroupRules.setEnabled(p,paused,false),false,"cannot deactivate after activation");
    eq(GroupRules.configure(p,a1,"漫画・動画",GroupRules.DAILY,30),false,"cannot extend time");
    eq(GroupRules.minutes(p,a1),15,"reject keeps original time");
    eq(GroupRules.configure(p,a1,"漫画・動画",GroupRules.DAILY,5),true,"can shorten time");
    eq(GroupRules.configure(p,a1,"漫画・動画",GroupRules.BLOCK,5),true,"can strengthen to block");
    eq(GroupRules.configure(p,a1,"漫画・動画",GroupRules.DAILY,1),false,"cannot relax full block");
    eq(GroupRules.assignApp(p,a1,"com.example.blocked"),true,"can add app to strict group");
    eq(GroupRules.assignApp(p,weak,"com.example.blocked"),false,"cannot move to weaker group");
    eq(GroupRules.removeAppFromGroup(p,a1,"com.example.blocked"),false,"cannot remove app from group");
    eq(GroupRules.disableApp(p,"com.example.blocked"),false,"cannot disable app protection");
    eq(GroupRules.assignDomain(p,a1,"youtube.com"),true,"can add protected site to strict group");
    eq(GroupRules.assignDomain(p,weak,"m.youtube.com"),false,"cannot override parent with weaker site rule");
    eq(GroupRules.assignDomain(p,a1,"example.org"),true,"can add custom site");
    eq(GroupRules.assignDomain(p,weak,"m.example.org"),false,"cannot override protected custom subdomain");
    eq(GroupRules.removeDomain(p,"example.org"),false,"cannot remove protected site");
    eq(GroupRules.removeDomainFromGroup(p,a1,"example.org"),false,"cannot move domain back");
    eq(GroupRules.delete(p,a1),false,"cannot delete strict group");
    eq(GroupRules.ids(p).contains(a1),true,"strict group remains");
    eq(GroupRules.configure(p,weak,"自由時間",GroupRules.DAILY,20),true,"weaker group can tighten");
    eq(GroupRules.assignKeyword(p,a1,"anime"),true,"keyword attached to entertainment group");
    eq(GroupRules.keywordGroup(p,"Best Anime episode"),a1,"keyword is case insensitive");
    eq(GroupRules.keywordGroup(p,"geography"),null,"unrelated query does not match");
    eq(GroupRules.assignKeyword(p,weak,"anime"),false,"strict mode denies moving keyword to weaker group");
    eq(GroupRules.removeKeyword(p,"anime"),false,"strict mode denies removing keyword");
    clock.boot++;
    clock.elapsed=1300;
    clock.wall=strictStart+7200000L; // future device clock after reboot
    eq(GroupRules.strict(p),true,"reboot with clock forward cannot unlock");
    eq(GroupRules.strictRemainingMs(p),-1L,"no trusted time after reboot");
    clock.network=strictStart+3000000L;
    eq(GroupRules.strict(p),true,"network time before deadline retains lock");
    clock.network=strictStart+3600000L;
    eq(GroupRules.strict(p),false,"network time at deadline unlocks after reboot");
    eq(GroupRules.strictUntil(p),0L,"expired deadline removed");
    eq(p.getBoolean("strict_mode",true),false,"expired lock flag cleared");
    eq(GroupRules.configure(p,a1,"漫画・動画",GroupRules.DAILY,30),true,"policy can relax after expiry");
    eq(GroupRules.mode(p,a1),GroupRules.DAILY,"strong lock can be relaxed after expiry");
    eq(GroupRules.setEnabled(p,a1,false),true,"group can be disabled after expiry");
    clock.network=-1;
    eq(GroupRules.enableStrictForDuration(p,86400000L,strictStart+86400000L,"1日"),true,"can re-enable after expiry");
    eq(GroupRules.strict(p),true,"restarted strict mode active");
    eq(GroupRules.strictRemainingMs(p),86400000L,"new lock monotonic remaining");
    clock.elapsed+=86400000L-1;
    eq(GroupRules.strict(p),true,"still locked 1 ms before monotonic expiry");
    clock.elapsed+=1;
    eq(GroupRules.strict(p),false,"monotonic clock expiry without wall clock");
    eq(GroupRules.configure(p,a1,"漫画・動画",GroupRules.DAILY,90),true,"expired lock permits relaxation");
    eq(GroupRules.enableStrictForDuration(p,86400000L,strictStart+86400000L,"1日"),true,"can restart after monotonic expiry");
    p.edit().putBoolean("strict_mode",true).remove("strict_until_ms").commit();
    eq(GroupRules.strict(p),true,"legacy indefinite lock remains respected");
    p.edit().putLong("strict_until_ms",strictStart+86400000L)
      .remove("strict_started_elapsed").remove("strict_duration_elapsed")
      .remove("strict_boot_count").commit();
    clock.network=-1;
    clock.wall=strictStart+86400000L*365;
    eq(GroupRules.strict(p),true,"legacy timed lock not bypassed by clock forward");
    clock.network=strictStart+86400000L;
    eq(GroupRules.strict(p),false,"legacy timed lock can use independent network time");
  }
}
""")
    classes=tmp/"classes"
    classes.mkdir()
    src=root/"app/src/main/java/jp/personal/entertainmentguard"
    files=[tmp/"android/content/SharedPreferences.java",src/"GroupRules.java",src/"CooldownPolicy.java",src/"CooldownSettings.java",src/"NewAppRules.java",src/"UsageLimit.java",src/"Classifier.java",src/"AppRules.java",src/"RestrictionTargets.java",tmp/"jp/personal/entertainmentguard/GroupRulesTest.java"]
    subprocess.run(["javac","-d",str(classes)]+list(map(str,files)),check=True)
    subprocess.run(["java","-cp",str(classes),"jp.personal.entertainmentguard.GroupRulesTest"],check=True)
