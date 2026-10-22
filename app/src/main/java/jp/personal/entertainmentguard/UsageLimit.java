package jp.personal.entertainmentguard;

/** Cumulative daily entertainment allowance, independent from the 30s cooldown. */
public final class UsageLimit {
  private UsageLimit(){}
  public static final int[] MINUTE_OPTIONS={1,3,5,10,15,30,60};
  public static boolean validMinutes(int minutes){
    for(int n:MINUTE_OPTIONS)if(n==minutes)return true;
    return false;
  }
  public static long accrue(long usedMs,long elapsedMs,long budgetMs){
    long safeBudget=Math.max(0,budgetMs);
    long safeUsed=Math.max(0,Math.min(usedMs,safeBudget));
    long safeElapsed=Math.max(0,Math.min(elapsedMs,2000));
    return Math.min(safeBudget,safeUsed+safeElapsed);
  }
  public static boolean isExhausted(long budgetMs,long usedMs){
    return budgetMs<=0||usedMs>=budgetMs;
  }
  public static int remainingSeconds(long budgetMs,long usedMs){
    long left=Math.max(0,budgetMs-Math.max(0,usedMs));
    return (int)((left+999)/1000);
  }
  public static String formatRemaining(long budgetMs,long usedMs){
    int seconds=remainingSeconds(budgetMs,usedMs);
    return (seconds/3600)+"時間"+((seconds%3600)/60)+"分"+(seconds%60)+"秒";
  }
}
