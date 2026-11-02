package jp.personal.entertainmentguard;

import java.util.LinkedHashSet;
import java.util.Objects;

/** Pure switching rules. A pause is owned only when this feature disabled the service. */
public final class CompatibilitySwitchPolicy {

  public static final long MAX_PAUSE_MS=5*60*1000L;
  public enum Action { NONE, PAUSE, RESTORE }
  private boolean waitForExit;
  public void waitForExit(){waitForExit=true;}
  private java.util.Set<String> targets=java.util.Collections.emptySet();
  public CompatibilitySwitchPolicy(boolean recoveredPause,java.util.Collection<String> values){
    waitForExit=recoveredPause;setTargets(values);
  }
  public void setTargets(java.util.Collection<String> values){
    targets=values==null?java.util.Collections.emptySet():new java.util.HashSet<>(values);
  }
  public Action decide(boolean enabled,boolean ready,boolean interactive,String foreground,
      boolean ownEnabled,boolean suspended,long elapsed,long pauseStart){
    boolean payment=interactive&&foreground!=null&&targets.contains(foreground);
    if(!payment)waitForExit=false;
    boolean expired=suspended&&(pauseStart<0||elapsed<pauseStart||elapsed-pauseStart>=MAX_PAUSE_MS);
    if(suspended&&(!enabled||!ready||!payment||expired)){
      if(expired)waitForExit=true;
      return Action.RESTORE;
    }
    if(enabled&&ready&&payment&&!suspended&&ownEnabled&&!waitForExit)return Action.PAUSE;
    return Action.NONE;
  }
  public static String foregroundDuringLaunch(boolean interactive,String pkg,String activity,
      String own,long elapsed,long deadline,String target,java.util.Set<String> targets){
    boolean valid=interactive&&target!=null&&targets.contains(target)&&deadline>elapsed&&deadline-elapsed<=3000;
    if(valid&&(pkg==null||(own.equals(pkg)&&(own+".CompatibilityLaunchActivity").equals(activity))))
      return target;
    return pkg;
  }
  private static String canonical(String value){
    int slash=value.indexOf('/');
    if(slash<0)return value;
    String pkg=value.substring(0,slash),name=value.substring(slash+1);
    return pkg+"/"+(name.startsWith(".")?pkg+name:name);
  }
  public static boolean contains(String value,String own){
    if(value==null)return false;
    for(String token:value.split(":"))if(canonical(token).equals(canonical(own)))return true;
    return false;
  }
  public static String editServices(String value,String own,boolean enabled){
    LinkedHashSet<String> result=new LinkedHashSet<>();
    if(value!=null)for(String token:value.split(":")){
      if(!token.isEmpty()&&!token.equals("null")&&!canonical(token).equals(canonical(own)))result.add(token);
    }
    if(enabled)result.add(own);
    return String.join(":",result);
  }
  /** Tracks activity ordering without persisting app names or usage history. */
  public static final class Foreground {
    private String pkg,activity;
    public void resume(String p,String a){pkg=p;activity=a;}
    public void pause(String p,String a){
      if(Objects.equals(pkg,p)&&Objects.equals(activity,a)){pkg=null;activity=null;}
    }
    public String current(){return pkg;}
    public String activity(){return activity;}
    public void clear(){pkg=null;activity=null;}
  }
}
