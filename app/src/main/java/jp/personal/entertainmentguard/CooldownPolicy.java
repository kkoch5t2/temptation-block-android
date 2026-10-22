package jp.personal.entertainmentguard;

/** Time-based delay on attempting to open a blocked target. */
public final class CooldownPolicy {
  private CooldownPolicy(){}
  public static final int DEFAULT_SECONDS=30;
  private static final int[] OPTIONS={0,5,10,30,60,120,150,180};
  public static int[] secondOptions(){return OPTIONS.clone();}
  public static String label(int seconds){return seconds==0?"なし":seconds+"秒";}
  public static boolean validSeconds(int seconds){
    for(int option:OPTIONS)if(option==seconds)return true;
    return false;
  }
  public static long deadline(long nowMs){return deadline(nowMs,DEFAULT_SECONDS);}
  public static long deadline(long nowMs,int seconds){
    if(!validSeconds(seconds))throw new IllegalArgumentException("Invalid cooldown duration");
    return nowMs+seconds*1000L;
  }
  public static boolean waiting(long nowMs,long untilMs){
    return untilMs>0 && nowMs<untilMs;
  }
  public static int remainingSeconds(long nowMs,long untilMs){
    return (int)Math.max(0,(untilMs-nowMs+999)/1000);
  }
}
